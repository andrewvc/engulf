(ns parbench.benchmark
  (:require [parbench.runner :as runner]
            [parbench.utils :as utils]
            [clojure.tools.logging :as log])
  (:use clojure.tools.logging
        noir-async.utils
        lamina.core
        [parbench.utils :only [send-bench-msg]]
        [parbench.url-worker :only [work create-single-url-worker]]
        [parbench.recorder :only [create-recorder
                                  record-result
                                  record-error
                                  record-start
                                  processed-stats
                                  record-end]]))

(defprotocol Benchmarkable
  (start [this])
  (start [this])
  (stop [this])
  (init-run [this])
  (increment-and-check-run-count [this])
  (check-result-recordability? [this])
  (receive-result [this worker-id data])
  (receive-error [this worker-id err])
  (broadcast-at-interval [this millis]))
  
(defrecord Benchmark
  [state max-runs run-count workers recorder output-ch broadcast-task]
  Benchmarkable
  
  (start [this]
    (if (not (compare-and-set! state :initialized :started))
      (io! (log/warn (str "Could not start from: @state")))
      (do
        (record-start recorder)
        (doseq [worker @workers]
          (work worker))
        (compare-and-set! broadcast-task nil
                          (broadcast-at-interval this 200)))))

  (stop [this]
    (println "Stopping run. " run-count)
    (if (not (compare-and-set! state :started :stopped))
      (log/warn "Could not stop run, not yet started")
      (do
        (record-end recorder)
        (doseq [worker @workers]
          (compare-and-set! (:state worker) :started :stopped)))))
  
  (increment-and-check-run-count [this]
    (dosync
      (if (>= @run-count max-runs)
        :over
        (if (= max-runs (alter run-count inc))
          :thresh
          :under))))                    
  
  (check-result-recordability? [this]
    (when (= @state :started)
      (let [thresh-status (increment-and-check-run-count this)]
        (cond (= :thresh thresh-status) (do (stop this) true)
              (= :under  thresh-status) true
              :else                     false))))
  
  (receive-result [this worker-id result]
    (when (check-result-recordability? this)
      (record-result recorder worker-id result)))

  (receive-error [this worker-id err]
    (log/warn err)
    (when (check-result-recordability? this)
      (record-error recorder worker-id err)))
           
  (broadcast-at-interval [this millis]
   (set-interval millis
                 (fn []
                   (enqueue output-ch {:dtype "state" :data @state})
                   (try
                     (send-bench-msg output-ch :stats (processed-stats recorder))
                     (catch Exception e
                       (.printStackTrace e)))))))

(defn create-workers-for-benchmark
  "Creates a vector of workers instantiated via worker-fn, which
   must be  afunction that takes a worker-id, a success handler and a failure
   handler"
  [worker-fn benchmark worker-count]
  (compare-and-set! (:workers benchmark) nil
   (vec (map (fn [worker-id]
               (worker-fn worker-id
                          #(receive-result benchmark worker-id %1)
                          #(receive-error  benchmark worker-id %1)))
             (range worker-count)))))

(defn create-benchmark
 "Create a new Benchmark record. This encapsulates the full benchmark state"
 [worker-count max-runs worker-fn]
 (let [recorder (create-recorder)
       benchmark (Benchmark. (atom :initialized) 
                             max-runs
                             (ref 0)     ; run-count
                             (atom nil)   ; workers
                             recorder
                             (channel)   ; output ch
                             (atom nil))] ; broadcast-task
   (create-workers-for-benchmark worker-fn benchmark worker-count)
   benchmark))
     
(defn create-single-url-benchmark
  "Create a new benchmark. You must call start on this to begin"
  [url concurrency requests]
  (let [worker-fn (partial create-single-url-worker :ning url)]
    (create-benchmark concurrency requests worker-fn)))