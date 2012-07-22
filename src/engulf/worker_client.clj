(ns engulf.worker-client
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

(def current (agent nil))

(defn start-job
  "Bridge the streaming results from the job-formula to the connection
   They get routed through a permanent channel to prevent close events from
   propagating"
  [job conn]
  (let [res (lc/result-channel)]
    (send-off
     current
     (fn [current]
       (try
         (let [pc (permanent-channel)
               fla (formula/init-job-formula job)]
           (siphon pc conn)
           (siphon (formula/start-edge fla) pc)
           (lc/enqueue res pc)
           {:job job :formula fla})
         (catch Exception e
           (log/warn e "Could not start job")
           (lc/error res e)
           nil))))
    res))

(defn- stop-job-derefed
  "Stops the job indicated by a derefereced version of current"
  [{f :formula j :job}]
  (try
    (when f (formula/stop f))
    (catch Exception e
      (log/warn e "Could not stop job!"))))

(defn stop-job
  "Stop the current job, setting current to nil"
  []
  (send-off current stop-job-derefed))

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