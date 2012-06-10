(ns engulf.comm.netchan
  (:require
   [clojure.tools.logging :as log])
  (:use aleph.tcp
        lamina.core
        gloss.core
        engulf.comm.message
        gloss.io)
  (:import java.nio.ByteBuffer
           java.util.Arrays))

;; Simple int32 prefixed frames
(def wire-protocol
  (finite-block (prefix :int32 inc dec)))

(defn decode-frame
  "Returns only the decoded frame payload, stripping off its length prefix"
  [frame]
  (let [frame-arr (.array (contiguous frame))]
    (decode-msg (Arrays/copyOfRange frame-arr 4 (alength frame-arr)))))

(defn encode-frame
  "Encodes a msg into a buffer-seq suitable for gloss framing"
  [type body]
  (to-buf-seq (encode-msg type body)))

(defn formatted-channel
  "Takes a channel from a tcp server or client, and returns a new channel that automatically
   decodes and encodes values"
  [conn]
  (let [tx (channel)
        rx (channel)]
    (receive-all conn (fn fmtd-rx [frame] (enqueue rx (decode-frame frame))))
    (receive-all tx (fn fmtd-tx [[type body]] (enqueue conn (encode-frame type body))))
    (splice rx tx)))

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