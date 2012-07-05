(ns engulf.test.formulas.http-benchmark-test
  (:require
   [engulf.formulas.http-benchmark :as htb]
   [engulf.formula :as fla])
  (:use midje.sweet))

(def test-params
  {:url "http://localhost:8282"
   :method "POST"
   :headers {"X-Bender" "Jimmy crack corn, and I don't care"}
   :concurrency "1"
   :body "a new, shiny metal body!"})

(facts
 "about initializing a benchmark"
 (let [b (htb/init-benchmark test-params)]
   (fact
    "it should be an engulf benchmark"
    (class b) => engulf.formulas.http_benchmark.HttpBenchmark)
   (fact
    "it should have a state of :initialized"
    @(:state b) => :initialized)))

(facts
 "about running a benchmark on an edge"
 (let [b (htb/init-benchmark test-params)
       res-ch (fla/start-edge b)]
   (fact
    "it should throw an exception if start-edge is invoked twice"
    (fla/start-edge b) => (throws Exception))))

(facts
 "about aggregation"
 (fact
  "it should set the runs-total to the length of the dataset"
  (:runs-total (htb/aggregate {:timeout 200} [1 2 3])) => 3))

(println "done")