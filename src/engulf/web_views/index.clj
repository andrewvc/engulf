(ns engulf.web-views.index
  (:require [engulf.web-views.common :as common])
  (:use [noir.core :only [defpage]]
        [hiccup.core :only [html]]
        [hiccup.element :only [link-to]]))

(defpage "/" []
  (common/layout
    [:div {:id "controls"}
      [:form
        [:label {:for "url"} "URL: "]
       [:input {:id "url" :name "url" :type "url"}]

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

      [:div {:id "scalars"}
        [:div {:id "stats"}
          [:table {:id "benchmark-stats"}
            [:tbody
              [:tr
                [:td {:class "k"} "Total"]
                [:td {:class "v" :id "runs-total"} "&#8734;"]]
              [:tr
                [:td {:class "k"} "Runs/Sec"]
                [:td {:class "v" :id "runs-sec"} "&#8734;"]]
              [:tr
                [:td {:class "k"} "Runtime"]
                [:td {:class "v" :id "runtime"} "&#8734;"]]
              [:tr
                [:td {:class "k"} "Median Rt."]
                [:td {:class "v" :id "median-runtime"} "&#8734;"]]
              [:tr
                [:td {:class "k"} "Failed"]
                [:td {:class "v" :id "runs-failed"} "&#8734;"]]
            ]
          ]

          [:h2 "Response Codes:"]
          [:table {:id "response-code-stats"}
            [:tbody]
          ]
         ]
       
       [:form
         [:input {:id "console-enabled", :type "checkbox"
                  :name="console-enabled"}]
         [:label {:for "console-enabled"} "Enable Console Logging"]
       ]
       
      ]
       
      [:h2 "Avg. Response Time Percentiles: "]
      [:div {:id "resp-time-percentiles"}]
       
      [:h2 "Avg. Response Time vs. Time: "]
      [:div {:id "resp-time-series"}]

      [:h2 "Throughput vs. Time: "]
      [:div {:id "throughput-time-series"}]
    ]
  ))
