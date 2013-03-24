(defproject engulf/engulf "3.0.0-beta12"
  :dependencies [[org.clojure/clojure "1.5.0"]
                 [org.clojure/tools.cli "0.2.2"]
                 [noir-async "1.1.0-beta11"]
                 [cheshire "5.0.2"]
                 [org.slf4j/slf4j-simple "1.6.6"]
                 [org.xerial/sqlite-jdbc "3.7.2"]
                 [korma "0.3.0-RC4"]
                 [ragtime "0.2.1"]
                 [ragtime/ragtime.sql "0.2.1"]
                 [org.clojure/tools.trace "0.7.5"]
                 [org.clojure/tools.nrepl "0.2.1"]
                 [org.clojure/tools.logging "0.2.6"]]
  :javac-options ["-target" "1.6" "-source" "1.6"],
  :profiles {:dev
             {
              ;:jvm-opts ["-agentlib:jdwp=transport=dt_socket,server=y,suspend=n"]
              :dependencies [[org.clojure/tools.trace "0.7.5"]
                             [midje "1.5.1"]]}}
  :java-source-paths ["java-src"]
  :main engulf.core
  :min-lein-version "2.0.0"
  :jvm-opts ["-server"]
  :description "HTTP Benchmarker/Visualizer")
