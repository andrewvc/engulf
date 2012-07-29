(ns engulf.web-server
  (:use aleph.http
   noir.core
   lamina.core)
  (:require [noir.server :as nr-server]
            engulf.web-views.index
            engulf.web-views.jobs
            engulf.web-views.nodes))

(defn start-webserver [port]
  (let [noir-handler (nr-server/gen-handler :prod)]
    (start-http-server
      (wrap-ring-handler noir-handler)
      {:port port :websocket true})))