(ns engulf.test.control
  (:require [engulf.control :as ctrl]
            [lamina.core :as lc]
            [cheshire.core :as chesh])
  (:use midje.sweet
        [clojure.walk :only [keywordize-keys]]))

(facts
 "about message encoding"
 (let [msg {"name" "a-msg-name" "body" "a-msg-body"}]
   (fact
    "it should match the original when decoded"
    (chesh/parse-smile (ctrl/encode-msg msg)) => msg)
   (fact
    "attempting to encode a bad message should raise an assertion"
    (ctrl/encode-msg {"not-valid" "msg"}) => (throws java.lang.AssertionError))))

(facts
 "about message parsing"
 (fact
  "parsing a good message should return it unchanged"
  (let [expected-msg {:name "set-state" :body "running"}
        encoded-msg (chesh/generate-smile expected-msg)]
    (ctrl/parse-msg encoded-msg) => expected-msg))
 (fact
  "keys should be kewordified"
  (let [sent-msg {"name" "set-state" "body" "running"}
        expected-msg (keywordize-keys sent-msg)
        encoded-msg (chesh/generate-smile sent-msg)]
    (ctrl/parse-msg encoded-msg) => expected-msg))
 (fact
  "parsing a bad message should raise an assertion"
  (let [bad-msg (chesh/generate-smile {"garbage" "blah"})]
    (ctrl/parse-msg bad-msg) => (throws java.lang.AssertionError))))

(facts
 "about creating nodes"
  (let [ident "a-unique-identifier"
        n  (ctrl/create-node ident)]
    (fact
     "the node should have the right uuid"
     (:uuid n) => ident)
    (fact
     "the node should have enqueued a creation message"
     (lc/receive ctrl/node-ch
                 (fn [msg]
                   msg => {:name "new-node"
                           :body n}
                   500))))
  (facts
   "for nodes that do currently exist"
   (let [ident "some-unique-id"
         existing  (ctrl/create-node ident)]
     (fact
      "calling create should return nil"
      (ctrl/create-node ident) => nil))))

(facts
 "about removing nodes"
 (facts
  "for nodes that don't yetexist"
  (let [ident "a-unique-identifier"]
    (fact
     "the node should no longer be present after removal"
     (ctrl/create-node ident)
     (ctrl/get-node ident) =not=> nil
     (ctrl/remove-node ident)
     (ctrl/get-node ident) => nil))))

(println "done")
