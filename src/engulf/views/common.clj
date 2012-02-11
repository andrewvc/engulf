(ns engulf.views.common
  (:use [noir.core :only [defpartial]]
        [hiccup.page-helpers :only [include-css html5 include-js javascript-tag link-to]]))

(defpartial layout [& content]
  (html5
    [:html {:class "no-js" :lang "en"}
    [:head [:meta {:charset "utf-8"}]
     [:title "Engulf HTTP Benchmarker"]
     [:meta {:name "description", :content "engulf http benchmarker"}]

     [:link {:rel "icon" :type "image/png" :href "/favicon.png"}]

     (include-css
       "http://fonts.googleapis.com/css?family=Inconsolata"
       "/css/style.css"
       "/css/main.css")
     (include-js
       "/js/libs/modernizr-2.0.min.js"
       "/js/libs/respond.min.js"
       "/js/libs/d3/d3.js"
       "/js/libs/d3/d3.chart.js"
       "/js/libs/respond.min.js"
       "/js/libs/script.js")
     (javascript-tag "try{Typekit.load();}catch(e){};")]
    [:body [:div {:id "container"}
             [:header
               [:h1
                "(engulf)"]]
             [:div {:id "main" :role "main"}
               content]

           ]
     (include-js "//ajax.googleapis.com/ajax/libs/jquery/1.6.2/jquery.min.js")
     (javascript-tag "window.jQuery || document.write('<script src=\"/js/libs/jquery-1.6.2.min.js\"><\\/script>');")
     (javascript-tag "
       $script(['/js/libs/jquery-ui.min.js','/js/libs/underscore.min.js', '/js/libs/backbone.min.js'], function () {
         $script(['/js/main.js']);
       });
     ")
     ]]))
