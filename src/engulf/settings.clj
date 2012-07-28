(ns engulf.settings)

(def ^:dynamic all
  {:http-port 4000
   :manager-port 4025
   :host "localhost"
   :mode :combined
   :connect-to ["localhost" 4025]
   :jdbc {:classname "org.sqlite.JDBC"
          :subprotocol "sqlite"
          :subname "engulf.sqlite3"}})