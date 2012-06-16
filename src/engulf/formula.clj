(ns engulf.formula)

(def registry (atom {}))

(defprotocol Formula
  (empty-results [this] "Return an empty result-set for starting a new job")
  (result-reduce [this result] "Take the result of perform, reduce onto current results")
  (perform [this params] "Takes a map of params, starts a job run"))

(defn register
  [name constructor]
  (swap! registry #(assoc %1 name constructor)))

(defn lookup
  [name]
  (get registry name))