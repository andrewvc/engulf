
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
  },
  append: function(msg) {
    this.$el.append('<div>' + msg + '</div>');
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
  
  var controlsView = window.controlsView = new ControlsView(
    {
      el: $('#controls')[0],
      model: benchmarker,
    }
  );
});
