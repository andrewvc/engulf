(ns parbench.benchmark
  (:require [parbench.runner :as runner]
            [parbench.utils :as utils]
            [clojure.tools.logging :as log])
  (:use clojure.tools.logging
        noir-async.utils
        lamina.core
        [parbench.url-worker :only [work create-single-url-worker]]
        [parbench.recorder :only [create-standard-recorder]]))

(defprotocol Benchmarkable
  (start [this])
  (stop [this])
  (started? [this])
  (stopped? [this])
  (set-started [this])
  (broadcast-at-interval this millis))
  
(defrecord Benchmark [state worker-count worker-fn workers
		      max-runs run-count recorder output-ch]
  (start [this]
	  (if (init-run worker-count max-runs)
	    (run-workers init-worker-fn worker-count)
	    (io! (log/warn "Could not start, already started"))))

  (stop [this]
    (dosync (ref-set state :stopped)
            (record-end recorder)))

  (running? [this] (dosync (= :started (ensure state)))
  (stopped? [this] (not (running? this)))
  
  (init-run [this]
    (dosync
      (if (not= :stopped @state)
	false
	(do (ref-set state :started)
	    (ref-set recorder (create-standard-recorder max-runs))
	    ; Initialize new workers
            (ref-set workers
		     (vec (map (fn [worker-id] (worker-fn worker-id recorder))
			       (range worker-count))))
	    true))))

  (execute [this]
    (doseq [worker @workers]
      (record-work this (work worker))))

  (increment-and-check-run-count
   [this]
   (dosync
     (if (>= (ensure run-count) max-runs)
          :over
        (if (= max-runs (alter run-count inc))
          :thresh
          :under))))                        
  
  (receive-results
    [this results]
   ; If we haven't already stopped, increment the run-count. If we do increment it record the actual work
    (dosync
      (when (running? this)
        (let [thresh-status (increment-and-check-run-count)]
          (record-results recorder results)
          (when (= :thresh thresh-status)
            (stop this))))))
           
  (broadcast-at-interval [this millis]
    (set-interval 200
      (fn []
        (enqueue output-ch {:dtype "state" :data @state})
        (try 
          (broadcast-agg-stats r)
          (catch Exception e
            (.printStackTrace e)
            (log/fatal e))))))
  )
  
(create-benchmark
 "Create a new Benchmark record. This encapsulates the full benchmark state"
 [worker-count max-runs worker-fn]
 (let [output-ch (channel)]
   (receive-all output-ch (fn [_])) ; keep the channel empty if no listeners
   (Benchmark. (ref :started)
             worker-count worker-fn []
             max-runs (ref 0) recorder output-ch)))
             
(defn create-single-url-benchmark [url concurrency requests]
  (start (partial create-single-url-worker url) concurrency requests))

;(def cub (create-single-url-benchmark))