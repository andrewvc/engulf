(defproject parbench "1.2.0"
  :description "Parallel HTTP Visualizer"
  :main parbench.core
  :jvm-opts ["-Xss256k"]
  :repositories {"Sonatype" "https://oss.sonatype.org/content/repositories/releases/"}
  :dependencies [[org.clojure/clojure "1.2.0"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [com.ning/async-http-client "1.6.2"]
                 [org.clojars.automata/rosado.processing "1.1.0"]])
