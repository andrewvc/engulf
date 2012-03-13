(ns engulf.worker
  (:require [engulf.runner :as runner]
            [engulf.config :as config]
            [engulf.ning-client :as ning-client]
            [aleph.http :as aleph-http]
            [clojure.tools.logging :as log])
  (:use [engulf.utils :only [send-bench-msg]]
        noir-async.utils
        lamina.core
        lamina.api)
  (:import java.util.concurrent.Executors
           java.util.concurrent.ExecutorService))

;; Emit all messages in a threadpool to ensure that outside users of this
;; don't black throughput
(def ^ExecutorService callback-pool (Executors/newFixedThreadPool 1))

(defn submit-result [^Callable cb]
  (.submit callback-pool cb))

(defprotocol BenchmarkWorkable
  (handle-success [this run-id req-start results])
  (handle-error [this run-id req-start err])
  (warmup [this] "Warm the VM up for this worker")
  (work [this] [this run-id result] "Execute the job"))

(defn format-response
  "Formats the response from an runner request with
   auxilliary data"
  [{:keys [worker-id run-id]} req-start resp]
  (let [req-end (System/currentTimeMillis)]
    {:worker-id worker-id
     :run-id    run-id
     :req-start req-start
     :req-end   req-end
     :runtime   (- req-end req-start)
     :response  resp}))

(defn format-error
  "Formats an error with worker data"
  [{:keys [worker-id run-id]} req-start error]
  {:worker-id worker-id :run-id run-id :error error})

(defrecord Worker [state client request worker-id]
  BenchmarkWorkable

  (work [this]
   (compare-and-set! state :initialized :started)
   (work this 0 (result-channel)))
  
  (work [this run-id result]
    (cond
     (not= @state :started)
     false
     :else
     (let [req-start (System/currentTimeMillis)
           next-result (result-channel)
           continue #(work this (inc run-id) next-result)]
       (when-let [ch (ning-client/execute-request client request)]
         (on-success ch (fn [response]
           (submit-result
            #(success! result
              [(format-response this req-start response) next-result]))
           (continue)))
         (on-error ch (fn [error]
           (submit-result
            #(error! result
              [(format-error this req-start error) next-result]))
           (continue)))
         result))))
      
  (warmup [this]
    (let [warmup-url (str "http://127.0.0.1:"
                            (config/opt :port)
                            "/test-responses/fast-async")]
      (runner/req-async :get warmup-url 1))))

(defn create-single-url-worker
  "Suitable for benchmarking a single URL at a time"
  [client url worker-id]
  (let [request (ning-client/build-request {:method "get" :url url})
        worker (Worker. (atom :initialized) client request worker-id)]
    worker))
