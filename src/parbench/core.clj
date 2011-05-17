(ns parbench.core
  (:gen-class)
  (:require [parbench.requests-state :as rstate]
            [parbench.displays       :as displays]
            [parbench.benchmarks     :as benchmarks])
  (:use [clojure.contrib.command-line]))

(defn- run-state-displays [reqs-state opts]
  (if (not (:cli-only? opts))
    (displays/status-code-gui reqs-state opts))
  (displays/console reqs-state opts))

(defn -main [& args]
  (with-command-line
    args
    "Usage: [OPTIONS] http://example.net "
    [[cli-only?   k? "Command Line Only" false]
     [concurrency c "Number of Workers" 50]
     [requests    r "Number of requests per worker" 100]
     [scale       g "Pixel Size of GUI Squares" 7]]

    (let [concurrency (Integer/valueOf concurrency)
          requests    (Integer/valueOf requests)
          reqs-state  (rstate/create-blank requests concurrency #(last args))
          opts {
            :url         (last args)
            :cli-only?   cli-only?
            :concurrency concurrency
            :requests    requests
            :scale        scale}]
      (run-state-displays reqs-state opts)
      (. Thread (sleep 1500)) ; Let the program warm up before running
      (benchmarks/user-agents  reqs-state opts)
      "Starting")))