(ns parbench.displays
  (:use [rosado.processing]
        [clojure.contrib.seq-utils :only [find-first]])
  (:require [parbench.requests-state :as rstate])
  (:import  [java.util TimerTask Timer]
            [javax.swing JFrame]
            [java.util TimerTask Timer]
            [processing.core PApplet]))

(def colors {
  :yellow      [(into-array [210 210   0]) (into-array [255 255   0])]
  :dark-gray   [(into-array [105 105 105]) (into-array [120 120 120])]
  :light-gray  [(into-array [220 220 220]) (into-array [235 235 235])]
  :blue        [(into-array [120 120 255]) (into-array [150 150 255])]
  :white       [(into-array [255 255 255]) (into-array [240 240 240])]
  :red         [(into-array [255 105 105]) (into-array [250 120 120])]
  :black       [(into-array [  0   0   0]) (into-array [255   0   0])]})

(defn- status-color [status]
  "Color tuple (fill, outline) based on HTTP status codes"
  (cond
  (and (>= status 200) (< status 300)) (colors :dark-gray)
  (and (>= status 300) (< status 400)) (colors :blue)
  (and (>= status 400) (< status 500)) (colors :white)
  (and (>= status 500) (< status 600)) (colors :red)
  :else                                (colors :black) ))

(defn render-square [col row scale request]
  "Render an individual square in the papplet"
  (let [state  (:state request)
        [fill-color stroke-color]
        (cond (= state :requested) (colors :yellow)
              (= state :untried)   (colors :light-gray)
              (= state :failed)    (colors :black)
              (= state :responded) (status-color (:status request))
              :else                (colors :black))]
        (apply fill-float   fill-color)
        (apply stroke-float stroke-color)
        (rect (* scale col) (* scale row) scale scale)))

(defn render-request [req-ref scale]
  "Render an individual request if it needs to be rendered"
  (dosync
    (let [request @req-ref
      col     (:x      request)
      row     (:y      request)]
      (cond (not (:rendered request))
        (do
          (render-square col row scale request)
          (alter req-ref assoc :rendered true))))))

(defn status-draw
  "Called on each render, renders each request"
  [dst reqs-state scale]
  (doseq [req-ref (flatten (:grid reqs-state))]
    (render-request req-ref scale)))

(defn create-pb-applet [reqs-state width height scale draw-fn]
  "Create an applet for processing, calling draw-fn on every draw cycle"
  (proxy [PApplet] []
    (setup []
      (binding [*applet* this]
        (size width height)
        (framerate 4)))
     (draw []
       (binding [*applet* this]
       (status-draw this reqs-state scale)))))

(defn block-till-all-rendered [reqs-state]
  "Intentionally block until all requests are rendered, useful for prewarming"
  (let [unfinished? (find-first #(not (:rendered (deref %1))) (flatten (:grid reqs-state)))]
    (cond unfinished?
      (do (Thread/sleep 100) (recur reqs-state)))))

(defn initialize-graphics [reqs-state width height scale draw-fn]
  "Sets up GUI output via Swing + Processing"
  (let [pb-applet   (create-pb-applet reqs-state width height scale draw-fn)
        swing-frame (JFrame. "Parbench")]
    (.init pb-applet)
      (doto swing-frame
        (.setDefaultCloseOperation JFrame/EXIT_ON_CLOSE)
        (.setSize width height)
        (.add pb-applet)
        (.pack)
        (.show)))
  ; block till GUI loaded
  (block-till-all-rendered reqs-state))

(defn status-code-gui [reqs-state ui-opts]
  "Displays a grid colored by request status code"
  (let [scale  (:scale ui-opts)
        width  (* scale (:requests    reqs-state))
        height (* scale (:concurrency reqs-state))]
    (initialize-graphics reqs-state width height scale status-draw)))

(defn console-full [reqs-state ui-opts]
  "Dumps the whole grid to the console. Warning: Extremely Verbose."
    (let [task (proxy [TimerTask] []
         (run [] (println reqs-state)))]
    (.scheduleAtFixedRate (Timer.) task (long 0) (long 1000))))

(defn console [reqs-state ui-opts]
  "Dumps a summary of stats to the console"
  (let [task (proxy [TimerTask] []
         (run [] (println (rstate/stats reqs-state))))]
    (.scheduleAtFixedRate (Timer.) task (long 0) (long 1000))))
