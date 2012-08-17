(ns engulf.worker-client
  (:require
   [engulf.utils :as utils]
   [engulf.comm.netchan :as nc]
   [engulf.settings :as settings]
   [engulf.formula :as formula]
   [clojure.tools.logging :as log]
   [lamina.core :as lc])
  (:use lamina.core
        [clojure.walk :only [keywordize-keys]])
  (:import java.util.UUID
           java.net.ConnectException))

(def uuid (str (UUID/randomUUID)))

(def current (agent nil))

(defn job-results-channel
  "Returns a channel suitable for hooking up to start-edge. It will route all results through the network connection with the proper metadata and message formatting"
  [{uuid :uuid :as job} conn]
  (when (not uuid)
    (throw (Exception. (str "Missing UUID for job!" job))))
  (let [ch (channel* :permanent? true :grounded? true)]
    (siphon
     (map* (fn jres-map [res] {"name" "job-result" "job-uuid" uuid "body" res}) ch )
     conn)
    ch))

(defn start-job
  "Bridge the streaming results from the job-formula to the connection
   They get routed through a permanent channel to prevent close events from
   propagating"
  [job conn]
  (utils/safe-send-off-with-result current res state
    (log/info (str "Starting job on worker: " job))
    (when-let [{old-fla :formula} state] (formula/stop old-fla))
    (let [res-ch (job-results-channel job conn)
          fla (formula/init-job-formula job)]
      (siphon
       (map* #(hash-map "job-uuid" (:uuid job)"results" %)
             (formula/start-edge fla))
       res-ch)
      (lc/enqueue res res-ch)
      {:job job :formula fla :results-channel res-ch})))


(defn stop-job
  "Stop the current job, setting current to nil"
  []
  (utils/safe-send-off-with-result current res state
    (when-let [{old-fla :formula res-ch :results-channel} state]
      (lc/enqueue res (formula/stop old-fla))
      (lc/close res-ch))
    nil))

(defn handle-message
  [conn {:strs [name body] :as msg}]
  (try
    (let [body (keywordize-keys body)] ;FIXME Keywordizing = bad
      (condp = name
        "job-start" (start-job body conn)
        "job-stop" (stop-job)
        (log/warn (str "Worker received unexpected msg" msg))))
    (catch Throwable t
      (log/warn t (str "Worker could not handle message!" msg)))))

(defn client-connect
  [host port]
  (try
    (let [conn @(nc/client-connect host port)]
      (log/info "Connected to relay!")
      
      (on-closed conn (fn []
                        (stop-job)
                        (log/warn "Connection to master closed! Reconnecting in 5s")
                        (Thread/sleep 5000)
                        (when (:reconnect settings/all) (client-connect host port))))
      
      ;; Send identity immediately
      (enqueue conn {"name" "uuid" "body" uuid})

      (on-error conn (fn [e] (log/warn e "Server Channel Error!") ))
      (receive-all conn (partial handle-message conn))
      conn)
    (catch java.net.ConnectException e
      (log/warn e "Could not connect to control server! Reconnecting in 5s")
      (Thread/sleep 5000)
      (when (:reconnect settings/all) (client-connect host port)))))

(defn start
  "Starts the worker client. Should be done once per process max."
  [host port]
  (client-connect host port))