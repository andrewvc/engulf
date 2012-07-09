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
    (when on-start-relay (on-start-relay this))))

(facts
 "about starting jobs"
 (let [srv (ctrl/start 3493)
       wc (wc/client-connect "localhost" 3493)
       seen (atom {})]
   (formula/register :mock-job
                     (fn [params]
                       (MockJob.
                        (fn [mj] (swap! seen #(assoc :start-edge true)))
                        (fn [mj] (swap! seen #(assoc :start-relay true)))
                        (fn [mj] (swap! swap! seen #(assoc :stop true))))))
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
    @seen => 2)
   ;; Disconnect server and client
   (srv)
   (lc/close wc)))