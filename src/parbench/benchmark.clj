(ns parbench.benchmark
  (:require [parbench.requests-state :as rstate]
            [parbench.runner :as runner]
            [clojure.tools.logging :as log])
  (:use clojure.tools.logging
        noir-async.utils
        aleph.http
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

(declare stop start)

(defn broadcast-data [data-type data]
  (io! (enqueue output-ch {:dtype data-type :data data})))

(defprotocol Recordable
  "Recording protocol"
  (check-recordable [this] "Check if this result should should be recorded")
  (record-work-success [this worker-id data] "record a successful work run")
  (record-work-failure [this worker-id err] "record a failed work run")
  (record-runtime [this worker-id run-id runtime])
  (processed-agg-stats [this])
  (runtime-agg-stats [this statsd])
  (broadcast-agg-stats [this]))

(defrecord StandardRecorder [started-at ended-at max-runs stats]
  Recordable
  (broadcast-agg-stats [this]
    (let [pstats (processed-agg-stats this)]
      (broadcast-data :agg pstats)
      ))
  (processed-agg-stats [this]
    (let [statsd @stats]
      ;(println  (runtime-agg-stats this statsd))
      (merge
        (runtime-agg-stats this statsd)
        (let [runtime (- (or @ended-at (System/currentTimeMillis)) @started-at)]
          {:runtime runtime
           :runs-sec (/ (:runs-total statsd) (/ runtime 1000))})
        (select-keys statsd
          [:runs-total :runs-succeeded :runs-failed
           :avg-runtime-by-start-time
           :response-code-counts]))))
  (runtime-agg-stats [this statsd]
    (let [runtimes (sort (:runtimes statsd))
          rt-count (count runtimes)]
      {:median-runtime
         (when (> rt-count 5) (nth runtimes (/ rt-count 2)))
       :runtime-percentiles
         (let [partn (let [n (int (/ rt-count 100))] (if (> n 1) n 1))]
           (map-indexed
             (fn [idx group]
               (let [min (first group)
                     max (last group)
                     avg (int (/ (+ min max) 2))]
                 {:min min :max max :avg avg
                  :idx idx
                  :count (count group)}))
             (partition-all partn runtimes)))
       }))
  (check-recordable [this]
    (dosync 
      (if (stopped?)
            false
            (do (when (>= (:runs-total (ensure stats)) (- max-runs 1))
                  (stop))
                true))))
  (record-runtime [this worker-id run-id runtime]
    (alter stats update-in [:runtimes] #(conj %1 runtime)))
  (record-work-success [this worker-id data]
    (dosync
      (when (check-recordable this)
        (let [rstart (:req-start data)]
          (alter stats update-in [:avg-runtime-by-start-time (int (/ rstart 1000))]
                 (fn [bucket]
                   (let [rcount (+ 1 (get bucket :count 0))
                         total  (+ (:runtime data) (get bucket :total 0))]
                     (merge bucket
                            {:count rcount
                             :total total
                             :avg   (int (/ total rcount))})))))
        (record-runtime this worker-id (:run-id data) (:runtime data))
        (alter stats update-in [:response-code-counts]
               increment-keys (:status (:response data)))
        (alter stats increment-keys :runs-succeeded :runs-total))))
  (record-work-failure [this worker-id err]
    (.printStackTrace err)
    (broadcast-data :err err)
    (dosync
      (when (check-recordable this)
              (alter stats increment-keys :runs-failed :runs-total)))))

(defn create-standard-recorder [max-runs]
  (StandardRecorder. (ref (System/currentTimeMillis))
                     (ref nil)
                     max-runs
                     (ref {:runs-total 0
                           :runs-succeeded 0
                           :runs-failed 0
                           :response-code-counts {}
                           :avg-runtime-by-start-time {}
                           :runtimes []})))

(set-interval 200 #(if-let [r @recorder]
                     (try 
                       (broadcast-agg-stats r)
                       (catch Exception e
                         (.printStackTrace e)
                         (println e)
                         (log/fatal e)))))
 
(defprotocol Workable
  "A worker aware of global job state"
  (work [this] [this run-id] "Execute the job")
  (set-stopped [this] "Mark this worker as stopped")
  (exec-runner [this run-id] "Execute the runner associated with this worker"))

(defrecord UrlWorker [state url worker-id client]
  Workable
  (set-stopped [this]
    (swap! state #(when (not= :stopped %1) :stopped)))
  (exec-runner [this run-id]
    (compare-and-set! state :initialized :running)
    (let [req-start (System/currentTimeMillis)
          ch        (client {:method :get :url url})]
      (on-success ch
        (fn [req-res]
          (let [req-end (System/currentTimeMillis)]
            (record-work-success @recorder worker-id
                                 {:run-id   run-id
                                  :req-start req-start
                                  :req-end   req-end
                                  :runtime  (- req-end req-start)
                                  :response req-res}))
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
      (exec-runner this run-id))))

(defn create-single-url-worker [url worker-id]
  (UrlWorker. (atom :initialized) url worker-id (http-client {:url url})))

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
        (io! (log/warn "Could not start, already started"))))
 
(defn stop []
  (dosync (ref-set state :stopped)
          (ref-set (:ended-at @recorder) (System/currentTimeMillis))))
 
(defn start-single-url [url concurrency requests target]
  (start (partial create-single-url-worker url) concurrency requests))
