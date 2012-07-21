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

(def current-job (agent nil))

(defn- stop-job-val
  "Stops a dereferenced job value based on its :formula"
  [job-val]
  (try
    (when-let [formula-inst (and job-val (:formula job-val))]
      (formula/stop formula-inst))))

(defn start-job
  [job]
  (send-off
   current-job
   (fn relay-frmla-start [current-job-val]
     (try
       (stop-job-val current-job-val)
       (let [formula-inst (formula/init-job-formula job)]
         (formula/start-relay (formula/init-job-formula job) receiver)
         (assoc job :formula formula-inst))
       (catch Exception e
         (log/warn e "Could not start job!"))))))

(defn stop-job
  []
  (send-off
   current-job
   (fn relay-frmla-stop [current-job-val]
     (try
       (stop-job-val current-job-val)
       (catch Throwable t
         (log/warn t (str  "Could not stop job in relay: " current-job-val))
         (strace/print-stack-trace t))))))

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