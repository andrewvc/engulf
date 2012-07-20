(ns engulf.formula)

(def registry (atom {}))

(defprotocol Formula
  (start-relay
    [this ingress]
    "Starts a formula in relay mode. Ingress is expected to be a channel of edge or other relay results")
  (start-edge
    [this]
    "Starts a formula in edge mode.")
  (stop
    [this]
    "Stops the currently running formula regardless of mode"))


(defn register
  [name constructor]
  (swap! registry #(assoc %1 name constructor)))

(defn lookup
  [name]
  (@registry (keyword name)))

(defn init-job-formula
  [{:keys [formula-name params] :as job}]
  (if-let [constructor (lookup formula-name)]
    (constructor params)
    (throw "Could not find formula for job!" job)))