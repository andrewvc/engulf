(ns engulf.test.control-test
  (:require
   [engulf.test.helpers :as helpers]
   [engulf.formula :as formula]
   [engulf.control :as ctrl]
   [engulf.comm.worker-client :as wc]
   [lamina.core :as lc])
  (:use midje.sweet)
  (import engulf.test.helpers.MockFormula))

(defmacro watched-run [watch-binding & body]
  `(let [srv# (ctrl/start 3493)
         wc# (wc/client-connect "localhost" 3493)
         ~watch-binding (atom {})]
     (formula/register :mock-job
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
     (lc/close wc#)))

(facts
 "about starting jobs"
 (watched-run
  seen
  (ctrl/start-job
   {:url "http://localhost/test"
    :method "POST"
    :concurrency 3
    :job-name :mock-job
    :headers {"X-Foo" "Bar"}
    :body "Ohai!"})
  (Thread/sleep 1000)
  (fact
   "the start-edge method should be executed"
   (:start-edge @seen) => truthy)))

(facts
 "about starting relay jobs"
 (watched-run
  seen
  (ctrl/start-job
   {:url "http://localhost/test"
    :method "POST"
    :concurrency 3
    :job-name :mock-job
    :headers {"X-Foo" "Bar"}
    :body "Ohai!"})
  (Thread/sleep 1000)
  (fact
   "the start-edge method should be executed"
   (:start-edge @seen) => truthy)))

(println "done control")