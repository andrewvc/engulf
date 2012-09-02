(defproject engulf/engulf "3.0.0-beta8"
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [org.clojure/tools.cli "0.2.1"]
                 [noir-async "1.1.0-beta9"]
                 [cheshire "4.0.0"]
                 [org.slf4j/slf4j-simple "1.6.6"]
                 [org.xerial/sqlite-jdbc "3.7.2"]
                 [korma "0.3.0-beta9"]
                 [ragtime "0.2.1"]
                 [ragtime/ragtime.sql "0.2.1"]
                 [org.clojure/tools.nrepl "0.2.0-beta9"]
                 [org.clojure/tools.logging "0.2.3"]]
  :profiles {:dev
             {
              ;:jvm-opts ["-agentlib:jdwp=transport=dt_socket,server=y,suspend=n"]
              :dependencies
              [[org.clojure/tools.trace "0.7.3"]
               [midje "1.4.0"]]
              :plugins [[lein-midje "2.0.0-SNAPSHOT"]
                        [lein-swank "1.4.4"]]}}
  :java-source-paths ["java-src"]
  :main engulf.core
  :min-lein-version "2.0.0"
  :jvm-opts ["-server"]
  :description "HTTP Benchmarker/Visualizer")
