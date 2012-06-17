(ns engulf.web-views.control
  (:use engulf.utils
        noir.core
        lamina.core)
  (:require [noir-async.core :as na]
            [cheshire.core :as json]
            [engulf.control :as ctrl]
            [engulf.bus :as bus]
            [engulf.job-manager :as job-manager]
            [clojure.tools.logging :as log])
  (:import java.net.URL))

(receive-all bus/global (fn [m] "global bus received" m))
(def json-socket-ch (channel))

(receive-all
 bus/global-json-safe
 (fn jsonifier [m]
   (try
     (enqueue json-socket-ch (json/generate-string m))
     (catch Exception e
       (log/warn "Could not jsonify" m)))))

(defpage [:get "/control/current-job"]  {}
  (json/generate-string @job-manager/current-job))

(defpage [:post "/control/current-job"] {}
  "")

(na/defpage-async "/control/stream" {} conn
  (receive-all json-socket-ch(fn [m] (na/async-push conn m))))