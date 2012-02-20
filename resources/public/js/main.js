
BenchmarkStream = Backbone.Model.extend({
  initialize: function () {
    var self = this;
    self.socket = new WebSocket(this.get('addr'));
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
  } 
});


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
     
    this.$el.append('<div>' + msg + '</div>');

    var children = this.$el.children();
    if (children.length > this.maxMessages) {
      var garbage = children.slice(0, children.length - this.maxMessages);
      $(garbage).remove();
    }

    this.scrollBottom();
  },
  scrollBottom: function () {
    this.$el 
    
    var lastTop = $(consoleView.$el.children().last()).offset().top;
    this.$el.scrollTop(lastTop);
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
      },
      function (data) {
        self.set({state: "started"});
        console.log(data);
    });
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
      self.set(data)
    });

    stream.bind('dtype-state', function (data) {
      self.set({state: data});
    });
  }
});

ControlsView = Backbone.View.extend({
  initialize: function () {
    this.$el = $(this.el);
     
    _.bindAll(this, "render");
    this.model.bind('change', this.render);
  },
  events: {
    "click #start-ctl": "start",
    "click #stop-ctl":  "stop",
    "change": "render"
  },
  start: function (e) {
    console.log("Starting");
    var self = this;
     
    self.disableInputs();
     
    var url = this.$el.find('#url').val();
    var concurrency = this.$el.find('#concurrency').val();
    var requests = this.$el.find('#requests').val();
    self.model.start(url, concurrency, requests);
  },
  stop: function (e) {
    this.model.stop();
  },
  render: function () {
    if (this.model.get('state') === 'stopped') {
      this.renderStartable();
    } else {
      this.renderStoppable();
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
    $('h1').text("(engulf " + $('#url').val().toLowerCase() + ")");
     
    var res = this.renderElements;
    res.completed.text(this.model.get('runs-total'));
    res.succeeded.text(this.model.get('runs-succeeded'));
    res.failed.text(this.model.get('runs-failed'));
    res.runtime.text(this.formatMillis(this.model.get('runtime')));

    var medianRuntime = this.model.get('median-runtime');
    if (medianRuntime) {
      res.medianRuntime.text(medianRuntime + " ms");
    }

    var runsSec = this.model.get('runs-sec');
    if (runsSec) {
      res.runsSec.text(parseInt(runsSec) + ' / sec');
    }

    this.renderResponseCodes(this.model.get('response-code-counts'));
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

ChartsView = Backbone.View.extend({
  filterData: function (data) {
    return _.map(data, function (d) { return d.avg} );
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
  initialize: function () {
    var self = this;
    this.$el = $(this.el);
     
    _.bindAll(this, "render");
    this.model.bind('change', this.render);

    var chartW = 595;
    var w = this.w = (parseInt(chartW/100));
    var h = this.h = 100;

    // Which field in the results to use as data
    this.yField = "avg";

    var data = _.range(100).map(function (i) {return {}});
 
    this.setYScale(100);
    this.setXScale(w);

    this.rtPercentiles = d3.select("#resp-time-percentiles").
         append("svg").
         attr("class", "chart").
         attr("width", w * 100).
         attr("height", h);

    // Setup bars
    this.rtPercentiles.selectAll("rect").
         data(data).
         enter().append("rect").
         attr("x", function(d, i) { return self.xScale(i) - .5; }).
         attr("y", function(d) { return h - self.yScale(d[self.yField]) - .5; }).
         attr("width", w).
         attr("height", function(d) { return self.yScale(d[self.yField]); });

    // Create bottom line
    this.rtPercentiles.append("line").
         attr("x1", 0).
         attr("x2", w * data.length).
         attr("y1", h - .5).
         attr("y2", h - .5).
         style("stroke", "#000");

    // Setup percentile labels
    this.rtPercentiles.selectAll("text").
         data(data).
         enter().append("text").
         attr("x", function (d, i) { return self.xScale(i); }).
         attr("y", function(d, i) { return 1; }).
         attr("dx", -3). // padding-right
         attr("dy", ".35em"). // vertical-align: middle
         attr("text-anchor", "end"). // text-align: right
         attr("class", function (d,i) { return (i === 0 || ((i+1) % 10 === 0)) ? "decile" : "non-decile" }).
         text("");
  },
  render: function () {
    var self = this;
    var rawData = this.model.get('runtime-percentiles');
    if (! rawData) return;
    
    var percentiles = this.filterData(rawData);
    
    // Grab every 10th percentile for the avg time overlay
    var deciles = [];
    _.each(percentiles, function (p, i) {
      if (i === 0 || ((i+1) % 10 === 0)) {
        deciles.push(p)
      }
    });
    
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
         duration(100).
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

ResponseTimeSeriesView = Backbone.View.extend({
  initialize: function () {
    var self = this;
     
    this.$el = $(this.el);
     
    _.bindAll(this, "render");
    this.model.bind('change', this.render);

    var chartW = self.chartW = 500;
    var data = _.range(100).map(function (i) { return {value: 0, count: 0}});
    var data = [];

    var w = this.w = chartW;
    var h = this.h = 80;

    self.setYMax = function(upper) {
      self.y = d3.scale.linear().
                  domain([0, upper]).
                  rangeRound([0, h]);
    };
     
    self.setXMax = function(upper) {
      self.x = d3.scale.linear()
              .domain([0, upper])
              .range([0, w]);
    }


    this.lastTotal = 0;
  },
  render: function () {
    var self = this;

     
    var data = [];
    var raw = this.model.get('avg-runtime-by-start-time');

    var valMax = 0;
    var rawTotal = 0;
    for (time in raw) {
      var d = raw[time];
       
      rawTotal += d.total;
       
      if (d.avg > valMax) {
        valMax = d.avg;
      }

      d.time = time;
      d.value = d.avg;
      data.push(d);
    }

    if (rawTotal != self.lastTotal) {
      self.lastTotal = rawTotal;
    } else {
      return;
    }

    $('#resp-time-series').html('');
     
     var chart = d3.select("#resp-time-series").append("svg")
      .attr("class", "chart")
      .attr("width", self.chartW)
      .attr("height", self.h);

    chart.append("line")
      .attr("x1", 0)
      .attr("x2", self.chartW)
      .attr("y1", self.h - .5)
      .attr("y2", self.h - .5)
      .style("stroke", "#000");


      this.chart = chart;
    

    this.setYMax(valMax);
    this.setXMax(data.length);

    if (!data) {
      return 
    }

    var w = this.w;
    var h = this.h;


    var x = this.x;
    var y = this.y;

    var rect = chart.selectAll("rect")
      .data(data, function(d) { return d.time; });

    rect.enter().append("rect", "line")
      .attr("x", function(d, i) { return x(i + 1) - .5; })
      .attr("y", function(d) { return h - y(d.value) - .5; })
      .attr("width", 2)
      .attr("height", function(d) { return y(d.value); })
    .transition()
      .duration(1)
      .attr("x", function(d, i) { return x(i) - .5; });

    /*
    rect.transition()
        .duration(1)
        .attr("x", function(d, i) { return x(i) - .5; });

    rect.exit().transition()
        .duration(1)
        .attr("x", function(d, i) { return x(i - 1) - .5; })
        .emove();
        */

  }
});



$(function () {
  var benchmarkStream = window.benchmarkStream = new BenchmarkStream(
      {addr: 'ws://' + location.host + '/benchmarker/stream'}
  );
   
  var consoleView  = window.consoleView = new ConsoleView(
    {
      el: $('#console')
    }
  );
  consoleView.logEvents(benchmarkStream, 'data');
   
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
   
  var chartsView = window.chartsView = new ChartsView(
    {
      el: $('#resp-time-percentiles')[0],
      model: benchmarker,
    }
  );


  var responseTimeSeriesView = window.responseTimeSeriesView = new ResponseTimeSeriesView(
    {
      el: $('#resp-time-series')[0],
      model: benchmarker,
    }
  );
});
