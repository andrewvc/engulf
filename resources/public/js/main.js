$(function () {
  if ($.browser.mozilla) {
    $('#firefox-warning').fadeIn(1500);
  }
});

function formatTimestamp (timestamp) {
    var date = new Date(timestamp * 1000);
    return date.getFullYear() + "-" +
           (sprintf('%02d', date.getMonth()+1)) + "-" + 
           sprintf('%02d', date.getDay()) + " " +
           sprintf('%02d', date.getHours()) + ":" + 
           sprintf('%02d', date.getMinutes()) + ":" +
           sprintf('%02d', date.getSeconds());

}

function formatMillis (millis) {
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

function setIndicatedJob (uuid) {
  $('.cur-job-related').removeClass('cur-job-related');
  $("[data-uuid=" + uuid + "]").addClass('cur-job-related');          
}

Job = Backbone.Model.extend({
  urlRoot: "/jobs",
  idAttribute: "uuid"
});

Node = Backbone.Model.extend({
  idAttribute: "uuid"
});
Nodes = Backbone.Collection.extend({
  model: Node
});

function toFixed(value, precision) {
    var precision = precision || 0,
    neg = value < 0,
    power = Math.pow(10, precision),
    value = Math.round(value * power),
    integral = String((neg ? Math.ceil : Math.floor)(value / power)),
    fraction = String((neg ? -value : value) % power),
    padding = new Array(Math.max(precision - fraction.length, 0) + 1).join('0');

    return precision ? integral + '.' +  padding + fraction : integral;
}

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
    
    console.log(msg.name, msg);
  },
  logEvents: function (obj, eventType) {
    var self = this;
    obj.bind(eventType, function (e) {
      self.append(e);
    });
  }
});

