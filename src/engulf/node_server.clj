(ns engulf.node-server
  (:require [engulf.comm.control :as ctrl]
            [lamina.core :as lc]))

(defn start-router
  []
  (lc/receive-all engulf.comm.control/emitter
   (fn message-router [[name body]]
     (condp = name
       "results" ()))))

(defn start
  []
  (start-router)
  (ctrl/start-server))

