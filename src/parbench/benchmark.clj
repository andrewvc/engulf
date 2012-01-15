(ns parbench.benchmark
  (:require [parbench.requests-state :as rstate])
  (:use clojure.tools.logging))

(def state (ref :stopped))


(defprotocol JobRecorder
  (record-job [this data]))

(defrecord StandardRecorder [responses]
  JobRecorder
  (record-job [this data]
    (println "Recording data")))

(defn create-standard-recorder []
  (StandardRecorder. []))
 
(defprotocol BenchJob
  (record-result [this data])
  (perform [this]))

(defrecord SingleUrlJob [url job-recorder]
  BenchJob
  (record-result [this data]
    (println (str "Received result: " data)))
  (perform [this] (println "empty")))

(defn create-single-url-jobs [url]
  (cons (SingleUrlJob. url (create-standard-recorder)) (lazy-seq create-single-url-jobs)))
 
(defn run-worker [worker-id jobs]
  (let [job (first jobs)]
    (println (str "Running job: " job))
    (perform job)))
 
(defn run-workers [jobs worker-count]
  (dotimes [worker-id worker-count]
    (run-worker worker-id jobs)))

(defn start [jobs worker-count]
  (if (dosync
        (when (= :stopped @state)
              (ref-set state :started)
              true)
              (do (run-workers jobs worker-count)
                  (ref-set state :started)))
      (run-workers jobs worker-count)))

(defn start-single-url [url worker-count]
  (start (create-single-url-jobs url) worker-count))

(defn stop []
  (dosync
    (ref-set state :stopped)))
