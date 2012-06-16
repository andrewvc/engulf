(ns engulf.formulas.http-benchmark
  (:require [aleph.http :as http])
  (:use engulf.formula)
  (:import fastPercentiles.PercentileRecorder))

(defrecord HttpBenchmark [params]
  Formula
  (empty-results [this]
    {:runtime nil
     :runs-sec nil
     :runs-total 0
     :runs-succeeded 0
     :runs-failed 0
     :response-code-counts {}
     :by-start-time {}
     :runtime-percentiles-recorder (PercentileRecorder. (or (:timeout params) 10000))})
  (result-reduce [this result])
  (perform
    [this]
    (println "Running job with params" params)))

(register :http-benchmark (fn init-benchmark [params] (HttpBenchmark. params)))
              