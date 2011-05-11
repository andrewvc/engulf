(defproject parbench "1.0.0-SNAPSHOT"
  :description "Parallel HTTP Visualizer"
  :main parbench.core
  :jvm-opts ["-server"]
  :dependencies [[org.clojure/clojure "1.2.0"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [diamondap/clj-apache-https  "1.0"]
                 [org.clojars.automata/rosado.processing "1.1.0"]])
