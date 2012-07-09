(ns engulf.comm.worker-client
  (:require
   [clojure.tools.logging :as log]
   [engulf.comm.netchan :as nc]
   [engulf.formula :as formula]
   [lamina.core :as lc])
  (:use lamina.core
        [clojure.walk :only [keywordize-keys]])
  (:import java.util.UUID
           java.net.ConnectException))

(def uuid (str (UUID/randomUUID)))

(def current-job (atom nil))

(defn start-job
  [job conn]
  (reset! current-job job)
  (if-let [job-formula-constructor (formula/lookup (:formula-name job))]
    ;; Bridge the streaming results from the job-formula to the connection
    ;; They get routed through a permanent channel to prevent close events from
    ;; propagating
    (lc/run-pipeline
     (formula/start-edge (job-formula-constructor (:params job)))
     #(let [pc (permanent-channel)] (siphon %1 pc) pc)
     #(siphon %1 conn))
    (log/warn (str "Could not find formula for job! " (:formula-name job) " in " @formula/registry))))

(defn stop-job
  []
  )

(defn handle-message
  [[conn name body]]
  (try
    (let [name (keyword name)
          body (keywordize-keys body)]
      (condp = name
        :job-start (start-job body conn)
        (log/warn (str "Client Received Unexpected Message" name " : " body))))
    (catch Exception e (log/warn e "Could not handle message!" name body))))
    
(defn client-connect
  [host port]
  (try
    (let [conn @(nc/client-connect host port)]
      (on-closed conn (fn [] (log/warn "Connection to master closed!")))
      (on-error conn (fn [e] (log/warn e "Server Channel Error!") ))
      
      (receive-all conn (partial handle-message conn))
      ;; Send identity immediately
      (enqueue conn ["uuid" uuid])
      conn)
    (catch java.net.ConnectException e
      (log/warn e "Could not connect to control server!"))))