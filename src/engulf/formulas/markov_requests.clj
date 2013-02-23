(ns engulf.formulas.markov-requests
  "Support for generating markov chains of requests."
  ;; TODO: This file's a bit of a messy in terms of organization and naming
  (:use [clojure.string :only [lower-case]]
        [clojure.walk :only [keywordize-keys]])
  (:require [clojure.string :as string]
            [clojure.tools.logging :as log]
            [cheshire.core :as json])
  (:import [java.util
            TreeMap
            NavigableMap
            Map$Entry]
           java.net.URL))

(defn counts->treemap
  [counts]
  (let [m (TreeMap.)]
    (reduce
     (fn [offset [req-id weight]]
       (let [off-ceil (+ offset weight)]
         (.put m off-ceil req-id)
         off-ceil))
     0.0
     counts)
    m))

(defn compile-transitions
  "Compile transitions into a format suitable for efficient markov chain generation. Internally this creates a lookup table of requests with treemaps used to map probabilities onto numerical ranges."
  [transitions]
  (doall
   (reduce
    (fn [out [req-id edges]]
      (assoc out req-id (counts->treemap edges)))
    {}
    transitions)))

(defn chain
  "Returns a lazy sequence of requests. Not guaranteed to terminate."
  ([{:keys [weighted-requests] :as compiled}]
     (chain compiled (.getValue
                      (.ceilingEntry ^NavigableMap weighted-requests (rand)))))
  ([{:keys [transitions requests] :as compiled} request]
     (if-let [edges (transitions request)]
       (let [e (.ceilingEntry ^NavigableMap edges (rand))]
         (if-let [e-req (.getValue e)]
           (lazy-seq (cons (first (requests e-req)) (chain compiled e-req)))
           (throw (Exception.
                   "This should never happen. The morkov chain is compromised!"))))
       (throw (Exception. (str "Could not find edge for: " request))))))

(defn build-requests
  [parsed]
  (reduce
   (fn [m request]
     (-> m
         (update-in [(.hashCode ^Object request)] #(if % (update-in % [1] inc) [request 1]))
         (update-in [:total] inc)))
   {:total 0}
   parsed))

(defn incr-or-one
  [v]
  (if v (inc v) 1))

(defn counted-tuples
  [parsed requests]
  (let [tuples (partition 2 1 parsed)
        first-node (first (first tuples))
        last-node (last (last tuples))
        counted (reduce
                 (fn [m [a b]]
                   (let [ac (.hashCode ^Object a)
                         bc (.hashCode ^Object b)]
                     (-> m
                         (update-in [ac bc] incr-or-one)
                         (update-in [ac :total] incr-or-one))))
                 {}
                 tuples)]
    ;; loop the end to the front to finish it
    (-> counted
        (update-in [(.hashCode ^Object last-node) (.hashCode ^Object first-node)] incr-or-one)
        (update-in [(.hashCode ^Object last-node) :total] incr-or-one))))

(defn counted-probabilities
  [counted]
  (map
   (fn [[request nodes]]
     [request
      (reduce (fn [m [nreq ncount]]
                (if (= nreq :total)
                  m
                  (assoc m nreq (float (/ ncount (:total nodes))))))
              {}
              nodes)])
   counted))

(defn weighted-requests
  [{total :total :as requests}]
  (reduce
   (fn [m [k v]]
     (if (= k :total)
       m
       (let [[_ cnt] v]
         (assoc m k (/ cnt total)))))
   {}
   requests))

(defn compile-preprocessed
  "Compiles a preprocessed corpus into a format suitable for the (chain) method"
  [{:keys [requests counted]}]
  (let [probabilities (counted-probabilities counted)]
    {:requests requests
     :weighted-requests (counts->treemap (weighted-requests requests))
     :transitions (compile-transitions probabilities)}))

(defn parse
  [corpus]
  (->> corpus
       (map #(if (map? %) % {:url %}))
       (filter (comp not string/blank? :url))
       ;; Ensure URLs are parsable
       (map (fn [req]
              (try
                (update-in req [:url] #(.toString (URL. %)))
                (catch Exception e
                  (throw (Exception. (str "Could not parse url in: " req)
                                     ))))))
       (map keywordize-keys)
       (map #(assoc % :timeout (if-let [t (:timeout %)] t 30000)))
       (map #(assoc % :method (if-let [m (:method %)]
                                (keyword (lower-case m))
                                :get)))))

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
  "Takes an array of requests, returns a lazy seq of the chain"
  [corpus]
  (-> corpus
      preprocess-corpus
      compile-preprocessed
      chain))