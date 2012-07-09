(ns engulf.test.control-test
  (:require
   [engulf.formula :as formula]
   [engulf.control :as ctrl]
   [engulf.comm.worker-client :as wc]
   [lamina.core :as lc])
  (:use midje.sweet))

(defrecord MockJob [on-start-edge on-start-relay on-stop]
  formula/Formula
  (stop [this]
    (when on-stop (on-stop this)))
  (start-edge [this]
    (when on-start-edge (on-start-edge this)))
  (start-relay [this ingress]
    (when on-start-relay (on-start-relay this ingress))))

(facts
 "about starting jobs"
 (let [srv (ctrl/start 3493)
       wc (wc/client-connect "localhost" 3493)
       seen (atom {})]
   (formula/register :mock-job
                     (fn [params]
                       (MockJob.
                        (fn mse [mj] (swap! seen #(assoc %1 :start-edge true)))
                        (fn msr [mj _] (swap! seen #(assoc %1 :start-relay true)))
                        (fn mss [mj] (swap! swap! seen #(assoc %1 :stop true))))))
   (ctrl/start-job
    {:url "http://localhost/test"
     :method "POST"
     :concurrency 3
     :job-name :mock-job
     :headers {"X-Foo" "Bar"}
     :body "Ohai!"})
   (Thread/sleep 1000)
   (fact
    "the start-edge method should be executed"
    (:start-edge @seen) => truthy)
   (fact
    "the start-relay method should be executed"
    (:start-relay @seen) => truthy)
   (fact
    "the stop method should be executed"
    (:start-relay @seen) => truthy)
   ;; Disconnect server and client
   (srv)
   (lc/close wc)))