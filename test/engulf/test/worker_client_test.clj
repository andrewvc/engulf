(ns engulf.test.worker-client-test
  (:require [engulf.test.helpers :as helpers]
            [engulf.worker-client :as wc]
            [engulf.formula :as formula]
            [lamina.core :as lc]
            [cheshire.core :as chesh])
  (:use midje.sweet)
  (import engulf.test.helpers.MockFormula))

(facts
 "about starting/stopping jobs"
 (let [started (atom false)
       stopped (atom false)
       job {:formula-name :mock-formula}
       conn-ch (lc/channel :conn-first-ch)
       res (wc/start-job job conn-ch)]
   (formula/register
    :mock-formula
    (fn [params]
      (MockFormula. (fn wc-start [_]
                      (reset! started true)
                      (lc/channel :first-msg))
                    nil
                    (fn wc-stop [_]
                      (reset! stopped true)))))
   (fact
    "it should start cleanly"
    res => truthy)
   (fact
    "it should update the current job"
    @wc/current-job => job)
   (fact
    "it should start the mock job"
    @started => true)
   (fact
    "it should enqueue messages from start edge onto the connection"
    @(lc/read-channel* conn-ch :timeout 1000) => :conn-first-ch)
   (fact
    "it should stop correctly"
    (wc/stop-job)
    @stopped => true)))

(println "done")