(ns engulf.job-manager
  (require [engulf.jobs.http-benchmark :as htb])
  (:import java.util.UUID))

(def jobs (ref {}))

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
     (alter jobs assoc (:uuid @j) j)))
  job)

(defn record-results
  [uuid results]
  (send (:results  (get jobs uuid))
        (fn rec-res-reduce [] (htb/result-reduce results))))