(ns engulf.formulas.http-benchmark
  (:require [lamina.core :as lc]
            [clojure.tools.logging :as log]
            [clojure.set :as cset])
  (:use [engulf.formula :only [Formula register]]
        [engulf.utils :only [set-timeout]]
        [aleph.http :only [http-client http-request]]
        [clojure.string :only [lower-case]])
  (:import fastPercentiles.PercentileRecorder))

(defn now [] (System/currentTimeMillis))

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

(defn run-real-request
  [params callback]
  (let [started-at (now)]
    (letfn
        [(succ-cb [response]
           (callback (success-result started-at (now) (:status response))))
         (error-cb [throwable]
           (callback (error-result started-at (now) throwable)))]
      (try
        (lc/on-realized (http-request (juxt [:method :url :timeout] params))
                     succ-cb
                     error-cb)
        (catch Exception e
          (error-cb e))))))

(defn run-mock-request
  "Fake HTTP response for testing"
  [params callback]
  (let [started-at (now)
        res (lc/result-channel)
        succ-cb #(lc/success res (success-result started-at (System/currentTimeMillis) 200))]
    (set-timeout 1 succ-cb)
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

(def valid-methods #{:get :post :put :delete :patch})

(defn int-val [i] (Integer/valueOf i))

(defn clean-params [params]
  ;; Ensure required keys
  (let [diff (cset/difference #{:url :method :concurrency :timeout} params)]
    (when (not (empty? diff))
      (throw (Exception. (str "Invalid parameters! Missing keys: " diff)))))
  (let [method (keyword (lower-case (:method params)))]
    ;; Ensure only valid methods are used
    (when (not (method valid-methods))
      (throw (Exception. (str "Invalid method: " method " expected one of " valid-methods))))
    ;; Transform the types of vals that need it
    (-> params
        (update-in [:concurrency] int-val)
        (update-in [:timeout] int-val)
        (assoc :method method))))

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
      (let [http-res-ch (lc/channel)
            runner (if (:mock params) run-mock-request run-real-request)]
        ;; Kick off the async workers
        (dotimes [t (Integer/valueOf (:concurrency params))]
          (run-repeatedly this http-res-ch runner))
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
