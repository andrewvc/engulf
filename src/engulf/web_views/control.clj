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

(defn jsonify-results
  [results]
  (json/generate-string
   (assoc results "percentiles"
          (.toString (results "percentiles")))))

(defpage [:get "/jobs/current"]  {}
  (if-let [job @job-manager/current-job]
    (json-resp 200 (job-manager/current-job-snapshot))
    (json-resp 404 {:message "No current job!"})))

(na/defpage-async [:post "/jobs/current14"] {:as params} conn
    (try
      (let [res-ch (ctrl/start-job params)]
        ;;(na/async-push conn (json-resp 200 (job-manager/job-snapshot job)))
        (na/async-push conn {:status 200 :chunked true})
        (receive-all res-ch #(na/async-push conn (str (jsonify-results %) "\n")))
        (on-closed res-ch #(log/warn "DID IT CLOSE!!!!"))
        (on-closed res-ch #(na/close conn)))
      (catch Exception e
        (log/warn e "Error starting job!")
        (na/async-push conn (json-resp 500 {:message (str e)})))))
