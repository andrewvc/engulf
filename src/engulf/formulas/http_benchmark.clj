(ns engulf.formulas.http-benchmark
  (:require [engulf.formulas.http-benchmark.request-sequences :as req-seqs]
            [lamina.core :as lc]
            [lamina.connections]
            [clojure.tools.logging :as log]
            [clojure.set :as cset])
  (:use [engulf.formula :only [Formula register stop]]
        [engulf.utils :only [set-timeout now merge-map-sums]]
        [aleph.http :only [http-client http-request]]
        [clojure.walk :only [keywordize-keys]])
  (:import fastPercentiles.PercentileRecorder
           fastPercentiles.Percentile
           java.net.URL
           java.util.concurrent.Executors
           java.util.concurrent.ExecutorService
           java.nio.channels.ClosedChannelException))

(def callbacks-pool (Executors/newSingleThreadExecutor))

(load "http_benchmark_aggregations")
(load "http_benchmark_runner")

(defprotocol IHttpBenchmark
  (run-repeatedly [this ch runner] [this ch runner client req-params]))  

(defrecord HttpBenchmark [state params job res-ch mode]
  IHttpBenchmark
  (run-repeatedly
    [this ch runner]
    (let [reqs (:req-seq params)
          keep-alive? (:keep-alive? (first reqs))
          client-props {:url (:url (first reqs))
                        :retry? true
                        :keep-alive? (:keep-alive? (:target params))}
          client (if keep-alive? (http-client client-props)  http-request)]
      (run-repeatedly this ch runner client reqs)))
  (run-repeatedly
    [this ch runner client reqs]
    (runner client
            (first reqs)
            (fn req-resp [res]
              (if (= @state :started)
                (do
                  ;; Suspended seems to mean that the connection is being reset
                  ;; (perhaps the server terminated a keep-alive connection)
                  (when (not= res :lamina/suspended) (lc/enqueue ch res))
                  (run-repeatedly this ch runner client (rest reqs)))
                (lamina.connections/close-connection client) ))))
  Formula
  (start-relay [this ingress]
    (when (compare-and-set! state :initialized :started)
      (when (not (compare-and-set! mode :unknown :relay))
        (throw (Exception. "Attempted to double-change formula mode!")))
      
      ;; Setup quantized reduction pipeline
      (let [reduced (lc/reductions*
                     (fn [initial aggs] (relay-aggregate job initial aggs))
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
            worker-concurrency (int (/ (:concurrency params) (:node-count job)))
            actual-concurrency (if (> worker-concurrency 1) worker-concurrency 1)
            runner (if (:mock params) run-mock-request run-real-request)]
        ;; Kick off the async workers
        (dotimes [t actual-concurrency]
          (run-repeatedly this http-res-ch runner))
        ;; Output is time-constrained for efficient messaging
        (lc/siphon 
         (lc/map* (partial edge-aggregate params)
                  (lc/partition-every 250 http-res-ch))
         res-ch)
        res-ch)))
  (stop [this]
    (log/info (str "Stopping job on " @mode))
    (reset! state :stopped)
    (lc/close res-ch)
    (lc/closed? res-ch)))

(defn init-benchmark
  [params job]
  ;; TODO: Clean this up, we should only pass in job, not job and params
  (let [cleaned-job (clean-job job)]
    (HttpBenchmark. (atom :initialized)
                    (:params cleaned-job)
                    cleaned-job
                    (lc/channel)
                    (atom :unknown))))

(register :http-benchmark init-benchmark)