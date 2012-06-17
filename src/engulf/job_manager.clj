(ns engulf.job-manager
  (require [engulf.formula :as forumla]
           [lamina.core :as lc])
  (:import java.util.UUID))

(def emitter (lc/permanent-channel))
(lc/ground emitter)

(def jobs (ref {}))

(def current-job (ref nil))

(defn job
  [formula-name params]
  (ref
   {:uuid (str (UUID/randomUUID))
    :formula-name formula-name
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
        '(fn rec-res-reduce [] (formula/result-reduce results))))