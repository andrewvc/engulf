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
  (record-start [this])
  (record-end [this])
  (record-result [this worker-id data])
  (record-error [this worker-id err]))

(defrecord StandardRecorder [started-at ended-at stats]
  Recordable

  (processed-stats [this]
    (let [statsd @stats]
      ;(println  (runtime-agg-stats this statsd))
      (merge
       (runtime-agg-stats statsd
                          @started-at
                          (or @ended-at (System/currentTimeMillis)))
        (select-keys statsd
          [:runs-total :runs-succeeded :runs-failed
           :avg-runtime-by-start-time
           :response-code-counts]))))
  (record-end [this]
    (dosync (ref-set ended-at (System/currentTimeMillis))))

  (record-start [this]
    (dosync
      (when (not @started-at)
            (ref-set started-at (System/currentTimeMillis)))))
  
  (record-result [this worker-id data]
   ;There should probably be a separate start method...
   (dosync
     (let [statsd @stats]
       (dorun (map #(%1 stats data)
            [record-avg-runtime-by-start-time
             record-runtime
             record-response-code-count
             record-run-succeeded])))))
  
  (record-error [this worker-id err]
    (dosync
      (alter stats increment-keys :runs-failed))))                                                
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

(defn create-recorder []
  (StandardRecorder. (ref nil)
                     (ref nil)
                     (ref (empty-stats))))