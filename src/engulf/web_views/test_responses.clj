(ns engulf.web-views.test-responses
  (use noir-async.utils
       noir.core
       lamina.core)
  (require
   [noir-async.core :as na]
   [cheshire.core :as json]))

(def fast-async-page-count (atom 0))

(na/defpage-async "/test-responses/fast-async" {} conn
  (set-timeout 1
   (fn []
       (na/async-push conn
                (str "Test Response #"
                (swap! fast-async-page-count inc))))))

(def delayed-page-count (atom 0))

(na/defpage-async "/test-responses/delay/:delay" {:keys [delay]} conn
  (set-timeout (Integer/valueOf delay)
    (fn []
      (let [i (swap! delayed-page-count inc)]
        (na/async-push conn (str "Test Response #" i))))))

(def statuses [200 202 200 200 200 200 404 302 301 500])

(defpage "/test-responses/rand-responses" {}
  (let [s (statuses (rand-int (count statuses)))]
    {:status s :body (str "Status" s)}))