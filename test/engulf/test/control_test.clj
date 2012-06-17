(ns engulf.test.control-test
  (:require
   [engulf.control :as ctrl]
   [engulf.comm.worker-client :as wc])
  (:use midje.sweet))

(facts
 "about starting jobs"
 (let [srv (ctrl/start 3493)
       wc (wc/client-connect "localhost" 3493)]
   (Thread/sleep 1)
   (ctrl/start-job
    {:url "http://localhost/test"
     :method "POST"
     :concurrency 3
     :headers {"X-Foo" "Bar"}
     :body "Ohai!"})
   (Thread/sleep 1)
   (fact
    "about life"
    true => truthy)
   (srv)))


