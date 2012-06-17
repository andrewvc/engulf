(ns engulf.formulas.http-benchmark
  (:require [aleph.http :as http]
            [lamina.core :as lc])
  (:use engulf.formula)
  (:import fastPercentiles.PercentileRecorder))

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
  (println "RUN"))

(defprotocol IHttpBenchmark
  (init-com-listener [this]))

(defrecord HttpBenchmark [state params results res-ch com-ch]
  IHttpBenchmark
  (init-com-listener
    [this]
    (when (= :initialized @state)
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
    (init-com-listener this)
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