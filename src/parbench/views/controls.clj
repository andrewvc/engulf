(ns parbench.views.controls
  (use noir-async.core
       noir.core)
  (require [cheshire.core :as json]
           [parbench.benchmark :as benchmark]))

(defn respond-state []
  (json/generate-string
    (let [state-only {:state (benchmark/current-state)}]
      (if (not (benchmark/started?))
        state-only
        (merge state-only @benchmark/current-run-opts)))))

(defpage [:get "/benchmarker/state"] {}
  (respond-state))

(defpage-async [:post "/benchmarker/state"]
  {:keys [state url concurrency requests]} conn
  (println (format "Running: s:%s u:%s c:%s r:%s" state url concurrency requests))
  (if (not (and state url concurrency requests))
    (respond conn {:status 409 :body "Could not start, missing info"})
    (let
      [concurrency (Integer/valueOf concurrency)
       requests    (Integer/valueOf requests)]
      (if (= "started" state)
        (benchmark/start-single-url url concurrency requests url)
        (benchmark/stop #(respond conn (respond-state)))))))
