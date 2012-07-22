(ns engulf.control
  (:require [engulf.comm.node-manager :as n-manager]
            [engulf.relay :as relay]
            [engulf.job-manager :as jmgr]
            [engulf.formulas.http-benchmark :as http-benchmark]
            [engulf.formula :as formula]
            [lamina.core :as lc]
            [clojure.tools.logging :as log])
  (:use [clojure.walk :only [keywordize-keys]]))

(declare stop-current-job)

(def ^:dynamic receiver (lc/channel* :grounded true :permanent true))
(def ^:dynamic emitter (lc/channel* :grounded true :permanent true))

(defn broadcast
  [name body & optional]
  (let [m (merge {:name name :body body} optional)]
    (lc/enqueue n-manager/receiver m)
    (lc/enqueue relay/receiver m)))


(defn start-job
  [{job-name :job-name :as params}]
  (log/info (str "Starting job with params: " params))
  (let [job (jmgr/register-job job-name params)]
      (stop-current-job)
      (broadcast :job-start job)
      job))

(defn stop-current-job
  []
  (jmgr/stop-job)
  (broadcast :job-stop nil))

(defn get-job
  [uuid])

(defn list-jobs
  [])

(defn del-job
  [uuid])

(defn handle-system-message
  [name body]
  (condp = name
    :node-connect
    (log/info (str "Node" (:uuid body) "connected. "
                   (n-manager/count-nodes) " total nodes."))
    :node-disconnect
    (log/info (str  "Node" (:uuid body) "disconnected. "
                    (n-manager/count-nodes) "total nodes."))
    (log/warn (str "Unknown system message: " name " " body))))

(def router-state (atom :idle))

(defn start-router
  []
  (when (compare-and-set! router-state :idle :started)
    (lc/receive-all
     n-manager/emitter
     (fn message-router [{:keys [entity name body] :as msg}]
       (let [name (keyword name) ]
         (condp = entity
           :system (handle-system-message name body)
           (println "Control router got something unexpected!" msg)))))))

(defn start
  [port]
  (start-router)
  (n-manager/start-server port))