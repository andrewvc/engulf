(ns engulf.test.formulas.http-benchmark.request-sequences-test
  (:require
   [engulf.utils :as utils]
   [lamina.core :as lc]
   [clojure.java.io :as io]
   [engulf.formulas.http-benchmark.request-sequences :as req-seqs]
   [engulf.test.helpers :as helpers]
   [engulf.formula :as fla])
  (:use midje.sweet)
  (:import java.io.File
           java.net.URL))

(def test-replay-log "replay_log_test")

(spit test-replay-log
      "http://localhost\n{\"url\": \"http://localhost/foo\", \"method\": \"POST\"}\n\nhttp://localhost:3000")

(def reqs (take 10 (req-seqs/get-seq :replay {:target {:url "replay_log_test"}})))

(io/delete-file test-replay-log)

;; TODO: Finish writing this
   