(ns engulf.views.benchmarker
  (use noir-async.core
       noir-async.utils
       noir.core
       lamina.core)
  (require [cheshire.core :as json]
           [engulf.benchmark :as benchmark]))

(def socket-ch (permanent-channel))
(receive-all socket-ch (fn [_] ))

(defn current-state
  "Returns the current benchmarker's state in JSON"
  []
  (json/generate-string
   (let [this-bench @benchmark/current-benchmark]
     (if (not this-bench)
       {:state :stopped
        :stats {}}
       {:state @(:state this-bench)
        :stats (benchmark/stats this-bench)}))))

(defpage [:get "/benchmarker"] {}
  (current-state))

(defpage-async [:post "/benchmarker"]
  {:keys [state url concurrency requests block-completion]} conn
  (println (format "Running: s:%s u:%s c:%s r:%s"
                    state url concurrency requests))
  (cond
   (= "started" state)
   (let [cur-bench (benchmark/run-new-benchmark
                    url
                    (Integer/valueOf concurrency)
                    (Integer/valueOf requests))]
     (siphon (:output-ch cur-bench) socket-ch))
   :else
     (benchmark/stop-current-benchmark))
  (respond conn (current-state)))

(defwebsocket "/benchmarker/stream" {} conn
  (receive-all socket-ch
               (fn [msg]
                 (send-message conn (json/generate-string msg)))))
