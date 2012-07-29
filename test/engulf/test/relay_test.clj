(ns engulf.test.relay-test
  (:require
   [engulf.relay :as relay]
   [engulf.test.helpers :as helpers]
   [engulf.formula :as formula]
   [engulf.utils :as utils]
   [lamina.core :as lc])
  (:use midje.sweet)
  (import engulf.test.helpers.MockFormula))

(reset! relay/state :stopped)
(let [started (atom false)
      ingress (lc/channel)
      stopped (atom false)
      job helpers/test-http-job
      fla (MockFormula. nil
                   (fn rly-start [_ inst-ingress]
                     (reset! started true)
                     (lc/siphon inst-ingress ingress)
                     (lc/channel :first-msg))
                   (fn rly-stop [_]
                     (reset! stopped true)))]
  (formula/register :http-benchmark (fn [_] fla))
  (relay/start)
  (lc/enqueue relay/receiver {"name" "job-start", "body" job})
  (Thread/sleep 20)
  (fact
   "it should start the relay on the job formula"
   @started => truthy)
  (fact
   "it should update the current job"
    (:job @relay/current) => job)
  (fact
   "it should update the current formula"
   (:formula @relay/current) => fla)
  (fact
   "it should provide a channel as ingress (2nd arg) to start-relay"
   ingress => lc/channel?)
  (fact
   "it should receive messages delivered over the receiver at its point of ingress"
   (let [results "a-fake-result"
         m {"name" "job-result" "body" {"results" results, "job-uuid" (:uuid job) }}]
     (lc/enqueue relay/receiver m)
     (Thread/sleep 20)
     @(lc/read-channel* ingress :timeout 500) => results))
  (fact
   "it should stop cleanly"
   (Thread/sleep 1000)
   @(relay/stop-job) => truthy
   (Thread/sleep 20)
   @stopped => truthy))
