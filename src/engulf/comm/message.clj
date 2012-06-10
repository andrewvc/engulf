(ns engulf.comm.message
  (:require [cheshire.core :as chesh])
  (:use [clojure.walk :only [keywordize-keys]]))

(defn encode-msg
  "Encodes a message using SMILE"
  ([type body]
     (chesh/encode-smile {:type type :body body})))

(defn decode-msg
  "Parses a SIMLE msg, ensures it's properly formatted as well"
  [msg]
  {:post [(not= nil (first %))]}
  (let [{:strs [type body]} (chesh/parse-smile msg)]
    [type body]))
