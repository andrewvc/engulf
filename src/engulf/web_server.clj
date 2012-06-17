(ns engulf.web-server
  (:use aleph.http
        noir.core
        lamina.core)
  (:require
    [noir.server :as nr-server] ))

(defn start-webserver [port]
  (let [noir-handler (nr-server/gen-handler :dev)]
    (start-http-server
      (wrap-ring-handler noir-handler)
      {:port port :websocket true})))