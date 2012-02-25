$(function () {
  if ($.browser.mozilla) {
    $('#firefox-warning').fadeIn(1500);
  }
});


RequestItem = Backbone.Model.extend({
  initialize: function () {

  }
});

RequestPlan = Backbone.Collection.extend({
  initialize: function () {

  },
  model: RequestItem
});

RequestPlanView = Backbone.View.extend({
  initialize: function () {
    this.$el = $(this.el); 
    
    _(this).bindAll('add', 'remove', 'reset');
    this.collection.bind('add', this.add);
    this.collection.bind('remove', this.remove);
    this.collection.bind('reset', this.reset);
  },
  tagName: 'ol',
  render: function () {
    return this.el;
  },
  add: function (item) {
    console.log("Collection add: ", item);
    
    var view = new RequestItemView({model: item});
    this.$el.append(view.render());
  },
  remove: function (item) {
    console.log("Collection remove", item);
    this.$el.find('#' + item.cid).remove();
  },
  reset: function (e) {
    console.log("Collection reset: ", e);
  },
});

RequestItemView = Backbone.View.extend({
  initialize: function () {
    this.$el = $(this.el);
    this.tmpl = _.template("<li><%= reqMethod %>: <%= reqUrl %>");
  },
  tagName: 'li',
  render: function () {
    return this.tmpl(this.model.toJSON());
  }
});

plan = new RequestPlan();
pview = new RequestPlanView({collection: plan});
plan.add({reqMethod: 'get', reqUrl: 'http://localhost:4000'})
plan.add({reqMethod: 'get', reqUrl: 'http://localhost:4000/foo'})



BenchmarkStream = function (addr) {
  this.addr = addr;
  _.extend(this, Backbone.Events);
  var self = this;
  
  if (typeof(WebSocket) !== 'undefined') {
    console.log("Using a standarb websocket");
    self.socket = new WebSocket(this.addr);
  } else if (typeof(MozWebSocket) !== 'undefined') {
    console.log("Using MozWebSocket")
    self.socket = new MozWebSocket(this.addr);
  } else {
    alert("Your browser does not support web sockets. No stats for you!");
  }
  
  self.socket.onopen = function (e) {
    self.trigger('open', e);
  };
  self.socket.onmessage = function (e) {
    self.trigger('message', e);
      self.trigger('data', e.data);
    
    var parsed = JSON.parse(e.data);
      self.trigger('jsonData', parsed);
    self.trigger('dtype-' + parsed.dtype, parsed.data);
  };
  self.socket.onclose = function (e) {
    self.trigger('close', e);
  };
  self.socket.onerror = function (e) {
      self.trigger('error', e);
  };
};


ConsoleView = Backbone.View.extend({
  initialize: function () {
    this.$el = $(this.el);
    this.maxMessages = 1000;
    this.consoleEnabledBox = $('#console-enabled');
  },
  append: function(msg) {
    if (!this.consoleEnabledBox.attr('checked')) {
      return;
    }
    
    console.log(msg);
  },
  logEvents: function (obj, eventType) {
    var self = this;
    obj.bind(eventType, function (e) {
      self.append(e);
    });
  }
});

