(ns engulf.control
  (:require [engulf.messages :as msgs]
            [cheshire.core :as chesh]
            [lamina.core :as lc])
  (:use [noir-async.core :only [on-close on-receive async-push]]
        [clojure.walk :only [keywordize-keys]]))

(defn encode-msg
  [msg]
  {:pre [(not= nil (or (msg "name") (msg :name)))
         (not= nil (or (msg "body") (msg :name)))]}
  (chesh/encode-smile msg))

(defn parse-msg
  [msg]
  {:post [(not= nil (or (% "name") (% :name)))
          (not= nil (or (% "body") (% :name)))]}
  "Parses a msg, ensures it's properly formatted as well"
  (keywordize-keys (chesh/parse-smile msg)))
  
;; Permanent channel streaming events from all nodes
(def node-ch (lc/permanent-channel))

(def nodes (ref {}))

(defn node
  [uuid]
  {:uuid uuid})

(defn get-node
  [uuid]
  (@nodes uuid))

(defn create-node
  [uuid]
  "Create a new node. Returns nil if the node already exists"
  (let [new-node (dosync
                  (if-let [n (get-node uuid)]
                    nil
                    (let [n (node uuid)]
                      (get (alter nodes assoc uuid n)
                           uuid))))]
    (when (not (nil? new-node))
      (lc/enqueue node-ch {:name "new-node" :body new-node}))
    new-node))
          
(defn remove-node
  [uuid]
  (dosync (alter nodes dissoc uuid)))

(defn handle-message
  [node raw-msg]
  (let [node (get-node (node :uuid))
        msg (parse-msg raw-msg)]
    (lc/enqueue node-ch msg)))

(defn connect [uuid conn]
  (on-receive conn (fn c-dispatch [raw-msg] (handle-message uuid raw-msg)))
  (on-close conn (fn c-close [] (remove-node uuid))))
