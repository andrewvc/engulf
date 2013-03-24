(defn result
  [started-at ended-at]
  {:started-at started-at
   :ended-at ended-at
   :runtime (- ended-at started-at)})

(defn error-result
  [started-at ended-at throwable]
  (assoc (result started-at ended-at)
    :status (if (keyword? throwable)
                          throwable
                          (last (.split ^String (str (class throwable)) " ")))
    :throwable throwable))

(defn success-result
  [started-at ended-at status]
  (assoc (result started-at ended-at)
    :status status))

;; Job cleaning related stuff

(defn int-val [i] (Integer/valueOf i))

(defn ensure-required-keys
  [job]
  (let [diff (cset/difference #{:concurrency :limit} (:params job))]
    (when (not (empty? diff))
      (throw (Exception. (str "Invalid parameters! Missing keys: " diff ".")))))
  job)

(defn validate-concurrency
  [job]
  (when (> (:node-count job) (int-val (:concurrency (:params job))))
    (throw
     (Exception. "Concurrency cannot be < node-count! Use a higher concurrency setting!")))
  job)

(defn cast-params
  [{params :params :as job}]
  (assoc job :params
         (-> (:params job)
             (update-in [:concurrency] int-val)
             (update-in [:target :timeout] #(when % (int-val %)))
             (update-in [:limit] int-val)
             (assoc :retry? true)
             (assoc-in [:target :keep-alive?] (not= "false" (:keep-alive (:target params)))))))

(defn instantiate-req-seq
  [{{{t-type :type} :target :as params} :params :as job}]
  (assoc-in
   job
   [:params :req-seq]
   (if-let [builder (req-seqs/get-builder t-type)]
     (builder params)
     (throw
      (Exception.
       (format "No req-seq builder for '%s', try one of '%s'"
               t-type (keys @req-seqs/registry)))))))

(defn clean-job
  [job]
  (-> job
      (update-in [:params] keywordize-keys)
      validate-concurrency
      ensure-required-keys
      cast-params
      instantiate-req-seq))

(defn run-real-request
  [client req-params callback]
  (let [started-at (now)]
    (letfn
        [(succ-cb [response]
           (if (= :lamina/suspended response)
             (callback :lamina/suspended)
             (callback (success-result started-at
                                       (now)
                                       (or (:status response) "err")))))
         (enqueue-succ-cb [response]
           (.submit ^ExecutorService callbacks-pool ^Runnable (partial succ-cb response)))
         (error-cb [throwable]
           (log/warn throwable (str "Error executing HTTP Request: " req-params))
           (callback (error-result started-at (now) throwable)))
         (enqueue-error-cb [throwable]
           (.submit ^ExecutorService callbacks-pool ^Runnable (partial error-cb throwable)))
         (exec-request []
           (lc/on-realized (client req-params) enqueue-succ-cb enqueue-error-cb))]
      (try
        (exec-request)
        (catch Exception e
          (.submit ^ExecutorService callbacks-pool ^Runnable (partial error-cb e)))))))

(defn run-mock-request
  "Fake HTTP response for testing"
  [client params callback]
  (let [started-at (now)
        res (lc/result-channel)
        succ-cb #(lc/success res (success-result started-at (System/currentTimeMillis) 200))]
    (set-timeout 1 succ-cb)
    (lc/on-realized res #(callback %1) #(callback %1))))