(ns engulf.core
  (:gen-class)
  (:require [engulf.benchmark :as benchmark]
            [noir.server :as nr-server]
            [engulf.config :as config]
            engulf.views.benchmarker
            engulf.views.common
            engulf.views.index
            engulf.views.test-responses)
  (:use [clojure.tools.cli :only [cli]]
        aleph.http
        noir.core
        lamina.core))

(defn start-webserver
  []
  (start-http-server
   (wrap-ring-handler (nr-server/gen-handler {:mode (config/get-opt :mode)}))
   {:port (config/get-opt :port) :websocket true}))
 
(defn -main [& args]
  (let [[opts trailing help]
        (cli args
             ["-p" "--port" "Bind to this port"
              :parse-fn #(Integer. %) :default (config/get-opt :port)]
             ["-m" "--mode" "Noir server mode"
              :parse-fn #(keyword %) :default (config/get-opt :mode)]
             ["-h" "--help" "Print Help" :flag true :default false])]
    (if (not (:help opts))
      (doseq [[k v] opts] (config/set-opt [k] v))
      (do
        (println help)
        (System/exit 1))))
  (start-webserver)
  (println "Engulf Started on port " (config/get-opt :port)))
