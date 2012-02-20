(ns engulf.recorder
  (:require [clojure.tools.logging :as log])
  (:use [engulf.utils :only [median percentiles increment-keys]]
        lamina.core)
  (:import fastPercentiles.PercentileRecorder
           fastPercentiles.Percentile))

(defn runs-per-second [runs millis]
  (if (or (= 0 runs) (= 0 millis))
    0
    (/ runs (float (/ millis 1000)))))

(defn format-percentiles
  "Takes a fastPercentiles.Percentile[] and formats it as [{},{},...]"
  [unformatted]
  (vec (map (fn [p]
         {:avg (.getAvg p)
          :min (.getMin p)
          :max (.getMax p)
          :median (.getMedian p)
          :count (.getCount p)
          :total (.getTotal p)})
       unformatted)))

(defn record-avg-runtime-by-start-time
  "Records, how long, on average requests issued in the current time bucket take to return. Requests are quantized into buckets of  1 second."
  [stats {:keys [req-start runtime]}]
  (update-in stats
         [:avg-runtime-by-start-time (long (/ req-start 1000))]
         (fn [bucket]
             (let [rcount (+ 1 (get bucket :count 0))
                   total  (+ runtime (get bucket :total 0))]
                   (merge bucket
                          {:count rcount
                           :total total
                           :avg   (long (/ total rcount))})))))

(defn record-runtime [stats {:keys [runtime]}]
  (.record (:runtime-percentiles-recorder stats) (int runtime))
  stats)

(defn record-response-code [stats {{resp-code :status} :response}]
  (update-in stats [:response-code-counts] increment-keys resp-code))

(defn record-run-succeeded [stats data]
  (increment-keys stats :runs-succeeded :runs-total))

(defn runtime-agg-stats
  "Analysis for high-level data"
  [{:keys [runtime-percentiles-recorder runs-total]} started-at ended-at]
  (let [runtime (- ended-at started-at)
        percentiles (format-percentiles (.percentiles runtime-percentiles-recorder))]
    {:runtime runtime
     :runs-sec (runs-per-second runs-total runtime)
     :median-runtime (:avg (nth percentiles 50))
     :runtime-percentiles percentiles}))

(defprotocol Recordable
  "Recording protocol"
  (processed-stats [this] "Stats with statistical analyses completed and integrated")
  (record-start [this] "Can be called once. Must be called before receiving data")
  (record-end [this] "Can be called once, ends collection of data")
  (record-result [this worker-id data] "Given a worker-id and the workers results, this records the data")
  (record-error [this worker-id err] "Attributes an error to a specific worker. err should be an Throwable or the like"))

(defrecord StandardRecorder [started-at ended-at stats]
  Recordable

  (processed-stats [this]
    (let [statsd @stats]
      (merge
       (runtime-agg-stats statsd
                          @started-at
                          (or @ended-at (System/currentTimeMillis)))
       (select-keys statsd
                    [:runs-total :runs-succeeded :runs-failed
                     :avg-runtime-by-start-time
                     :response-code-counts]))))
  
  (record-start [this]
    (compare-and-set! started-at nil (System/currentTimeMillis)))

  (record-end [this]
    (compare-and-set! ended-at nil (System/currentTimeMillis)))  
  
  (record-result
   [this worker-id data]
   (send stats ; Stats are asynchronously processed
         (fn [statsd]
           (reduce ; Reduce via each analysis function
            (fn [v stat-fn] (stat-fn v data))
            statsd
            [record-avg-runtime-by-start-time
             record-runtime
             record-response-code
             record-run-succeeded]))))

  (record-error [this worker-id err]
    (send stats increment-keys :runs-failed)) )

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
   :runtime-percentiles-recorder (PercentileRecorder. 100000)})


(defn create-recorder []
  (StandardRecorder. (atom nil)
                     (atom nil)
                     (agent (empty-stats))))
