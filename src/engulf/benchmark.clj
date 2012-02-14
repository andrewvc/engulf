(ns engulf.benchmark
  (:require [engulf.runner :as runner]
            [engulf.utils :as utils]
            [clojure.tools.logging :as log])
  (:use clojure.tools.logging
        noir-async.utils
        lamina.core
        [engulf.utils :only [send-bench-msg]]
        [engulf.url-worker :only [work create-single-url-worker]]
        [engulf.recorder :only [create-recorder
                                  record-result
                                  record-error
                                  record-start
                                  processed-stats
                                record-end]])
  (:import java.util.concurrent.atomic.AtomicLong))

(def current-benchmark (ref nil))

(defprotocol Benchmarkable
  (start [this] "Start the benchmark")
  (stop [this] "Stop the benchmark")
  (increment-and-check-run-count [this] "Internal use only")
  (check-result-recordability? [this] "Internal use only")
  (receive-result [this worker-id data] "Handle a worker result")
  (receive-error [this worker-id err] "Handle a worker error")
  (broadcast-state [this] "Enqueues the current state/stats on the output ch")
  (broadcast-at-interval [this millis])
  (stats [this] "Returns processed stats"))
  
(defrecord Benchmark
  [state max-runs ^AtomicLong run-count workers recorder output-ch broadcast-task]
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
    (println "Stopping run at: " max-runs " runs.")
    (when (compare-and-set! state :started :stopped)
      (do
        (record-end recorder)
        (doseq [worker @workers]
          (compare-and-set! (:state worker) :started :stopped))
        (broadcast-state this))))
  
  (increment-and-check-run-count [this]
    (let [n (.incrementAndGet run-count)]
      (cond
       (> max-runs n) :under
       (= max-runs n) :thresh
       :else          :over)))
        
  (check-result-recordability? [this]
    (when (= @state :started)
      (let [thresh-status (increment-and-check-run-count this)]
        (condp = thresh-status
            :thresh (do (stop this) true)
            :under  true
            :over   false))))
  
  (receive-result [this worker-id result]
    (when (check-result-recordability? this)
      (record-result recorder worker-id result)))

  (receive-error [this worker-id err]
    (log/warn err)
    (when (check-result-recordability? this)
      (record-error recorder worker-id err)))

  (broadcast-state [this]
    (send-bench-msg output-ch :state @state)
    (send-bench-msg output-ch :stats (stats this)))

  (broadcast-at-interval [this millis]
   (set-interval millis #(broadcast-state this)))
         
  (stats [this]
    (processed-stats recorder)))

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
                             (AtomicLong.)     ; run-count
                             (atom nil)   ; workers
                             recorder
                             (permanent-channel)   ; output ch
                             (atom nil))] ; broadcast-task
   (receive-all (:output-ch benchmark) (fn [_] )) ; Keep output ch drained
   (create-workers-for-benchmark worker-fn benchmark worker-count)
   benchmark))
     
(defn create-single-url-benchmark
  "Create a new benchmark. You must call start on this to begin"
  [url concurrency requests]
  (let [worker-fn (partial create-single-url-worker :ning url)]
    (create-benchmark concurrency requests worker-fn)))

(defn run-new-benchmark
  "Attempt to run a new benchmark"
  [url concurrency requests]
  (let [benchmarker (create-single-url-benchmark url concurrency requests)]
    (dosync
     ;; Cancel the current broadcast task
     ;; TODO: refactor the codebase to not stream results constantly
     ;; so this won't be necessary 
     (when-let [b @current-benchmark]
       (when-let [bt @(:broadcast-task b)]
         (.cancel bt)))
     (ref-set current-benchmark benchmarker)))
    (start @current-benchmark)
    @current-benchmark)

(defn stop-current-benchmark
  "Stop current benchmark if it exists"
  []
  (when-let [b @current-benchmark] (stop b)))