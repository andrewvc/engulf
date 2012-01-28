(ns parbench.url-worker
  (:require [parbench.runner :as runner]
            [clojure.tools.logging :as log])
  (:use [parbench.utils :only [send-bench-msg]]
        aleph.http
        lamina.core))

(defprotocol StartStoppable
  (start [this])
  (started? [this])
  (stopped? [this])
  (stop [this]))

(defprotocol Workable
  "A worker aware of global job state"
  (work [this] [this run-id] "Execute the job")
  (exec-runner [this run-id] "Execute the runner associated with this worker"))

(defrecord UrlWorker [state url worker-id client output-ch]
  StartStoppable

  (start [this]
    (
  
  (stop [this]
    (println "Worker " worker-id " stopped")
    (swap! state #(when (not= :stopped %1) :stopped)))

  (started? [this] (= @state :stopped))
  (stopped? [this] (not started? this])
   
  
  
  Workable
   
  (exec-runner [this run-id]
    (compare-and-set! state :initialized :running)
    (let [req-start (System/currentTimeMillis)
          ch        (client {:method :get :url url})]
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
    (if (started? this)
      (exec-runner this run-id)))

(defn create-single-url-worker [url worker-id recorder]
  (UrlWorker. (atom :initialized)
              url
              worker-id
              (http-client {:url url})
              (channel)))
