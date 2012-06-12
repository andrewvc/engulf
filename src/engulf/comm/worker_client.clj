(ns engulf.comm.worker-client
  (:require
   [clojure.tools.logging :as log]
   [engulf.comm.netchan :as nc])
  (:use lamina.core)
  (:import java.util.UUID
           java.net.ConnectException))

(def uuid (str (UUID/randomUUID)))

(defn handle-message
  [msg]
  (try
    (println "Received message" msg)
    (catch Exception e (log/warn e "Could not handle message!"))))
    

(defn client-connect
  [host port]
  (try
    (let [conn @(nc/client-connect host port)]
      (on-closed conn (fn [] (log/warn "Connection to master closed!")))
      (on-error conn (fn [e] (log/warn e "Server Channel Error!") ))
      (receive-all conn (fn client-conn-handler [msg] (handle-message msg)))
      ;; Send identity immediately
      (enqueue conn ["uuid" uuid])
      conn)
    (catch java.net.ConnectException e
      (log/warn e "Could not connect to control server!"))))