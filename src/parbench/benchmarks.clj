(ns parbench.benchmarks
  (:require [com.twinql.clojure.http :as http])
  (:import java.util.Calendar))

(def default-http-parameters
  (http/map->params {:handle-redirects false}))

(defn timestamp []
  (.getTimeInMillis (Calendar/getInstance)))

(defn run-request [request]
  "Runs a single HTTP request"
  (dosync (alter request assoc :state :requested :requested-at (timestamp)))
  (let [result (http/get (:url @request) :parameters default-http-parameters)]
    (dosync
      (alter request assoc
        :responded-at (timestamp)
        :state        :responded
        :status       (:code result)))))

(defn run-requests [request-list]
  (doseq [request request-list]
    (run-request request)))

(defn user-agents [reqs-state opts]
  "Visualization showing each row as a user agent"
  (let [request-lists (for [row (:grid reqs-state)] (agent row))]
    (for [request-list request-lists]
      (send-off request-list run-requests))))
