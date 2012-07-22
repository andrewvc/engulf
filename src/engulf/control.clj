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
  (let [m (merge {"name" name "body" body} optional)]
    (lc/enqueue n-manager/receiver m)
    (lc/enqueue relay/receiver m)))

(defn start-job
  [{formula-name :formula-name :as params}]
  (stop-current-job)
  ;; Attempt to initialize the formula. This should throw any errors it gets related to invalid params
  (formula/init-job-formula {:formula-name formula-name :params params})
  (Thread/sleep 300) ;; This is a courtesy to let the old job spin down
  (when (not formula-name)
    (throw (Exception. "Missing formula name!")))
  (log/info (str "Starting job with params: " params))
  (let [job (jmgr/register-job formula-name params)]
    (broadcast "job-start" (dissoc job :results))
    job))

(defn stop-current-job
  []
  (jmgr/stop-job)
  (broadcast "job-stop" nil))

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
    (lc/siphon (lc/filter* #(= "job-result" (get % "name")) n-manager/emitter) relay/receiver)
    (lc/receive-all (lc/filter* (fn [{e :entity}] (= :system e)) n-manager/emitter) handle-system-message)))

(defn start
  [port]
  (start-router)
  (n-manager/start-server port))