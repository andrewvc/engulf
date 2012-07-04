(ns engulf.formulas.http-benchmark
  (:require [lamina.core :as lc]
            [clojure.tools.logging :as log])
  (:use engulf.formula
        [aleph.http :only [http-client http-request]])
  (:import fastPercentiles.PercentileRecorder))

(defn increment-keys
  "Given a map and a list of keys, this will return an identical map with those keys
   with those keys incremented.
    
   Keys with a null value will be set to 1."
  [src-map & xs]
  (into src-map (map #(vector %1 (inc (get src-map %1 0))) xs)))

(defn empty-aggregation
  [params]
  {:runtime nil
   :runs-sec nil
   :runs-total 0
   :runs-succeeded 0
   :runs-failed 0
   :response-code-counts {}
   :by-start-time {}
   :runtime-percentiles-recorder (PercentileRecorder. (or (:timeout params) 10000))})

(defn run-request
  [params callback]
  (let [res (lc/success-result true)] ; (http-request {:url (:url params)})
    (lc/on-realized res #(callback %1) #(callback %1))))

(defn aggregate
  [params results]
  (-> (empty-aggregation params)
      (assoc :runs-total (count results))))

(defprotocol IHttpBenchmark
  (run-repeatedly [this]))

(defrecord HttpBenchmark [state params res-ch]
  IHttpBenchmark
  (run-repeatedly [this]
    (run-request
     params
     (fn req-resp [res]
       (when (= @state :started) ; Discard results and don't recur when stopped
         (lc/enqueue res-ch res)
         (run-repeatedly this)))))
  Formula
  (start-relay [this]
    
    )
  (start-edge [this]
    (when (compare-and-set! state :initialized :started)
      (dotimes [t (:concurrency params)] (run-repeatedly this))
      (lc/map* (lc/partition-every 250 res-ch) (partial params aggregate))))
  (stop [this]
    (reset! state :stopped)))

(register :http-benchmark #(HttpBenchmark. (atom :initialized) %1 (lc/channel)))
