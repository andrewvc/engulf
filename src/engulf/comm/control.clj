(ns engulf.comm.control
  (:require [engulf.messages :as msgs]
            [lamina.core :as lc]
            [engulf.comm.netchan :as nc]
            [clojure.tools.logging :as log])
  (:use [engulf.comm.message]))

;; Permanent channel streaming events from all nodes
;; Dynamic for testing
(def ^:dynamic node-ch (lc/permanent-channel))
(lc/receive-all node-ch (fn global-node-ground [_]))

(def nodes (ref {}))

(defn node
  [uuid conn]
  (let [ch (lc/channel)]
    (lc/receive-all ch (fn node-ground [_]))
    {:uuid uuid
     :conn conn
     :channel ch}))

(defn get-node
  [uuid]
  (@nodes uuid))

(defn register-node
  [uuid conn]
  "Create a new node and adds it the global list of available nodes.
   Returns nil if the node already exists"
  (let [new-node (dosync
                  (if-let [n (get-node uuid)]
                    nil
                    (let [n (node uuid conn)]
                      (get (alter nodes assoc uuid n)
                           uuid))))]
    (when (not (nil? new-node))
      (lc/enqueue node-ch {:type "new-node" :body new-node}))
    new-node))
          
(defn deregister-node
  "Removes a node from the global list of nodes"
  [node]
  (lc/close (:channel node))
  (dosync (alter nodes dissoc (:uuid node))))

(defn handle-message
  [node msg]
  (let [tagged-msg (assoc msg :node node)]
    (lc/enqueue node-ch tagged-msg)
    (lc/enqueue (:channel node) tagged-msg)
    tagged-msg))

(defn server-handler
  [msg uuid-atom]
  (println "GOT MESSAGE!")
  (println (String. msg))
  (try
    (let [parsed (parse-msg msg)]
      (cond
       (and (nil? @uuid-atom) (= "uuid" (:type parsed)))
         (reset! uuid-atom (:body parsed))
       @uuid-atom
         (handle-message @uuid-atom parsed)
       :else
       (log/warn (str "Unexpected non-identity message received" parsed))))
    (catch Exception e (log/warn e "Error Handling Message"))))

(defn start-server
  [port]
  (nc/start-server
   port
   (fn [ch client-info]
     (let [uuid (atom nil)]
       (lc/on-error ch (fn [e] (log/warn e "Server Channel Error!") ))
       (lc/receive-all
        ch
        (fn [m] (server-handler m uuid)))))))