Benchmarker = Backbone.Model.extend({
  url: "/benchmarker",
  initialize: function () {
    this.fetch();
  },
  start: function (url, concurrency, requests) {
    var self = this;
    $.post("/benchmarker",
      {state: "started",
       concurrency: concurrency,
       requests: requests,
       url: url
      }).
      success(function (data) {
        var parsed = JSON.parse(data);
        if (parsed.error) {
          alert("Could not start: " + parsed.error);
          self.set({state: "stopped"});
        } else {
          self.set({state: "started"});
          self.set({state: "stopped"});
        }
      }).
      error(function (error) {
        alert("Error processing request: " + error);
      })
  },
  stop: function () {
    var self = this;
    $.post("/benchmarker", {state: "stopped"}, function () {
      self.set({state: "stopped"});
    });
  },
  bindToStream: function (stream) {
    var self = this;
    stream.bind('dtype-stats', function (data) {
      self.set({stats: data});
    });

    stream.bind('dtype-state', function (data) {
      self.set(data);
    });
  },
  timeSeriesFor: function (field) {
    var raw = this.get('stats')['by-start-time'];
    var data = [];
    for (time in raw) {
      data.push({time: time, value: raw[time][field]});
    }
    return data;
  },
  maxInTimeSeries: function (timeSeries) {
    var max = 0;
    _.each(timeSeries, function (d) {
      if (d.value > max) {
        max = d.value;
      }
    });
    return max;
  },
  percentileAvgs: function () {
    return _.map(this.get('stats')['runtime-percentiles'],
                 function (d) { return d.avg}
                );
  },
  decileAvgs: function () {
    // Grab every 10th percentile for the avg time overlay
    var deciles = [];
    _.each(this.percentileAvgs(), function (p, i) {
      if (i === 0 || ((i+1) % 10 === 0)) {
        deciles.push(p)
      }
    });
    return deciles;
  }
});

ControlsView = Backbone.View.extend({
  initialize: function () {
    this.$el = $(this.el);
     
    _.bindAll(this, "render");
    this.model.bind('change', this.render);

    this.$urlInput = $('#url');
    this.$concInput = $('#concurrency');
    this.$reqsInput = $('#requests');
  },
  events: {
    "click #start-ctl": "start",
    "click #stop-ctl":  "stop",
    "change": "render"
  },
  start: function (e) {
    var url = this.$el.find('#url').val();
    var concurrency = parseInt(this.$el.find('#concurrency').val(), 10);
    var requests = parseInt(this.$el.find('#requests').val(), 10);
    
    if (!url || url.length < 3) {
      alert("Could not start benchmark, no URL specified!")
      return;
    }
    if (!concurrency || concurrency < 1) {
      alert("Concurrency must be a positive integer!");
      return;
    }
    if (!requests || requests < 1) {
      alert("Requests must be a positive integer!");
      return;
    }
    
    console.log("Starting");
    this.disableInputs();
    this.model.start(url, concurrency, requests);
  },
  stop: function (e) {
    this.model.stop();
  },
  render: function () {
    if (this.model.get('state') === 'stopped') {
      this.renderStartable();
      if (this.$urlInput.val() === '') {
        this.$urlInput.val('http://' + location.host + '/test-responses/fast-async');
      }
    } else {
      this.renderStoppable();
      this.$urlInput.val(this.model.get('url'));
      this.$concInput.val(this.model.get('workers'));
      this.$reqsInput.val(this.model.get('max-runs'));
    }


  },
  renderStartable: function () {
    this.enableInputs();
    
    var startCtl = this.$el.find('#start-ctl');
    startCtl.removeAttr('disabled');
    startCtl.show();
     
    this.$el.find('#stop-ctl').hide();
  },
  renderStoppable: function () {
    this.disableInputs();
    
    var stopCtl = this.$el.find('#stop-ctl');
    stopCtl.removeAttr('disabled');
    stopCtl.show();
     
    this.$el.find('#start-ctl').hide();
  },
  disableInputs: function () {
    this.$el.find('input').attr('disabled', true);
  },
  enableInputs: function () {
    this.$el.find('input').removeAttr('disabled');
  }
});

