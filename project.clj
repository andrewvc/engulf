(defproject engulf "2.0.0"
  :description "HTTP Benchmarker/Visualizer"
  :main engulf.core
  :aot [engulf.core]
  :jvm-opts ["-server"]
  :java-source-path "java-src"
  :jar-exclusions [#"\.DS_Store"]
  :repositories {"Sonatype" "https://oss.sonatype.org/content/repositories/releases/"}

  ;; :warn-on-reflection true
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.clojure/tools.cli "0.2.1"]
                 [noir-async "1.0.0"]
                 [com.googlecode.protobuf-rpc-pro/protobuf-rpc-pro-duplex "1.2.0"]
                 [protobuf "0.6.0-beta16"]
                 [cheshire "4.0.0"]
                 [log4j/log4j "1.2.16"]
                 [org.slf4j/slf4j-simple "1.6.4"]
                 [com.ning/async-http-client "1.7.4"]
                 [org.clojure/tools.logging "0.2.3"]]
  :dev-dependencies [[org.clojure/tools.trace "0.7.3"]
                     [midje "1.3.1"]])
