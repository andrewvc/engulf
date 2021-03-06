(ns engulf.comm.netchan
  (:require
   [engulf.utils :as utils]
   [clojure.tools.logging :as log]
   [cheshire.core :as chesh])
  (:use
   aleph.tcp
   lamina.core
   gloss.core
   gloss.io
   [aleph.formats :only [bytes->byte-array]]
   [clojure.walk :only [keywordize-keys]])
  (:import java.util.Arrays
           java.util.zip.ZipException))

(defn encode-msg
  "Encodes a message using SMILE and GZIP"
  [msg]
  (utils/compress-byte-array (chesh/encode-smile msg)))

(defn decode-msg
  "Parses a GZIPed SIMLE msg, ensures it's properly formatted as well"
  [msg]
  (chesh/parse-smile (utils/decompress-byte-array msg)))

;; Simple int32 prefixed frames
(defcodec wire-protocol (finite-block (prefix :int32)))

(defn decode-frame
  "Returns only the decoded frame payload, stripping off its length prefix"
  [frame]
  (decode-msg (bytes->byte-array (contiguous frame))))

(defn encode-frame
  "Encodes a msg into a buffer-seq suitable for gloss framing"
  [msg]
  (to-buf-seq (encode-msg msg)))

(defn formatted-channel
  "Takes a channel from a tcp server or client, and returns a new channel that automatically
   decodes and encodes values"
  [conn]
  (let [receiver (channel)
        emitter (map* decode-frame conn)]
    (siphon (map* encode-frame receiver) conn)
    (splice emitter receiver)))

(defn start-server
  "Starts a TCP server. Returns an aleph server"
  [port handler]
  (start-tcp-server
   (fn srv-handler [conn client-info]
     (handler (formatted-channel conn) client-info))
   {:port port :frame wire-protocol}))

(defn client-connect
  "Connect to a server running on a given host/port."
  [host port]
  (let [raw-conn (tcp-client {:host host :port port :frame wire-protocol})
        fmtd-conn (result-channel)]
    (on-realized
     raw-conn
     (fn client-conn-succ [ch] (enqueue fmtd-conn (formatted-channel ch)))
     (fn client-conn-err [t] (error fmtd-conn t)))
    fmtd-conn))