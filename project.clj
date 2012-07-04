(defproject engulf "2.0.0"
  :description "HTTP Benchmarker/Visualizer"
  :main engulf.core
  :jvm-opts ["-server", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n"]
  :extra-classpath-dirs ["/usr/lib/jvm/java-6-sun/lib/tools.jar"]
  :java-source-path "java-src"
  :jar-exclusions [#"\.DS_Store"]
  :repositories {"Sonatype" "https://oss.sonatype.org/content/repositories/releases/"}
  ;; :warn-on-reflection true
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.clojure/tools.cli "0.2.1"]
                 [noir-async "1.1.0-beta2"]
                 [cheshire "4.0.0"]
                 [log4j/log4j "1.2.16"]
                 [org.slf4j/slf4j-simple "1.6.4"]
                 [com.ning/async-http-client "1.7.4"]
                 [org.clojure/tools.logging "0.2.3"]]
  :dev-dependencies [[org.clojure/tools.trace "0.7.3"]
                     [lein-midje "1.0.9"]
                     [lein-swank "1.4.4"]
                     [midje "1.4.0"]])