Benchmarker = Backbone.Model.extend({
  url: "/jobs/current",
  initialize: function () {
  },
  start: function (params, on_success, on_error) {
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
        if (on_success) on_success(parsed);
        self.set({currentJob: parsed});
      }).
      error(function (errorRaw) {
        var error = JSON.parse(errorRaw.responseText);
        console.log("Error", error);
        if (on_error) on_error(error);
        console.log(error);
        if (error.message) {
          alert("Error processing request: " + error.message);
        } else {
          alert("Error processing request: " + JSON.stringify(error));            
        }
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
      if (self.get('nodes')) {
        self.get('nodes').reset(d);
      } else {
        var nodes = new Nodes(d);
        self.set({'nodes': nodes});
      }
    });

    stream.bind("name-node-connect", function (d) {
      self.get('nodes').add(d);
      self.trigger('change');
    });

    stream.bind("name-node-disconnect", function (d) {
      var nodes = self.get('nodes');
      var n = nodes.get(d.uuid);
      self.get('nodes').remove(n);
      self.trigger('change');
    });

    stream.bind("name-result", function (d) {
      self.set({stats: d.value});
    });

    stream.bind('name-current-job', function (data) {
      self.set({currentJob: data});
    });

    stream.bind('name-job-start', function (data) {
      self.trigger("new-start");
      self.set({currentJob: data});
    });

    stream.bind('name-job-stop', function (data) {
      var j = self.get('currentJob');
      self.trigger("new-stop", j);
      self.set({currentJob: null});
      if (j) {
        setIndicatedJob(j.uuid);
      }
    });
  },
  timeSeriesFor: function (field) {
    if (! this.get("stats")) return;
    var raw = this.get('stats')['time-slices'];
    var data = [];
    for (time in raw) {
      data.push({time: time, value: raw[time][field] || 0});
    }
    data.sort(function (a,b) {
     return a.time - b.time;
    });
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
    this.$titleInput = $('#title');
    this.$limitInput = $('#limit');
  },
  events: {
    "click #start-ctl": "start",
    "click #stop-ctl":  "stop",
    "change": "formChange"
  },
  formChange: function(e) {
    engRouter.navigate('', {trigger: true});
    engRouter.benchmarker.set({currentJob: null});
    this.render();  
  },
  start: function (e) {
    engRouter.navigate("", {trigger: true});
    var params = {};
    var self = this;


    params.concurrency = parseInt(this.$concInput.val(), 10);
    params.limit = parseInt(this.$limitInput.val(), 10);
    params.timeout = $('#timeout').val();
    params['_title'] = this.$titleInput.val();
    params['keep-alive'] = this.$keepAliveInput.is(":checked") + "";
    params['formula-name'] = 'http-benchmark';
    params['_stream'] = 'false';
    
    if (!params.concurrency || params.concurrency < 1) {
      alert("Concurrency must be a positive integer!");
      return;
    }
    if (!params.limit || params.limit < 1) {
      alert("Limit must be a positive integer!");
      return;
    }

    if (this.type() == "url") {
      params.url = $.trim(this.$el.find('#url').val());      
      params.method = $('#method').val();

      if (!params.url || params.url.length < 3) {
        alert("Could not start benchmark, no URL specified!");
        return;
      }
    } else {
      var c = $('#markov-corpus', this.el).val();
      var lines = _.map(c.split(/\n/), function (l) {return $.trim(l);});
      var filtered = _.filter(lines, function (l) { return !(l == "" || l == "\n");});
      params['markov-corpus'] = _.map(filtered,
                                      function (s) {
                                          var a = $.trim(s).split(/[ \t]+/);
                                          if (a.length == 1) {
                                            return {method: "get", url: a[0]};
                                          } else {
                                            return {method: a[0], url: a[1]};
                                          }
                                      });
    }
    
    this.disableInputs();
    this.model.start(params, null, function (e) {
                       self.renderStartable();
                     });
  },
  stop: function (e) {
    this.model.stop();
  },
  render: function () {
    var curJob = this.model.get('currentJob');
    if (!curJob || (curJob && curJob["ended-at"] != null)) {
      this.renderStartable();
      if (this.$urlInput.val() === '') {
        this.$urlInput.val('http://' + location.host + '/test-responses/delay/15');
      }
    } else {
      this.renderStoppable();
    }

    if (curJob) {
      var params = curJob.params;
      this.$urlInput.val(params.url);
      this.$concInput.val(params.concurrency);
      this.$limitInput.val(params.limit);
      this.$timeoutInput.val(params.timeout);
      this.$titleInput.val(curJob["title"]);
      
      if (params["keep-alive"] === "true") {
        this.$keepAliveInput.prop("checked", true);
      } else {
        this.$keepAliveInput.prop("checked", false);
      }

      this.$reqsInput.val(params.limit);
    }

    if (params) {
      $('#method option[value=' + params.method + ']').attr('selected',true);
    } else {
      $('#method option[value=get]').attr('selected',true);        
    }
    
    if (params ? !params['markov-corpus'] : this.type() === 'url') {
      $('option#type-url', this.el).attr('selected',true);
      $('#url', this.el).show();
      if (params)  $('#url', this.el).val(params.url);
      $('#method', this.el).show();
      $('#markov-corpus', this.el).hide();
      $('#markov-corpus', this.el).text('');
      $('#markov-help', this.el).hide();
    } else {
      $('option#type-markov', this.el).attr('selected',true);
      $('#url', this.el).hide();
      $('#method', this.el).hide();
      $('#markov-corpus', this.el).show();
      if (params && params['markov-corpus']) {
        $('#markov-corpus', this.el).text(_.map(params['markov-corpus'], function (u) { return u.method + " " + u.url;}).join("\n"));          
      } else {
        $('#markov-corpus', this.el).text('');          
      }

      $('#markov-help', this.el).show();
    }
  },
  type: function () {
    return $('#type', this.el).val();
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
    };
  },
  render: function () {
    var res = this.renderElements;
    var curJob = this.model.get('currentJob');

    var nodes = this.model.get('nodes');
    res.nodes.text(nodes ? nodes.length : (curJob && curJob["node-count"]));
    
    var stats = this.model.get('stats');
    if (stats) {
      res.completed.text(stats['runs-total']);
      res.succeeded.text(stats['runs-succeeded']);
      res.failed.text(stats['runs-failed']);
      res.runtime.text(formatMillis(stats['runtime']));
      res.walltime.text(formatMillis(stats['walltime']));

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
    
    if (! this.model.get("stats") || this.model.get('stats')['percentiles'] < 100) return;
    
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
         text(function (d, i) {
                  return d > 1000 ? sprintf("%0.2f",d / 1000) + 's' : d + 'ms';
         } );
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
  events: {
    "mouseover rect": "showTip",
    "mouseout rect": "clearTip"
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
      attr("height", this.h + 30);
    
    chart.append("line").
      attr("x1", 0).
      attr("x2", this.w).
      attr("y1", this.h - .5).
      attr("y2", this.h - .5).
      style("stroke", "#000");
    
    this.chart = chart;
  },
  render: function () {
    if (!this.model.get('currentJob')) return;
    var self = this;
    
    if (!this.field) return;
    var times = this.model.timeSeriesFor(this.field);
    if (!times) { return; };

    var max = this.model.maxInTimeSeries(times);
    
    this.setYScale(max);
    this.setXScale(times.length);

    var rect = this.chart.selectAll("rect").
      data(times, function(d) { return d.time; });

    var cjStart = self.model.get('currentJob')['started-at'];
    rect.enter().
      append("rect").
      attr("width", 3).
      attr("x", function(d, i) { return self.xScale(i) - .5; }).
      attr("y", function(d) { return  self.h - self.yScale(d.value);  }).
      attr("data-val", function (d) { return (d.value); } ).
      attr("data-elapsed", function (d) { return d.time - cjStart; } ).
      attr("height", function(d) { return self.yScale(d.value); });

    rect.
      attr("x", function(d, i) { return self.xScale(i) - .5; }).
      attr("y", function(d) { return self.h - self.yScale(d.value);  }).
      attr("data-val", function (d) { return (d.value); } ).
      attr("data-side", function (d,i) { return i > (times.length / 2) ? "r" : "l";  }).
      attr("height", function(d) { return  self.yScale(d.value); });

    rect.exit().remove();
    
    return;
  },
  showTip: function (e) {
    var elem = $(e.currentTarget);
    var tip = this.chart.append('text');
    var offset = elem.data('side') == 'l' ? 0 : -130;
    tip.text(elem.data('val') + ' reqs @ ' + formatMillis(elem.data('elapsed')));
    tip.attr('class', 'tip');
    tip.attr('x', parseInt(elem.attr('x'),0) + offset);
    tip.attr('y', this.h + 20);
    
    tip.attr('fill', "black");
  },
  clearTip: function (e) {
    this.chart.selectAll('.tip').remove();
  }
});

