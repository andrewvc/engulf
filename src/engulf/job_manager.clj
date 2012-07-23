(ns engulf.job-manager
  (require [engulf.formula :as forumla]
           [engulf.utils :as utils]
           [lamina.core :as lc])
  (:import java.util.UUID))

(def emitter (lc/permanent-channel))
(lc/ground emitter)

(def jobs (ref {}))

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
  (dissoc job :results))

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
       (ref-set current-job nil)))))