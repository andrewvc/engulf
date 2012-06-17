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
  (let [res (http-request {:url (:url params)})]
    (lc/on-realized res #(callback %1) #(callback %1))))

(defprotocol IHttpBenchmark
  (init-listeners [this]))

(defrecord HttpBenchmark [state params results res-ch com-ch]
  IHttpBenchmark
  (init-listeners
    [this]
    (when (= :initialized @state)
      (lc/receive-all
       res-ch
       (fn res-listener [res]
         (dosync
          (alter results increment-keys :runs-total)
          (println "RES!" @results))))
      (lc/receive-all
       com-ch
       (fn com-handler [command]
         (when (and  (= :run command) (= :started @state))
           (run-request params
                        (fn run-cb [res]
                          (lc/enqueue res-ch res)
                          (lc/enqueue com-ch :run))))))))
  Formula
  (get-and-reset-aggregate
    [this]
    (dosync
     (let [snapshot @results]
       (ref-set results assoc (empty-aggregation params))
       @results)))
  (get-aggregate
    [this]
    @results)
  (stop
    [this]
    (reset! state :stopped))
  (perform
    [this]
    (init-listeners this)
    (when (compare-and-set! state :initialized :started)
      (dotimes [t (int (:concurrency params))]
        (lc/enqueue com-ch :run)))))

(register :http-benchmark (fn init-benchmark [params]
                            (HttpBenchmark.
                             (atom :initialized)
                             params
                             (ref (empty-aggregation params))
                             (lc/channel)
                             (lc/channel))))