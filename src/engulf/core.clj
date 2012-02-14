(ns engulf.core
  (:gen-class)
  (:require [engulf.benchmark :as benchmark]
            [noir.server :as nr-server])
  (:use aleph.http
        noir.core
        lamina.core))

(defn start-webserver [args]
  (nr-server/load-views "src/engulf/views")
   
  (let [mode (keyword (or (first args) :prod))
          port (Integer. (get (System/getenv) "PORT" "3000"))
          noir-handler (nr-server/gen-handler {:mode mode})]
      (start-http-server
        (wrap-ring-handler noir-handler)
        {:port port :websocket true})))
 
(defn -main [& args]
  (start-webserver args)
  (println "Engulf Started!"))
