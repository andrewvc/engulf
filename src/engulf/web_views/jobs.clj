(ns engulf.web-views.jobs
  (:use engulf.utils
        noir.core
        lamina.core
        aleph.formats)
  (:require [noir-async.core :as na]
            [noir.request :as noir-req]
            [cheshire.core :as json]
            [engulf.control :as ctrl]
            [engulf.job-manager :as job-manager]
            [clojure.tools.logging :as log]
            [clojure.walk :as walk])
  (:import java.net.URL))

(defn json-resp [status body]
  {:status status
   :content-type "application/json"
   :body (json/generate-string body)})

(defn jsonify-results
  [results]
  (json/generate-string
   (assoc results "percentiles"
          (.toString (results "percentiles")))))

(defn async-stream
  [conn ch]
  (na/async-push conn {:status 200 :chunked true})
  (receive-all ch #(na/async-push conn (str (json/generate-string %) "\n")))
  (on-closed ch #(na/close-connection conn)))

(defpage [:get "/jobs/current"]  {}
  (if-let [job @job-manager/current-job]
    (json-resp 200 (job-manager/current-job-snapshot))
    (json-resp 404 {:message "No current job!"})))

(na/defpage-async [:post "/jobs/current"] {} conn
  (try
    (let [params (walk/keywordize-keys (json/parse-string (na/request-body-str conn)))
          {:keys [results-ch job]} (ctrl/start-job params)]
      (if (= (:_stream params) "true")
        (async-stream conn results-ch)
        (na/async-push conn (json-resp 200 (job-manager/job-snapshot job)))))
    (catch Exception e
      (log/warn e "Error starting job!")
      (na/async-push conn (json-resp 500 {:message (str e)})))))

(defpage [:delete "/jobs/current"] {}
  (if-let [stopped (ctrl/stop-job)]
    (json-resp 200 {:job (job-manager/serializable )})
    (json-resp 404 {})))