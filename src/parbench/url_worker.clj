(ns parbench.url-worker
  (:require [parbench.runner :as runner]
            [clojure.tools.logging :as log])
  (:use [parbench.recorder :only [record-work-success
                                  record-work-failure]]
        aleph.http
        lamina.core))

(defprotocol Workable
  "A worker aware of global job state"
  (work [this] [this run-id] "Execute the job")
  (set-stopped [this] "Mark this worker as stopped")
  (exec-runner [this run-id] "Execute the runner associated with this worker"))

(defn- record-result [recorder req-res]
  (fn [req-res]
    (let [req-end (System/currentTimeMillis)]
      (record-work-success
        recorder
        worker-id
        {:run-id    run-id
         :req-start req-start
         :req-end   req-end
         :runtime   (- req-end req-start)
         :response  req-res}))
    (work this (inc run-id))))

(defrecord UrlWorker [state url worker-id client recorder]
  Workable
   
  (set-stopped [this]
    (swap! state #(when (not= :stopped %1) :stopped)))
   
  (exec-runn [this run-id]
    (compare-and-set! state :initialized :running)
    (let [req-start (System/currentTimeMillis)
          ch        (client {:method :get :url url})]
      (on-success ch
        (fn [res]
          (record-result recorder res)
          (work this (inc run-id))))
      (on-error ch
        (fn [err]
          (swap! state :ran)
          (record-work-failure @recorder worker-id err)))))
   
  (work [this]
    (work this 0))
   
  (work [this run-id]
    (if (stopped?)
      (set-stopped this)
      (exec-run this run-id))))

(defn create-single-url-worker [url worker-id recorder]
  (UrlWorker. (atom :initialized)
              url
              worker-id
              (http-client {:url url})
              recorder))
