(ns engulf.formulas.markov-requests
  "Support for generating markov chains of requests"
  (:use clojure.pprint
        [clojure.walk :only [keywordize-keys]])
  (:require [clojure.string :as string]
            [clojure.tools.logging :as log]
            [cheshire.core :as json])
  (:import java.util.TreeMap))

(defn compile-transitions
  "Compile a system's transitions into a format suitable for efficient markov chain generation.
   Internally this creates a lookup table of URLs with treemaps used to map probabilities
   onto numerical ranges."
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
     (chain compiled :root))
  ([{:keys [transitions requests] :as compiled} request]
     (when-let [edges (transitions request)]
       (let [e (.ceilingEntry edges (rand))]
         (if-let [e-req (.getValue e)]
           (lazy-seq
            (cons (requests e-req) (chain compiled e-req)))
           (chain compiled :root) )))))

(defn parse-corpus
  [corpus]
  (map
   #(let [[method url raw-opts] (string/split (string/trim %) #"\s+" 3)
          opts (if (not raw-opts)
                 {}
                 (try (json/parse-string raw-opts)
                      (catch com.fasterxml.jackson.core.JsonParseException e
                        (log/warn e (str "Could not parse line: " %))
                        (throw e)
                        )))]
      (merge (keywordize-keys opts) {:method method :url url }))
   (filter (comp not string/blank?) (string/split-lines corpus))))

(defn build-requests
  [parsed]
  (reduce
   (fn [m request]
     (-> m
         (update-in [(.hashCode request)] #(if % (update-in % [1] inc) [request 1]))
         (update-in [:total] inc)))
   {:total 0}
   parsed))

(defn counted-tuples
  [parsed requests]
  (reduce
   (fn [m [a b]]
     (let [ac (.hashCode a)
           bc (.hashCode b)]
       (-> m
           (update-in [ac bc] #(if % (inc %) 1))
           (update-in [ac :total] #(if % (inc %) 1)) )))
   {}
   (partition 2 1 parsed)))

(defn add-root-tuples
  [counted requests]
  (assoc counted
    :root
    (reduce
     (fn [m [k v]]
       (if (= k :total)
         (assoc m k v)
         (let [[_ count] v] (assoc m k count))))
     {}
     requests)))

(defn counted-probabilities
  [counted]
  (map
   (fn [[request edges]]
     [request
      (reduce (fn [m [ereq ecount]]
                (if (= ereq :total) m (assoc m ereq (float (/ ecount (:total edges))))
                    ))
              {}
              edges)])
   counted))

(defn compile-preprocessed
  "Compiles a preprocessed corpus into a format suitable for the (chain) method"
  [{:keys [requests counted-partial]}]
  (let [counted (add-root-tuples counted-partial requests)
        probabilities (counted-probabilities counted)]
    {:requests requests
     :transitions (compile-transitions probabilities)}))

(defn preprocess-corpus
  "Compiles the corpus into something that's a processed as possible but can still
   be serialized into JSON. Call 'compile-preprocessed' to finish it."
  [corpus]
  (let [parsed (parse-corpus corpus)
        requests (build-requests parsed)
        counted-partial (counted-tuples parsed requests)]
    {:requests requests :counted-partial counted-partial}))