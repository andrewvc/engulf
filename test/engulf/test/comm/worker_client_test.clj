(ns engulf.test.comm.worker-client-test
  (:require [engulf.test.helpers :as helpers]
            [engulf.comm.worker-client :as wc]
            [lamina.core :as lc]
            [cheshire.core :as chesh])
  (:use midje.sweet
        [clojure.walk :only [keywordize-keys]])
  (import engulf.test.helpers.MockFormula))

(facts
 "about starting jobs"
 (let [started (atom nil)
       mock-formula (MockFormula. (fn [_]
                                    (reset! started true)
                                    (lc/channel :first-msg))
                                  nil
                                  nil)
       job {}
       conn-ch (lc/channel 1 2 3)
       res (wc/start-job job (fn [_] mock-formula) conn-ch)]
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
    @(lc/read-channel* conn-ch :timeout 1000) => :first-msg)))

(println "done")