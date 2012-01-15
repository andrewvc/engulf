(ns parbench.views.index
  (:require [parbench.views.common :as common])
  (:use [noir.core :only [defpage]]
        [hiccup.core :only [html]]
        [hiccup.page-helpers :only [link-to]]))

(defpage "/" []
  (common/layout
    [:p "HTTP Benchmarker"]))
    
