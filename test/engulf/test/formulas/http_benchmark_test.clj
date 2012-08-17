(ns engulf.test.formulas.http-benchmark-test
  (:require
   [engulf.utils :as utils]
   [lamina.core :as lc]
   [engulf.formulas.http-benchmark :as htb]
   [engulf.test.helpers :as helpers]
   [engulf.formula :as fla])
  (:use midje.sweet))

(defn init-benchmark
  []
  (htb/init-benchmark helpers/test-http-job-params
                      {:params helpers/test-http-job-params}))

(facts
 "about initializing a benchmark"
 (let [b (init-benchmark)]
   (fact
    "it should be an engulf benchmark"
    (class b) => engulf.formulas.http_benchmark.HttpBenchmark)
   (fact
    "it should have a state of :initialized"
    @(:state b) => :initialized)))

(facts
 "about starting an edge"
 (let [b (init-benchmark)
       res-ch (fla/start-edge b)]
   (fact
    "it should return nil if it's already started"
    (fla/start-edge b) => nil)
   (fact
    "it should change state to started"
    @(:state b) => :started)
   (fact
    "it should return aggregate data in short order"
    (get @(lc/read-channel* res-ch :timeout 300) "type") => "aggregate-edge")
   (fact
    "it should stop cleanly"
    (fla/stop b) => truthy)
   (fact
    "blah"
    (:res-ch b) => lc/closed?)))

(facts
 "about starting a relay"
   (let [b (init-benchmark)
         res-ch (fla/start-relay b (lc/channel))]
   (fact
    "it should return nil if it's already started"
    (fla/start-relay b (lc/channel)) => nil)
   (fact
    "it should change state to started"
    @(:state b) => :started)
   (fact
    "it should return aggregate data in short order"
    (get @(lc/read-channel* res-ch :timeout 300) "type") => "aggregate-relay")
   (fact
    "it should stop cleanly"
    (fla/stop b) => truthy)
   (fact
    "blah"
    (:res-ch b) => lc/closed?)))

(def ea-start (utils/now))

(defn eagg
  []
  (htb/edge-aggregate {:timeout 500}
                          [(htb/success-result ea-start (+ ea-start 10) 200)
                           (htb/success-result ea-start (+ ea-start 10) 200)
                           (htb/success-result ea-start (+ ea-start 20) 404)
                           (htb/success-result (+ ea-start 2000) (+ ea-start 2025) 404)
                           (htb/error-result (+ ea-start 2000)
                                             (+ ea-start 2025)
                                             (Exception. "wtf"))]
                          ))

(facts
 "about relay aggregation"
 (let [params {:timeout 500 :limit 500}
       agg (htb/relay-aggregate
            {:params params :started-at ea-start}
            (htb/empty-relay-aggregation params) (repeatedly 2 eagg))]
   (fact
    "it should sum run totals"
    (agg "runs-total") => 10)
   (fact
    "it should sum success totals"
    (agg "runs-succeeded") => 8)
   (fact
    "it should sum failure totals"
    (agg "runs-failed") => 2)
   (fact
    "it should sum runtimes"
    (agg "runtime") => 180)
   (fact
    "it should merge time slices"
    (vec (vals (into (sorted-map) (agg "time-slices")))) =>
    [{200 4, 404 2 "total" 6}, {"total" 4 "thrown" 2 404 2}])
   (fact
    "it should sum aggregated statuses"
    (agg "status-codes") => {"thrown" 2, 404 4, 200 4})))

(facts
 "about edge aggregation"
 (let [agg (eagg)]
   (fact
    "it should set the runs-total to the length of the dataset"
    (agg "runs-total")  => 5)
   (fact
    "it should count successes correctly"
    (agg "runs-succeeded") => 4)
   (fact
    "it should count failures correctly"
    (agg "runs-failed") => 1)
   (fact
    "it should add up times correctly"
    (agg "runtime") => 90)
   (fact
    "it should properly aggregate the status codes"
    (agg "status-codes") => {200 2
                            404 2
                            "thrown" 1})
   (fact
    "it should record as many samples as given"
    (count (agg "all-runtimes")) => 5)
   (fact
    "it should aggregate response codes by time-slice"
    (vec (vals (into (sorted-map) (agg "time-slices")))) =>
    [{404 1, 200 2 "total" 3} {"thrown" 1, 404 1, "total" 2}] )))

