(ns engulf.formula)

(def registry (atom {}))

(defprotocol Formula
  (start-relay [this ingress])
  (start-edge [this])
  (stop [this])
  (relay [this]))

(defn register
  [name constructor]
  (swap! registry #(assoc %1 name constructor)))

(defn lookup
  [name]
  (when-let [formula (get @registry (keyword name))]
    formula)) ; this may get extra args one day