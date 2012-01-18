
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
  },
  append: function(msg) {
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
      self.set({'runs-succeeded': data['runs-succeeded'],
                'runs-failed':    data['runs-failed'],
                'runs-total':     data['runs-total'],
                'median-runtime': data['median-runtime']
      });
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
      medianRuntime: this.$el.find('#median-runtime'),
    }
  },
  render: function () {
    var res = this.renderElements;
    res.completed.text(this.model.get('runs-total'));
    res.succeeded.text(this.model.get('runs-succeeded'));
    res.failed.text(this.model.get('runs-failed'));
    res.medianRuntime.text(this.model.get('median-runtime'));
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

  var controlsView = window.controlsView = new AggregateStatsView(
    {
      el: $('#stats')[0],
      model: benchmarker,
    }
  );

});
