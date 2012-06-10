(ns engulf.chan-client-test)

(use 'lamina.core)
(require 'engulf.comm.message 'engulf.comm.worker-client 'engulf.comm.netchan :reload-all)

(def m (engulf.comm.message/encode-msg "another-type" "ohai"))
(println "ENCODED: " (String. m))
(def c (engulf.comm.worker-client/client-connect "localhost" 5001))
;;(def c @(aleph.tcp/tcp-client {:host "localhost" :port 5000 :frame engulf.comm.netchan/netstring-frame}))



(println "START")
(println c)
(println (closed? c))
(println (enqueue c m))
(println c)
(println "DONE")