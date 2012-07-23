(defn now [] (System/currentTimeMillis))

(defn result
  [started-at ended-at]
  {:started-at started-at
   :ended-at ended-at
   :runtime (- ended-at started-at)})

(defn error-result
  [started-at ended-at throwable]
  (assoc (result started-at ended-at)
    :status "thrown"
    :throwable throwable))

(defn success-result
  [started-at ended-at status]
  (assoc (result started-at ended-at)
    :status status))

(def valid-methods #{:get :post :put :delete :patch})

(defn int-val [i] (Integer/valueOf i))

(defn clean-params [str-params]
  (let [params (keywordize-keys str-params)]
    ;; Ensure required keys
    (let [diff (cset/difference #{:url :method :concurrency :timeout :limit} params)]
      (when (not (empty? diff))
        (throw (Exception. (str "Invalid parameters! Missing keys: " diff ". Got: " str-params)))))
    (let [method (keyword (lower-case (:method params)))]
      ;; Ensure only valid methods are used
      (when (not (method valid-methods))
        (throw (Exception. (str "Invalid method: " method " expected one of " valid-methods))))
      ;; Transform the types of vals that need it
      (-> params
          (update-in [:concurrency] int-val)
          (update-in [:timeout] int-val)
          (update-in [:limit] int-val)
          (assoc :keep-alive? #(not= "false" (:keep-alive params)))
          (assoc :method method)))))

(defn run-real-request
  [client req-params callback]
  (let [started-at (now)]
    (letfn
        [(succ-cb [response]
           (callback (success-result started-at (now) (:status response))))
         (enqueue-succ-cb [response]
           (.submit ^ExecutorService callbacks-pool ^Runnable (partial succ-cb response)))
         (error-cb [throwable]
           (log/warn throwable "Error executing HTTP Request")
           (callback (error-result started-at (now) throwable)))
         (enqueue-error-cb [throwable]
           (.submit ^ExecutorService callbacks-pool ^Runnable (partial error-cb throwable)))]
      (try
        (lc/on-realized (http-request req-params) enqueue-succ-cb enqueue-error-cb)
        (catch Exception e
          (.submit ^ExecutorService callbacks-pool ^Runnable (partial error-cb e)))))))

(defn run-mock-request
  "Fake HTTP response for testing"
  [params callback]
  (let [started-at (now)
        res (lc/result-channel)
        succ-cb #(lc/success res (success-result started-at (System/currentTimeMillis) 200))]
    (set-timeout 1 succ-cb)
    (lc/on-realized res #(callback %1) #(callback %1))))