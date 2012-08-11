(ns engulf.web-views.index
  (:require [engulf.web-views.common :as common])
  (:use [noir.core :only [defpage]]
        [hiccup.core :only [html]]
        [hiccup.element :only [link-to]]))

(defpage "/" []
  (common/layout
   [:div {:id "controls"}
    [:form
     [:div {:id "controls-top"}
      [:select {:id "type"}
       [:option {:value "url"} "Single URL"]
       [:option {:value "markov-corpus"} "URL List"]]
      [:select {:id "method" :class "short-num" :name "method"}
       [:option {:value "get"} "GET"]
       [:option {:value "post"} "POST"]
       [:option {:value "put"} "PUT"]
       [:option {:value "delete"} "DELETE"]
       [:option {:value "patch"} "PATCH"]
       ]
      [:input {:id "url" :name "url" :type "url"}]
      
      [:span {:id "markov-help"}
       "Newline Separate URLs as: "
       [:strong "GET http://localhost/foo"]
       ". Constructs a "
       [:a {:href "https://github.com/andrewvc/engulf/wiki/HTTP-API"}
        "Markov chain."]
       ]
      [:textarea {:id "markov-corpus" :name "markov-corpus"}]
      ]

     [:table
      [:thead
       [:th [:label {:for "timeout"} "Timeout "]]
       [:th [:label {:for "concurrency"} "Concurrency "]]
       [:th [:label {:for "limit"} "Limit "]]
       [:th [:label {:for "keep-alive"} "KeepAlive"]]
       [:th [:label {:for "start-stop"} "&middot;"]]
       ]
      [:tbody
       [:tr
        [:td [:input {:id "timeout" :class "short-num" :name "timeout" :type "number" :min 1 :max 200000 :value 5000 }]]
        [:td [:input {:id "concurrency" :class "short-num" :name "concurrency" :type "number" :min 1 :value 4 }]]
        [:td [:input {:id "limit" :class "short-num" :name "requests" :type "number" :min 1 :value 200 }]]
        [:td [:input {:id "keep-alive" :name "method" :type "checkbox" :checked "true"}]]
        [:td
         [:input {:id "start-ctl" :type "button" :value "▷ Start"}]
         [:input {:id "stop-ctl"  :type "button" :value "▢ Stop"}]]]]]]]
   
   [:div {:id "output"}
    [:div {:id "scalars"}
     [:h1 "(engulf)"]
     [:div {:id "nodes"}
      [:table {:id "node-stats"}
       [:tbody
        [:tr
         [:td {:class "k"} "Nodes Connected"]
         [:td {:class "v" :id "nodes-connected"} "&#8734;"]]]]]
     [:div {:id "stats"}
      [:h2 "Aggregates"]
      [:table {:id "benchmark-stats"}
       [:tbody
        [:tr
         [:td {:class "k"} "Total"]
         [:td {:class "v" :id "runs-total"} "&#8734;"]]
        [:tr
         [:td {:class "k"} "Runs/Sec"]
         [:td {:class "v" :id "runs-sec"} "&#8734;"]]
        [:tr
         [:td {:class "k"} "Walltime"]
         [:td {:class "v" :id "walltime"} "&#8734;"]]
        [:tr
         [:td {:class "k"} "Runtime"]
         [:td {:class "v" :id "runtime"} "&#8734;"]]
        [:tr
         [:td {:class "k"} "Median Rt."]
         [:td {:class "v" :id "median-runtime"} "&#8734;"]]
        [:tr
         [:td {:class "k"} "Failed"]
         [:td {:class "v" :id "runs-failed"} "&#8734;"]]]]
      [:h2 "Status Codes"]
      [:table {:id "response-code-stats"}
       [:tbody
        [:tr
         [:td {:class "code k"} "200"]
         [:td {:class "count v"} "&#8734;"]]]]]
     [:form
      [:input {:id "console-enabled", :type "checkbox"
               :name="console-enabled"}]
      [:label {:for "console-enabled"} "Enable Console Logging"]]]

    [:div {:class "charts"}
    [:h2 "Avg. Response Time Percentiles"]
    [:div {:id "resp-time-percentiles"}]

     [:h2 "Throughput Over Time"]
     [:div {:id "time-series"}
      [:div {:id "resp-time-series"}]
      [:div {:id "throughput-time-series"}]]]

    ]
   ))
