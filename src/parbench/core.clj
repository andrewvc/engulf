(ns parbench.core
  (:gen-class)
  (:require [parbench.requests-state :as rstate]
            [parbench.displays       :as displays]
            [parbench.benchmarks     :as benchmarks])
  (:use [clojure.contrib.command-line])
  )

(defn- run-state-displays [reqs-state opts]
  (displays/status-code-gui reqs-state opts)
  ;(displays/console-full reqs-state opts)
  ;(displays/console reqs-state opts)
  )

(defn -main [& args]
  (with-command-line
    args
    "Usage: [OPTIONS] http://example.net "
    [[cli-only? k? "Command Line Only" false]
     [concurrency c "Number of Workers" 50]
     [requests    r "Number of requests per worker" 100]
     [scale g "Pixel Size of GUI Squares" 7]]

    (let [concurrency (Integer/valueOf concurrency)
          requests    (Integer/valueOf requests)
          reqs-state  (rstate/create-blank requests concurrency)
          opts {
            :url         (last args)
            :cli-only    cli-only?
            :concurrency concurrency
            :requests    requests
            :scale        scale}]
      (run-state-displays reqs-state opts)
      (benchmarks/agents  reqs-state opts))))