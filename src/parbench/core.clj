(ns parbench.core
  (:require [parbench.requests-state :as rstate]
            [parbench.benchmark :as benchmark]
            [noir.server :as nr-server])
  (:use aleph.http
        noir.core
        lamina.core))

(defn start-webserver [args]
  (nr-server/load-views "src/parbench/views")
   
  (let [mode (keyword (or (first args) :dev))
          port (Integer. (get (System/getenv) "PORT" "3000"))
          noir-handler (nr-server/gen-handler {:mode mode})]
      (start-http-server
        (wrap-ring-handler noir-handler)
        {:port port :websocket true})))
 
(defn -main [& args]
  (println "OHAI!")
  (start-webserver args))
