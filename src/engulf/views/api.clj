(ns engulf.views.api
  (:require [engulf.control :as control])
  (:use noir-async.core))

(defpage-async "/control" {} conn
  (control/connect conn))