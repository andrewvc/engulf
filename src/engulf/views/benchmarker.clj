(ns engulf.views.benchmarker
  (:use noir-async.core
       noir-async.utils
       noir.core
       lamina.core)
  (:require [cheshire.core :as json]
            [engulf.benchmark :as benchmark]
            [clojure.tools.logging :as log])
  (:import java.net.URL))

(def socket-ch (permanent-channel))
(def json-socket-ch (map* #(json/generate-string %1) socket-ch))
(receive-all json-socket-ch (fn [_])) ; Ground

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
   (try
     (let [url (str (URL. url))
           conc (Integer/valueOf concurrency)
           reqs (Integer/valueOf requests)]
       (log/info "About to start test for " url)
       (benchmark/run-new-benchmark url conc reqs)
       (siphon (:output-ch @benchmark/current-benchmark) socket-ch)
       (respond conn (current-state)))
     (catch Exception e
       (log/error e "Could not start benchmarker")
       (respond conn (json/generate-string
                      {:error (str (class e) ": " (.getMessage e))}))))
   :else (benchmark/stop-current-benchmark)))

(defwebsocket "/benchmarker/stream" {} conn
  (receive-all json-socket-ch #(send-message conn %1)))