(ns engulf.formulas.markov-requests
  "Support for generating markov chains of requests"
  (:use clojure.pprint
        [clojure.walk :only [keywordize-keys]])
  (:require [clojure.string :as string]
            [clojure.tools.logging :as log]
            [cheshire.core :as json])
  (:import java.util.TreeMap))

(defn compile-transitions
  "Compile a system's transitions into a format suitable for efficient markov chain generation. Internally this creates a lookup table of URLs with treemaps used to map probabilities onto numerical ranges."
  [transitions]
  (doall
   (reduce
    (fn [out [url-id edges]]
      (assoc out
        url-id
        (let [m (TreeMap.)]
          (reduce
           (fn [offset [edge-url-id weight]]
             (let [off-ceil (+ offset weight)]
               (.put m off-ceil edge-url-id)
               off-ceil))
           0.0
           edges)
          m)))
    {}
    transitions)))

(defn chain
  "Returns a lazy sequence of requests. Not guaranteed to terminate."
  ([compiled]
     (chain compiled :rand))
  ([{:keys [transitions request-keys requests] :as compiled} request]
     (let [r (request-keys (rand-int (count request-keys)))]
       (if-let [edges (transitions r)]
         (let [e (.ceilingEntry edges (rand))]
           (if-let [e-req (.getValue e)]
             (lazy-seq (cons (first (requests e-req)) (chain compiled e-req)))
             (throw (Exception.  "This should never happen. The morkov chain is compromised!"))))
         (throw (Exception. (str "Could not find edge for: " request)))))))

(defn build-requests
  [parsed]
  (reduce
   (fn [m request]
     (-> m
         (update-in [(.hashCode request)] #(if % (update-in % [1] inc) [request 1]))
         (update-in [:total] inc)))
   {:total 0}
   parsed))

(defn incr-or-one
  [v]
  (if v (inc v) 1))

(defn counted-tuples
  [parsed requests]
  (let [tuples (partition 2 1 parsed)
        first-edge (first (first tuples))
        last-edge (last (last tuples))
        counted (reduce
                 (fn [m [a b]]
                   (let [ac (.hashCode a)
                         bc (.hashCode b)]
                     (-> m
                         (update-in [ac bc] incr-or-one)
                         (update-in [ac :total] incr-or-one))))
                 {}
                 tuples)]
    ;; loop the end to the front to finish it
    (-> counted
        (update-in [(.hashCode last-edge) (.hashCode first-edge)] incr-or-one)
        (update-in [(.hashCode last-edge) :total] incr-or-one))))

(defn counted-probabilities
  [counted]
  (map
   (fn [[request edges]]
     [request
      (reduce (fn [m [ereq ecount]]
                (if (= ereq :total)
                  m
                  (assoc m ereq (float (/ ecount (:total edges))))))
              {}
              edges)])
   counted))

(defn compile-preprocessed
  "Compiles a preprocessed corpus into a format suitable for the (chain) method"
  [{:keys [requests counted]}]
  (let [probabilities (counted-probabilities counted)]
    {:requests requests
     :request-keys (vec (filter #(not= :total %) (keys requests)))
     :transitions (compile-transitions probabilities)}))

(defn parse
  [corpus]
  (->> corpus
       (map #(if (map? %) % {:url %}))
       (map keywordize-keys)
       (map #(assoc % :method (keyword (or  (:method %) :get))))))

(defn preprocess-corpus
  "Compiles the corpus into something that's a processed as possible but can still
   be serialized into JSON. Call 'compile-preprocessed' to finish it."
  [corpus]
  (let [parsed (parse corpus)
        requests (build-requests parsed)
        counted (counted-tuples parsed requests)]
    {:requests requests
     :counted counted}))

(defn corpus-chain
  [corpus]
  (-> corpus
      preprocess-corpus
      compile-preprocessed
      chain))