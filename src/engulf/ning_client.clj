(ns engulf.ning-client
  "A fast HTTP client based on sonatype's asyn client. Conforms to the aleph http interface more or less"
  (:use lamina.core
        lamina.api)
   (:require [clojure.tools.logging :as log])
   (:import java.util.concurrent.Executors
            java.util.concurrent.ExecutorService
            com.ning.http.client.AsyncHttpClient
            com.ning.http.client.AsyncHandler
            com.ning.http.client.AsyncHandler$STATE
            com.ning.http.client.PerRequestConfig
            com.ning.http.client.AsyncCompletionHandler
            com.ning.http.client.Request
            com.ning.http.client.RequestBuilder
            com.ning.http.client.AsyncHttpClientConfig$Builder
            com.ning.http.client.PerRequestConfig
            java.net.ConnectException
            java.io.IOException
            com.ning.http.client.AsyncHttpClient$BoundRequestBuilder))

(defrecord ResponseHandler [status res-ch]
  com.ning.http.client.AsyncHandler
  (onStatusReceived
   [this status-resp]
   (compare-and-set! status nil (.getStatusCode status-resp))
   AsyncHandler$STATE/CONTINUE)
  (onHeadersReceived
   [this headers]
   AsyncHandler$STATE/CONTINUE)
  (onBodyPartReceived
   [this body-part]
   AsyncHandler$STATE/CONTINUE)
  (onCompleted
   [this]
   (success! res-ch {:status @status})
   AsyncHandler$STATE/ABORT)
  (onThrowable
   [this e]
   (error! res-ch e)))

(defn create-response-handler
  (^ResponseHandler [res-ch]
    (ResponseHandler. (atom nil) res-ch)))

(def default-client-options
     {:max-conns-per-host 4
      :timeout 90000
      :executor-service (Executors/newFixedThreadPool 2)
      :connection-pooling true})

(defn create-client
  (^AsyncHttpClient
   []
   (create-client {}))
  (^AsyncHttpClient
   [arg-opts]
   (let [opts (merge default-client-options arg-opts)
         {:keys [max-conns-per-host
                 timeout
                 connection-pooling]} opts]
    (-> (AsyncHttpClientConfig$Builder.)
        (.setMaximumConnectionsPerHost max-conns-per-host)
        (.setConnectionTimeoutInMs timeout)
        (.setRequestTimeoutInMs timeout)
        (.setAllowPoolingConnection connection-pooling)
        (.setMaxRequestRetry 0)
        (.setFollowRedirects false)
        (.build)
        (AsyncHttpClient.)))))

(defn close-client
  [client]
  (.close client))

(def default-request-opts
     {:method "get"})

(defn build-request
  [arg-opts]
  (let [opts (merge default-request-opts arg-opts)
        {:keys [method url]} opts]
    (.build
     (.setUrl
      (RequestBuilder. (str method))
      url))))

(defn execute-request
  [client request]
    (let [result (result-channel)
          handler (create-response-handler result)]
      (try
        (.executeRequest client request handler)
        result
        (catch Exception e
          nil))))

(defn request
  [client opts]
  (execute-request client (build-request opts)))