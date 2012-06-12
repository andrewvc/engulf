(ns engulf.comm.control
  (:require [lamina.core :as lc]
            [engulf.comm.netchan :as nc]
            [clojure.tools.logging :as log]))

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
      (lc/enqueue node-ch [ :system "new-node" new-node]))
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
  (println "GOT MESSAGE!")
  (println name body)
  (try
    (cond
     (and (nil? @uuid-atom) (= "uuid" name))
     (reset! uuid-atom (register-node body conn))
     @uuid-atom
     (lc/enqueue node-ch [node name body])
     :else
     (log/warn (str "Unexpected non-identity message received" name body)))
    (catch Exception e (log/warn e "Error Handling Message"))))

(defn start-server
  [port]
  (nc/start-server
   port
   (fn [conn client-info]
     (let [uuid (atom nil)]
       (lc/on-error conn (fn [e] (log/warn e "Server Channel Error!") ))
       (lc/receive-all conn (fn [m] (server-handler m uuid conn)))))))