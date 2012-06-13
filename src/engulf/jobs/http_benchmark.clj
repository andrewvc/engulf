(ns engulf.jobs.http-benchmark
  (:import fastPercentiles.PercentileRecorder))


(defn empty-results []
  {:runtime nil
   :runs-sec nil
   :runs-total 0
   :runs-succeeded 0
   :runs-failed 0
   :response-code-counts {}
   :by-start-time {}
   :runtime-percentiles-recorder (PercentileRecorder. 100000)})

(defn result-reduce
  [result])

(defn perform
  [params])