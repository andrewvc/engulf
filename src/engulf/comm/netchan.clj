(ns engulf.comm.netchan
  (:use aleph.tcp
        lamina.core
        gloss.core
        gloss.io)
  (:import java.nio.ByteBuffer
           java.util.Arrays))

(def netstring-frame
  (finite-block
   (prefix :int32
           inc
           dec)))

(def frame-encoder
  (compile-frame
   {:length :string
    :colon :string
    :data  (finite-frame [] prefix )
    :comma :string}))

(defn encode-frame
  [data]
  (encode frame-encoder
          {:length (alength data)
           :colon ":"
           :data (to-buf-seq data)
           :comma ","}))

(defn start-server
  [port handler]
  (start-tcp-server
   (fn server-handler [conn client-info]
     (println (str "S: CLIENT CONNECT" client-info))
     (let [emitter (channel) ]
       (receive-all
        conn
        (fn byte-arrayifier [m]
          (enqueue
           emitter
           (let [frame-arr (.array (contiguous m))]
             (Arrays/copyOfRange frame-arr 4 (alength frame-arr))))))
       (receive-all  (splice emitter conn) (fn [m] (println (str "EMITTED" (class m) "IT"))))
       (handler (splice emitter conn) client-info)))
   {:port port :frame netstring-frame}))

(defn start-client
  [host port]
  (let [conn @(tcp-client {:host host :port port :frame netstring-frame})
        receiver (channel)]
    ;; We want to pre-pack SMILE Byte[] into an aleph/gloss friendly BufferSeq
    (receive-all receiver (fn buff-seq-converter [m] (println (str "GOT" m)) (enqueue conn (to-buf-seq m))))
    (splice conn receiver)))