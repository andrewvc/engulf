(ns parbench.testy
  (:require [parbench.runner :as runner]
            [parbench.utils :as utils]
            [clojure.tools.logging :as log])
  (:use clojure.tools.logging
        noir-async.utils
        lamina.core))

(defprotocol Benchmarkable
  (start [this])
  (stop [this])
  (started? [this])
  (stopped? [this])
  (set-started [this])
  (broadcast-at-interval [this millis]))


  
(defrecord Benchmark [state work max-runs run-count recorder output-ch]
  (start [this]
	  (if (init-run worker-count max-runs)
	    (run-workers init-worker-fn worker-count)
	    (io! (log/warn "Could not start, already started"))))

  )
