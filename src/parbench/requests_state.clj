(ns parbench.requests-state)

(defrecord RequestsState [requests concurrency grid])

(defn create-blank [requests concurrency url-generator]
  "Creates a blank representation of request state"
  (let [grid (for [row (range concurrency)]
                  (for [col (range requests)]
                       (ref {:y row :x col
                             :url     (url-generator)
                             :state   :untried
                             :status  nil
                             :runtime nil}) ))]()
        (RequestsState. requests concurrency grid )))

(defn stats [requests-state]
  "Returns a mapping of RequestsState http states states to counts"
  (reduce
    (fn [stats request]
      (let [r        @request
            state    (:state  r)
            status   (:status r)
            statuses (:statuses stats)]
        (assoc stats
               :total     (inc (stats :total 0))
               state      (inc (stats state  0))
               :statuses  (assoc statuses status (inc (statuses status 0)))
               :progress  (if  (not=  state :untried)
                               (inc  (stats :progress 0))
                               (stats :progress 0)))))
          {:statuses {}}
          (flatten (:grid requests-state))))