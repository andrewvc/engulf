(ns engulf.test.comm.netchan
  (:require [engulf.comm.netchan :as nc])
  (:use midje.sweet
        lamina.core
        gloss.io)
  (:import java.net.ConnectException))

(facts
 "about starting and stopping servers"
 (let [handler (fn [ch info] (receive-all ch (fn [m]  (enqueue ch m))))
       port (+ 10000 (int (rand 20000)))
       srv (nc/start-server port handler)]
   (fact "the server should start" srv => truthy)
   (fact "the server should stop" (srv) => truthy)))

;; We'll just do integration tests for now, since I'm lazy
(facts
 "about sending messages from client to server"
 (let [handler (fn [ch info] (receive-all ch (fn [m] (enqueue ch m)))) 
       port (+ 10000 (int (rand 20000)))
       srv (nc/start-server port handler)]
   (fact
    "the client should raise an error when connecting to a non-existant server"
    (let [c (nc/client-connect "localhost" 1828)]
      (try
        @c
        true => :a_val_we_should_never_see
        (catch Exception e
          (class e) => java.net.ConnectException))))
   (facts
    "about successful connections"
    (let [conn @(nc/client-connect "localhost" port)]
      (fact
       "it should return a connection channel"
       conn => channel?)
      (fact
       "it should send messages without error"
       (enqueue conn ["ohai" "there"]) => truthy)
      (fact
       "it should receive sent messages back"
       (enqueue conn ["ohai" "there"])
       @(read-channel* conn :timeout 1000) => ["ohai" "there"] )))))