AggregateStatsView = Backbone.View.extend({
   initialize: function () {
    this.$el = $(this.el);
     
    _.bindAll(this, "render");
    this.model.bind('change', this.render);

    this.renderElements = {
      completed: this.$el.find('#runs-total'),
      succeeded: this.$el.find('#runs-succeeded'),
      failed: this.$el.find('#runs-failed'),
      runtime: this.$el.find('#runtime'),
      medianRuntime: this.$el.find('#median-runtime'),
      runsSec: this.$el.find('#runs-sec'),
      responseCodes: this.$el.find('#response-code-stats tbody')
    }
  },
  render: function () {
    $('h1').text("(enulf " + $('#url').val().toLowerCase() + ")");
     
    var res = this.renderElements;
    var stats = this.model.get('stats');
    res.completed.text(stats['runs-total']);
    res.succeeded.text(stats['runs-succeeded']);
    res.failed.text(stats['runs-failed']);
    res.runtime.text(this.formatMillis(stats['runtime']));

    var medianRuntime = stats['median-runtime'];
    if (medianRuntime) {
      res.medianRuntime.text(medianRuntime + " ms");
    }

    var runsSec = stats['runs-sec'];
    if (runsSec) {
      res.runsSec.text(parseInt(runsSec) + ' / sec');
    }

    this.renderResponseCodes(stats['response-code-counts']);
  },
  renderResponseCodes: function (codeCounts) {
    var self = this;
     
    var tbody = self.renderElements.responseCodes;
    tbody.html('');

    if (!codeCounts) { return };

    if (!self.tmpl) {
      self.tmpl = _.template("<tr><td class='code k'><%= code %></td><td class='count v'><%= count %></tr>");
    }
    
    for (code in codeCounts) {
      var count = codeCounts[code];
      tbody.append(self.tmpl({code: code, count: count}));
    }
  },
  formatMillis: function(millis) {
    totalSec = parseInt(millis / 1000);
    hours = parseInt( totalSec / 3600 ) % 24;
    minutes = parseInt( totalSec / 60 ) % 60;
    seconds = totalSec % 60;
    millis = millis % 1000;

    return (hours < 10 ? "0" + hours : hours) +
            ":" + (minutes < 10 ? "0" + minutes : minutes) +
            ":" + (seconds  < 10 ? "0" + seconds : seconds) +
            "." + millis;
  }
});

PercentilesView = Backbone.View.extend({
  initialize: function () {
    var self = this;
    this.$el = $(this.el);
    
    _.bindAll(this, "render");
    this.model.bind('change', this.render);
    
    var chartW = 595;
    var w = this.w = (parseInt(chartW/100));
    var h = this.h = 100;
    
    var blankData = _.range(100).map(function (i) {return {}});
    this.initializeBox();
    this.initializeBars(blankData);
    this.initializeLabels(blankData);
  },
  yScale: null,
  setYScale: function(newYMax) {
    this.yScale = d3.scale.linear().
      domain([0, newYMax]).
      rangeRound([0, this.h-40]);
  },
  xScale: null,
  setXScale: function (width) {
    this.xScale = d3.scale.linear().
      domain([0, 1]).
      range([0, width]);
  },
  initializeBox: function () {
    // Create the container
    this.rtPercentiles = d3.select("#resp-time-percentiles").
         append("svg").
         attr("class", "chart").
         attr("width", this.w * 100).
         attr("height", this.h);

    // Create bottom line
    this.rtPercentiles.append("line").
         attr("x1", 0).
         attr("x2", this.w * 100).
         attr("y1", this.h - .5).
         attr("y2", this.h - .5).
         style("stroke", "#000");
  },
  initializeBars: function (data) {
    var self = this;
    this.setYScale(100);
    this.setXScale(this.w);

    this.rtPercentiles.selectAll("rect").
         data(data).
         enter().append("rect").
         attr("x", function(d, i) { return self.xScale(i) - .5; }).
         attr("y", 0).
         attr("width", this.w - 2).

         attr("height", 0);    
  },
  initializeLabels: function (data) {
    var self = this;
    // Setup percentile labels
    this.rtPercentiles.selectAll("text").
         data(data).
         enter().append("text").
         attr("x", function (d, i) { return self.xScale(i); }).
         attr("y", function(d, i) { return 1; }).
         attr("dx", -3). // padding-right
         attr("dy", ".35em"). // vertical-align: middle
         attr("text-anchor", "end"). // text-align: right
         attr("class", function (d,i) {
           return (i === 0 || ((i+1) % 10 === 0)) ? "decile" : "non-decile"
         }).
         text("");
  },
  render: function () {
    var self = this;
    
    if (! this.model.get('stats')['runtime-percentiles']) return;
    
    var percentiles = this.model.percentileAvgs();
    var deciles = this.model.decileAvgs();
    
    this.setYScale(percentiles[percentiles.length-1]);

    self.rtPercentiles.selectAll("rect").
         data(percentiles).
         transition().
         duration(50).
         attr("y", function(d) { return self.h - self.yScale(d) - .5; }).
         attr("height", function(d) { return self.yScale(d); });

    self.rtPercentiles.selectAll(".decile").
         data(deciles).
         transition().
         duration(50).
         attr("x", function (d, i) {
           return self.xScale((i+1) * self.w * 1.83) - 8; 
         }).
         attr("y", function(d, i) { return 20; }).
         attr("dx", -3). // padding-right
         attr("dy", ".35em"). // vertical-align: middle
         attr("text-anchor", "end"). // text-align: right
         text(function (d, i) { return d + 'ms' } );
  }
});

