(ns engulf.relay
  (:require
   [clojure.tools.logging :as log]
   [engulf.formula :as formula]
   [engulf.job-manager :as job-mgr]
   [lamina.core :as lc]))

(def ^:dynamic receiver (lc/channel* :grounded true :permanent true))
(def ^:dynamic emitter (lc/channel* :grounded true :permanent true))

(def state (atom :stopped))
(def receive-cb (atom nil))

(defn handle-message
  [[name body]]
  (condp = name
    :job-start (start-job body)
    :job-stop (stop-job)
    (log/error (str "Got unknown message " name body))))

(defn start
  "Startup the relay"
  []
  (if (compare-and-set! state :stopped :started)
    (let [cb (lc/receive-all receiver handle-message)]
      #(fn relay-stop [] (lc/cancel-callback cb)))
    (log/warn "Double relay start detected! Ignoring.")))