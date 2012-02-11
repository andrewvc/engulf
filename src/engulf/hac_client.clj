(ns engulf.hac-client
  (:use lamina.core)
  (:require [clojure.tools.logging :as log]
            [http.async.client :as client]
            [http.async.client.request :as req]))

(defn http-client
  "Creates http.async.client client"
  [options]
  (let [client (apply client/create-client (apply concat options))]
    (fn h
      ([request] (h request -1))
      ([{:keys [method url]} timeout]
         (let [result (result-channel)
               request (req/prepare-request method url :timeout timeout)]
           (io!
            (apply req/execute-request
                   client request
                   (apply concat
                          (merge
                           req/*default-callbacks*
                           {:completed (fn [response]
                                         (let [code (:code (client/status response))
                                               content-type (:content-type
                                                             (client/headers response))]
                                          (enqueue (.success result)
                                                   {:status code
                                                    :content-typet content-type})))
                            :error (fn [response error]
                                     (log/error "Error in http.async.client" error)
                                     (enqueue result error))}))))
           result)))))