TimeSeriesView = Backbone.View.extend({
  initialize: function (opts) {
    this.$el = $(this.el);
    this.field = opts.field;
    
    _.bindAll(this, "render");
    this.model.bind('change', this.render);

    this.w = 500;
    this.h = 80;
    
    this.setupContainer();
  },
  yScale: null,
  setYScale: function (upper) {
    this.yScale= d3.scale.linear().
      domain([0, upper]).
      rangeRound([0, this.h]);
  },
  xScale: null,
  setXScale: function (upper) {
    this.xScale = d3.scale.linear().
      domain([0, upper]).
      range([0, this.w]);
  },
  setupContainer: function () {
    var chart = d3.select(this.el).
      append("svg").
      attr("class", "chart").
      attr("width", this.w).
      attr("height", this.h);
    
    chart.append("line").
      attr("x1", 0).
      attr("x2", this.w).
      attr("y1", this.h - .5).
      attr("y2", this.h - .5).
      style("stroke", "#000");
    
    this.chart = chart;
  },
  render: function () {
    var self = this;
    var data = [];
    
    var times = this.model.timeSeriesFor(this.field);
    if (!times) { return };
    
    var max = this.model.maxInTimeSeries(times);
    
    this.setYScale(max);
    this.setXScale(times.length);

    var rect = this.chart.selectAll("rect").
      data(times, function(d) { return d.time; });
    
    rect.enter().
      append("rect").
      attr("x", function(d, i) { return self.xScale(i + 1) + 1; }).
      attr("y", function(d) { return self.h - self.yScale(d.value) - .5; }).
      attr("width", 1).
      attr("height", function(d) { return self.yScale(d.value); }).
      transition().
      duration(1000).
      attr("x", function(d, i) { return self.xScale(i) - .5; });


    rect.transition().
      duration(200).
      attr("x", function(d, i) { return self.xScale(i) - .5; }).
      attr("y", function(d) { return self.h - self.yScale(d.value) - .5; }).
      attr("height", function(d) { return self.yScale(d.value); });

    rect.exit().transition().duration(0).remove();
  }
});



$(function () {
  var benchmarkStream = window.benchmarkStream = new BenchmarkStream('ws://' + location.host + '/benchmarker/stream');
   
  var consoleView  = window.consoleView = new ConsoleView(
    {
      el: $('#console')
    }
  );
  consoleView.logEvents(benchmarkStream, 'jsonData');
   
  var benchmarker  = window.benchmarker = new Benchmarker();
  benchmarker.bindToStream(benchmarkStream);
  
  var controlsView = window.controlsView = new ControlsView(
    {
      el: $('#controls')[0],
      model: benchmarker,
    }
  );

  var statsView = window.statsView = new AggregateStatsView(
    {
      el: $('#stats')[0],
      model: benchmarker,
    }
  );
   
  var percentilesView = window.percentilesView = new PercentilesView(
    {
      el: $('#resp-time-percentiles')[0],
      model: benchmarker,
    }
  );

  var responseTimeAvgSeriesView = new TimeSeriesView(
    {
      el: $('#resp-time-series')[0],
      model: benchmarker,
      field: 'avg'
    }
  )
    
  var throughputTimeAvgSeriesView = new TimeSeriesView(
    {
      el: $('#throughput-time-series')[0],
      model: benchmarker,
      field: 'count'
    }
  );
});
