(ns engulf.api
  (:require
   [engulf.job-manager :as jmgr]
   [engulf.comm.control :as ctrl])
  (:use
   lamina.core))

;; We probably don't need the lock here but it's easier than designing weird UI
;; Failure states
(def start-lock (Object.))

(defn start-job
  [params]
  (locking start-lock
    (let [job (jmgr/register-job :http-benchmark params)]
      (stop-current-job)
      (enqueue ctrl/receiver [:job-start job])
      job)))

(defn stop-current-job
  [uuid]
  (jmgr/stop-job)
  (enqueue ctrl/receiver [:job-stop]))

(defn get-job
  [uuid])

(defn list-jobs
  [])

(defn del-job
  [uuid])