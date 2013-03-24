(ns engulf.formulas.http-benchmark.request-sequences
  (:require [engulf.formulas.markov-requests :as markov]
            [clojure.java.io :as io]
            [cheshire.core :as json]
            [clojure.string :as string])
  (:use [clojure.string :only [lower-case]]
        [clojure.walk :only [keywordize-keys]])
  (:import java.net.URL))

(def registry (atom {}))

(defn register
  [name function]
  (swap! registry #(assoc % (keyword name) function)))

(defn get-builder
  [name]
  (@registry (keyword name)))

(defn get-seq
  [seq-name target]
  ((get-builder seq-name) target))

(def valid-methods #{:get :post :put :delete :patch})

(defn better-url-check
  [url-str]
  (try
    (URL. url-str)
    (catch java.net.MalformedURLException e
      (throw (Exception. (format "Could not parse url '%s': %s" url-str (.getMessage e)))))))

;; Make these real functions so we get nice stack traces
(defmacro def-rseq
  [fname binding & body]
  `(register ~fname (fn [~binding] ~@body)))

(def-rseq :markov
  {{:keys [corpus keep-alive?]} :target}
  ;; TODO: Maybe consider having this just work...
  (when (< (count corpus) 2)
    (throw (Exception. (str "Markov corpus must contain at least 2 URLs. "
                            "Got: " (count corpus)))))
  (map #(assoc % :keep-alive? keep-alive?)
       (markov/corpus-chain corpus) ))

(defn read-file-inf
  "Returns a lazy seq of a file's lines in a constant loop"
  ([file]
     (let [rdr (io/reader file)]
       (read-file-inf file (line-seq rdr) rdr)))
  ([file f-lines rdr]
     (if-let [line (first f-lines)]
       (lazy-seq
        (cons line
              (read-file-inf file (rest f-lines) rdr)))
       (do
         (.close rdr)
         (read-file-inf file)))))

(def parse-request-line
  ;; TODO: optimize the JSON check
  (letfn [(check-json [r] (try (keywordize-keys (json/parse-string r))
                               (catch Exception e
                                 r)))
          (check-map [r] (if (map? r) r {:url r}))
          (url-object [r] (update-in r [:url] better-url-check))
          (default-timeout [r]
            (update-in r [:timeout]
                       #(if % (Integer/parseInt %) 30000)))
          (default-method [r]
            (update-in r [:method]
                       #(if %(keyword (lower-case %))
                            :get)))]
    (comp
     default-method
     default-timeout
     url-object
     check-map
     check-json)))

(def-rseq :replay
  {target :target}
  (when (not target) (throw (Exception. "No target!")))
  (->> (read-file-inf (:url target))
       (map string/trim)
       (filter (comp not string/blank?))
       (filter (comp not nil?))
       (map (fn [r] (println (format "Line: '%s'" r)) r))
       (map parse-request-line)))

(def-rseq :url
 {target :target}
 (letfn [(validate [refined]
           ;; Throw on bad URLs.
           (better-url-check (:url refined))           
           (when (not ((:method refined) valid-methods))
             (throw (Exception. (str "Invalid method: " (:method target) " "
                                     "expected one of " valid-methods))))
           refined)
         (refine [target]
           (validate
            (assoc target
              :method (keyword (lower-case (or (:method target) "get")))
              :timeout (or (:timeout target) 1000))))
         (lazify [req] (lazy-seq (cons req (lazify req))))]
   (lazify (refine target))))

(def-rseq :script
  forms-str
  (letfn [(check-fn [val]
            (when (not (ifn? val))
              (throw (Exception.
                      (str  "User script '" val
                            "' did not return an fn!"))))
            val)
          (generator [forms]
            (let [cur-ns *ns*
                  script-ns (create-ns 'engulf.user-script-ns)]
              (try
                (in-ns (ns-name script-ns))
                (eval  '(clojure.core/refer 'clojure.core))
                (check-fn (eval forms))
                (finally
                 (in-ns (ns-name cur-ns))
                 (remove-ns (ns-name script-ns))))))
          (lazify [script-fn]
            (lazy-seq (cons (script-fn) (lazify script-fn))))]
    (lazify (generator (read-string forms-str)))))
