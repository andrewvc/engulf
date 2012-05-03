(ns engulf.remote-control
  (:require [aleph.http :as ah])
  (:use lamina.core lamina.api))

(def client (ah/http-client {}))

(let [rc (client {:url "http://localhost:4000/benchmarker/stream"})]
  (on-success rc (fn [m] (println m)))
  (on-error rc (fn [m] (println "F" m))))