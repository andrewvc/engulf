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
  (stop [this])
  (started? [this])
  (stopped? [this])
  (init-run [this])
  (increment-and-check-run-count [this])
  (receive-result [thi worker-id data])
  (receive-error [this worker-id err])
  (broadcast-at-interval [this millis]))
  
(defrecord Benchmark [state max-runs run-count workers recorder output-ch]
  Benchmarkable
  (start [this]
    (broadcast-at-interval this 200)
    (if (not (init-run this))
        (io! (log/warn "Could not start, already started"))
        (doseq [worker workers]
          (let [{worker-ch :output-ch worker-id :worker-id} worker]
            (receive-all (:output-ch worker)
              (fn [{:keys [dtype data]}]
                (cond (= :worker-result dtype)
                        (receive-result this worker-id data)
                      (= :worker-error dtype)
                        (receive-error this worker-id data)))))
             (work worker))))

  (stop [this]
    (dosync (ref-set state :stopped)
            (record-end recorder)))

  (started? [this] (dosync (= :started (ensure state))))
  (stopped? [this] (not (started? this)))
  
  (init-run [this]
    (dosync
      (if (not= :stopped @state)
	false
	(do (ref-set state :started)
            (record-start recorder)

	    true))))

  (increment-and-check-run-count [this]
    (dosync
      (if (>= (ensure run-count) max-runs)
           :over
         (if (= max-runs (alter run-count inc))
           :thresh
           :under))))
  
  (receive-result
    [this worker-id result]
   ; If we haven't already stopped, increment the run-count. If we do increment it record the actual work
    (dosync
      (when (started? this)
        (let [thresh-status (increment-and-check-run-count this)]
          (record-result recorder worker-id result)
          (when (= :thresh thresh-status)
            (stop this))))))

  (receive-error [this worker-id err] (.printStackTrace err))
           
  (broadcast-at-interval [this millis]
    (set-interval millis
      (fn []
        (enqueue output-ch {:dtype "state" :data @state})
        (try
          (send-bench-msg output-ch :stats (processed-stats recorder))
          (catch Exception e
            (.printStackTrace e)
            (log/fatal e))))))
  )
  
(defn create-benchmark
 "Create a new Benchmark record. This encapsulates the full benchmark state"
 [worker-count max-runs worker-fn]
 (let [output-ch (channel)
       recorder (create-recorder)
       workers (vec (map (fn [worker-id] (worker-fn worker-id recorder))
                         (range worker-count)))]
   (receive-all output-ch (fn [_] (println "OCH: " _))) ; keep the channel empty if no listeners
   (Benchmark. (ref :stopped)
               max-runs (ref 0) workers
               recorder output-ch)))
             
(defn create-single-url-benchmark [url concurrency requests]
  (let [worker-fn (partial create-single-url-worker url)
        benchmark (create-benchmark concurrency requests worker-fn)]
    benchmark))