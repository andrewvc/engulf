(defproject parbench "1.0.0-SNAPSHOT"
  :description "Parallel HTTP Visualizer"
  :main parbench.core
  :aot [parbench.core]
  :dependencies [[org.clojure/clojure "1.2.1"]
                 [clj-http            "0.1.3"]
                 [org.clojars.automata/rosado.processing "1.1.0"]])
