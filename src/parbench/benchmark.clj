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
		      recorder output-ch]
  (start [this]
	  (if (init-run worker-count max-runs)
	    (run-workers init-worker-fn worker-count)
	    (io! (log/warn "Could not start, already started"))))

  (stop [this]
	(dosync (ref-set state :stopped)
		(ref-set (:ended-at @recorder) (System/currentTimeMillis))))

  (started? [this] (= :started @state))
  (stopped? [this] (= :stopped @state))
  
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
	   (doseq [worker @workers] (work

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
 [worker-count worker-fn]
 (let [output-ch (channel)]
   (receive-all output-ch (fn [_])) ; keep the channel empty if no listeners
   (Benchmark. (ref :started)
             worker-count worker-fn []
             recorder output-ch)))
             
(defn start-single-url [url concurrency requests]
  (start (partial create-single-url-worker url) concurrency requests))
