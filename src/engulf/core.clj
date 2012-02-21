(ns engulf.core
  (:gen-class)
  (:require [engulf.benchmark :as benchmark]
            engulf.views.benchmarker
            engulf.views.common
            engulf.views.index
            [noir.server :as nr-server])
  (:use aleph.http
        noir.core
        lamina.core))

(defn start-webserver [port mode]
  (start-http-server
   (wrap-ring-handler (nr-server/gen-handler {:mode mode}))
   {:port port :websocket true}))
 
(defn -main [& args]
  (let [mode (keyword (or (first args) :prod))
        port (Integer/valueOf (or (System/getenv "PORT") 4000))]
    (start-webserver port mode)
    (println "Engulf Started on port " port)))
