(ns parbench.core
  (:gen-class)
  (:require [parbench.requests-state :as rstate]
            [parbench.displays       :as displays]
            [parbench.benchmark     :as benchmark])
  (:use [clojure.contrib.command-line]))

(defn- run-state-displays [reqs-state opts]
  (if (not (:cli-only? opts))
    (displays/status-code-gui reqs-state opts))
  (displays/console reqs-state opts))

(defn -main [& args]
  (with-command-line
    args
    "Usage: [OPTIONS] -u http://example.net "
    [[cli-only?   k? "Command Line Only" false]
     [concurrency c "Number of Workers" 100]
     [requests    r "Number of requests per worker" 200]
     [scale       g "Pixel Size of GUI Squares" 2]
     [url         u "URL to benchmark"]]
    (let [concurrency (Integer/valueOf concurrency)
          requests    (Integer/valueOf requests)
          reqs-state  (rstate/create-blank requests concurrency (fn [col row] url) )
          scale       (Integer/valueOf scale)
          opts {
            :url         url
            :cli-only?   cli-only?
            :concurrency concurrency
            :requests    requests
            :scale        scale}]
      (cond (not url)
        (do
          (println "You must specify a URL! See -h for options")
          (System/exit 1)))
      (println "Initializing displays")
      (run-state-displays reqs-state opts)
      (str "Starting run against " url
        " with concurrency: " concurrency
        " and requests: " requests)
      (benchmark/run  reqs-state opts)
      "Run Complete")))
