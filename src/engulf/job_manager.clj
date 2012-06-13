(ns engulf.job-manager
  (require [engulf.jobs.http-benchmark :as htb])
  (:import java.util.UUID))

(def jobs (ref {}))

(def current-job (ref {}))

(defn job
  [type params]
  (ref
   {:uuid (str (UUID/randomUUID))
    :type type
    :started-at (System/currentTimeMillis)
    :ended-at   nil
    :params params
    :results (agent {})}))

(defn register-job
  [type params]
  (let [j (job type params)]
    (dosync
     (alter jobs assoc (:uuid @j) j)
     (ref-set current-job j)))
  job)

(defn stop-job
  [uuid]
  (dosync
   (let [j (get @jobs uuid)]
     (dosync
      (alter current-job assoc :ended-at (System/currentTimeMillis))))))


(defn record-results
  [uuid results]
  (send (:results  (get jobs uuid))
        (fn rec-res-reduce [] (htb/result-reduce results))))