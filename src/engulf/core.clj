(ns engulf.core
  (:use [clojure.tools.cli :only [cli]]
        [clojure.string :only [split]]))

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
  (println "Starting with settings: " settings))