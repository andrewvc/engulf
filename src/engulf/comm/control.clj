(ns engulf.comm.control
  (:require [engulf.messages :as msgs]
            [lamina.core :as lc])
  (:use [noir-async.core :only [on-close on-receive async-push]]
        [engulf.comm.message]))

;; Permanent channel streaming events from all nodes
;; Dynamic for testing
(def ^:dynamic node-ch (lc/permanent-channel))

(def nodes (ref {}))

(defn node
  [uuid]
  {:uuid uuid})

(defn get-node
  [uuid]
  (@nodes uuid))

(defn register-node
  [uuid]
  "Create a new node and adds it the global list of available nodes.
   Returns nil if the node already exists"
  (let [new-node (dosync
                  (if-let [n (get-node uuid)]
                    nil
                    (let [n (node uuid)]
                      (get (alter nodes assoc uuid n)
                           uuid))))]
    (when (not (nil? new-node))
      (lc/enqueue node-ch {:type "new-node" :body new-node}))
    new-node))
          
(defn deregister-node
  "Removes a node from the global list of nodes"
  [uuid]
  (dosync (alter nodes dissoc uuid)))

(defn handle-message
  [node raw-msg]
  (let [tagged-msg (assoc (parse-msg raw-msg) :node node)]
    (lc/enqueue node-ch tagged-msg)
    tagged-msg))

(defn connect
  "Convenience method for wiring into noir-async.
   Pass in the UUID (presumably a URL param) and the conn object, and this guy handles the rest"
  [uuid conn]
  (on-receive conn (fn c-dispatch [raw-msg] (handle-message uuid raw-msg)))
  (on-close conn (fn c-close [] (deregister-node uuid))))