Jobs = Backbone.Collection.extend({
  model: Job,
  url: function () { return "/jobs?page=" + this.page; },
  page: 1,
  fetchPage: function(pnum,opts,cb) {
    this.page = pnum;
    return this.fetch(opts);
  }
});

JobBrowser = Backbone.View.extend({
  visible: false,
  events: {
    'click .tab-grip': 'toggle',
    'click .next': 'next',
    'click .prev': 'prev',
    'click tbody tr': 'select'
  },
  jobsListTmpl: _.template("<table class='jobs'>"
                           + "<thead>"
                           + "<th class='timestamp'>Date</th>"
                           + "<th>Title</th>"
                           + "<th>URL</th>"
                           + "<th class='limit stat'>Limit</th>"
                           + "<th class='nodes stat'>Nodes</th>"
                           + "<th class='conc stat'>Conc.</th>"
                           + "<th class='reqs stat'>reqs/sec</th>"
                           + "<th class='med-resp stat'>med resp.</th>"
                           + "</thead><tbody>"
                + "<% _.each(jobs, function (job) { %>"
                + "<tr data-uuid='<%= job.uuid %>'>"
                + "<td class='timestamp'><%= formatTimestamp(job['started-at'] / 1000) %> </td>"
                + "<td class='title'><div>"
                + "<%= job.title || \"Untitled\" %>"
                + "</div></td>"
                + "<td class='url'><div><%= job.params['markov-corpus'] ? 'Markov URL List' : job.params.url %></div></td>"
                + "<td class='limit stat'><%= job.params.limit %></td>"
                + "<td class='nodes stat'><%= job['node-count'] %></td>"
                + "<td class='conc stat'><%= job.params.concurrency %></td>"
                + "<td class='reqs stat'><%= job['last-result'] ? sprintf('%d', job['last-result']['total-runs-per-second']) : 'N/A'  %></td>"
                + "<td class='med-resp stat'>"
                + "<%= job['last-result'] && job['last-result']['percentiles'] && job['last-result']['percentiles'][49] && job['last-result']['percentiles'][49].median %>"
                + "</td>"
                + "</tr>"
                + "<% });  %></tbody><table>"),
  initialize: function (opts) {
    var self = this;
    this.benchmarker = opts.benchmarker;
    this.jobs = new Jobs();

    this.fetchOpts = {
      success: function (data) {
        self.checkPagination.call(self);
        self.render.call(self, data);
      },
      error: function (e) {
        console.log("Could not fetch jobs!", e);
      }
    };

    /* Highlight the current job in the list */
    this.benchmarker.on("change", function () {
      var cj = self.benchmarker.get('currentJob');
      if (cj) {
          setIndicatedJob(cj.uuid);
      }
    });
   
    var reloadIfFirst = function (curOrLastJob) {
      if (self.jobs.page === 1) {
        var origSuccCb = self.fetchOpts.success;
        var fOpts = _.extend(self.fetchOpts, {
                   success: function (data) {
                       origSuccCb(data);
                       if (curOrLastJob) setIndicatedJob(curOrLastJob.uuid);
                   }  
                 });
        self.jobs.fetchPage(1, fOpts);
      }
    };
    this.benchmarker.on('new-start', reloadIfFirst);
    this.benchmarker.on('new-stop', reloadIfFirst);

    this.jobs.on("reset", function () { self.render.call(self); });

    this.jobs.fetchPage(1, this.fetchopts);
  },  
  render: function () {
    var self = this;
    $('.page-num', self.el).text(self.jobs.page);
    $('#job-list-cont').html(
      self.jobsListTmpl({jobs: self.jobs.toJSON()})
    );
    self.checkPagination();
  },
  toggle: function (e) {
    if (this.visible) {
      $(this.el).removeClass('visible');
      this.visible = false;
    } else {
      $(this.el).addClass('visible');
      this.visible = true;
    }
  },
  next: function (e) {
    e.preventDefault();
    this.jobs.fetchPage(this.jobs.page+1,this.fetchOpts);
  },
  prev: function (e) {
    e.preventDefault();
    this.jobs.fetchPage(this.jobs.page-1,this.fetchOpts);
  },
  checkPagination: function () {
      if (this.jobs.page <= 1) {
        $('.prev', self.el).hide();
      } else {
        $('.prev', self.el).show();          
      }

      if (this.jobs.length === 0) {
        $('.next', self.el).hide();
      } else {
        $('.next', self.el).show();          
      }
  },
  select: function (e) {
    var uuid = $(e.currentTarget).data('uuid');
    window.engRouter.navigate("#jobs/" + uuid, {trigger: true});
  }
});

