(ns engulf.web-views.nodes
  (:use engulf.utils
        engulf.web-views.common
        noir.core
        lamina.core
        aleph.formats)
  (:require [engulf.comm.node-manager :as nmgr]
            [noir-async.core :as na]
            [noir.request :as noir-req]
            [cheshire.core :as json]
            [clojure.tools.logging :as log]
            [clojure.walk :as walk]))

(defpage "/nodes" {}
  (json-resp 200 (nmgr/json-nodes)))

(defpage "/nodes/:uuid" {:keys [uuid]}
  (if-let [n (nmgr/get-json-node uuid)]
    (json-resp 200 n)
    (json-resp 404 {:message "not found!"})))