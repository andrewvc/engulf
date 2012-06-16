(ns engulf.node-server
  (:require [engulf.comm.control :as ctrl]
            [lamina.core :as lc])
  (:use [clojure.walk :only [keywordize-keys]]))

(defn start-router
  []
  (lc/receive-all
   engulf.comm.control/emitter
   (fn message-router [[name body]]
     (let [name (keyword name)
           body (keywordize-keys body)]
       (condp = name
         :results (println "Got results!")
         :system (println "System message: " body)
         (println "Got something unexpected!" name body))))))

(defn start
  []
  (start-router)
  (ctrl/start-server 3493))

