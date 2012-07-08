(ns engulf.formulas.http-benchmark
  (:require [lamina.core :as lc]
            [clojure.tools.logging :as log]
            [clojure.set :as cset])
  (:use [engulf.formula :only [Formula register]]
        [engulf.utils :only [set-timeout]]
        [aleph.http :only [http-client http-request]]
        [clojure.string :only [lower-case]])
  (:import fastPercentiles.PercentileRecorder))

(load "http_benchmark_aggregations")
(load "http_benchmark_runner")