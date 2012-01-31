(ns parbench.url-worker
  (:require [parbench.runner :as runner]
            [parbench.ning-client :as ning-http]
            [aleph.http :as aleph-http]
            [clojure.tools.logging :as log])
  (:use [parbench.utils :only [send-bench-msg]]
        
        lamina.core))

(defprotocol Workable
  "A worker aware of global job state"
  (work [this] [this run-id] "Execute the job")
  (exec-runner [this run-id] "Execute the runner associated with this worker"))

(defrecord UrlWorker [state url worker-id client output-ch]
  Workable
   
  (exec-runner [this run-id]
    (let [req-start (System/currentTimeMillis)
         ; ch (ning-client/http-get url)]
          ch (client {:method :get :url url} 2000)]
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

(def aleph-client (atom nil))
(def ning-client (ning-http/http-client {}))

(defn create-single-url-worker [client-type url worker-id recorder]
  (compare-and-set! aleph-client nil (aleph-http/http-client {:url url}))
  (let [client (if (= :aleph client-type) @aleph-client
                                          ning-client)]
    (UrlWorker. (atom :initialized)
                url
                worker-id
                client
                (channel))))
