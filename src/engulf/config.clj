(ns engulf.config)

(def defaults
     {
      :port 4000
      :mode :prod
      })

(def configured (ref {}))

(defn opts
  "Get all options"
  []
  (merge defaults @configured))

(defn set-opt
  "Set a configuration option in a transaction"
  [path value]
  (dosync
   (alter configured assoc-in path value)))

(defn get-opt
  [path]
  (dosync
   (get-in (opts) [path])))