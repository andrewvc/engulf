(ns engulf.messages
  (:require [cheshire.core :as chesh]))

(defn create-message [type body]
  (chesh/generate-string {:type type :body body}))

(defn decode-message [contents]
  (chesh/parse-string contents))