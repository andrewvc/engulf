(ns parbench.benchmarks
  (:require [com.twinql.clojure.http :as http])
  (:import com.ning.http.client.AsyncHttpClient)
  (:import com.ning.http.client.AsyncCompletionHandler)
  (:import java.util.concurrent.Future)
  (:import java.util.Calendar))

(def default-http-parameters
  (http/map->params {:handle-redirects false}))

(defn timestamp []
  (.getTimeInMillis (Calendar/getInstance)))

(def ning-client (AsyncHttpClient.))

(defn ning-handler [on-success on-error]
  (proxy [com.ning.http.client.AsyncCompletionHandler] [] 
          (onCompleted [ning-response]
            (let [content-type   (.getContentType ning-response)
                  status         (.getStatusCode  ning-response)]
                 (on-success {:status status :content-type content-type})))
          (onThrowable [throwable]
            (on-error [throwable] )) ))

(defn ning-get [url on-success on-error]
  (let [handler (ning-handler on-success on-error)]
    (.execute (.prepareGet ning-client url) handler)))

(defn success-callback-for [request callback]
  (fn [response]
    (dosync
      (alter request assoc
        :rendered      false
        :responded-at (timestamp)
        :state        :responded
        :status       (:status response)))
    (callback)))

(defn failure-callback-for [request callback]
  (fn [throwable]
    (println "Error: " throwable)
    (alter request assoc
        :responded-at (timestamp)
        :state        :failed)
    (callback)))

(defn run-request [request callback]
  "Runs a single HTTP request"
  (dosync (alter request assoc :rendered false :state :requested :requested-at (timestamp)))
  (ning-get (:url @request) 
            (success-callback-for request callback)
            (failure-callback-for request callback)))


(defn run-nth-request [request-list n]
  (run-request (nth request-list n) 
    (fn []
      (let [next-n (inc n)]
        (cond (nth request-list n)
          (send (agent request-list) run-nth-request next-n))))))

(defn user-agents [reqs-state opts]
  "Visualization showing each row as a user agent"
  (let [request-lists (for [row (:grid reqs-state)] row)]
    (doseq [request-list request-lists]
      (send (agent request-list) run-nth-request 0))))
