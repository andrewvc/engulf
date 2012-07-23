(ns engulf.core
  (:require [engulf.control :as ctrl]
            [engulf.worker-client :as w-client]
            [engulf.relay :as relay]
            [engulf.web-server :as w-server]
            [clojure.tools.logging :as log])
  (:use [clojure.tools.cli :only [cli]]
        [clojure.string :only [split join]]))

(def ^:dynamic settings
  {:http-port 4000
   :manager-port 4025
   :host "localhost"
   :mode :combined
   :connect-to ["localhost" 4025]})

(defn parse-args
  [args]
  (cli args
       ["-p" "--http-port" "Listen on this port for the HTTP UI"
        :parse-fn #(Integer. %) :default (:http-port settings)]
       ["-n" "--manager-port" "TCP Port for manager to listen on"
        :parse-fn #(Integer. %) :default (:manager-port settings)]
       ["-m" "--mode" "{combined:master:worker}"
        :parse-fn keyword :default (:mode settings)]
       ["-c" "--connect-to" "When in worker mode, connect to this TCP host:port"
        :parse-fn #(let [[h p] (split % #":")] [h (Integer/valueOf p)]) :default (:connect-to settings)]
       ["-h" "--help" "Show help, then exit" :default false :flag true]))
  
(defn -main [& args]
  (let [[opts args banner] (parse-args args)]
    (when (:help opts) (println banner) (System/exit 0))
    ;; These really will only change *once*
    (alter-var-root (var settings) (fn [s] (merge s opts))))
  (log/info "Initializing in" (:mode settings) " mode")

  (when (#{:combined :master} (:mode settings))
    (log/info "Starting webserver on port" (:http-port settings))
    (w-server/start-webserver (:http-port settings))
    
    (log/info "Starting control on port" (:manager-port settings))
    (ctrl/start (:manager-port settings))

    (log/info "Starting relay")
    (relay/start))

  (when (#{:combined :worker} (:mode settings))
    (log/info "Connecting worker to" (join ":"  (:connect-to settings)))
    (apply w-client/start (:connect-to settings)))
  
  (log/info "Done initializing!"))