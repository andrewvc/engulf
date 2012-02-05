(ns parbench.url-worker
  (:require [parbench.runner :as runner]
            [parbench.ning-client :as ning-http]
            [aleph.http :as aleph-http]
            [clojure.tools.logging :as log])
  (:use [parbench.utils :only [send-bench-msg]]
        noir-async.utils
         lamina.core))

(defprotocol Workable
  "A worker aware of global job state"
  (work [this] [this run-id] "Execute the job")
  (exec-runner [this run-id] "Execute the runner associated with this worker"))

(def tot-requests (atom 0))
(def open-requests (atom 0))
(def failures (atom 0))

(set-interval 1000
             (fn []
               (println
                 " T: " tot-requests
                 " O: " open-requests
                 " F: " failures)))

(defrecord UrlWorker [state url worker-id client output-ch]
  Workable
   
  (exec-runner [this run-id]
    (swap! tot-requests inc)
    (swap! open-requests inc)
    (let [req-start (System/currentTimeMillis)
          ch (client {:method :get :url url} 2000)]
      (on-success ch
        (fn [res]
          (swap! open-requests dec)
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
          (swap! failures inc)
          (println "STACK!")
          (.printStackTrace err)
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
