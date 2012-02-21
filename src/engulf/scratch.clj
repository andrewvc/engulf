(ns scratch)
(use 'engulf.runner):
(use 'lamina.core)

(+ 1 1)

(def ^:dynamic *bt* "old")

@(benchmark
    (let [a (req :get "http://www.google.com")]
      (str "ohai " *bt* " | "(:status a))))

(def src (read-string "(req :get http://www.google.com)"))

(postwalk (fn [body]
            (if (not= (first body) 'req)
              body
              (list 'req 'dstore (rest body))))
          src)

(postwalk-replace {'req (list 'blargh 'sarg)} src)

(postwalk-demo src)

(macroexpand-1 (postwalk-replace {:a +} '(:a 1 :b 5)))