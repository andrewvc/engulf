(ns engulf.control
  (:require [engulf.comm.node-manager :as nmgr]
            [engulf.relay :as relay]
            [lamina.core :as lc]
            [clojure.tools.logging :as log])
  (:use [clojure.walk :only [keywordize-keys]]))

(def ^:dynamic receiver (lc/channel* :grounded? true :permanent? true))
(def ^:dynamic emitter (lc/channel* :grounded? true :permanent? true))

(defn stop-job
  []
  (let [stop-msg {"entity" "system" "name" "job-stop"}]
    (lc/enqueue nmgr/receiver stop-msg)
    (lc/enqueue emitter stop-msg))
  (relay/stop-job))


(defn start-job
  [job]
  (let [start-res @(relay/start-job job)
        start-msg {"name" "job-start" "body" job}]
    (lc/enqueue nmgr/receiver start-msg)
    (lc/enqueue emitter start-msg)
    (lc/on-closed (:results-ch start-res) #(stop-job))
    start-res))

(defn get-job
  [uuid])

(defn list-jobs
  [])

(defn del-job
  [uuid])

(defn handle-system-message
  [{:strs [name {uuid "uuid" :as body}]}]
  (condp = name
    "node-connect"
    (log/info (str "Node " uuid " connected. "
                   (nmgr/count-nodes) " total nodes."))
    "node-disconnect"
    (log/info (str  "Node " uuid " disconnected. "
                    (nmgr/count-nodes) "total nodes."))
    (log/warn (str "Unknown system message: " name " " body))))

(def router-state (atom :idle))

(defn start-router
  []
  (when (compare-and-set! router-state :idle :started)
    (lc/siphon
     (lc/filter* #(= "job-result" (get % "name")) nmgr/emitter)
     relay/receiver)
    (let [sys-ch (lc/filter* (fn [{e "entity"}] (= "system" e)) nmgr/emitter)]
      (lc/receive-all sys-ch handle-system-message)
      (lc/siphon sys-ch emitter))))

(defn start
  [port]
  (start-router)
  (nmgr/start-server port))