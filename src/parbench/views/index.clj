(ns parbench.views.index
  (:require [parbench.views.common :as common])
  (:use [noir.core :only [defpage]]
        [hiccup.core :only [html]]
        [hiccup.page-helpers :only [link-to]]))

(defpage "/" []
  (common/layout
    [:div {:id "controls"}
      [:form
        [:label {:for "url"} "URL: "]
        [:input {:id "url" :name "url" :type "text" :value "http://localhost:3000" }]

        [:label {:for "concurrency"} "Concurrency: "]
        [:input {:id "concurrency" :class "short-num" :name "concurrency" :type "text" :value 4 }]
          
        [:label {:for "requests"} "Requests: "]
        [:input {:id "requests" :class "short-num" :name "requests" :type "text" :value 200 }]
        
        [:input {:id "start-ctl" :type "button" :value "Start"}]
        [:input {:id "stop-ctl"  :type "button" :value "Stop"}]
      ]
    ]
      
    [:div {:id "output"}
      [:h2 "Stats: "]
      [:table {:id "stats"}
        [:thead
          [:th "Remaining"]
          [:th "Completed"]
          [:th "Processing"]
        ]
        [:tbody
          [:tr
           [:td {:id "jobs-remaining"} "N/A"]
           [:td {:id "jobs-completed"} "N/A"]
           [:td {:id "jobs-processing"} "N/A"]
          ]
        ]
      ]
      [:h2 "Console: "]
      [:div {:id "console"}]
    ]
  ))
