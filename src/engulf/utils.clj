(ns engulf.utils
  (:use lamina.core))

(defn increment-keys
  "Given a map and a list of keys, this will return an identical map with those keys
   with those keys incremented.
    
   Keys with a null value will be set to 1."
  [src-map & xs]
  (into src-map (map #(vector %1 (inc (get src-map %1 0))) xs)))

(defn send-bench-msg
  "Enqueue a message of the format {:dtype data-type :data data}
   on channel ch. This uses the io! macro since its assumed bench
   messages
will always hit clients"
  [ch data-type data]
  (io! (enqueue ch {:dtype data-type :data data})))

(defn mean
  "Returns the mean value of a coll"
  [coll]
  (/ (reduce + coll) (count coll)))
  
(defn median
  "Returns the median element in a collection"
  [coll]
  (let [len (count coll)]
    (cond (empty? coll) nil
          (= len 1)     (first coll)
          :else        (nth coll (int (/ (count coll) 2))))))

(defn percentiles
  "Splits a collection of numbers and divides it into a collection
   of percentiles"
  [coll]
  (let [len   (count coll)
        partn (if (>= len 100) (int (/ len 100)) 1)]
    (map-indexed
     (fn [idx group]
       {:min (first group)
        :max (last group)
        :median (median group)
        :avg (mean group)})
     (partition-all partn coll))))
