(ns engulf.bus
  "Global event bus"
  (:use lamina.core)
  (:require [engulf.comm.node-manager :as n-manager]))

(def global (permanent-channel))
(ground global)
(siphon n-manager/emitter global)

;; Poor quality sanitization. I need to devise a better strategy...
(def global-json-safe
  (map*
   (fn json-sanitizer [[entity name body]]
     (condp = [entity name]
       [:system :node-connect] [entity name (dissoc body :conn)]
       [:system :node-connect] [entity name (dissoc body :conn)]
       [entity name body]))
   (filter*
    (fn json-filter [[entity name body]]
      (condp = [entity name]
        [:system :node-connect] true
        [:system :node-disconnect] true
        false))
    global)))

