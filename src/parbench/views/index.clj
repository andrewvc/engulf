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
        [:input {:id "url" :name "url" :type "url" :value "http://localhost:3000" }]

        [:label {:for "concurrency"} "Concurrency: "]
        [:input {:id "concurrency" :class "short-num" :name "concurrency" :type "number" :min 1 :value 4 }]
          
        [:label {:for "requests"} "Requests: "]
        [:input {:id "requests" :class "short-num" :name "requests" :type "number" :min 1 :value 200 }]
        
        [:input {:id "start-ctl" :type "button" :value "Start"}]
        [:input {:id "stop-ctl"  :type "button" :value "Stop"}]
      ]
    ]
      
    [:div {:id "output"}
      [:h2 "Overview "]
      [:div {:id "stats"}
        [:table {:id "benchmark-stats"}
          [:thead
            [:th "Total"]
            [:th "Completed"]
            [:th "Failed"]
            [:th "Median Runtime"]
            [:th "Runs"]
            [:th "Runtime"]
          ]
          [:tbody
            [:tr
             [:td {:id "runs-total"} "&#8734;"]
             [:td {:id "runs-succeeded"} "&#8734;"]
             [:td {:id "runs-failed"} "&#8734;"]
             [:td {:id "median-runtime"} "&#8734;"]
             [:td {:id "runs-sec"} "&#8734;"]
             [:td {:id "runtime"} "&#8734;"]
            ]
          ]
        ]

        [:h2 "Response Codes:"]
        [:table {:id "response-code-stats"}
          [:thead
            [:th "Code"]
            [:th "Responses"]
          ]
          [:tbody]
        ]
      ]
       
      [:h2 "Response Time Percentiles: "]
      [:div {:id "resp-time-percentiles"}]
       
      [:h2 "Response Time vs. Time: "]
      [:div {:id "resp-time-series"}]

      [:h2 "Console: "]
      [:form
        [:input {:id "console-enabled", :type "checkbox"
                 :name="console-enabled"}]
        [:label {:for "console-enabled"} "Enable"]
      ]
       
      [:div {:id "console"}]
    ]
  ))
