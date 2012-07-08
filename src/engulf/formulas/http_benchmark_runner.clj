
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

(defprotocol IHttpBenchmark
  (run-repeatedly [this ch runner]))

(defrecord HttpBenchmark [state params res-ch mode]
  IHttpBenchmark
  (run-repeatedly [this ch runner]
    (runner
     params
     (fn req-resp [res]
       (when (= @state :started) ; Discard results and don't recur when stopped
         (lc/enqueue ch res)
         (run-repeatedly this ch runner)))))
  Formula
  (start-relay [this ingress]
    (when (compare-and-set! state :initialized :started)
      (reset! mode :relay)
      (lc/siphon
       @(lc/run-pipeline
         ingress
         (partial lc/reductions*
                  (partial relay-aggregate params)
                  (empty-relay-aggregation params))
         (partial lc/sample-every 250))
       res-ch)
      res-ch))
  (start-edge [this]
    (when (compare-and-set! state :initialized :started)
      (reset! mode :edge)
      (let [http-res-ch (lc/channel)
            runner (if (:mock params) run-mock-request run-real-request)]
        ;; Kick off the async workers
        (dotimes [t (Integer/valueOf (:concurrency params))]
          (run-repeatedly this http-res-ch runner))
        ;; Every 250ms siphon out a chunk of the output to the res-ch
        (lc/siphon 
         (lc/map* (partial edge-aggregate params)
                  (lc/partition-every 250 http-res-ch))
         res-ch)
        res-ch)))
  (stop [this]
    (reset! state :stopped)
    (lc/close res-ch)
    (lc/closed? res-ch)))

(defn init-benchmark
  [params]
  (HttpBenchmark. (atom :initialized)
                  (clean-params params)
                  (lc/channel)
                  (atom :unknown)))

(register :http-benchmark init-benchmark)