(ns parbench.benchmark
  (:require [parbench.requests-state :as rstate]
            [parbench.runner :as runner])
  (:use clojure.tools.logging
        lamina.core))

(def state (ref :stopped))

(def current-run-opts (ref {}))

(defn current-state []
  @state)

(defn started? []
  (= :started (current-state)))

(defprotocol Recordable
  "Recording protocol"
  (record-work [this _] "record it"))

(defrecord StandardRecorder [stats]
  Recordable
  (record-work [this data]
    (println @stats)
    (dosync
      (alter stats assoc :jobs-succeeded
                         (inc (:jobs-succeeded @stats))))))

(defn create-standard-recorder []
  (StandardRecorder. (ref {:jobs-succeeded 0
                           :jobs-failed 0})))

(def recorder (ref (create-standard-recorder)))

(defprotocol Workable
  "Worker Protocol"
  (work [this _] "Execute"))

(defrecord UrlWorker [url recorder]
  Workable
  (work [this worker-id] 
    (when (started?)
      (let [ch (runner/benchmark (runner/req :get url))]
        (on-success ch
          (fn [res]
            (println (str "RES: " (:status res)))
            (record-work recorder res)
            (work this worker-id)))
        (on-error ch
          (fn [err]
            (println (str worker-id " encountered error: " err))))))))

(defn create-single-url-job [url]
  (UrlWorker. url (create-standard-recorder)))

(defn run-workers [job worker-count max-jobs]
  (println (format "Running workers with %s %s %s" job worker-count max-jobs))
  (dotimes [worker-id worker-count]
    (println (str "The job is: " str))
    (work (create-single-url-job "http://www.andrewvc.com") worker-id)))

(defn set-started [job worker-count max-jobs]
  "Attempts to set the global state to started
   if already started returns false, else true"
  (dosync
    (if (not (= :stopped @state))
        false
        (do (ref-set state :started)
            (ref-set recorder (create-standard-recorder))
            (ref-set current-run-opts
                     {:job job
                      :worker-count worker-count
                      :max-jobs max-jobs})
            true))))

(defn start [job worker-count max-jobs]
  (if (set-started job worker-count max-jobs)
    (run-workers job worker-count max-jobs)
    (println "Could not start, already started")))

(defn start-single-url [url concurrency requests target]
  (start (create-single-url-job url) concurrency requests))

(defn stop []
  (dosync
    (ref-set state :stopped)))
