(ns engulf.control
  (:require [engulf.comm.node-manager :as n-manager]
            [lamina.core :as lc])
  (:use [clojure.walk :only [keywordize-keys]]))

(defn start-router
  []
  (lc/receive-all
   n-manager/emitter
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
  (n-manager/start-server 3493))

(defn broadcast
  [name & body]
  (lc/enqueue n-manager/receiver [name body]))