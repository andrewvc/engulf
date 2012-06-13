(ns engulf.test.comm.control
  (:require [engulf.comm.control :as ctrl]
            [lamina.core :as lc]
            [cheshire.core :as chesh])
  (:use midje.sweet
        [clojure.walk :only [keywordize-keys]]))

(defmacro with-clean-emitter
  "Used in testing to reset currently queued messages"
  [& body]
  `(binding [ctrl/emitter (lc/permanent-channel)]
     ~@body))

(defn clear-nodes
  []
  (dosync (ref-set ctrl/nodes {})))

(facts
 "about registering nodes"
 (with-clean-emitter
   (clear-nodes)
   (let [ident "a-unique-identifier"
         n  (ctrl/register-node ident {})]
     (fact
      "the node should have the right uuid"
      (:uuid n) => ident)
     (fact
      "the node should include its connection"
      (:conn n) =not=> nil?)
     (fact
      "the node should have enqueued a creation message"
      (lc/receive ctrl/emitter
                  (fn [msg]
                    msg => [:system "new-node" n] )))))
 (facts
   "for nodes that do currently exist"
   (let [ident "some-unique-id"
         existing  (ctrl/register-node ident {})]
     (fact
      "calling create should return nil"
      (ctrl/register-node ident {}) => nil))))

(facts
 "about removing nodes"
 (clear-nodes)
 (facts
  "for nodes that don't yet exist"
  (let [ident "a-unique-identifier"
        n (ctrl/register-node ident (lc/channel))]
    (fact
     "the node should no longer be present after removal"
     (ctrl/get-node ident) =not=> nil
     (ctrl/deregister-node n)
     (ctrl/get-node ident) => nil))))

(facts
 "about starting and stopping servers"
 (let [port (+ 10000 (int (rand 20000)))
       srv (ctrl/start-server port)]
   (fact "the server should start" srv => truthy)
   (fact "the server should stop" (srv) => truthy)))