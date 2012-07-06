(ns engulf.test.formulas.http-benchmark-test
  (:require
   [lamina.core :as lc]
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
    (fla/start-edge b) => (throws Exception))
   (fact
    "it should change state to started"
    @(:state b) => :started)
   (fact
    "it should return aggregate data in short order"
    (:type @(lc/read-channel* res-ch :timeout 400)) => :aggregate)))

(facts
 "about aggregation"
 (let [agg (htb/aggregate {:timeout 200} [{} {} {} ])]
   (fact
    "it should set the runs-total to the length of the dataset"
    (:runs-total agg)  => 4)
   (fact
    "it should count successes correctly"
    (agg :runs-succeeded) => 3)
   (fact
    "it should count failures correctly"
    (agg :runs-failed) => 1)))

(println "done")