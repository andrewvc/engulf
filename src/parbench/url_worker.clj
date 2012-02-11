(ns parbench.url-worker
  (:require [parbench.runner :as runner]
            [parbench.ning-client :as ning-http]
            [parbench.hac-client :as hac-http]
            [aleph.http :as aleph-http]
            [clojure.tools.logging :as log])
  (:use [parbench.utils :only [send-bench-msg]]
        noir-async.utils
        lamina.core)
  (:import java.util.concurrent.Executors))

; Run callbacks in a cached thread pool for maximum throughput
(def callback-pool (Executors/newCachedThreadPool))

(defprotocol BenchmarkWorkable
  "A worker aware of global job state"
  (handle-success [this run-id req-start results])
  (handle-error [this run-id req-start err])
  (work [this] [this run-id] "Execute the job")
  (exec-runner [this run-id] "Execute the runner associated with this worker"))

(defrecord UrlWorker [state url worker-id client succ-callback err-callback]
  BenchmarkWorkable

  (handle-success [this run-id req-start response]
   (.submit callback-pool
    #(succ-callback
      (let [req-end (System/currentTimeMillis)]
           {:worker-id worker-id
            :run-id    run-id
            :req-start req-start
            :req-end   req-end
            :runtime   (- req-end req-start)
            :response  response})))
    (work this (inc run-id)))

  (handle-error [this run-id req-start err]
    (.submit callback-pool #(err-callback err))
    (work this (inc run-id)))
   
  (exec-runner [this run-id]
    (let [req-start (System/currentTimeMillis)
          ch (client {:method :get :url url} 2000)]
      (on-success ch (partial handle-success this run-id req-start))
      (on-error   ch (partial handle-error this run-id req-start))))

  (work [this]
   (compare-and-set! state :initialized :started)
   (work this 0))
  
  (work [this run-id]
   (when (= @state :started)
         (exec-runner this run-id))))

(defn create-single-url-worker
  [client-type url worker-id succ-callback err-callback]
  (UrlWorker. (atom :initialized)
                url
                worker-id
                (ning-http/http-client {})
                succ-callback
                err-callback))