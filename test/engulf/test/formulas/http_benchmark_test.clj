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
    (:type @(lc/read-channel* res-ch :timeout 400)) => :aggregate)
   (fact
    "it should stop cleanly"
    (fla/stop b) => truthy)))

(defn eagg
  []
  (htb/edge-aggregate {:timeout 500}
                          [(htb/success-result 0 10 200)
                           (htb/success-result 0 10 200)
                           (htb/success-result 0 20 404)
                           (htb/error-result 0 40 (Exception. "wtf"))]))

(facts
 "about relay aggregation"
 (let [agg (htb/relay-aggregate {:timeout 500} (repeatedly 2 eagg))]
   (fact
    "it should sum run totals"
    (agg :runs-total) => 8)
   (fact
    "it should sum success totals"
    (agg :runs-succeeded) => 6)
   (fact
    "it should sum failure totals"
    (agg :runs-failed) => 2)
   (fact
    "it should sum runtimes"
    (agg :runtime) => 160)
   (fact
    "it should sum aggregated statuses"
    (agg :status-codes) => {:thrown 2, 404 2, 200 4}
    )))

(facts
 "about edge aggregation"
 (let [agg (eagg)]
   (fact
    "it should set the runs-total to the length of the dataset"
    (:runs-total agg)  => 4)
   (fact
    "it should count successes correctly"
    (agg :runs-succeeded) => 3)
   (fact
    "it should count failures correctly"
    (agg :runs-failed) => 1)
   (fact
    "it should add up times correctly"
    (agg :runtime) => 80
    (fact
     "it should properly aggregate the status codes"
     (agg :status-codes) => {200 2
                             404 1
                             :thrown 1})
    (fact
     "it should record as many samples as given"111
     
     (.getCount (agg :runtime-percentiles)) => 4)
    (fact
     "it should the individual values correctly in the percentiles"
     (let [raw (.getRawData (agg :runtime-percentiles))]
       (aget raw 10) => 2
       (aget raw 20) => 1
       (aget raw 40) => 1))
    (fact
     "it should aggregate response codes by time-slice"
     (agg :time-slices) => {0 {:thrown 1, 404 1, 200 2}}
     ))))

(println "done")