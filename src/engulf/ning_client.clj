(ns engulf.ning-client
  "A fast HTTP client based on sonatype's asyn client. Conforms to the aleph http interface more or less"
  (:use lamina.core
        lamina.api)
   (:require [clojure.tools.logging :as log])
   (:import com.ning.http.client.AsyncHttpClient
            com.ning.http.client.AsyncHandler
            com.ning.http.client.AsyncHandler$STATE
            com.ning.http.client.PerRequestConfig
            com.ning.http.client.AsyncCompletionHandler))

(defrecord Response [status])

(deftype StatusHandler [status res-ch]
  com.ning.http.client.AsyncHandler
  (onStatusReceived [this status-resp]
                    (compare-and-set! status nil (.getStatusCode status-resp))
                    AsyncHandler$STATE/CONTINUE)
  (onHeadersReceived [this headers]
                     AsyncHandler$STATE/CONTINUE)
  (onBodyPartReceived [this body-part]
                      AsyncHandler$STATE/CONTINUE)
  (onCompleted [this]
               (success! res-ch (Response. @status))
               AsyncHandler$STATE/CONTINUE)
  (onThrowable [this e] (error! res-ch e)))

(defn client-handler [res-ch]
  (StatusHandler. (atom nil) res-ch))

(defn create-http-client [options]
  "Currently ignores all options. You probably don't want to use this directly, but rather want http-client"
  (let [client (AsyncHttpClient.)]
    (fn this
      ([request]
         (this request 90000))
      ([{:keys [method url]} timeout]
        (let [result (result-channel)
              handler (client-handler result)
              requestConfig (doto (PerRequestConfig.)
                                  (.setRequestTimeoutInMs (int timeout)))]
          (-> (.prepareGet client url)
              (.setPerRequestConfig requestConfig)
              (.execute handler))
          result)))))

(def default-client (create-http-client {}))

(defn http-client
  ([] http-client {})
  ([options]
  "Returns the default http client. Sonatype's client really works best with a single instance seeing as how it's fully asynchronous. Multiple clients eat up memory fast, and you'll hit an OOM"
  default-client))
                  
(defn http-request
  "Aleph style interface"
  ([request]
     (http-request request 90000))
  ([request timeout]
     (default-client request timeout)))
