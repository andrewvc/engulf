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

(receive-all ctrl/emitter (fn [m] "ctrl received" m))
(def json-socket-ch (channel))

(receive-all
 ctrl/emitter
 (fn jsonifier [m]
   (try
     (enqueue json-socket-ch (json/generate-string m))
     (catch Exception e
       (log/warn "Could not jsonify" m)))))

(defpage [:get "/control/current-job"]  {}
  (json/generate-string @job-manager/current-job))

(na/defpage-async [:post "/control/current-job"] {} conn
  (let [parsed (json/parse-string (bytes->string (:body (:ring-request conn))))]
    (ctrl/start-job parsed))
  (na/async-push conn {:status 200
                       :content-type "application/json"
                       :body (json/generate-string {:status "OK"})}))

(na/defpage-async "/control/stream" {} conn
  (receive-all json-socket-ch (fn [m] (na/async-push conn m))))