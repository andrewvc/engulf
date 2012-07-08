(ns engulf.formulas.http-benchmark
  (:require [lamina.core :as lc]
            [clojure.tools.logging :as log]
            [clojure.set :as cset])
  (:use [engulf.formula :only [Formula register]]
        [engulf.utils :only [set-timeout]]
        [aleph.http :only [http-client http-request]])
  (:import fastPercentiles.PercentileRecorder))

(defn result
  [started-at ended-at]
  {:started-at started-at
   :ended-at ended-at
   :runtime (- ended-at started-at)})

(defn error-result
  [started-at ended-at throwable]
  (assoc (result started-at ended-at)
    :status :thrown
    :throwable throwable))

(defn success-result
  [started-at ended-at status]
  (assoc (result started-at ended-at)
    :status status))

(defn empty-edge-aggregation
  [params]
  {:type :aggregate-edge
   :runtime 0
   :runs-total 0
   :runs-succeeded 0
   :runs-failed 0
   :status-codes {}
   :time-slices {}
   :all-runtimes []})

(defn empty-relay-aggregation
  [params]
  {:type :aggregate-relay
   :runtime 0
   :runs-total 0
   :runs-succeeded 0
   :runs-failed 0
   :status-codes {}
   :time-slices {}
   :percentiles (PercentileRecorder. (or (:timeout params) 10000))})

(defn run-request
  [params callback]
  (let [res (lc/result-channel)
        started-at (System/currentTimeMillis)] ; (http-request {:url (:url params)})
    (set-timeout 1 #(lc/success res (success-result started-at (System/currentTimeMillis) 200) ))
    (lc/on-realized res #(callback %1) #(callback %1))))

(defn successes
  [results]
  (filter #(not= :thrown (get %1 :status))
          results))

(defn edge-agg-totals
  [{:keys [runs-total runs-failed runs-succeeded] :as stats} results]
  (let [total (+ runs-total (count results))
        succeeded (+ runs-succeeded (count (successes results)))
        failed (- total succeeded)]
    (assoc stats :runs-total total :runs-failed failed :runs-succeeded succeeded)))

(defn edge-agg-times
  [{runtime :runtime :as stats} results]
  (assoc stats
    :all-runtimes (map :runtime results)
    :runtime (reduce
              (fn [m r] (+ m (:runtime r)))
              runtime
              results)))

(defn edge-agg-statuses
  [{scounts :status-codes :as stats} results]
  (assoc stats :status-codes
         (into scounts (map (fn [[k v]] [k (count v)]) (group-by :status results)))))

(defn edge-agg-percentiles
  [{rps :percentiles :as stats} results]
  (doseq [{e :ended-at s :started-at} results] (.record rps (- e s)))
  stats)

(defn edge-agg-time-slices
  "Processes time-slices segmented by status"
  [stats results]
  (assoc stats :time-slices
         (reduce
          (fn [buckets result]
            (let [time-slice (long (/ (:started-at result) 250))]
              (update-in buckets
                         [time-slice (:status result)]
                         #(if %1 (inc %1) 1))))
          (:time-slices stats)
          results)))

(defn relay-agg-totals
  [stats aggs]
  (assoc stats
    :runs-total (reduce + (map :runs-total aggs))
    :runs-succeeded (reduce + (map :runs-succeeded aggs))
    :runs-failed (reduce + (map :runs-failed aggs))))

(defn relay-agg-times
  [stats aggs]
  (assoc stats :runtime (reduce + (map :runtime aggs))))

(defn relay-agg-statuses
  [stats aggs]
  (assoc stats :status-codes
         (reduce
          (fn [m agg]
            (reduce (fn [mm [status count]]
                      (update-in mm [status] #(if %1 (+ %1 count) count)))
                    m
                    agg))
          {}
          (map :status-codes aggs))))

(defn relay-agg-percentiles
  "Handles both list and PercentileRecorder data"
  [stats aggs]
  (let [recorder (:percentiles stats)]
    (doseq [agg aggs]
      (if-let [agg-rec (:percentiles agg)]
        (.merge recorder agg-rec)
        (.record recorder (:all-runtimes agg)))))
  stats)

(defn relay-aggregate
  [params initial aggs]
  (-> initial
      (relay-agg-totals aggs)
      (relay-agg-times aggs)
      (relay-agg-statuses aggs)
      (relay-agg-percentiles aggs)))

(defn edge-aggregate
  [params results]
  (let [stats (empty-edge-aggregation params)]
    (-> stats
        (edge-agg-totals results)
        (edge-agg-times results)
        (edge-agg-statuses results)
        (edge-agg-time-slices results))))

(defn clean-params [params]
  (let [diff (cset/difference #{:url :method :concurrency :timeout} params)]
    (when (not (empty? diff))
      (throw (Exception. (str "Invalid parameters! Missing keys: " diff)))))
  (reduce (fn params-integerify [params p]
            (update-in params [p] #(Integer/valueOf %1)))
   params
   [:concurrency :timeout]))

(defprotocol IHttpBenchmark
  (run-repeatedly [this ch]))

(defrecord HttpBenchmark [state params res-ch mode]
  IHttpBenchmark
  (run-repeatedly [this ch]
    (run-request
     params
     (fn req-resp [res]
       (when (= @state :started) ; Discard results and don't recur when stopped
         (lc/enqueue ch res)
         (run-repeatedly this ch)))))
  Formula
  (start-relay [this ingress]
    (when (compare-and-set! state :initialized :started)
      (reset! mode :relay)
      (lc/siphon
       @(lc/run-pipeline
         ingress
         (partial lc/reductions*
                  (partial relay-aggregate params)
                  (empty-relay-aggregation params))
         (partial lc/sample-every 250))
       res-ch)
      res-ch))
  (start-edge [this]
    (when (compare-and-set! state :initialized :started)
      (reset! mode :edge)
      (let [http-res-ch (lc/channel)]
        ;; Kick off the async workers
        (dotimes [t (Integer/valueOf (:concurrency params))]
          (run-repeatedly this http-res-ch))
        ;; Every 250ms siphon out a chunk of the output to the res-ch
        (lc/siphon 
         (lc/map* (partial edge-aggregate params)
                  (lc/partition-every 250 http-res-ch))
         res-ch)
        res-ch)))
  (stop [this]
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
