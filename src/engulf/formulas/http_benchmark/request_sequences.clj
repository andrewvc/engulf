(ns engulf.formulas.http-benchmark.request-sequences
  (:require [engulf.formulas.markov-requests :as markov])
  (:use [clojure.string :only [lower-case]])
  (:import java.net.URL))

(def registry (atom {}))

(defn register
  [name function]
  (swap! registry #(assoc % (keyword name) function)))

(defn get-builder
  [name]
  (@registry (keyword name)))

(def valid-methods #{:get :post :put :delete :patch})

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

(def-rseq :url
 {target :target}
 (letfn [(validate [refined]
           ;; Throw on bad URLs.
           (try
             (URL. (:url target))
             (catch java.net.MalformedURLException e
               (throw (Exception. (format "Could not parse url '%s' in %s" (:url target) target)))))
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