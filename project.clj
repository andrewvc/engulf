(defproject parbench "1.3.2"
  :description "Parallel HTTP Benchmarker/Visualizer"
  :main parbench.core
  :repositories {"Sonatype" "https://oss.sonatype.org/content/repositories/releases/"}
  :dependencies [[org.clojure/clojure "1.2.1"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [com.ning/async-http-client "1.6.2"]
                 [org.slf4j/slf4j-api "1.6.1"]
                 [org.slf4j/slf4j-log4j12 "1.6.1"]
                 [log4j/log4j "1.2.16"]
                 [org.clojars.automata/rosado.processing "1.1.0"]])
