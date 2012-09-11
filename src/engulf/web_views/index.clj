(ns engulf.web-views.index
  (:require [engulf.web-views.common :as common]
            [engulf.utils :as utils])
  (:use [noir.core :only [defpage]]
        [hiccup.core :only [html]]
        [hiccup.element :only [link-to]]))

(defpage "/" []
  (common/layout
   [:div {:id "job-browser"}
    [:div {:id "job-list-cont"}]
    [:div {:id "job-list-pagination"}
     [:div {:class "prev-cont"} [:a {:class "prev"} "◀ Prev"]]
     " ◇ "
     [:span {:class "page-num"} "1"]
     " ◇ "
     [:div {:class "next-cont"} [:a {:class "next"} "Next ▶ "]]
     ]
    [:div {:class "tab-grip"}
     [:h2 {:id "past-jobs-title"} "Job History ⇣⇡"]]
    ]
   [:div {:id "benchmarker"}
    [:div {:id "info-bar"}
     [:span {:class "mode"}
      [:span {:class "k"} "Mode:"]
      [:span {:class "v"} "&#8734;"]]
     [:span {:class "go-live"}
      [:a {:class "v" :href "#"} "[Go Live]"]]
     [:span {:class "socket-state" :data-tooltip "WebSocket State"}
      [:span {:class "k"}
       "WebSocket:"]
      [:span {:class "v"}
       "&#8734;"]]
     ]
    [:div {:id "controls"}
     ]]
   
   [:div {:id "output"}
    [:div {:id "scalars"}
     [:h1
      [:div {:class "engulf"} "Engulf / "
       [:span {:class "version"} (utils/version)]]
      [:div {:class "status live"} "LIVE"]
      [:div {:class "status playback"}
       [:a {:href "#"} "PLAYBACK"]]]
     [:div {:id "nodes"}
      [:h2 "System"]
      [:table {:id "node-stats"}
       [:tbody
        [:tr
         [:td {:class "k"} "Nodes"]
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
      [:h2 "Responses"]
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
   [:div {:class "modal" :style "display: none;"}
    [:div {:class "modal-inner"}
     [:div {:class "modal-title"} "¡ Error !"]
     [:div {:class "modal-body"} "Ohai"]
     [:div {:class "modal-close"} "X"]
     ]]

   [:script {:type "x-underscore-tmpl" :id "controls-tmpl"}
    [:form
     [:div {:id "controls-top"}
      [:label {:for "_title"} "Title:"]
      "<input id='title' name='_title' type='text'
              placeholder='Untitled' value='<%= job.title %>'></input>"
      [:select {:id "type"}
       "<option id='type-url' value='url' <%= job.params.target.type === 'url' ? 'selected=\\'true\\'' : '' %>>Single URL</option>"
       "<option id='type-markov' value='markov-corpus' <%= job.params.target.type === 'markov-corpus' ? 'selected=\\'true\\'' : '' %>>URL List</option>"
        "Single URL"]
      "<% if (job.params.target.type === 'url') { %>"
      [:select {:id "method" :class "short-num" :name "method"}
       [:option {:value "get"} "GET"]
       [:option {:value "post"} "POST"]
       [:option {:value "put"} "PUT"]
       [:option {:value "delete"} "DELETE"]
       [:option {:value "patch"} "PATCH"]
       ]
      "<input id='url' type='url' name='url' value='<%= job.params.target.url %>'>"
      "<% } else  { %>"
      [:span {:id "markov-help"}
       "Newline Separate URLs as: "
       [:strong "GET http://localhost/foo"]
       ". Constructs a "
       [:a {:href "https://github.com/andrewvc/engulf/wiki/HTTP-API"}
        "Markov chain."]
       ]
      [:textarea {:id "markov-corpus" :name "markov-corpus"}
       "<%= _.map(job.params.target.corpus, function (r) { return r.method + ' ' + r.url;}).join('\\n') %>"
       ]
      "<% } %>"
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
        [:td
         "<input id='timeout' class='short-num' name='timeout' type='number' min='1' max='200000' value='<%= job.params.target.timeout %>'>"]
        [:td
         "<input id='concurrency' class='short-num' name='concurrency' type='number' min='1' value='<%= job.params.concurrency %>'>"]
        [:td
         "<input id='limit' class='short-num' name='limit' type='number' min='1' value='<%= job.params.limit %>'>"]
        [:td
         "<input id='keep-alive' name='keep-alive' type='checkbox' checked='<%= job.params.target['keep-alive'] == 'true' ? 'true' : '' %>'>"]
         [:td
          "<% if (job['started-at'] && !job['ended-at']) { %>"
          [:input {:id "stop-ctl"  :type "button" :value "▢ Stop"}]
          "<% } else { %>"
          [:input {:id "start-ctl" :type "button" :value "▷ Start"}]

          "<% } %>"
          ]]]]]]
   ))
