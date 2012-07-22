(ns engulf.test.worker-client-test
  (:require [engulf.test.helpers :as helpers]
            [engulf.worker-client :as wc]
            [engulf.formula :as formula]
            [lamina.core :as lc]
            [cheshire.core :as chesh])
  (:use midje.sweet)
  (:import engulf.test.helpers.MockFormula
           java.util.UUID))

(facts
 "about starting/stopping jobs"
 (let [started (atom false)
       stopped (atom false)
       job {:formula-name :mock-formula :uuid (str (UUID/randomUUID))}
       conn-ch (lc/channel :conn-first-ch)
       fla (MockFormula. (fn wc-start [_]
                           (reset! started true)
                           (lc/channel :first-msg))
                         nil
                         (fn wc-stop [_]
                           (reset! stopped true)))
       _ (formula/register :mock-formula (fn [_] fla ))
       res (wc/start-job job conn-ch)
       ]
   (fact
    "it should start cleanly"
    @res => truthy)
   (fact
    "it should update the current job"
    (:job @wc/current) => job)
   (fact
    "it should update the current formula"
    (:formula @wc/current) => fla)
   (fact
    "it should setup the results ch"
    (:results-channel @wc/current) => lc/channel?)
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

(println "DONE")
