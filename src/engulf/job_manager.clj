(ns engulf.job-manager
  (require [engulf.job :as ejob])
  (:import java.util.UUID))

(def jobs (ref {}))

(def current-job (ref nil))

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
     (ref-set current-job j)
     @j)))

(defn stop-job
  []
  (let [stop-time (System/currentTimeMillis)]
    (dosync
     (when @current-job
       (alter @current-job assoc :ended-at stop-time)
       (ref-set current-job nil)))))

(defn record-results
  [uuid results]
  (send (:results  (get jobs uuid))
        '(fn rec-res-reduce [] (ejob/result-reduce results))))