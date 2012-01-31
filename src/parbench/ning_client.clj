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
            (log/error e "We hit a throwable in Ning")
            (enqueue res-ch e))))

(defn http-get
  "Convenience method to execute a GET request with the client"
  [url]
  (let [handler-res-ch (result-channel)
        handler (client-handler handler-res-ch)
        requestConfig (doto (PerRequestConfig.)
                            (.setRequestTimeoutInMs (int 2000)))]
    (-> (.prepareGet client url)
        (.setPerRequestConfig requestConfig)
        (.execute handler))
    handler-res-ch))