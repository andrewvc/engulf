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

(defn job
  "Creates a new job map"
  [formula-name params]
  {:uuid (utils/rand-uuid-str)
   :formula-name formula-name
   :started-at (utils/now)
   :ended-at   nil
   :params params})

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
  (let [stop-time (System/currentTimeMillis)]
    (dosync
     (when @current-job
       (alter current-job assoc :ended-at stop-time)
       (ref-set current-job nil)
       @current-job))))