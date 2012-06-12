(ns engulf.runner
  (:use engulf.ning-client
        aleph.formats
        noir-async.utils
        lamina.core))

(def ^{:dynamic true} cname nil)

(defmacro benchmark [& body]
  `(async ~@body))

(defn record-result [res-ch]
  res-ch)

(defn req-async
  "Dispatches an asynchronous request for a URL"
  ([client method url]
    (req-async client method url 30000))
  ([client method url timeout]   
    (client {:method method :url url} timeout)))

(defn req
  "Issue a request for a URL"
  ([method url]
    (req method url 30000))
  ([method url timeout]
    (force (async
      (let [request (req-async method url)]
       (record-result request))))))

(defn- sleep-ch
  [millis]
    (let [ch (result-channel)]
      (set-timeout millis (fn [] (enqueue (.success ch) true)))
      ch))

;Not sure why this needs to be a macro and force can't
;be in the function sleep-ch
(defmacro sleep
  "async friendly sleep operation."
  [millis]
  `(force (sleep-ch ~millis)))
