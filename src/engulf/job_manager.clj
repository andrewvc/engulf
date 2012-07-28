(ns engulf.job-manager
  (:require [engulf.formula :as forumla]
            [engulf.database :as database]
            [engulf.settings :as settings]
            [engulf.utils :as utils]
            [cheshire.core :as json]
            [clojure.java.jdbc :as jdbc]
            [clojure.walk :as walk]
            [lamina.core :as lc])
  (:use korma.db korma.core))

(def current-job (ref nil))

(defn job
  "Creates a new job map"
  [formula-name params]
  {:uuid (utils/rand-uuid-str)
   :formula-name formula-name
   :started-at (utils/now)
   :ended-at   nil
   :params params})

(defn record-result
  "Record an individual job result in the DB"
  [job result]
  (insert database/results
          (values {:uuid (utils/rand-uuid-str)
                   :job-uuid (:uuid job)
                   :value result
                   :created-at (utils/now)})))

(defn record-results
  "Given a job and a channel, will store the latest value of the channel in the results for that job"
  [job ch]
  (lc/receive-all ch (partial record-result job)))

(defn register-job
  "Registers a new job and marks it as started"
  [formula-name params]
  (let [j (job formula-name params)]
    (insert database/jobs (values j))
    (dosync
     (ref-set current-job j)
     j)))

(defn stop-job
  "Marks the currently running job as stopped"
  []
  (when-let [job (dosync
                  (when-let [job @current-job]
                    (ref-set current-job nil)
                    job))]
    (update database/jobs
            (set-fields {:ended-at (System/currentTimeMillis)})
            (where {:uuid (:uuid job)}))))

(defn find-job-by-uuid
  [uuid]
  (first (select database/jobs
                 (where {:uuid uuid})
                 (limit 1)
                 (with database/results))))

(defn paginated-jobs
  [page per-page order-direction]
  (select database/jobs
          (order :started-at order-direction)
          (limit per-page)
          (offset (* per-page (- page 1)))))