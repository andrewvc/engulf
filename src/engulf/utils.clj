(ns engulf.utils
  (:import [java.util
            Timer
            TimerTask
            concurrent.TimeUnit
            UUID
            zip.GZIPOutputStream
            zip.GZIPInputStream]
           [java.io
            ByteArrayOutputStream
            ByteArrayInputStream
            ]
           java.nio.ByteBuffer))

(def default-timer ^Timer (Timer. true))

(defn- fn->timer-task ^TimerTask [func]
  (proxy [TimerTask] []
        (run [] (func))))

(defn set-timeout
  "Run a function after a delay"
  ([millis func] (set-timeout default-timer millis func))
  ([timer millis func]
    (let [timer-task (fn->timer-task func)]
      (.schedule ^Timer timer ^TimerTask timer-task ^long (long millis))
      timer-task)))

(defn set-interval
  "Repeatedly run a function"
  ([millis func] (set-interval default-timer millis func))
  ([timer millis func]
    (let [timer-task (fn->timer-task func)]
      (.schedule ^Timer timer ^TimerTask timer-task  (long 0) (long millis))
      timer-task)))

(defmacro safe-send-off-with-result
  "Convenience utility for managing stateful transitions returning a result channel over an agent"
  [state-agent res-binding bindings & body]
  `(let [~res-binding (lc/result-channel)]
     (send-off ~state-agent
               (fn ssowr-cb [~bindings]
                 (try
                   ~@body
                   (catch Throwable t#
                     (log/warn t# "Exception during safe-send-off!")
                     (lc/error ~res-binding t#)
                     nil))))
     ~res-binding))

(defn rand-uuid-str
  "Shorthand to create a stringified random UUID"
  []
  (.toString (UUID/randomUUID)))

(defn now
  "Shorthand for System/currentTimeMillis"
  []
  (System/currentTimeMillis))

(defn compress-byte-array
  [^bytes ba]
  (let [baos (ByteArrayOutputStream.)
        gzos (GZIPOutputStream. baos)]
    (doto gzos (.write ba) (.close))
    (.toByteArray baos)))

(defn decompress-byte-array
  [^bytes ba]
  (let [bais (ByteArrayInputStream. ba)
        gzis (GZIPInputStream. bais)
        buf-size 2048
        baos (ByteArrayOutputStream.)]
    (loop []
      (let [buf (byte-array buf-size)
            read-len (.read gzis buf 0 buf-size)]
        (when (> read-len 0)
          (.write baos buf 0 read-len)
          (recur))))
    (.close gzis)    
    (.toByteArray baos)))

(defn merge-map-sums
  "Given n maps, will return a single map with key values summed"
  [& maps]
  (reduce
   (fn [m this-map]
     (reduce
      (fn [mm [k v]]
        (update-in mm [k] #(if % (+ % v) v)))
        m
      this-map))
   {}
   maps))