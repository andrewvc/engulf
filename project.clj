(defproject parbench "1.3.2"
  :description "Parallel HTTP Benchmarker/Visualizer"
  :main parbench.core
  :repositories {"Sonatype" "https://oss.sonatype.org/content/repositories/releases/"}
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.clojure/tools.cli "0.2.1"]
                 [noir-async "0.1.0-SNAPSHOT2"]
                 [cheshire "2.0.4"]
                 [org.clojure/tools.logging "0.2.3"]
                 ])
