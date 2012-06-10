(ns engulf.comm.worker-client
  (:require
   [clojure.tools.logging :as log]
   [engulf.comm.netchan :as nc])
  (:use lamina.core
        engulf.comm.message)
  (:import java.util.UUID))

(def uuid (str (UUID/randomUUID)))

(defn handle-message
  [msg]
  (try
    (let [parsed (parse-msg msg)]
      (println "Received message" parsed))
    (catch Exception e (log/warn e "Could not handle message!"))))

(defn client-connect
  [host port]
  (let [ch (nc/start-client host port)]
    (on-error ch (fn [e] (log/warn e "Server Channel Error!") ))
    (receive-all ch (fn client-conn-handler [msg] (handle-message msg)))
    (enqueue ch (encode-msg "uuid" uuid))
    ch))