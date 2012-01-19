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
      [:div {:id "stats"}
        "Benchmark Totals"
        [:table {:id "benchmark-stats"}
          [:thead
            [:th "Total"]
            [:th "Completed"]
            [:th "Failed"]
            [:th "Runtime"]
          ]
          [:tbody
            [:tr
             [:td {:id "runs-total"} 0]
             [:td {:id "runs-succeeded"} 0]
             [:td {:id "runs-failed"} 0]
             [:td {:id "runtime"} "N/A"]
            ]
          ]
        ]

        "Response Codes:"
        [:table {:id "response-code-stats"}
          [:thead
            [:th "Code"]
            [:th "Responses"]
          ]
          [:tbody]
        ]
      ]
      [:h2 "Charts: "]
      [:div {:id "charts"}]
      [:h2 "Console: "]
      [:form
        [:input {:id "console-enabled", :type "checkbox"
                 :name="console-enabled"}]
        [:label {:for "console-enabled"} "Enable"]
      ]
      [:div {:id "console"}]
    ]
  ))
