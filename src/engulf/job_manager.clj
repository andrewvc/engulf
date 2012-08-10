(ns engulf.job-manager
  (:require [engulf.formula :as forumla]
            [engulf.database :as database]
            [engulf.settings :as settings]
            [engulf.control :as ctrl]
            [engulf.utils :as utils]
            [engulf.formula :as formula]
            engulf.formulas.http-benchmark
            [cheshire.core :as json]
            [clojure.java.jdbc :as jdbc]
            [clojure.walk :as walk]
            [clojure.tools.logging :as log]
            [lamina.core :as lc])
  (:use korma.db korma.core))

(def emitter (lc/channel* :permanent? true :grounded? true))

(def current-job (ref nil))

(defn job
  "Creates a new job map"
  [formula-name title notes params]
  {:uuid (utils/rand-uuid-str)
   :formula-name formula-name
   :started-at (utils/now)
   :ended-at   nil
   :title title
   :notes notes
   :params params})

(defn record-result
  "Record an individual job result in the DB"
  [job result]
  (let [recorded-value {:uuid (utils/rand-uuid-str)
                        :job-uuid (:uuid job)
                        :value result
                        :created-at (utils/now)}]
    (update database/jobs
            (set-fields {:last-result result})
            (where {:uuid (:uuid job)}))
    ;;TODO: We'll enable this again some day...
    ;;(insert database/results (values recorded-value))
    recorded-value))

(defn record-results
  "Given a job and a channel, will store the latest value of the channel in the results for that job"
  [job ch]
  (let [results (lc/grounded-channel)]
    (lc/siphon (lc/map* (partial record-result job) ch) results)
    (lc/on-closed ch #(lc/close results))
    results))

(defn register-job
  "Registers a new job and marks it as started"
  [formula-name title notes params]
  (let [j (job formula-name title notes params)]
    (insert database/jobs (values j))
    (dosync
     (ref-set current-job j)
     j)))

(defn stop-job
  "Marks the currently running job as stopped"
  []
  (ctrl/stop-job)
  (when-let [job (dosync
                  (when-let [job @current-job]
                    (ref-set current-job nil)
                    job))]
    (update database/jobs
            (set-fields {:ended-at (System/currentTimeMillis)})
            (where {:uuid (:uuid job)}))))

(defn start-job
  "Starts the job, returns a stream of results"
  [title notes {formula-name :formula-name :as params}]
  (ctrl/stop-job)

  (when (not formula-name)
    (throw (Exception. "Missing formula name!")))

  (log/info (str "Starting job with params: " params))
  
  ;; Attempt to initialize the formula. This should throw any errors it gets related to invalid params
  (formula/init-job-formula {:formula-name formula-name :params params})
  
  (let [job (register-job formula-name title notes params)
        results-ch (record-results job (:results-ch (ctrl/start-job job)))
        res-msgs (lc/map* (fn [m] {"entity" "system" "name" "result" "body" m}) results-ch)]
    (lc/siphon res-msgs emitter)
    (lc/on-closed results-ch #(stop-job))
    {:job job :results-ch results-ch}))

(defn find-job-by-uuid
  [uuid]
  (first (select database/jobs
                 (where {:uuid uuid})
                 (limit 1)
                 (with database/results))))

(defn delete-job-by-uuid
  [uuid]
  (delete database/jobs (where {:uuid uuid}))
  (delete database/results (where {:job-uuid uuid})))

(defn paginated-jobs
  [page per-page order-direction]
  (select database/jobs
          (order :started-at order-direction)
          (limit per-page)
          (offset (* per-page (- page 1)))))