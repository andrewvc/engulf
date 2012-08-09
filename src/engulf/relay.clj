(ns engulf.relay
  (:require
   [engulf.formula :as formula]
   [engulf.utils :as utils]
   [clojure.stacktrace :as strace]
   [clojure.tools.logging :as log]
   [lamina.core :as lc]))

(def ^:dynamic receiver (lc/channel* :grounded? true :permanent? true))

(def state (atom :stopped))
(def receive-cb (atom nil))

(def current (agent nil))

(defn job-results-filter
  "Returns a function that will only let results through for a given job UUID"
  [uuid]
  (fn [{name "name" {job-uuid "job-uuid"} "body" :as m}]
    (and (= "job-result" name) (= uuid job-uuid))))

(defn job-ingress-channel
  [{uuid :uuid :as job}]
  (when (not uuid)
    (throw (Exception. (str "Missing UUID for job!" job))))
  (->> receiver
       (lc/filter* (job-results-filter uuid))
       (lc/map* (fn [{{results "results"} "body"}] results))))

(defn start-job
  [job]
  (utils/safe-send-off-with-result current res state
    (log/info (str "Starting job on relay: " job))
    
    (when-let [{old-fla :formula} state] (formula/stop old-fla))
    
    (let [fla (formula/init-job-formula job)
          in-ch (job-ingress-channel job)
          res-ch (formula/start-relay fla in-ch)
          current-state {:job job
                         :formula fla
                         :ingress-channel in-ch
                         :results-ch res-ch}]

      (lc/ground res-ch)
      (lc/enqueue res current-state)
      current-state)))


(defn stop-job
  []
  (utils/safe-send-off-with-result current res state
    (when-let [{fla :formula} state]
      (lc/enqueue res (formula/stop fla))
      (lc/close (:ingress-channel state)))
    nil))

(defn handle-message
  [{:strs [name body] :as msg}]
  (condp = name
    "job-start" (start-job body)
    "job-stop" (stop-job)
    "job-result" nil ;; These are handled straight off the receiver
    (log/error (str "Got unknown message " msg))))

(defn start
  "Startup the relay"
  []
  (if (compare-and-set! state :stopped :started)
    (let [cb (lc/receive-all receiver handle-message)]
      #(fn relay-stop [] (lc/cancel-callback cb)))
    (log/warn "Double relay start detected! Ignoring.")))