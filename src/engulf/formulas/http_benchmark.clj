(ns engulf.formulas.http-benchmark
  (:require [lamina.core :as lc]
            [clojure.tools.logging :as log]
            [clojure.set :as cset])
  (:use [engulf.formula :only [Formula register]]
        [engulf.utils :only [set-timeout]]
        [aleph.http :only [http-client http-request]])
  (:import fastPercentiles.PercentileRecorder))

(defn increment-keys
  "Given a map and a list of keys, this will return an identical map with those keys
   with those keys incremented.
    
   Keys with a null value will be set to 1."
  [src-map & xs]
  (into src-map (map #(vector %1 (inc (get src-map %1 0))) xs)))

(defn error-result
  [started-at ended-at throwable]
  {:started-at started-at
   :ended-at ended-at
   :status :thrown
   :throwable throwable})

(defn success-result
  [started-at ended-at status]
  {:started-at started-at
   :ended-at ended-at
   :status status})

(defn empty-aggregation
  [params]
  {:type :aggregate
   :runtime 0
   :runs-total 0
   :runs-succeeded 0
   :runs-failed 0
   :status-codes {}
   :time-slices {}
   :runtime-percentiles (PercentileRecorder. (or (:timeout params) 10000))})

(defn run-request
  [params callback]
  (let [res (lc/result-channel)
        started-at (System/currentTimeMillis)] ; (http-request {:url (:url params)})
    (set-timeout 1 #(lc/success res {:started-at started-at
                                     :status 200
                                     :ended-at (System/currentTimeMillis)}))
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
  (assoc stats :runtime
         (reduce
          (fn [m r] (+ m (- (:ended-at r) (:started-at r))))
          runtime
          results)))

(defn edge-agg-statuses
  [{scounts :status-codes :as stats} results]
  (assoc stats :status-codes
         (into scounts (map (fn [[k v]] [k (count v)]) (group-by :status results)))))

(defn edge-agg-percentiles
  [{rps :runtime-percentiles :as stats} results]
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

(defn relay-aggregate
  [params aggs]
  (let [stats (empty-aggregation params)]
    (-> stats
        (relay-agg-totals aggs)
        (relay-agg-times aggs)
        (relay-agg-statuses aggs))))

(defn edge-aggregate
  [params results]
  (let [stats (empty-aggregation params)]
    (-> stats
        (edge-agg-totals results)
        (edge-agg-times results)
        (edge-agg-statuses results)
        (edge-agg-percentiles results)
        (edge-agg-time-slices results))))

(defn validate-params [params]
  (let [diff (cset/difference #{:url :method :concurrency} params)]
    (when (not (empty? diff))
      (throw (Exception. (str "Invalid parameters! Missing keys: " diff))))))

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
    (validate-params params)
    (if (not (compare-and-set! state :initialized :started))
      (throw (Exception. (str "Expected state :initialized, not: ") @state))
      (do
        (dotimes [t (Integer/valueOf (:concurrency params))] (run-repeatedly this))
        (lc/map* (partial aggregate params) (lc/partition-every 250 res-ch)))))
  (stop [this]
    (reset! state :stopped)
    (lc/close res-ch)))

(defn init-benchmark
  [params]
  (HttpBenchmark. (atom :initialized)
                  params
                  (lc/channel)))

(register :http-benchmark init-benchmark)
