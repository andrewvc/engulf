(ns engulf.web-views.river
  (:use engulf.utils
        engulf.web-views.common
        noir.core
        lamina.core
        aleph.formats)
  (:require [engulf.job-manager :as jmgr]
            [engulf.comm.node-manager :as nmgr]
            [engulf.control :as ctrl]
            [noir-async.core :as na]
            [clojure.tools.logging :as log]
            [clojure.walk :as walk]))

(na/defpage-async "/river" {} conn
  (when (not (na/websocket? conn))
    (na/async-push conn {:status 200 :chunked true}))
  
  (na/async-push
   conn
   (json-chunk {"entity" "system" "name" "current-nodes" "body" (nmgr/json-nodes)}))
  (na/async-push
   conn
   (json-chunk {"entity" "system" "name" "current-job" "body" @jmgr/current-job}))
  
  ;; TODO: There's a race condiion here where node data isn't coordinated
  ;; With the  initial full node list. Some events could slip by, though it's
  ;; unlikely in most systems

  (let [output (na/writable-channel conn)]
    (siphon (map* json-chunk ctrl/emitter) output)
    (siphon (map* json-chunk jmgr/emitter) output)))
