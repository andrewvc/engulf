(ns engulf.control
  (:require [engulf.messages :as msgs])
  (:use [noir-async.core :only [on-close on-receive push-async]]))

(def nodes (atom {}))

(defn connect [conn]
  (let [node (atom nil)] ; Awaits broadcast of the node info
    (on-receive conn
                
                (swap! node #(