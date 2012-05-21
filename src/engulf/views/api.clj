(ns engulf.views.api
  (:require [engulf.comm.control :as control])
  (:use noir-async.core))

(defpage-async "/control" {} conn
  (if (not (websocket? conn))
    (async-push "Since you aren't a websocket, this page isn't so useful, is it?")
    (control/connect conn)))