(ns engulf.job-manager
  (:require [engulf.formula :as forumla]
            [engulf.utils :as utils]
            [lamina.core :as lc])
  (:use korma.db korma.core)
  (:import java.util.UUID))

(defdb db {:classname "org.sqlite.JDBC"
           :subprotocol "sqlite"
           :subname "engulf.sqlite3"})

(defentity jobs)

;;(insert jobs        (values         {:uuid (utils/rand-uuid-str)          :formula_name "test-formula-name"          :started_at (utils/now)          :ended_at  123          :params "test-params"          }))

;;(println "JOBS: " (select jobs (limit 10)))

(def emitter (lc/permanent-channel))
(lc/ground emitter)

(def current-job (ref nil))

(defn job-snapshot
  [j]
  (assoc j :results @(:results j)))

(defn current-job-snapshot
  "A version of the current job with all concurrency references dereferenced"
  []
  (when-let [j @current-job] (job-snapshot j)))

(defn serializable
  [job]
  (assoc job :results @(:results job)))

(defn record-results
  "Given a job and a channel, will store the latest value of the channel in the results for that job"
  [job ch]
  (lc/receive-all ch #(reset! (:results job) %)))

(defn job
  [formula-name params]
   {:uuid (utils/rand-uuid-str)
    :formula-name formula-name
    :started-at (utils/now)
    :ended-at   nil
    :params params
    :results (atom nil)})

(defn register-job
  [formula-name params]
  (let [j (job formula-name params)]
    (dosync
     (ref-set current-job j)
     j)))

(defn stop-job
  []
  (let [stop-time (System/currentTimeMillis)]
    (dosync
     (when @current-job
       (alter current-job assoc :ended-at stop-time)
       (ref-set current-job nil)
       @current-job))))