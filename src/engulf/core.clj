(ns engulf.core
  (:require [engulf.settings :as settings]
            [engulf.database :as database]
            [engulf.job-manager :as jmgr]
            [engulf.migrations :as migrations]
            [engulf.control :as ctrl]
            [engulf.worker-client :as w-client]
            [engulf.relay :as relay]
            [engulf.web-server :as w-server]
            [clojure.tools.logging :as log])
  (:use [clojure.tools.cli :only [cli]]
        [clojure.string :only [split join]]))

(defn parse-args
  [args]
  (cli args
       ["-p" "--http-port" "Listen on this port for the HTTP UI"
        :parse-fn #(Integer. %) :default (:http-port settings/all)]
       ["-n" "--manager-port" "TCP Port for manager to listen on"
        :parse-fn #(Integer. %) :default (:manager-port settings/all)]
       ["-m" "--mode" "{combined:master:worker}"
        :parse-fn keyword :default (:mode settings/all)]
       ["-c" "--connect-to" "When in worker mode, connect to this TCP host:port"
        :parse-fn #(let [[h p] (split % #":")] [h (Integer/valueOf p)]) :default (:connect-to settings/all)]
       ["-h" "--help" "Show help, then exit" :default false :flag true]))
  
(defn -main [& args]
  (let [[opts args banner] (parse-args args)]
    (when (:help opts) (println banner) (System/exit 0))
    ;; These really will only change *once*
    (alter-var-root (var settings/all) (fn [s] (merge s opts))))
  (log/info "Initializing in" (:mode settings/all) " mode")

  (when (#{:combined :master} (:mode settings/all))
    (log/info "Connecting to DB")
    (database/connect)
    
    (log/info "Checking for DB Migrations")
    (migrations/ensure-all)
    
    (log/info "Starting webserver on port" (:http-port settings/all))
    (w-server/start-webserver (:http-port settings/all))
    
    (log/info "Starting control on port" (:manager-port settings/all))
    (ctrl/start (:manager-port settings/all))

    (log/info "Starting relay")
    (relay/start))

  (when (#{:combined :worker} (:mode settings/all))
    (log/info "Connecting worker to" (join ":"  (:connect-to settings/all)))
    (apply w-client/start (:connect-to settings/all)))
  
  (log/info "Done initializing!"))