(ns parbench.core
  (:gen-class)
  (:use rosado.processing)
  (:require [clj-http.client :as client])
  (:import  [java.util.concurrent Executors]
            [javax.swing JFrame]
            [processing.core PApplet]))

; Size of Rectangles
(def gfx-scale 6)

; Frame rate in FPS
(def target-frame-rate 15)

; Current state of all requests
(def req-map (ref []))

(defn set-request-state [request state]
  "Alters the state of an HTTP request"
  (dosync (alter request assoc 2 state)))

(defn run-request [request url]
  "Runs a single HTTP request"
  (set-request-state request :sent)
  (let [result (client/get url)]
      (set-request-state request (:status result))))

(defn initialize-requests [requests concurrency]
  "Resets the req-map to a blank state"
  (dosync 
    (ref-set req-map
      (for [r (range requests) c (range concurrency)] 
           (ref [r c :untried])))))

(defn run-requests [requests concurrency url]
  "Runs all HTTP Requests in a thread pool"
  (initialize-requests requests concurrency)
  (let [pool    (Executors/newFixedThreadPool concurrency)
        tasks   (map (fn [request] #(run-request request url)) 
                         (deref req-map))]
      (.get (future (.invokeAll pool tasks)))
      (.shutdown pool)))

(defn on-draw
  "Draws the canvas based on the data in req-map"
  [dst]
  (doseq [[col row state] (map deref (deref req-map))]
          ((fn [[fc sc]] (apply fill-float fc) (apply stroke-float sc))
            (cond (= state :sent)    [[210 210   0] [255 255   0]]
                  (= state :untried) [[220 220 220] [235 235 235]]
                  (= state 200)      [[105 105 105] [120 120 120]]
                  :else              [[255 125 125] [255   0   0]] ))
          (rect (* gfx-scale col) (* gfx-scale row) gfx-scale gfx-scale) ) )

(defn create-pb-applet [width height]
  "Create the processing applet"
  (proxy [PApplet] []
    (setup []
      (binding [*applet* this]
        (size width height)
        (framerate target-frame-rate)))
     (draw []
       (binding [*applet* this]
       (on-draw this)))))

(defn initialize-graphics [width height]
  (let [pb-applet   (create-pb-applet width height)
        swing-frame (JFrame. "Graphical HTTP Tester")]
    (.init pb-applet)
      (doto swing-frame
        (.setDefaultCloseOperation JFrame/EXIT_ON_CLOSE)
        (.setSize width height)
        (.add pb-applet)
        (.pack)
        (.show))))

(defn -main [concurrencyArg reqsArg url]
  (let [requests    (Integer. reqsArg)
        concurrency (Integer. concurrencyArg)
        width       (* gfx-scale requests)
        height      (* gfx-scale concurrency)]
    (initialize-graphics width height)
    (run-requests requests concurrency url)))
