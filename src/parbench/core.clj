(ns parbench.core
  (:require [parbench.requests-state :as rstate]
            [parbench.displays       :as displays]
            [parbench.benchmark     :as benchmark]))
  
(defn- run-state-displays
  "Initializes displays, this is where things like GUI warmup can happen"
  [reqs-state opts]
  (displays/console reqs-state opts))
 
(defn -main [& args]
  (println "ohai!"))
