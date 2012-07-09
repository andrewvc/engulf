
(defn now [] (System/currentTimeMillis))

(defn result
  [started-at ended-at]
  {:started-at started-at
   :ended-at ended-at
   :runtime (- ended-at started-at)})

(defn error-result
  [started-at ended-at throwable]
  (assoc (result started-at ended-at)
    :status :thrown
    :throwable throwable))

(defn success-result
  [started-at ended-at status]
  (assoc (result started-at ended-at)
    :status status))


(def valid-methods #{:get :post :put :delete :patch})

(defn int-val [i] (Integer/valueOf i))

(defn clean-params [params]
  ;; Ensure required keys
  (let [diff (cset/difference #{:url :method :concurrency :timeout} params)]
    (when (not (empty? diff))
      (throw (Exception. (str "Invalid parameters! Missing keys: " diff)))))
  (let [method (keyword (lower-case (:method params)))]
    ;; Ensure only valid methods are used
    (when (not (method valid-methods))
      (throw (Exception. (str "Invalid method: " method " expected one of " valid-methods))))
    ;; Transform the types of vals that need it
    (-> params
        (update-in [:concurrency] int-val)
        (update-in [:timeout] int-val)
        (assoc :method method))))

(defn run-real-request
  [params callback]
  (let [started-at (now)]
    (letfn
        [(succ-cb [response]
           (callback (success-result started-at (now) (:status response))))
         (error-cb [throwable]
           (callback (error-result started-at (now) throwable)))]
      (try
        (lc/on-realized (http-request (juxt [:method :url :timeout] params))
                     succ-cb
                     error-cb)
        (catch Exception e
          (error-cb e))))))

(defn run-mock-request
  "Fake HTTP response for testing"
  [params callback]
  (let [started-at (now)
        res (lc/result-channel)
        succ-cb #(lc/success res (success-result started-at (System/currentTimeMillis) 200))]
    (set-timeout 1 succ-cb)
    (lc/on-realized res #(callback %1) #(callback %1))))