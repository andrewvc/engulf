$(function () {
  if ($.browser.mozilla) {
    $('#firefox-warning').fadeIn(1500);
  }
});

BenchmarkStream = function (addr) {
  console.log("Connecting to addr", addr);
  this.addr = addr;
  _.extend(this, Backbone.Events);
  var self = this;
  
  if (typeof(WebSocket) !== 'undefined') {
    console.log("Using a standard websocket");
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
    
    var parsed = JSON.parse(e.data);
      self.trigger('jsonData', parsed);
    self.trigger('name-' + parsed.name, parsed.body);
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

Node = Backbone.Model.extend({});
Nodes = Backbone.Collection.extend({
  model: Node
});

Benchmarker = Backbone.Model.extend({
  url: "/jobs/current",
  initialize: function () {
  },
  start: function (params) {
    var self = this;
    $.ajax(
      "/jobs/current",
      {
        contentType: "application/json",
        type: "POST",
        dataType: "json",
        data: JSON.stringify(params)
      }
    ).success(function (parsed) {
        if (parsed.error) {
          alert("Could not start: " + parsed.error);
          self.set({currentJob: null});
        } else {
          self.set({currentJob: parsed});
        }
      }).
      error(function (error) {
        console.log("Error", error);
        alert("Error processing request: " + error);
      });
  },
  stop: function () {
    var self = this;
    $.ajax("/jobs/current", {
               type: "DELETE",
               success: function (data) {
                   self.set({currentJob: null});
               }
           });
          
  },
  bindToStream: function (stream) {
    var self = this;
    
    stream.bind("all", function(name, body) {
      //console.log(name, body);
    });

    stream.bind("name-current-nodes", function (d) {
      console.log("NODES", d);
      if (self.get('nodes')) {
        self.get('nodes').reset(d);
      } else {
        var nodes = new Nodes(d);
        self.set({'nodes': nodes});
      }
    });

    stream.bind("name-result", function (d) {
      self.set({stats: d.value});
    });

    stream.bind('name-current-job', function (data) {
      self.set({currentJob: data});
    });

    stream.bind('name-job-start', function (data) {
      self.set({currentJob: data});
    });

    stream.bind('name-job-stop', function (data) {
      self.set({currentJob: null});
    });
  },
  timeSeriesFor: function (field) {
    if (! this.get("stats")) return;
    var raw = this.get('stats')['time-slices'];
    var data = [];
    for (time in raw) {
      data.push({time: time, value: raw[time][field] || 0});
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
    return _.map(this.get('stats')['percentiles'],
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
    this.$keepAliveInput = $('#keep-alive');
    this.$concInput = $('#concurrency');
    this.$timeoutInput = $('#timeout');
    this.$reqsInput = $('#requests');
  },
  events: {
    "click #start-ctl": "start",
    "click #stop-ctl":  "stop",
    "change": "render"
  },
  start: function (e) {
    var params = {};
    
    params.url = this.$el.find('#url').val();
    params.concurrency = parseInt(this.$el.find('#concurrency').val(), 10);
    params.limit = parseInt(this.$el.find('#limit').val(), 10);
    params.method = $('#method').val();
    params.timeout = $('#timeout').val();
    params['keep-alive'] = 'true';
    params['formula-name'] = 'http-benchmark';
    params['_stream'] = 'false';
    
    
    if (!params.url || params.url.length < 3) {
      alert("Could not start benchmark, no URL specified!")
      return;
    }
    if (!params.concurrency || params.concurrency < 1) {
      alert("Concurrency must be a positive integer!");
      return;
    }
    if (!params.limit || params.limit < 1) {
      alert("Limit must be a positive integer!");
      return;
    }
    
    this.disableInputs();
    this.model.start(params);
  },
  stop: function (e) {
    this.model.stop();
  },
  render: function () {
    if (!this.model.get('currentJob')) {
      this.renderStartable();
      if (this.$urlInput.val() === '') {
        this.$urlInput.val('http://' + location.host + '/test-responses/delay/15');
      }
    } else {
      var params = this.model.get('currentJob').params;
      this.renderStoppable();
      this.$urlInput.val(params.url);
      this.$concInput.val(params.concurrency);
      this.$timeoutInput.val(params.timeout);
      if (params["keep-alive"] === "true") {
        this.$keepAliveInput.prop("checked", true);
      } else {
        this.$keepAliveInput.prop("checked", false);
      }


      this.$reqsInput.val(params.limit);
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
      nodes: $("#nodes-connected"),
      completed: this.$el.find('#runs-total'),
      succeeded: this.$el.find('#runs-succeeded'),
      failed: this.$el.find('#runs-failed'),
      runtime: this.$el.find('#runtime'),
      walltime: this.$el.find('#walltime'),
      medianRuntime: this.$el.find('#median-runtime'),
      runsSec: this.$el.find('#runs-sec'),
      responseCodes: this.$el.find('#response-code-stats tbody')
    }
  },
  render: function () {
    var res = this.renderElements;

    

    res.nodes.text(this.model.get('nodes').length);
    
    var stats = this.model.get('stats');
    if (stats) {
      res.completed.text(stats['runs-total']);
      res.succeeded.text(stats['runs-succeeded']);
      res.failed.text(stats['runs-failed']);
      res.runtime.text(this.formatMillis(stats['runtime']));
      res.walltime.text(this.formatMillis(stats['walltime']));

      var medianRuntime = stats.percentiles['50'].median;
      if (medianRuntime) {
        res.medianRuntime.text(medianRuntime + " ms");
      }

      var runsSec = stats['total-runs-per-second'];
      if (runsSec) {
        res.runsSec.text(parseInt(runsSec) + ' / sec');
      }

      this.renderResponseCodes(stats['status-codes']);        
    }
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
    
    if (! this.model.get("stats") || ! this.model.get('stats')['percentiles']) return;
    
    var percentiles = this.model.percentileAvgs();
    var deciles = this.model.decileAvgs();
    
    this.setYScale(percentiles[percentiles.length-1]);

    self.rtPercentiles.selectAll("rect").
         data(percentiles).
         transition().
         duration(50).
         attr("y", function(d) { return self.h - self.yScale(d) - .5; }).
         attr("height", function(d) { return self.yScale(d); });

         //attr("height", 2);
      
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
    
    if (!this.field) return null;
    var times = this.model.timeSeriesFor(this.field);
    if (!times) { return null; };
    
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
      attr("height", 20).
      transition().
      duration(1000).
      attr("x", function(d, i) { return self.xScale(i) - .5; });


    rect.transition().
      duration(20).
      attr("x", function(d, i) { return self.xScale(i) - .5; }).
      attr("y", function(d) { return self.h - self.yScale(d.value) - .5; }).
      attr("height", function(d) { return self.yScale(d.value); });


    rect.exit().transition().duration(0).remove();
  }
});



$(function () {
  var benchmarkStream = window.benchmarkStream = new BenchmarkStream('ws://' + location.host + '/river');
   
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
      model: benchmarker
    }
  );

  var statsView = window.statsView = new AggregateStatsView(
    {
      el: $('#stats')[0],
      model: benchmarker
    }
  );
   
  var percentilesView = window.percentilesView = new PercentilesView(
    {
      el: $('#resp-time-percentiles')[0],
      model: benchmarker
    }
  );
    
  var throughputTimeAvgSeriesView = new TimeSeriesView(
    {
      el: $('#throughput-time-series')[0],
      model: benchmarker,
      field: "total"
    }
  );
});
