(defproject engulf "2.0.0-ALPHA"
  :description "HTTP Benchmarker/Visualizer"
  :main engulf.core
  :aot [engulf.core]
  :jvm-opts ["-server", "-Xmx2000M"]
  :repositories {"Sonatype" "https://oss.sonatype.org/content/repositories/releases/"}
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.clojure/tools.cli "0.2.1"]
                 [noir-async "0.1.2"]
                 [cheshire "2.0.4"]
                 [log4j/log4j "1.2.16"]
                 [org.slf4j/slf4j-simple "1.6.4"]
                 [com.ning/async-http-client "1.7.0"]
                 [http.async.client "0.4.1"]
                 [org.clojure/tools.logging "0.2.3"]])
