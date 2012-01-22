o(ns parbench.recorder
  (:require [clojure.tools.logging :as log])
  (:use noir-async.utils
        aleph.http
        lamina.core))
 
(defn median [s]
  (nth s (int (/ (count s) 2))))

(defprotocol Recordable
  "Recording protocol"
  (broadcast-data [this data-type data] "Enqueues data to the output channel")
  (check-recordable [this] "Check if this result should should be recorded")
  (record-work-success [this worker-id data] "record a successful work run")
  (record-work-failure [this worker-id err] "record a failed work run")
  (record-runtime [this worker-id run-id runtime])
  (processed-agg-stats [this])
  (runtime-agg-stats [this statsd])
  (broadcast-agg-stats [this]))

(defrecord StandardRecorder [started-at ended-at max-runs stats output-ch]
  Recordable

  (broadcast-data [this data-type data]
    (io! (enqueue output-ch {:dtype data-type :data data})))
   
  (broadcast-agg-stats [this]
    (let [pstats (processed-agg-stats this)]
      (broadcast-data this :agg pstats)
      ))
   
  (processed-agg-stats [this]
    (let [statsd @stats]
      ;(println  (runtime-agg-stats this statsd))
      (merge
        (runtime-agg-stats this statsd)
        (let [runtime (- (or @ended-at (System/currentTimeMillis)) @started-at)]
          {:runtime runtime
           :runs-sec (/ (:runs-total statsd) (/ runtime 1000))})
        (select-keys statsd
          [:runs-total :runs-succeeded :runs-failed
           :avg-runtime-by-start-time
           :response-code-counts]))))
   
  (runtime-agg-stats [this statsd]
    (let [runtimes (sort (:runtimes statsd))
          rt-count (count (vec runtimes))]
      {:median-runtime
         (when (> rt-count 5) (nth runtimes (/ rt-count 2)))
       :runtime-percentiles
         (let [partn (let [n (int (/ rt-count 100))] (if (> n 1) n 1))]
           (map-indexed
             (fn [idx group]
               (let [min (first group)
                     max (last group)
                     avg (median group)]
                 {:min min :max max :avg avg
                  :idx idx
                  :count (count group)}))
             (partition-all partn runtimes)))
       }))
   
  (check-recordable [this]
    (dosync 
      (if (stopped?)
            false
            (do (when (>= (:runs-total (ensure stats)) (- max-runs 1))
                  (stop))
                true))))
   
  (record-runtime [this worker-id run-id runtime]
    (alter stats update-in [:runtimes] #(conj %1 runtime)))
   
  (record-work-success [this worker-id data]
    (dosync
      (when (check-recordable this)
        (let [rstart (:req-start data)]
          (alter stats update-in [:avg-runtime-by-start-time (int (/ rstart 1000))]
                 (fn [bucket]
                   (let [rcount (+ 1 (get bucket :count 0))
                         total  (+ (:runtime data) (get bucket :total 0))]
                     (merge bucket
                            {:count rcount
                             :total total
                             :avg   (int (/ total rcount))})))))
        (record-runtime this worker-id (:run-id data) (:runtime data))
        (alter stats update-in [:response-code-counts]
               increment-keys (:status (:response data)))
        (alter stats increment-keys :runs-succeeded :runs-total))))
   
  (record-work-failure [this worker-id err]
    (.printStackTrace err)
    (broadcast-data this :err err)
    (dosync
      (when (check-recordable this)
              (alter stats increment-keys :runs-failed :runs-total)))))

(defn- empty-stats []
  {:runs-total 0
   :runs-succeeded 0
   :runs-failed 0
   :response-code-counts {}
   :avg-runtime-by-start-time {}
   :runtimes []})

(defn create-standard-recorder [max-runs]
  (let [output-ch (channel)]
    (receive-all output-ch (fn [_])) ; keep it from backing up
  (StandardRecorder. (ref (System/currentTimeMillis))
                     (ref nil)
                     max-runs
                     (ref (empty-stats)))
                     output-ch))
