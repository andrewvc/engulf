(ns engulf.formula)

(def registry (atom {}))

(defprotocol Formula
  (reduce-aggregate [this agg])
  (get-and-reset-aggregate [this])
  (get-aggregate [this])
  (stop [this])
  (perform [this] "Takes a map of params, starts a job run"))

(defn register
  [name constructor]
  (swap! registry #(assoc %1 name constructor)))

(defn lookup
  [name]
  (when-let [formula (get @registry (keyword name))]
    formula)) ; this may get extra args one day