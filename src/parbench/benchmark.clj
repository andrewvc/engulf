(ns parbench.benchmark
  (:require [parbench.requests-state :as rstate])
  (:import com.ning.http.client.AsyncHttpClient
           com.ning.http.client.AsyncCompletionHandler
           java.util.concurrent.Future
           java.util.Calendar))

(defn timestamp []
  (.getTimeInMillis (Calendar/getInstance)))

(def client (AsyncHttpClient.))

(defn success-callback-for [request]
  (fn [response]
    (dosync
      (alter request assoc
        :rendered      false
        :responded-at (timestamp)
        :state        :responded
        :status       (:status response)))))

(defn failure-callback-for [request]
  (fn [throwable]
    (println "Error: " throwable)
    (alter request assoc
        :responded-at (timestamp)
        :state        :failed)))

(defn client-handler [on-success on-error]
  (proxy [com.ning.http.client.AsyncCompletionHandler] []
          (onCompleted [response]
            (let [content-type   (.getContentType response)
                  status         (.getStatusCode  response)]
                 (on-success {:status status :content-type content-type})))
          (onThrowable [throwable]
            (on-error [throwable] )) ))

(defn http-get
  "Convenience method to execute a GET request with the client"
  [url on-success on-error]
  (let [handler (client-handler on-success on-error)]
    (.get (.execute (.prepareGet client url) handler))))

(defn run-request
  "Runs a single HTTP request"
  [request]
  (dosync (alter request assoc :rendered false :state :requested :requested-at (timestamp)))
  (http-get (:url @request)
            (success-callback-for request)
            (failure-callback-for request)))

(defn run-requests
  [request-list]
  (doseq [request request-list]
    (run-request request)))

(defn block-till-done
  [reqs-state]
  (let [stats (rstate/stats reqs-state)]
       (cond (not (= (:total stats)  (:progress stats)))
         (do (Thread/sleep 500)
             (recur reqs-state)))))

(defn run
  "Visualization showing each row as a user agent"
  [reqs-state opts]
  (dosync (alter reqs-state assoc :bench-started-at (timestamp)))
  (let [request-lists (for [row (:grid @reqs-state)] row)]
    (doseq [request-list request-lists]
        (future (run-requests request-list))))
  (block-till-done reqs-state)
  (dosync (alter reqs-state assoc :bench-ended-at (timestamp))))
