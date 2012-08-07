(ns engulf.database
  (:require [engulf.formula :as forumla]
            [engulf.settings :as settings]
            [engulf.utils :as utils]
            [cheshire.core :as json]
            [clojure.java.jdbc :as jdbc]
            [clojure.walk :as walk])
  (:use korma.db korma.core)
  (:import java.util.UUID))

(defn connect
  "Connect to the db specified in settings"
  []
  (defdb db (assoc (:jdbc settings/all)
              :naming {:entity (partial jdbc/as-quoted-str \`)
                       :fields  #(.replace % "-" "_")
                       :keyword #(.replace % "-" "_") })))

(defn- dash-keys
  "Convert underscores in a map's keys into dashes"
  [m]
  (into {} (map (fn [[k v]] [(keyword (.replaceAll (name k) "_" "-")) v]) m)))

(defn- serialize-record-params
  [{:keys [params last-result] :as record}]
  (reduce
   (fn [m [k v]]
     (condp = k
       :params (assoc m k (json/generate-string v))
       :last-result (assoc m k (json/generate-string v))
       (assoc m k v)))
   {}
   record))

(defn- deserialize-record-params
  [record]
  (-> record
      (update-in [:params] (comp walk/keywordize-keys json/parse-string))
      (update-in [:last-result] json/parse-string)))

(defn- serialize-record-value
  [record]
  (update-in record [:value] json/generate-string))

(defn- deserialize-record-value
  [record]
  (update-in record [:value] json/parse-string))

(defentity results
  (pk :uuid)
  (prepare serialize-record-value)
  (transform (comp deserialize-record-value dash-keys)))

(defentity jobs
  (pk :uuid)
  (prepare serialize-record-params)
  (transform (comp deserialize-record-params dash-keys))
  (has-many results {:fk :job_uuid}))