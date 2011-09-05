(ns parbench.requests-state)

(defn create-blank
  "Creates a blank representation of request state.
   This returns a hash with a few important attributes concerning
   metadata and options, but of chief importance is the :grid
   item, this is a 2d vector of refs to indvidual request hashes
   this represents the state of all open requests"
  [requests concurrency url-generator]
  (let [grid (for [row (range concurrency)]
                  (for [col (range requests)]
                       (ref {:y row :x col
                             :url     (url-generator col row)
                             :state   :untried
                             :status  nil
                             :runtime nil}) ))]()
        (ref {:requests requests
              :concurrency concurrency
              :grid grid
              :bench-started-at nil
              :bench-ended-at nil})))

(defn stats
  "Returns a mapping of RequestsState http states states to counts"
  [requests-state]
  (reduce
    (fn [stats request]
      (let [{:keys [statuses total]} stats
            {:keys [state status]}   @request]
        (assoc stats
               :total     (inc (stats :total 0))
               state      (inc (stats state  0))
               :statuses  (assoc statuses status (inc (statuses status 0)))
               :progress  (if  (not=  state :untried)
                               (inc  (stats :progress 0))
                               (stats :progress 0)))))
          {:statuses {}}
          (flatten (:grid @requests-state))))

(defn complete?
  "Returns true if the requests are done running"
  [requests-state]
  (if (:bench-ended-at @requests-state) true false))