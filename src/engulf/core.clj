(ns engulf.core
  (:require [engulf.settings :as settings]
            [engulf.database :as database]
            [engulf.utils :as utils]
            [engulf.job-manager :as jmgr]
            [engulf.migrations :as migrations]
            [engulf.control :as ctrl]
            [engulf.worker-client :as w-client]
            [clojure.tools.nrepl.server :as nrepl-srv]
            [engulf.relay :as relay]
            [engulf.web-server :as w-server]
            [clojure.tools.logging :as log])
  (:use [clojure.tools.cli :only [cli]]
        [clojure.string :only [split join]])
  (:gen-class))

(defn parse-args
  [args]
  (cli args
       ["--http-port" "Listen on this port for the HTTP UI"
        :parse-fn #(Integer. %) :default (:http-port settings/all)]
       ["--manager-port" "TCP Port for manager to listen on"
        :parse-fn #(Integer. %) :default (:manager-port settings/all)]
       ["--mode" "{combined:master:worker}"
        :parse-fn keyword :default (:mode settings/all)]
       ["--connect-to" "When in worker mode, connect to this TCP host:port"
        :parse-fn #(let [[h p] (split % #":")] [h (Integer/valueOf p)]) :default (:connect-to settings/all)]
       ["--help" "Show help, then exit" :default false :flag true]))
  
(defn -main [& args]
  (let [[opts args banner] (parse-args args)]
    (when (:help opts) (println banner) (System/exit 0))
    ;; These really will only change *once*
    (alter-var-root (var settings/all) (fn [s] (merge s opts))))
  (log/info (str "Initializing Engulf " (utils/version) " in " (:mode settings/all) " mode"))

  (when (not (#{:combined :master :worker} (:mode settings/all)))
    (binding [*out* *err*]
      (println "Aborting! Invalid mode option: " (:mode settings/all))
      (System/exit 1)))

  (when (#{:combined :master} (:mode settings/all))
    (log/info "Starting nrepl server on port 7888")
    (nrepl-srv/start-server :port 7888)
    
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

  (log/info "Done initializing!")

  (when (#{:combined :master} (:mode settings/all))
    (let [message (format "* Just Point your browser to http://localhost:%s/ *" (:http-port settings/all))
          border (str (reduce str (repeat (count message) "*")))]
      (println "\n\n")
      (println border)
      (println message)
      (println border)
      (println "\n\n"))))