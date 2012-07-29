(ns engulf.test.control-test
  (:require
   [engulf.test.helpers :as helpers]
   [engulf.formula :as formula]
   [engulf.control :as ctrl]
   [engulf.relay :as relay]
   [engulf.utils :as utils]
   [engulf.worker-client :as wc]
   [lamina.core :as lc])
  (:use midje.sweet)
  (import engulf.test.helpers.MockFormula))

(defmacro watched-run [watch-binding & body]
  `(let [srv# (ctrl/start 3493)
         wc# (wc/client-connect "localhost" 3493)
         ~watch-binding (atom {})
         relay-stop# (relay/start)]
     (formula/register :mock-formula
                       (fn [params#]
                         (MockFormula.
                          (fn mf-edge [mj#]
                            (swap! ~watch-binding #(assoc %1 :start-edge true))
                            (lc/channel))
                          (fn mf-relay [mj# _#]
                            (swap! ~watch-binding #(assoc %1 :start-relay true))
                            (lc/channel))
                          (fn mf-stop [mj#]
                            (swap! ~watch-binding #(assoc %1 :stop true))
                            (lc/channel)))))
     ~@body
     (srv#)
     (lc/close wc#)
     (relay-stop#)))

(watched-run
 seen
 (ctrl/start-job
  {:url "http://localhost/test"
   :uuid (utils/rand-uuid-str)
   :method "POST"
   :concurrency 3
   :formula-name :mock-formula
   :headers {"X-Foo" "Bar"}
   :body "Ohai!"})
 (Thread/sleep 500)
 (fact
  "the start-edge method should be executed"
  (:start-edge @seen) => truthy)
 (fact
  "the start-relay method should be executed"
  (:start-relay @seen) => truthy))
