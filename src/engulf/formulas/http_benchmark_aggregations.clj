(defn empty-edge-aggregation
  [params]
  {"type" "aggregate-edge"
   "runtime" 0
   "runs-total" 0
   "runs-succeeded" 0
   "runs-failed" 0
   "status-codes" {}
   "time-slices" {}
   "all-runtimes" []})

(defn empty-relay-aggregation
  [params]
  {"type" "aggregate-relay"
   "runtime" 0
   "runs-total" 0
   "runs-succeeded" 0
   "runs-failed" 0
   "status-codes" {}
   "time-slices" {}
   "percentiles" (PercentileRecorder. (or (params "timeout") 90000))})

(defn successes
  [results]
  (filter #(number? (:status %1)) results))

(defn edge-agg-totals
  [{:strs [runs-total runs-failed runs-succeeded] :as stats} results]
  (let [total (+ runs-total (count results))
        succeeded (+ runs-succeeded (count (successes results)))
        failed (- total succeeded)]
    (assoc stats
      "runs-total" total
      "runs-failed" failed
      "runs-succeeded" succeeded)))

(defn edge-agg-times
  [{runtime "runtime" :as stats} results]
  (assoc stats
    "all-runtimes" (map :runtime results)
    "runtime" (reduce
               (fn [m r] (+ m (:runtime r)))
               runtime
               results)))

(defn edge-agg-statuses
  [{scounts "status-codes" :as stats} results]
  (assoc stats "status-codes"
         (into scounts (map (fn [[k v]] [k (count v)]) (group-by :status results)))))

(defn edge-agg-percentiles
  [{rps "percentiles" :as stats} results]
  (doseq [{e :ended-at s :started-at} results] (.record ^PercentileRecorder rps ^int (int (- e s))))
  stats)

(defn edge-agg-time-slices
  "Processes time-slices segmented by status"
  [stats results]
  (assoc stats "time-slices"
         (reduce
          (fn [buckets result]
            (let [time-slice (- (result :started-at) (mod (result :started-at) 500))]
              (-> buckets
                  (update-in [time-slice "total"]
                             #(if %1 (inc %1) 1))
                  (update-in [time-slice (:status result)]
                             #(if %1 (inc %1) 1)))))
          (stats :time-slices)
          results)))

(defn relay-agg-time-slices
  "Processes time-slices segmented by status"
  [stats aggs]
  (let [agg-slices (map #(get % "time-slices") aggs)
        tc-seq (partition
                2
                (flatten
                 (reduce
                  (fn [m as] (conj m (filter identity (seq as))))
                  []
                  agg-slices)))
        exp-codes (apply concat
                         (map (fn [[t codes]]
                                (reduce (fn [m [code quant]] (conj m [t code quant]))
                                        []
                                        codes))
                              tc-seq))]
    (assoc stats
      "time-slices"
      (reduce
        (fn [m [ts code quant]]
          (update-in m [ts code] #(if % (+ % quant) quant)))
        {}
        exp-codes))))

(defn relay-agg-quantize-time-slices
  "Quantize time slices such that we have no more than 100"
  [stats aggs job]
  (assoc stats "time-slices"
         (let [wt (stats "walltime")
               ideal (int (/ wt 50))
               slice-size (if (< ideal 500)
                            500
                            (* 500 (int (Math/pow 2 (Math/ceil (/ (Math/log (/ ideal 500)) (Math/log 2)))))))]
           (reduce
            (fn [m [ts counts]]
              (update-in m [ts] (partial merge-map-sums counts)))
            (sorted-map)
            (map
             (fn [[ts counts]]
               (let [ts (Long/valueOf ts)]
                 [(- ts (mod ts slice-size)) counts]))
             (stats "time-slices"))))))
  

(defn relay-agg-totals
  [stats aggs job]
  (let [total (reduce + (map #(get % "runs-total") aggs))
        walltime (- (now) (:started-at job))]
    (assoc stats
      "runs-total" total
      "runs-succeeded" (reduce + (map #(get % "runs-succeeded") aggs))
      "runs-failed" (reduce + (map #(get % "runs-failed") aggs))
      "walltime" walltime
      "total-runs-per-second" (/ total (/ walltime 1000)) )))

(defn relay-agg-times
  [stats aggs]
  (assoc stats "runtime" (reduce + (map #(get % "runtime") aggs))))

(defn relay-agg-statuses
  [stats aggs]
  (assoc stats "status-codes"
         (reduce
          (fn [m agg]
            (reduce (fn [mm [status count]]
                      (update-in mm [status] #(if %1 (+ %1 count) count)))
                    m
                    agg))
          {}
          (map #(get % "status-codes") aggs))))

(defn relay-agg-percentiles
  "Handles both list and PercentileRecorder data"
  [stats aggs]
  (let [^PercentileRecorder recorder (stats "percentiles")]
    (doseq [agg aggs]
      (if-let [agg-rec (agg "percentiles")]
        (.merge recorder agg-rec)
        (try
          (.record recorder (int-array (agg "all-runtimes")))
          (catch Error e
              (log/warn e "Could not record percentile"))))))
  stats)

(defn format-percentiles
  "Takes a fastPercentiles.Percentile[] and formats it as [{},{},...]"
  [unformatted]
  (vec (map (fn [^Percentile p]
         {:avg (.getAvg p)
          :min (.getMin p)
          :max (.getMax p)
          :median (.getMedian p)
          :count (.getCount p)
          :total (.getTotal p)})
       unformatted)))

(defn relay-agg-jsonify
  "Take a relay aggregation and turn it into a structure suitable for JSON"
  [agg]
  (assoc agg "percentiles"
    (format-percentiles
     (.percentiles ^PercentileRecorder (agg "percentiles")))))

(defn relay-aggregate
  [job initial aggs]
  (let [all-aggs (conj aggs initial)]
    (-> initial
        (relay-agg-totals all-aggs job)
        (relay-agg-times all-aggs)
        (relay-agg-time-slices all-aggs)
        (relay-agg-quantize-time-slices all-aggs job)
        (relay-agg-statuses all-aggs)
        (relay-agg-percentiles aggs))))

(defn edge-aggregate
  [params results]
  (let [stats (empty-edge-aggregation params)]
    (-> stats
        (edge-agg-totals results)
        (edge-agg-times results)
        (edge-agg-statuses results)
        (edge-agg-time-slices results))))
