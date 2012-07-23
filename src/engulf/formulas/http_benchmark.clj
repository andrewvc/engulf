(ns engulf.formulas.http-benchmark
  (:require [lamina.core :as lc]
            [clojure.tools.logging :as log]
            [clojure.set :as cset])
  (:use [engulf.formula :only [Formula register stop]]
        [engulf.utils :only [set-timeout]]
        [aleph.http :only [http-client http-request]]
        [clojure.string :only [lower-case]]
        [clojure.walk :only [keywordize-keys]])
  (:import fastPercentiles.PercentileRecorder
           java.util.concurrent.Executors
           java.util.concurrent.ExecutorService))

(def callbacks-pool (Executors/newSingleThreadExecutor))

(load "http_benchmark_aggregations")
(load "http_benchmark_runner")

(defprotocol IHttpBenchmark
  (run-repeatedly [this ch runner]))

(defrecord HttpBenchmark [state params res-ch mode]
  IHttpBenchmark
  (run-repeatedly [this ch runner]
    (runner
     params
     (fn req-resp [res]
       (when (= @state :started) ; Discard results and don't recur when stopped
         (lc/enqueue ch res)
         (run-repeatedly this ch runner)))))
  Formula
  (start-relay [this ingress]
    (when (compare-and-set! state :initialized :started)
      (when (not (compare-and-set! mode :unknown :relay))
        (throw (Exception. "Attempted to double-change formula mode!")))
      
      ;; Setup quantized reduction pipeline
      (let [reduced (lc/reductions*
                     (fn [initial aggs] (relay-aggregate params initial aggs))
                     (empty-relay-aggregation params)
                     (lc/partition-every 100 ingress))
            jsonified (lc/map* relay-agg-jsonify reduced)]
        (lc/siphon jsonified res-ch))

      ;; Monitor for job end
      (lc/receive-all
       (lc/fork res-ch)
       (fn [results]
         (when (>= (results "runs-total") (:limit params))
           (stop this))))
      
      res-ch))
  (start-edge [this]
    (when (compare-and-set! state :initialized :started)
      (when (not (compare-and-set! mode :unknown :edge))
        (throw (Exception. "Attempted to double-change formula mode!")))
      (let [http-res-ch (lc/channel)
            runner (if (:mock params) run-mock-request run-real-request)]
        ;; Kick off the async workers
        (dotimes [t (:concurrency params)]
          (run-repeatedly this http-res-ch runner))
        ;; Output is time-constrained for efficient messaging
        (lc/siphon 
         (lc/map* (partial edge-aggregate params)
                  (lc/partition-every 100 http-res-ch))
         res-ch)
        res-ch)))
  (stop [this]
    (log/info (str "Stopping job on " @mode))
    (reset! state :stopped)
    (lc/close res-ch)
    (lc/closed? res-ch)))

(defn init-benchmark
  [params]
  (HttpBenchmark. (atom :initialized)
                  (clean-params params)
                  (lc/channel)
                  (atom :unknown)))

(register :http-benchmark init-benchmark)