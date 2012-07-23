(ns engulf.comm.node-manager
  (:require [lamina.core :as lc]
            [engulf.comm.netchan :as nc]
            [clojure.tools.logging :as log]))


(def ^:dynamic receiver (lc/channel* :grounded? true :permanent? true))
(def ^:dynamic emitter (lc/channel* :grounded? true :permanent? true))

(def nodes (ref {}))

(defn count-nodes []
  "Returns the current number of connected nodes"
  (count (keys @nodes)))

(defn node
  [uuid conn]
  (let [ch (lc/channel)]
    (lc/receive-all ch (fn node-ground [_]))
    {:uuid uuid
     :conn conn}))

(defn get-node
  [uuid]
  (@nodes uuid))

(defn register-node
  "Create a new node and adds it the global list of available nodes.
   Returns nil if the node already exists"
  [uuid conn]
  (let [new-node (dosync
                  (if-let [n (get-node uuid)]
                    nil
                    (let [n (node uuid conn)]
                      (get (alter nodes assoc uuid n)
                           uuid))))]
    (when (not (nil? new-node))
      (lc/enqueue emitter {"entity" "system"
                           "name" "node-connect"
                           "body" {"uuid" uuid "conn" conn}}))
    new-node))
          
(defn deregister-node
  "Removes a node from the global list of nodes"
  [node]
  (lc/close (:conn node))
  (when-let [r (dosync (alter nodes dissoc (:uuid node)))]
    (lc/enqueue emitter {"entity" "system"
                         "name" "node-disconnect"
                         "body" {"uuid" (:uuid node)}})
    r))

(defn deregister-node-by-uuid
  [uuid]
  (if-let [n (get @nodes uuid)]
    (deregister-node n)
    (log/warn (str "Not deregistering node " uuid " not in list!"))))

(defn conn-handler
  [{:strs [name body] :as msg} uuid-atom conn]
  (try
    (cond
     ;; Handle the initial UUID message
     (and (nil? @uuid-atom) (= "uuid" name)) (reset! uuid-atom (:uuid (register-node body conn)))
     ;; Handle all subsequent messages
     @uuid-atom (lc/enqueue emitter {"entity" @uuid-atom "name" name "body" body})
     ;; Send warnings when normal messages sent before a UUID
     :else (log/warn (str "Unexpected non-identity message received: " msg)))
    (catch Exception e
      (log/warn e (str "Error Handling Message: " msg)))))

(defn server-handler
  [conn client-info]
  (let [uuid (atom nil)]
    ;; Send broadcast messages to the client
    (lc/receive-all receiver (fn [m] (lc/enqueue conn m)))
    (lc/receive-all conn (fn [m] (conn-handler m uuid conn)))
    (lc/on-error    conn (fn [e] (log/warn e "Server Channel Error!") ))
    (lc/on-closed   conn (fn []  (deregister-node-by-uuid @uuid)))))

(defn start-server
  [port]
  (let [nc (nc/start-server port server-handler)]
    ;; Stop the server when this is called
    (fn []
      (dosync
       (doseq [n (vals @nodes)] (deregister-node n)))
      (nc))))