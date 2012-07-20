(ns engulf.formula)

(def registry (atom {}))

(defprotocol Formula
  (start-relay [this ingress])
  (start-edge [this])
  (stop [this]))


(defn register
  [name constructor]
  (swap! registry #(assoc %1 name constructor)))

(defn lookup
  [name]
  (@registry (keyword name)))

(defn init-job
  [{:keys [formula-name params]}]
  ((lookup name) params))
