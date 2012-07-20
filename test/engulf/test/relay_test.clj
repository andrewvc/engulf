(ns engulf.relay-test
  (:require
   [engulf.relay :as relay]
   [engulf.test.helpers :as helpers]
   [lamina.core :as lc])
  (:use midje.sweet))

(facts
 "about starting a job"
 (let [relay-stop (relay/start)]
   (fact
    "it should start the relay on the job formula"
    (lc/enqueue relay/receiver [:job-start helpers/test-http-job])
    1 => 2
    
    )
   (Thread/sleep 1000)
   (relay-stop)))