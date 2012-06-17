(ns engulf.core
  (:require [engulf.control :as ctrl]
            [engulf.comm.worker-client :as w-client]
            [engulf.web-server :as w-server])
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
       ["-p" "--http-port" "Listen on this por for HTTP"
        :parse-fn #(Integer. %) :default (:http-port settings)]
       ["-n" "--manager-port" "Port for manager to listen on"
        :parse-fn #(Integer. %) :default (:manager-port settings)]
       ["-m" "--mode" "{combined:master:worker}"
        :parse-fn keyword :default (:mode settings)]
       ["-c" "--connect-to" "When in worker mode, connect to this master host:port"
        :parse-fn #(let [[h p] (split % #":")] [h (Integer/valueOf p)]) :default (:connect-to settings)]
       ["-h" "--help" "Show help, then exit" :default false :flag true]))
  
(defn -main [& args]
  (let [[opts args banner] (parse-args args)]
    (when (:help opts) (println banner) (System/exit 0))
    ;; These really will only change *once*
    (alter-var-root (var settings) (fn [s] (merge s opts))))
  (println "Initializing in" (:mode settings) " mode")

  (when (#{:combined :server} (:mode settings))
    (println "Starting webserver on port" (:http-port settings))
    (w-server/start-webserver (:http-port settings))
    
    (println "Starting manager on port" (:manager-port settings))
    (ctrl/start (:manager-port settings)))

  (when (#{:combined :client} (:mode settings))
    (println "Connecting worker to" (join ":"  (:connect-to settings)))
    (apply w-client/client-connect (:connect-to settings)))
  
  (println "Done initializing!"))