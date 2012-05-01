(ns engulf.coordinator
  (:requires [[cheshire :as json]])
  (:use [lamina.core :only [receive-all enqueue close permanent-channel])))

(def slaves (ref {})) ; Map of ids -> slaves

(def incoming (permanent-channel))

(defn slave
  "Creates a new representation of a slave benchmark"
  [id ch]
  {:id id
   :state (atom nil)
   :stats (atom {}}
   :channel ch})

(defn slave-connect
  "Adds a slave and its messaging channel to the slave pool"
  [id ch]
  (let [s  (slave id ch)]
        (dosync (alter slaves assoc id (slave id ch)))
        (on-receive ch (fn [m]
                         (enqueue incoming
                                  {:slave s
                                   :message (json/decode m)})))))

(defn slave-disconnect
  [s]
  (dosync (alter slaves dissoc (:id s)))
  (close (:channel s)))