EngulfRouter = Backbone.Router.extend({
  routes: {
    "": "river",
    "river": "river",
    "jobs/:uuid": "job"
  },
  initialize: function () {
    this.benchmarker = new Benchmarker();
    
    this.jobBrowser = new JobBrowser({el: $('#job-browser')[0], benchmarker: this.benchmarker});

    this.controlsView = new ControlsView({
     el: $('#controls')[0],
      model: this.benchmarker
    });

    this.statsView = new AggregateStatsView({
      el: $('#stats')[0],
      model: this.benchmarker
    });
   
    this.percentilesView = new PercentilesView({
      el: $('#resp-time-percentiles')[0],
      model: this.benchmarker
    });

    this.throughputTimeAvgSeriesView = new TimeSeriesView({
      el: $('#throughput-time-series')[0],
      model: this.benchmarker,
      field: "total"
    });          

    this.benchmarkStream = new BenchmarkStream('ws://' + location.host + '/river');
  },
  river: function () {
    $('.status.live').show();      
    $('.status.playback').hide();
    $('.cur-job-related').removeClass('cur-job-related');
    this.benchmarker.set({currentJob: null});
    this.benchmarker.bindToStream(this.benchmarkStream);
    this.consoleView  = new ConsoleView({el: $('#console')});
    this.consoleView.logEvents(this.benchmarkStream, 'jsonData');
  },
  job: function(uuid) {
    $('.status.live').hide();      
    $('.status.playback').show();
    var self = this;
    if (this.benchmarkStream) {
      this.benchmarkStream.unbind();        
    }

    $.getJSON("/jobs/" + uuid, 
              function (job) {
                self.benchmarker.set({currentJob: job});
                self.benchmarker.set({stats: job["last-result"]});
              });
  }
});

$(function () {
  window.engRouter = new EngulfRouter();
  if (document.location.hash == "") {
    engRouter.navigate("", {trigger: true});
  }
  Backbone.history.start();
});
