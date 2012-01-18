(ns parbench.benchmark
  (:require [parbench.requests-state :as rstate]
            [parbench.runner :as runner])
  (:use clojure.tools.logging
        lamina.core))

(defn increment-keys
  "Given a map and a list of keys, this will return an identical map with those keys
   with those keys incremented.
    
   Keys with a null value will be set to 1."
  [src-map & xs]
  (merge
    src-map
    (into {} (map #(vector %1 (inc (or (get src-map %1) 0))) xs))))


(def state (ref :stopped))
(def current-run-opts (ref {}))
(def workers (ref []))
(def recorder (ref nil))
(def output-ch (channel))
(receive-all output-ch (fn [_])) ; keep the channel empty if no listeners

(defn current-state [] @state)

(defn started? []
  (= :started (current-state)))

(def stopped? (comp not started?))

(defprotocol Recordable
  "Recording protocol"
  (check-recordable [this] "Check if this result should should be recorded")
  (record-work-success [this worker-id data] "record a successful work run")
  (record-work-failure [this worker-id err] "record a failed work run")
  (start-stats-broadcast [this] "Starts broadcasting stats on this recorders output-ch")
  (stop-stats-broadcast [this] "Stops broadcasting on the stats channel"))

(defrecord StandardRecorder [max-runs stats]
  Recordable
  (start-stats-broadcast [this] )
  (stop-stats-broadcast [this] )
  (check-recordable [this]
    (dosync 
      (cond
        (>= (and (started?) (:runs-total (ensure stats))) (- max-runs 1))
          (do
            (ref-set state :stopped)
            true)
        (started?)
          true
        (stopped?)
          false)))
  (record-work-success [this worker-id data]
    (println "recw" @stats)
    (dosync
      (when (check-recordable this)
        (alter stats increment-keys :runs-succeeded :runs-total)))
    (enqueue output-ch @stats))
  (record-work-failure [this worker-id err]
    (println "Encountered err: " err)
    (dosync
      (when (check-recordable this)
              (alter stats increment-keys :runs-failed :runs-total)))
    (enqueue output-ch @stats)))

(defn create-standard-recorder [max-runs]
  (StandardRecorder. max-runs
                     (ref {:runs-total 0
                           :runs-succeeded 0
                           :runs-failed 0})))

(defprotocol Workable
  "A worker aware of global job state"
  (work [this] "Execute the job")
  (set-stopped [this] "Mark this worker as stopped")
  (exec-runner [this] "Execute the runner associated with this worker"))

(defrecord UrlWorker [state url worker-id]
  Workable
  (set-stopped [this]
    (println "STOPPING WORKER")
    (swap! state #(when (not= :stopped %1) :stopped)))
  (exec-runner [this]
    (compare-and-set! state :initialized :running)
    (let [ch (runner/benchmark (runner/req :get url))]
      (on-success ch
        (fn [res]
          (record-work-success @recorder worker-id res)
          (work this)))
      (on-error ch
        (fn [err]
          (swap! state :ran)
          (record-work-failure @recorder worker-id err)))))
  (work [this] 
    (if (stopped?)
      (set-stopped this)
      (exec-runner this))))

(defn create-single-url-worker [url worker-id]
  (UrlWorker. (atom :initialized) url worker-id ))

(defn run-workers [init-worker-fn worker-count]
  (let [new-workers (vec (map #(init-worker-fn %1) (range worker-count)))]
    (dosync (ref-set workers new-workers))
      (doseq [worker @workers] (work worker))))

(defn set-started [worker-count max-runs]
  "Attempts to set the global state to started
   if already started returns false, else true"
  (dosync
    (if (not (= :stopped @state))
        false
        (do (ref-set state :started)
            (ref-set recorder (create-standard-recorder max-runs))
            true))))

(defn start [init-worker-fn worker-count max-runs]
  (if (set-started worker-count max-runs)
        (run-workers init-worker-fn worker-count)
        (println "Could not start, already started")))
 
(defn stop []
  (dosync (ref-set state :stopped)))
 
(defn start-single-url [url concurrency requests target]
  (start (partial create-single-url-worker url) concurrency requests))
