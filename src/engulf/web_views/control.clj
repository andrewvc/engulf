(ns engulf.web-views.control
  (:use engulf.utils
        noir.core
        lamina.core
        aleph.formats)
  (:require [noir-async.core :as na]
            [noir.request :as noir-req]
            [cheshire.core :as json]
            [engulf.control :as ctrl]
            [engulf.job-manager :as job-manager]
            [clojure.tools.logging :as log])
  (:import java.net.URL))

(defn json-resp [status body]
  {:status status
   :content-type "application/json"
   :body (json/generate-string body)})

(receive-all ctrl/emitter (fn [m] "ctrl received" m))
(def json-socket-ch (channel))

(receive-all
 ctrl/emitter
 (fn jsonifier [m]
   (try
     (enqueue json-socket-ch (json/generate-string m))
     (catch Exception e
       (log/warn "Could not jsonify" m)))))

(na/defpage-async "/control/stream" {} conn
  (receive-all json-socket-ch (fn [m] (na/async-push conn m))))


(defpage [:get "/jobs/current"]  {}
  (if-let [job @job-manager/current-job]
    (json-resp 200 (job-manager/current-job-snapshot))
    (json-resp 404 {:message "No current job!"})))

(na/defpage-async [:post "/jobs/current14"] {:as params} conn
    (try
      (let [job (ctrl/start-job params)]
        (na/async-push conn (json-resp 200 (job-manager/job-snapshot job))))
      (catch Exception e
        (log/warn e "Error starting job!")
        (na/async-push conn (json-resp 500 {:message (str e)})))))
