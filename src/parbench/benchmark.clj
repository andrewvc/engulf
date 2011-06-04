(ns parbench.benchmark
  (:require [parbench.requests-state :as rstate])
  (:import com.ning.http.client.AsyncHttpClient
           com.ning.http.client.AsyncCompletionHandler
           java.util.concurrent.Future
           java.util.Calendar))

(defn timestamp []
  (.getTimeInMillis (Calendar/getInstance)))

(def client (AsyncHttpClient.))

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

(defn client-handler [on-success on-error]
  (proxy [com.ning.http.client.AsyncCompletionHandler] []
          (onCompleted [response]
            (let [content-type   (.getContentType response)
                  status         (.getStatusCode  response)]
                 (on-success {:status status :content-type content-type})))
          (onThrowable [throwable]
            (on-error [throwable] )) ))

(defn http-get [url on-success on-error]
  "Convenience method to execute a GET request with the client"
  (let [handler (client-handler on-success on-error)]
    (.execute (.prepareGet client url) handler)))

(defn run-request [request callback]
  "Runs a single HTTP request"
  (dosync (alter request assoc :rendered false :state :requested :requested-at (timestamp)))
  (http-get (:url @request)
            (success-callback-for request callback)
            (failure-callback-for request callback)))


(defn run-nth-request [request-list n]
  "Run a specific request in the current 'row' of requests"
  (run-request (nth request-list n) 
    (fn []
      (let [next-n (inc n)]
        (cond (nth request-list n)
          (send (agent request-list) run-nth-request next-n))))))

(defn block-till-done [reqs-state]
  (let [stats (rstate/stats reqs-state)]
       (cond (not (= (:total stats)  (:progress stats)))
         (do (Thread/sleep 100)
             (recur reqs-state)))))


(defn run [reqs-state opts]
  "Visualization showing each row as a user agent"
  (dosync (alter reqs-state assoc :bench-started-at (timestamp)))
  (let [request-lists (for [row (:grid @reqs-state)] row)]
    (doseq [request-list request-lists]
      (send (agent request-list) run-nth-request 0)))
  (block-till-done reqs-state)
  (dosync (alter reqs-state assoc :bench-ended-at (timestamp)))
  )
