(ns engulf.test.comm.worker-client-test
  (:require [engulf.test.helpers :as helpers]
            [engulf.comm.worker-client :as wc]
            [lamina.core :as lc]
            [cheshire.core :as chesh])
  (:use midje.sweet
        [clojure.walk :only [keywordize-keys]])
  (import engulf.test.helpers.MockJob))


(facts
 "about starting jobs"
 (fact
  "they should start cleanly"
  (wc/start-job {} (fn [_] (MockJob. nil nil nil)) (lc/channel)) => truthy))