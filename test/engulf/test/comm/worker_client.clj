(ns engulf.test.comm.worker-client
  (:require [engulf.comm.worker-client :as wclient]
            [lamina.core :as lc]
            [cheshire.core :as chesh])
  (:use midje.sweet
        [clojure.walk :only [keywordize-keys]]))