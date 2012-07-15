(ns engulf.test.worker-client-test
  (:require [engulf.test.helpers :as helpers]
            [engulf.worker-client :as wc]
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
       conn-ch (lc/channel :conn-first-ch)
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
    @(lc/read-channel* conn-ch :timeout 1000) => :conn-first-ch)))

(println "done")