
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
  url: "/benchmarker/state",
  initialize: function () {
    this.fetch();
  },
  start: function (url, concurrency, requests) {
    $.post("/benchmarker/state",
      {state: "started",
       concurrency: concurrency,
       requests: requests,
       url: url
      },
      function (data) {
        console.log(data);
    });
  },
  bindToStream: function (stream) {
    var self = this;
    stream.bind('dtype-agg', function (data) {
      self.set(data)
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
    console.log("Stopping");
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
      responseCodes: this.$el.find('#response-code-stats tbody')
    }
  },
  render: function () {
    var res = this.renderElements;
    res.completed.text(this.model.get('runs-total'));
    res.succeeded.text(this.model.get('runs-succeeded'));
    res.failed.text(this.model.get('runs-failed'));
    res.runtime.text(this.formatMillis(this.model.get('runtime')));

    this.renderResponseCodes(this.model.get('response-code-counts'));
  },
  renderResponseCodes: function (codeCounts) {
    var self = this;
     
    var tbody = self.renderElements.responseCodes;
    tbody.html('');

    if (!codeCounts) { return };

    if (!self.tmpl) {
      self.tmpl = _.template("<tr><td class='code'><%= code %></td><td class='count'><%= count %></tr>");
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

    return (hours < 10 ? "0" + hours : hours) +
            ":" + (minutes < 10 ? "0" + minutes : minutes) +
            ":" + (seconds  < 10 ? "0" + seconds : seconds);
  }
});

ChartsView = Backbone.View.extend({
  initialize: function () {
    var self = this;
     
    this.$el = $(this.el);
     
    _.bindAll(this, "render");
    this.model.bind('change', this.render);

    var chartW = 760;
    var w = this.w = (parseInt(760/100));
    var h = this.h = 100;

    // Which field in the results to use as data
    this.yField = "avg";

    var data = _.range(100).map(function (i) { return {avg: 0} });
 
    this.x = d3.scale.linear().
               domain([0, 1]).
               range([0, w]);
 
    this.setYMax = function(upper) {
      self.y = d3.scale.linear().
                  domain([0, upper]).
                  rangeRound([0, h]);
    };
     
    this.setYMax(100);
    
    this.rtPercentiles = d3.select("#charts").
         append("svg").
         attr("class", "chart").
         attr("width", w * 100).
         attr("height", h);

    this.rtPercentiles.selectAll("rect").
         data(data).
         enter().append("rect").
         attr("x", function(d, i) { return self.x(i) - .5; }).
         attr("y", function(d) { return h - self.y(d[self.yField]) - .5; }).
         attr("width", w).
         attr("height", function(d) { return self.y(d[self.yField]); });

    this.rtPercentiles.append("line")
         .attr("x1", 0)
         .attr("x2", w * data.length)
         .attr("y1", h - .5)
         .attr("y2", h - .5)
         .style("stroke", "#000");

    window.rtp = this.rtPercentiles;

  },
  render: function () {
   var self = this;
   var rtpData = this.model.get('runtime-percentiles');

   if (!rtpData) {
    return 
   }
    
   self.setYMax(rtpData[rtpData.length-1]["avg"]);

   self.rtPercentiles.selectAll("rect").
       data(rtpData).
      transition().
       duration(1).
       attr("y", function(d) { return self.h - self.y(d[self.yField]) - .5; }).
       attr("height", function(d) { return self.y(d[self.yField]); });
  }
});


$(function () {
  var benchmarkStream = window.benchmarkStream = new BenchmarkStream(
    {addr: 'ws://localhost:3000/benchmarker/stream'}
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
      el: $('#charts')[0],
      model: benchmarker,
    }
  );
});
