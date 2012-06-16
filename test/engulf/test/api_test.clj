(ns engulf.test.api-test
  (:require
   [engulf.api :as api]
   [engulf.comm.control :as ctrl]
   [engulf.node-server :as nserver]
   [engulf.comm.worker-client :as wc])
  (:use midje.sweet))

(facts
 "about starting jobs"
 (let [srv (nserver/start)
       wc (wc/client-connect "localhost" 3493)
       job-handler]
   (Thread/sleep 1)
   (api/start-job
    {:url "http://localhost/test"
     :method "POST"
     :headers {"X-Foo" "Bar"}
     :body "Ohai!"})
   (Thread/sleep 1)
   (fact
    "about life"
    true => truthy)
   (srv)))


