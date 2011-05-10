;; processing test
(ns parbench.core
  (:use rosado.processing)
  (:require [clj-http.client :as client])
  (:import (java.util.concurrent Executors))
  (:import (javax.swing JFrame))
  (:import (processing.core PApplet)))

(def *gfx-scale* 15)

(def *req-map* (ref []))

(defn run-request [request url]
  (dosync (alter request assoc 2 :sent))
  (client/get url)
  (dosync (alter request assoc 2 :recvd)))

(defn run-requests [requests concurrency url]
  (let [
    pool    (Executors/newFixedThreadPool concurrency)
    tasks   (map-indexed (fn [idx request] #(run-request request url)) (deref *req-map*))]
      (.get (future (.invokeAll pool tasks)))
      (.shutdown pool)))

(defn on-draw
  [dst]
  (doseq [[col row lit] (map deref (deref *req-map*))]
          ((fn [[fc sc]] (apply fill-float fc) (apply stroke-float sc))
            (cond (= lit :recvd)   [[120 120 120] [130 130 130]]
                  (= lit :sent)    [[210 210 0] [255 255 0]]
                  (= lit :untried) [[220 220 220] [235 235 235]]))
          (rect (* *gfx-scale* col) (* *gfx-scale* row) *gfx-scale* *gfx-scale*))
  (framerate 10))

(defn create-pb-applet [width height]
  (proxy [PApplet] []
    (setup []
      (binding [*applet* this]
        (size width height)
        (smooth)
        (no-stroke)
        (fill 226)
        (framerate 10)))
     (draw []
       (binding [*applet* this]
       (on-draw this)))))

(defn -main [concurrencyArg reqsArg url]
  (let [requests    (Integer. reqsArg)
        concurrency (Integer. concurrencyArg)
        width       (* *gfx-scale* requests)
        height      (* *gfx-scale* concurrency)
        pb-applet   (create-pb-applet width height)
        swing-frame (JFrame. "Processing with Clojure")]
    (.init pb-applet)

    (dosync (ref-set *req-map* 
                     (for [r (range requests) c (range concurrency)] 
                          (ref [r c :untried]))))
     
    (doto swing-frame
      (.setDefaultCloseOperation JFrame/EXIT_ON_CLOSE)
      (.setSize width height)
      (.add pb-applet)
      (.pack)
      (.show))
    (run-requests requests concurrency url)))
