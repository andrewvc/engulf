(ns parbench.views.controls
  (use noir-async.core
       noir.core
       lamina.core)
  (require [cheshire.core :as json]
           [parbench.benchmark :as benchmark]))

(defn respond-state []
  (json/generate-string
    (let [state (benchmark/current-state)]
      (if (not (benchmark/started?))
            {:state state}
            (assoc @benchmark/current-run-opts :state state)))))

(defpage [:get "/benchmarker/state"] {}
  (respond-state))

(defpage "/sleepy" {}
  (Thread/sleep 2000)
  "Done sleeping!")

(def blocker (agent 0))
 
(defpage-async "/blocking-sleepy" {} conn
  (send-off blocker (fn [i]
                      (Thread/sleep 2000)
                      (respond conn (str "Done sleeping!" @blocker))
                      (inc i))))

(defpage [:post "/benchmarker/state"]
  {:keys [state url concurrency requests]}
  (println (format "Running: s:%s u:%s c:%s r:%s" state url concurrency requests))
  (if (not state)
      (json/generate-string {:status 409 :body "Could not start, missing info"})
      (if (= "started" state)
        (let
          [concurrency (Integer/valueOf concurrency)
           requests    (Integer/valueOf requests)]
          (benchmark/start-single-url url concurrency requests url))
        (do
            (println "STOPPING")
            (benchmark/stop))))
  (respond-state))

(defwebsocket "/benchmarker/stream" {} conn
  (receive-all benchmark/output-ch
               (fn [msg] (send-message conn (json/generate-string msg)))))
