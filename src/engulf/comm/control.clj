(ns engulf.comm.control
  (:require [lamina.core :as lc]
            [engulf.comm.netchan :as nc]
            [clojure.tools.logging :as log]))

;; Permanent channel streaming events from all nodes
;; Dynamic for testing
(def ^:dynamic emitter (lc/permanent-channel))
(lc/ground emitter)

(def ^:dynamic receiver (lc/permanent-channel))
(lc/ground receiver)

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
      (lc/enqueue emitter [ :system {:type :new-node :node uuid}]))
    new-node))
          
(defn deregister-node
  "Removes a node from the global list of nodes"
  [node]
  (lc/close (:conn node))
  (dosync (alter nodes dissoc (:uuid node))))

(defn handle-message
  [node name body]
  )

(defn server-handler
  [[name body] uuid-atom conn]
  (try
    (cond
     ;; Handle the initial UUID message
     (and (nil? @uuid-atom) (= "uuid" name))
     (reset! uuid-atom (register-node body conn))
     ;; Handle all subsequent messages
     @uuid-atom
     (lc/enqueue emitter [node name body])
     ;; Send warnings when normal messages sent before a UUID
     :else (log/warn (str "Unexpected non-identity message received" name body)))
    (catch Exception e (log/warn e "Error Handling Message"))))

(defn start-server
  [port]
  (let [nc (nc/start-server
            port
            (fn [conn client-info]
              (let [uuid (atom nil)]
                (lc/receive-all receiver
                               (fn [m]
                                 (lc/enqueue conn m)))
                (lc/on-error conn (fn [e] (log/warn e "Server Channel Error!") ))
                (lc/receive-all conn (fn [m] (server-handler m uuid conn))))))]
    ;; Stop the server when this is called
    (fn []
      (dosync
       (doseq [n (vals @nodes)] (deregister-node n)))
      (nc))))