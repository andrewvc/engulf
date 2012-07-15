(ns engulf.test.helpers
  (:require
   [engulf.formula :as formula]
   [lamina.core :as lc])
  (:use midje.sweet))

(defrecord MockFormula [on-start-edge on-start-relay on-stop]
  formula/Formula
  (stop [this]
    (when on-stop (on-stop this)))
  (start-edge [this]
    (when on-start-edge (on-start-edge this)))
  (start-relay [this ingress]
    (when on-start-relay (on-start-relay this ingress))))