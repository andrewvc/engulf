(ns engulf.relay
  (:require
   [engulf.formula :as formula]
   [engulf.job-manager :as job-mgr]
   [engulf.utils :as utils]
   [clojure.stacktrace :as strace]
   [clojure.tools.logging :as log]
   [lamina.core :as lc]))

(def ^:dynamic receiver (lc/channel* :grounded true :permanent true))
(def ^:dynamic emitter (lc/channel* :grounded true :permanent true))

(def state (atom :stopped))
(def receive-cb (atom nil))

(def current (agent nil))

(defn start-job
  [job]
  (utils/safe-send-off-with-result current res state
    (when-let [{old-fla :formula} state] (formula/stop old-fla))
    (let [fla (formula/init-job-formula job)]
      (lc/enqueue res (formula/start-relay fla receiver))
      {:job job :formula fla})))


(defn stop-job
  []
  (utils/safe-send-off-with-result current res state
    (when state (lc/enqueue res (formula/stop (:formula state))))
    nil))

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