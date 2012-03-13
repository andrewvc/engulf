(ns engulf.core
  (:gen-class)
  (:require [engulf.benchmark :as benchmark]
            engulf.views.benchmarker
            engulf.views.common
            engulf.views.index
            engulf.views.test-responses
            [engulf.config :as config]
            [noir.server :as nr-server])
  (:use aleph.http
        noir.core
        lamina.core))

(defn start-webserver [port mode]
  (start-http-server
   (wrap-ring-handler (nr-server/gen-handler {:mode mode}))
   {:port (config/opt :port) :websocket true}))
 
(defn -main [& args]
  (let [mode (keyword (or (first args) :prod))
        port (config/opt :port)]
    (start-webserver port mode)
    (println "Engulf Started on port " port)))
