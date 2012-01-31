(ns parbench.url-worker
  (:require [parbench.runner :as runner]
            [parbench.ning-client :as ning-client]
            [clojure.tools.logging :as log])

  (:use [parbench.utils :only [send-bench-msg]]
        aleph.http
        lamina.core))

(defprotocol Workable
  "A worker aware of global job state"
  (work [this] [this run-id] "Execute the job")
  (exec-runner [this run-id] "Execute the runner associated with this worker"))

(defrecord UrlWorker [state url worker-id client output-ch]
  Workable
   
  (exec-runner [this run-id]
    (let [req-start (System/currentTimeMillis)
          ch (ning-client/http-get url)]
          ;          ch        (client {:method :get :url url})]
      (on-success ch
        (fn [res]
          (send-bench-msg output-ch :worker-result
            (let [req-end (System/currentTimeMillis)]
              {:worker-id worker-id
               :run-id    run-id
               :req-start req-start
               :req-end   req-end
               :runtime   (- req-end req-start)
               :response  res}))
          (work this (inc run-id))))
      (on-error ch
        (fn [err]
          (send-bench-msg output-ch :worker-error err)
          (work this (inc run-id))))))

  (work [this]
        (work this 0))
  
  (work [this run-id]
    (compare-and-set! state :initialized :started)
    (when (= @state :started)
         (exec-runner this run-id))))

(defn create-single-url-worker [url worker-id recorder]
  (UrlWorker. (atom :initialized)
              url
              worker-id
              (http-client {:url url})
              (channel)))
