(ns engulf.relay
  (:require
   [engulf.control :as ctrl]
   [clojure.tools.logging :as log]
   [engulf.formula :as formula]
   [lamina.core :as lc]))

(def start-cb (agent nil))

(defn start
  []
  (send start-cb
        (fn [v]
          (when v
            (lc/cancel-callback v))
          (lc/receive-all
           ctrl/broadcast
           (fn [m] (println "GOT M!" m))))))

(defn stop
  []
  (send start-cb
        (fn [v]
          (when v
            (lc/cancel-callback v)))))