(ns engulf.control
  (:require [engulf.comm.node-manager :as n-manager]
            [engulf.job-manager :as jmgr]
            [engulf.formulas.http-benchmark :as http-benchmark]
            [lamina.core :as lc]
            [clojure.tools.logging :as log])
  (:use [clojure.walk :only [keywordize-keys]]))

(declare stop-current-job)

(defn broadcast
  [name body]
  (lc/enqueue n-manager/receiver [name body]))

;; We probably don't need the lock here but it's easier than designing weird UI
;; Failure states
(def start-lock (Object.))

(defn start-job
  [params]
  (locking start-lock
    (log/info (str "Starting job with params" params))
    (let [job (jmgr/register-job :http-benchmark params)
          serializable-job (dissoc job :results)]
      (stop-current-job)
      (broadcast :job-start serializable-job)
      job)))

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
    :node-connect (println "Node" (:uuid body) "connected." (n-manager/count-nodes) "total nodes.")
    :node-disconnect (println "Node" (:uuid body) "disconnected." (n-manager/count-nodes) "total nodes.")
    (println "Unknown system message: " name body)))

(defn start-router
  []
  (lc/receive-all
   n-manager/emitter
   (fn message-router [[entity name body]]
     (let [name (keyword name)
           body (keywordize-keys body)]
       (condp = entity
         :results (println "Got results!")
         :system (handle-system-message name body)
         (println "Got something unexpected!" entity name body))))))

(defn start
  [port]
  (start-router)
  (n-manager/start-server port))