(ns parbench.utils)

(defn increment-keys
  "Given a map and a list of keys, this will return an identical map with those keys
   with those keys incremented.
    
   Keys with a null value will be set to 1."
  [src-map & xs]
  (merge
    src-map
    (into {} (map #(vector %1 (inc (or (get src-map %1) 0))) xs))))