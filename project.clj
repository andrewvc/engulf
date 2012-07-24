(defproject engulf/engulf "3.0.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [org.clojure/tools.cli "0.2.1"]
                 [noir-async "1.1.0-beta6"]
                 [cheshire "4.0.0"]
                 [log4j/log4j "1.2.16"]
                 [org.slf4j/slf4j-simple "1.6.4"]
                 [com.ning/async-http-client "1.7.4"]
                 [org.clojure/tools.logging "0.2.3"]]
  :profiles {:dev
             {:jvm-opts ["-agentlib:jdwp=transport=dt_socket,server=y,suspend=n"]
              :dependencies
              [[org.clojure/tools.trace "0.7.3"]
               [midje "1.4.0"]]
              :plugins [[lein-midje "2.0.0-SNAPSHOT"]
                        [lein-swank "1.4.4"]]}}
  :java-source-paths ["java-src"]
  :main ^{:skip-aot true} engulf.core
  :min-lein-version "2.0.0"
  :jvm-opts ["-server"]
  :description "HTTP Benchmarker/Visualizer")
