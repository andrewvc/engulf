(ns engulf.test.comm.worker-node-manager-integration-test
  (:require [engulf.comm.node-manager :as n-manager]
            [engulf.comm.worker-client :as wc]
            [lamina.core :as lc]
            [cheshire.core :as chesh])
  (:use midje.sweet
        [clojure.walk :only [keywordize-keys]]))

(facts
 "about workers connecting"
 (let [port (+ 10000 (int (rand 20000)))
       server (n-manager/start-server port)
       wrkr-ch (wc/client-connect "localhost" port)]
   (fact "Connections should complete without error"
         (Thread/sleep 500) => nil)
   (fact
    "worker connect should return a channel"
    wrkr-ch => lc/channel?)
   (fact
    "There should be one node, and it should be the worker"
    (keys @n-manager/nodes) => [wc/uuid])
   (fact
    "closing the worker channel should remove the worker from the server as well"
    (lc/close wrkr-ch)
    ;; Needs to be in a separate thread to be reliable
    ;; otherwise wrkr-ch's close doesn't propagate
    (future (Thread/sleep 500)
            @n-manager/nodes => empty?))
   (fact
    "re-opening a connection to the server should add a client once again"
    (wc/client-connect "localhost" port)
    (keys @n-manager/nodes) => [wc/uuid])
   ;; stop the server
   (server)
   (Thread/sleep 300)
   (fact
    "There should no longer be any nodes"
    @n-manager/nodes => empty?)
   (Thread/sleep 200)))