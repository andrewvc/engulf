(ns engulf.test.formulas.http-benchmark-test
  (:require
   [engulf.formulas.http-benchmark :as htb])
  (:use midje.sweet))

(facts
 "about aggregation"
 (fact
  "it should set the runs-total to the length of the dataset"
  (:runs-total (htb/aggregate {:timeout 200} [1 2 3])) => 3))