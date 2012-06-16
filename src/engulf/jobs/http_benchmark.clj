(ns engulf.jobs.http-benchmark
  (:use engulf.job)
  (:import fastPercentiles.PercentileRecorder))

(defrecord HttpBenchmark []
  EngulfJob
  (empty-results [this]
    {:runtime nil
     :runs-sec nil
     :runs-total 0
     :runs-succeeded 0
     :runs-failed 0
     :response-code-counts {}
     :by-start-time {}
     :runtime-percentiles-recorder (PercentileRecorder. 100000)})
  (result-reduce [this result])
  (perform
    [this params]
    (println "!!!HTTP BENCHMARK!!! JOB START")))

(register :http-benchmark (fn init-benchmark [] (HttpBenchmark.)))
              