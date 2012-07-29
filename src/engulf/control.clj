(ns engulf.control
  (:require [engulf.comm.node-manager :as n-manager]
            [engulf.relay :as relay]
            [lamina.core :as lc]
            [clojure.tools.logging :as log])
  (:use [clojure.walk :only [keywordize-keys]]))

(def ^:dynamic receiver (lc/channel* :grounded true :permanent true))
(def ^:dynamic emitter (lc/channel* :grounded true :permanent true))

(lc/siphon relay/emitter emitter)

(defn stop-job
  []
  (lc/enqueue n-manager/receiver {"name" "job-stop"})
  (relay/stop-job))


(defn start-job
  [job]
  (let [start-res @(relay/start-job job)]
    (lc/enqueue n-manager/receiver {"name" "job-start"
                                    "body" job})
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
                   (n-manager/count-nodes) " total nodes."))
    "node-disconnect"
    (log/info (str  "Node " uuid " disconnected. "
                    (n-manager/count-nodes) "total nodes."))
    (log/warn (str "Unknown system message: " name " " body))))

(def router-state (atom :idle))

(defn start-router
  []
  (when (compare-and-set! router-state :idle :started)
    (lc/siphon
     (lc/filter* #(= "job-result" (get % "name")) n-manager/emitter)
     relay/receiver)
    (lc/receive-all
     (lc/filter* (fn [{e "entity"}] (= "system" e)) n-manager/emitter)
     handle-system-message)))

(defn start
  [port]
  (start-router)
  (n-manager/start-server port))