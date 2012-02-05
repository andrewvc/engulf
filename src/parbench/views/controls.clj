(ns parbench.views.controls
  (use noir-async.core
       noir-async.utils
       noir.core
       lamina.core)
  (require [cheshire.core :as json]
           [parbench.benchmark :as benchmark]))

(def bench (ref nil))
(def socket-ch (channel))

(defn respond-state []
  (json/generate-string
   (let [state (if @bench @(:state @bench) :stopped)]
     {:state state})))

(def test-page-count (atom 0))
(defpage-async "/test" {} conn
  (set-timeout 500
               (fn []
                 (respond conn (str "Test Response #" (swap! test-page-count inc))))))

(defpage [:get "/benchmarker/state"] {}
  (respond-state))

(defpage [:post "/benchmarker/state"]
  {:keys [state url concurrency requests]}
  (println (format "Running: s:%s u:%s c:%s r:%s" state url concurrency requests))
  (if (= "started" state)
      (let [concurrency (Integer/valueOf concurrency)
          requests    (Integer/valueOf requests)
          benchmarker (benchmark/create-single-url-benchmark url concurrency requests)]
      (dosync
        (if-let [b (ensure bench)]
          (if-let [bt @(:broadcast-task b)]
            (.cancel bt)))
        (ref-set bench benchmarker))
      (benchmark/start benchmarker)
      (siphon (:output-ch benchmarker) socket-ch))
    (benchmark/stop @bench))
  (respond-state))

(defwebsocket "/benchmarker/stream" {} conn
  (receive-all socket-ch
               (fn [msg]
;                 (println msg)
                 (send-message conn (json/generate-string msg)))))
