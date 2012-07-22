(ns engulf.relay
  (:require
   [clojure.tools.logging :as log]
   [engulf.formula :as formula]
   [engulf.job-manager :as job-mgr]
   [clojure.stacktrace :as strace]
   [lamina.core :as lc]))

(def ^:dynamic receiver (lc/channel* :grounded true :permanent true))
(def ^:dynamic emitter (lc/channel* :grounded true :permanent true))

(def state (atom :stopped))
(def receive-cb (atom nil))

(def current (agent nil))

(defmacro safe-send-off-with-result
  "Convenience utility for managing stateful transitions returning a result channel over an agent"
  [state-agent res-binding bindings & body]
  `(let [~res-binding (lc/result-channel)]
     (send-off ~state-agent
               (fn ssowr-cb [~bindings]
                 (try
                   ~@body
                   (catch Exception e#
                     (log/warn e# "Exception during safe-send-off!")
                     (lc/error ~res-binding e#)
                     nil))))))

(defn start-job
  [job]
  (safe-send-off-with-result current res state
    (let [fla (formula/init-job-formula job)]
      (when-let [{old-fla :formula} state] (formula/stop old-fla))
      (lc/enqueue res (formula/start-relay fla receiver))
      {:job job :formula fla})))


(defn stop-job
  []
  (safe-send-off-with-result current res state
    (when state (formula/stop (:formula state)))
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