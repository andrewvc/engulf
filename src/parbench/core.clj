(ns parbench.core
  (:gen-class)
  (:use [rosado.processing]
        [clojure.contrib.command-line])

  (:require [com.twinql.clojure.http :as http])
  (:import  [java.util.concurrent Executors]
            [javax.swing JFrame]
            [java.util TimerTask Timer]
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
  (let [result (http/get url :parameters (http/map->params {:handle-redirects false}))]
      (set-request-state request (:code result))))

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

(defn req-map-counts []
  "Returns a mapping of req-map states to counts"
  (reduce (fn [stats point] 
            (let [[x y state] (deref point)]
               (assoc stats
                 :total    (inc (stats :total 0))
                 state     (inc (stats state  0))
                 :progress (if (not= state :untried)
                               (inc (stats :progress 0))
                               (stats :progress 0)))))
          {}
          (deref req-map) ))

(defn on-draw
  "Draws the canvas based on the data in req-map"
  [dst]
  (doseq [[col row state] (map deref (deref req-map))]
          ((fn [[fc sc]] (apply fill-float fc) (apply stroke-float sc))
            (cond (= state :sent)                    [[210 210   0] [255 255   0]]
                  (= state :untried)                 [[220 220 220] [235 235 235]] 
                  (and (>= state 200) (< state 300)) [[105 105 105] [120 120 120]]
                  (and (>= state 300) (< state 400)) [[120 120 255] [150 150 255]]
                  (and (>= state 400) (< state 500)) [[255 255 255] [240 240 240]]
                  (and (>= state 500) (< state 600)) [[255 105 105] [250 120 120]]
                  :else                              [[  0   0   0] [255   0   0]] ))
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
  "Sets up GUI output via Processing"
  (let [pb-applet   (create-pb-applet width height)
        swing-frame (JFrame. "Parbench HTTP Tester")]
    (.init pb-applet)
      (doto swing-frame
        (.setDefaultCloseOperation JFrame/EXIT_ON_CLOSE)
        (.setSize width height)
        (.add pb-applet)
        (.pack)
        (.show))))

(defn initialize-console []
  "Sets up display loop for console output"
  (let [task (proxy [TimerTask] []
               (run [] (println (req-map-counts))))]
    (. (new Timer) (scheduleAtFixedRate task (long 0) (long 1000)))))

(defn -main [& args]
  (with-command-line
    args
    "Usage: [-k,-c NUM_WORKERS,-r NUM_REQUESTS] http://example.net "
    [[cli-only?   k? "Command Line Only" false]
     [concurrency c  "Number of Workers" 50]
     [requests    r  "Number of requests per worker" 100]]
     
    (let [url         (last args)
          requests    (Integer. requests)
          concurrency (Integer. concurrency)
          width       (* gfx-scale requests)
          height      (* gfx-scale concurrency)]
       
      (initialize-console)
      (cond (not cli-only?) (initialize-graphics width height))
      (run-requests requests concurrency url))))
