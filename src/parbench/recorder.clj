(ns parbench.recorder
  (:require [clojure.tools.logging :as log])
  (:use [parbench.utils :only [median percentiles increment-keys]]
        lamina.core))
 
(defn record-avg-runtime-by-start-time [stats {:keys [req-start runtime]}]
  (alter stats
         update-in
         [:avg-runtime-by-start-time (int (/ req-start 1000))]
         (fn [bucket]
             (let [rcount (+ 1 (get bucket :count 0))
                   total  (+ runtime (get bucket :total 0))]
                   (merge bucket
                          {:count rcount
                           :total total
                           :avg   (int (/ total rcount))})))))

(defn record-runtime [stats {rt :runtime}]
  (alter stats update-in [:runtimes] #(conj %1 rt)))

(defn record-response-code-count [stats {{resp-code :status} :response}]
  (alter stats update-in [:response-code-counts] increment-keys resp-code))

(defn record-run-succeeded [stats data]
  (alter stats increment-keys :runs-succeeded :runs-total))

(defn runtime-agg-stats [{:keys [runtimes runs-total]} started-at ended-at]
  (let [runtime (- ended-at started-at)
        sorted-runtimes (vec (sort runtimes))]
    {:runtime runtime
     :runs-sec (/ runs-total (/ runtime 1000))
     :median-runtime (median sorted-runtimes)
     :runtime-percentiles (percentiles sorted-runtimes)}))

(defprotocol Recordable
  "Recording protocol"
  (processed-stats [this])
  (record-work [this worker-id data]))

(defrecord StandardRecorder [started-at ended-at max-runs stats]
  Recordable

  (processed-stats [this]
    (let [statsd @stats]
      ;(println  (runtime-agg-stats this statsd))
      (merge
       (runtime-agg-stats statsd
                          (or @ended-at (System/currentTimeMillis))
                          @started-at)
        (select-keys statsd
          [:runs-total :runs-succeeded :runs-failed
           :avg-runtime-by-start-time
           :response-code-counts]))))

  (record-work
   [this worker-id data]
   (dosync
    (let [statsd @stats]
      (map #(%1 stats data)
           [record-avg-runtime-by-start-time
            record-runtime
            record-response-code-count
            record-run-succeeded])))))
                  
(defn- empty-stats []
  {:started-at nil
   :ended-at nil
   :runtime nil
   :runs-sec nil
   :runs-total 0
   :runs-succeeded 0
   :runs-failed 0
   :response-code-counts {}
   :avg-runtime-by-start-time {}
   :runtimes []})

(defn create-standard-recorder [max-runs]
  (StandardRecorder. (ref (System/currentTimeMillis))
                     (ref nil)
                     max-runs
                     (ref (empty-stats))))