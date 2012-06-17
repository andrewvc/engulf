(ns engulf.utils
  (:import [java.util Timer TimerTask concurrent.TimeUnit]))

(def default-timer (Timer. true))

(defn- fn->timer-task [func]
  (proxy [TimerTask] []
        (run [] (func))))

(defn set-timeout
  "Run a function after a delay"
  ([millis func] (set-timeout default-timer millis func))
  ([timer millis func]
    (let [timer-task (fn->timer-task func)]
      (.schedule timer timer-task (long millis))
      timer-task)))

(defn set-interval
  "Repeatedly run a function"
  ([millis func] (set-interval default-timer millis func))
  ([timer millis func]
    (let [timer-task (fn->timer-task func)]
      (.schedule timer timer-task  (long 0) (long millis))
      timer-task)))