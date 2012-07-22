(ns engulf.test.relay-test
  (:require
   [engulf.relay :as relay]
   [engulf.test.helpers :as helpers]
   [engulf.formula :as formula]
   [lamina.core :as lc])
  (:use midje.sweet)
  (import engulf.test.helpers.MockFormula))

(reset! relay/state :stopped)
(let [started (atom false)
      stopped (atom false)]
  (relay/start)
  (formula/register
   :http-benchmark
   (fn [params]
     (MockFormula. nil
                   (fn rly-start [_ __]
                     (reset! started true)
                     (lc/channel :first-msg))
                   (fn rly-stop [_]
                     (reset! stopped true)))))
  (lc/enqueue relay/receiver [:job-start helpers/test-http-job])
  (Thread/sleep 20)
  (fact
   "it should start the relay on the job formula"
   @started => truthy)
  (fact
   "it should stop cleanly"
   @(relay/stop-job) => truthy
   (Thread/sleep 20)
   @stopped => truthy))
