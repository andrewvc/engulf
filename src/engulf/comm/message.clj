(ns engulf.comm.message
  (:require [cheshire.core :as chesh])
  (:use [clojure.walk :only [keywordize-keys]]))

(defn encode-msg
  "Encodes a message using SMILE"
  [msg]
  {:pre [(not= nil (or (msg "type") (msg :type)))
         (not= nil (or (msg "body") (msg :body)))]}
  (chesh/encode-smile msg))

(defn parse-msg
  "Parses a SIMLE msg, ensures it's properly formatted as well"
  [msg]
  {:post [(not= nil (or (% "type") (% :type)))
          (not= nil (or (% "body") (% :body)))]}
  (keywordize-keys (chesh/parse-smile msg)))
