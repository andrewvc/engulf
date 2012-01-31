3(ns parbench.ning-client
   (:use lamina.core)
   (:require [clojure.tools.logging :as log])
   (:import com.ning.http.client.AsyncHttpClient
            com.ning.http.client.PerRequestConfig
            com.ning.http.client.AsyncCompletionHandler))

;      AsyncHttpClient c = new AsyncHttpClient(new AsyncHttpClientConfig.Builder().setRequestTimeoutInMs(...).build());

(def client (AsyncHttpClient.))

(defn client-handler [res-ch]
  (proxy [com.ning.http.client.AsyncCompletionHandler] []
          (onCompleted [response]
            (let [content-type   (.getContentType response)
                  status         (.getStatusCode  response)]
              (enqueue (.success res-ch)
                       {:status status :content-type content-type})))
          (onThrowable [e]
            (.printStackTrace e)
            (log/error e "bad error for ning!")
            (enqueue res-ch e))))

(defn http-get
  "Convenience method to execute a GET request with the client"
  [url]
  (let [requestConfig (doto (PerRequestConfig.)
                      (.setRequestTimeoutInMs (int 2000)))
        handler-res-ch (result-channel)
        handler (client-handler handler-res-ch)]
    (.execute
      (.setPerRequestConfig
        (.prepareGet client url)
        requestConfig)
      handler)
    handler-res-ch))