(ns engulf.test.comm.node-manager-test
  (:require [engulf.comm.node-manager :as n-manager]
            [lamina.core :as lc]
            [cheshire.core :as chesh])
  (:use midje.sweet
        [clojure.walk :only [keywordize-keys]]))

(defmacro with-clean-emitter
  "Used in testing to reset currently queued messages"
  [& body]
  `(binding [n-manager/emitter (lc/permanent-channel)]
     ~@body))

(defn clear-nodes
  []
  (dosync (ref-set n-manager/nodes {})))

(facts
 "about registering nodes"
 (with-clean-emitter
   (clear-nodes)
   (let [ident "a-unique-identifier"
         n  (n-manager/register-node ident {} {"address" "127.0.0.1"})]
     (fact
      "the node should have the right uuid"
      (:uuid n) => ident)
     (fact
      "the node should include its connection"
      (:conn n) =not=> nil?)
     (fact
      "the node should have enqueued a creation message"
      (lc/receive n-manager/emitter
                  (fn [msg]
                    msg => {"entity" "system"
                            "name" "node-connect"
                            "body" {"uuid" (:uuid n)}})))))
 (facts
   "for nodes that do currently exist"
   (let [ident "some-unique-id"
         existing  (n-manager/register-node ident {} {"address" "127.0.0.1"})]
     (fact
      "calling create should return nil"
      (n-manager/register-node ident {} {}) => nil))))

(facts
 "about removing nodes"
 (clear-nodes)
 (facts
  "for nodes that don't yet exist"
  (let [ident "a-unique-identifier"
        n (n-manager/register-node ident (lc/channel) {})]
    (fact
     "the node should no longer be present after removal"
     (n-manager/get-node ident) =not=> nil
     (n-manager/deregister-node n)
     (n-manager/get-node ident) => nil))))

(facts
 "about starting and stopping servers"
 (let [port (+ 10000 (int (rand 20000)))
       srv (n-manager/start-server port)]
   (fact "the server should start" srv => truthy)
   (fact "the server should stop" (srv) => truthy)))