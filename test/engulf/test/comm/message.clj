(ns engulf.test.comm.message
  (:require [engulf.comm.message :as cmsg]
            [cheshire.core :as chesh])
  (:use midje.sweet
        [clojure.walk :only [keywordize-keys]]))

(facts
 "about message encoding"
 (let [msg {"type" "a-msg-type" "body" "a-msg-body"}]
   (fact
    "it should match the original when decoded"
    (chesh/parse-smile (cmsg/encode-msg msg)) => msg)
   (fact
    "attempting to encode a bad message should raise an assertion"
    (cmsg/encode-msg {"not-valid" "msg"}) => (throws java.lang.AssertionError))))

(facts
 "about message parsing"
 (fact
  "parsing a good message should return it unchanged"
  (let [expected-msg {:type "set-state" :body "running"}
        encoded-msg (chesh/generate-smile expected-msg)]
    (cmsg/parse-msg encoded-msg) => expected-msg))
 (fact
  "keys should be kewordified"
  (let [sent-msg {"type" "set-state" "body" "running"}
        expected-msg (keywordize-keys sent-msg)
        encoded-msg (chesh/generate-smile sent-msg)]
    (cmsg/parse-msg encoded-msg) => expected-msg))
 (fact
  "parsing a bad message should raise an assertion"
  (let [bad-msg (chesh/generate-smile {"garbage" "blah"})]
    (cmsg/parse-msg bad-msg) => (throws java.lang.AssertionError))))
