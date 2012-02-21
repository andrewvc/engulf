(ns engulf.url-worker
  (:require [engulf.runner :as runner]
            [aleph.http :as aleph-http]
            [clojure.tools.logging :as log])
  (:use [engulf.utils :only [send-bench-msg]]
        noir-async.utils
        lamina.core)
  (:import java.util.concurrent.Executors
           java.util.concurrent.ExecutorService))

; Run callbacks in a thread pool for maximum throughput
(def ^ExecutorService callback-pool (Executors/newFixedThreadPool 2))

(defprotocol BenchmarkWorkable
  "A worker aware of global job state"
  (handle-success [this run-id req-start results])
  (handle-error [this run-id req-start err])
  (work [this] [this run-id] "Execute the job")
  (exec-runner [this run-id] "Execute the runner associated with this worker"))

(defn-async async-fetch [url]
  (runner/req :get url))

(defrecord UrlWorker [state url worker-id succ-callback err-callback]
  BenchmarkWorkable

  (handle-success [this run-id req-start response]
    ;; This type-hint actually matters
    (let [^Callable callback-dispatcher
                    #(succ-callback
                      (let [req-end (System/currentTimeMillis)]
                        {:worker-id worker-id
                         :run-id    run-id
                         :req-start req-start
                         :req-end   req-end
                         :runtime   (- req-end req-start)
                         :response  response}))]
      (.submit callback-pool callback-dispatcher))
    (work this (inc run-id)))

  (handle-error [this run-id req-start err]
    (let [^Callable callback-dispatcher #(err-callback err)]
      (.submit callback-pool callback-dispatcher))
    (work this (inc run-id)))
   
  (exec-runner [this run-id]
    (let [req-start (System/currentTimeMillis)
          ch (runner/req-async :get url)]
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
  (let [worker (UrlWorker. (atom :initialized)
                           url
                           worker-id
                           succ-callback
                    err-callback)]
    worker))
