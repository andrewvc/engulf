(ns engulf.migrations
  (:require [ragtime.sql.database :as sql]
            [ragtime.strategy :as strategy]
            [engulf.settings :as settings]
            [clojure.java.jdbc :as jdbc])
  (:use ragtime.core)
  (:import ragtime.sql.database.SqlDatabase))

(def db )


(def create-jobs
  {:id "create-jobs"
   :up (fn [db] (jdbc/with-connection db
                  (jdbc/create-table "jobs"
                                     [:uuid "VARCHAR(255)" "PRIMARY KEY"]
                                     [:formula_name "VARCHAR(255)" "NOT NULL"]
                                     [:started_at "INTEGER"]
                                     [:ended_at "INTEGER"]
                                     [:params "TEXT"]
                                     [:title "VARCHAR"]
                                     [:notes "TEXT"])))
   :down (fn [db] )})

(def create-results
  {:id "create-results"
   :up (fn [db] (jdbc/with-connection db
                  (jdbc/create-table "results"
                                     [:uuid "VARCHAR(255)" "PRIMARY KEY"]
                                     [:job_uuid "VARCHAR(255)" "NOT NULL"]
                                     [:value "TEXT" "NOT NULL"]
                                     [:created_at "INTEGER" "NOT NULL"])
                  (jdbc/do-commands
                   "CREATE INDEX results_job_uuid_idx ON results(job_uuid)")))})

(defn ensure-all
  []
  (let [{{:keys [classname subprotocol subname user password]}:jdbc} settings/all
        db (ragtime.sql.database.SqlDatabase. classname subprotocol subname
                                             user password)]
    (migrate-all db
                 [create-jobs
                  create-results]
                 strategy/apply-new)))