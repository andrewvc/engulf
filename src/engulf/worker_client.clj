(ns engulf.worker-client
  (:require
   [engulf.utils :as utils]
   [engulf.comm.netchan :as nc]
   [engulf.formula :as formula]
   [clojure.tools.logging :as log]
   [lamina.core :as lc])
  (:use lamina.core
        [clojure.walk :only [keywordize-keys]])
  (:import java.util.UUID
           java.net.ConnectException))

(def uuid (str (UUID/randomUUID)))

(def current (agent nil))

(defn start-job
  "Bridge the streaming results from the job-formula to the connection
   They get routed through a permanent channel to prevent close events from
   propagating"
  [job conn]
  (utils/safe-send-off-with-result current res state
    (when-let [{old-fla :formula} state] (formula/stop old-fla))
    (let [pc (permanent-channel)
          fla (formula/init-job-formula job)]
      (siphon pc conn)
      (siphon (formula/start-edge fla) pc)
      (lc/enqueue res pc)
      {:job job :formula fla})))


(defn stop-job
  "Stop the current job, setting current to nil"
  []
  (utils/safe-send-off-with-result current res state
    (when-let [{old-fla :formula} state] (lc/enqueue res (formula/stop old-fla)))
    nil))

(defn handle-message
  [conn [name body]]
  (try
    (let [name (keyword name)
          body (keywordize-keys body)]
      (condp = name
        :job-start (start-job body conn)
        :job-stop (stop-job)
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