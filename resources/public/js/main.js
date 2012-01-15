/**
 * Requirements:
 * - jQuery (John Resig, http://www.jquery.com/)
 **/
 // Disable text select plugin
(function($){if($.browser.mozilla){$.fn.disableTextSelect=function(){return this.each(function(){$(this).css({"MozUserSelect":"none"})})};$.fn.enableTextSelect=function(){return this.each(function(){$(this).css({"MozUserSelect":""})})}}else{if($.browser.msie){$.fn.disableTextSelect=function(){return this.each(function(){$(this).bind("selectstart.disableTextSelect",function(){return false})})};$.fn.enableTextSelect=function(){return this.each(function(){$(this).unbind("selectstart.disableTextSelect")})}}else{$.fn.disableTextSelect=function(){return this.each(function(){$(this).bind("mousedown.disableTextSelect",function(){return false})})};$.fn.enableTextSelect=function(){return this.each(function(){$(this).unbind("mousedown.disableTextSelect")})}}}})(jQuery)

// Convert drag stuff to touch stuff for mobile

function touchHandler(event)
{
 var touches = event.changedTouches,
    first = touches[0],
    type = "";

     switch(event.type)
{
    case "touchstart": type = "mousedown"; break;
    case "touchmove":  type="mousemove"; break;
    case "touchend":   type="mouseup"; break;
    default: return;
}
var simulatedEvent = document.createEvent("MouseEvent");
simulatedEvent.initMouseEvent(type, true, true, window, 1,
                          first.screenX, first.screenY,
                          first.clientX, first.clientY, false,
                          false, false, false, 0/*left*/, null);

first.target.dispatchEvent(simulatedEvent);
event.preventDefault();
}

function touchInit()
{
   document.addEventListener("touchstart", touchHandler, true);
   document.addEventListener("touchmove", touchHandler, true);
   document.addEventListener("touchend", touchHandler, true);
   document.addEventListener("touchcancel", touchHandler, true);
}

if (XMLHttpRequest.prototype.sendAsBinary === undefined) {
  XMLHttpRequest.prototype.sendAsBinary = function(string) {
    var bytes = Array.prototype.map.call(string, function(c) {
      return c.charCodeAt(0) & 0xff;
    });
    this.send(new Uint8Array(bytes).buffer);
  };
}

function postCanvasToURL(url, name, fn, canvas, type, callback) {
  var data = canvas.toDataURL(type);
  data = data.replace('data:' + type + ';base64,', '');

  var xhr = new XMLHttpRequest();
  xhr.open('POST', url, true);

  xhr.onreadystatechange = function () {
    if (xhr.readyState === 4) {
      callback(xhr.response);
    }
  };
  var boundary = 'ohaiimaboundary';
  xhr.setRequestHeader(
    'Content-Type', 'multipart/form-data; boundary=' + boundary);
  xhr.sendAsBinary([
    '--' + boundary,
    'Content-Disposition: form-data; name="' + name + '"; filename="' + fn + '"',
    'Content-Type: ' + type,
    '',
    data,
    '--' + boundary + '--'
  ].join('\r\n'));
}

function relMouseCoords(event) {
    // Thanks to: http://stackoverflow.com/questions/55677/how-do-i-get-the-coordinates-of-a-mouse-click-on-a-canvas-element
    var totalOffsetX = 0;
    var totalOffsetY = 0;
    var canvasX = 0;
    var canvasY = 0;
    var currentElement = this;

    do {
        totalOffsetX += currentElement.offsetLeft;
        totalOffsetY += currentElement.offsetTop;
    }
    while (currentElement = currentElement.offsetParent)

    canvasX = event.pageX - totalOffsetX;
    canvasY = event.pageY - totalOffsetY;

    return {
        x: canvasX,
        y: canvasY
    }
}
HTMLCanvasElement.prototype.relMouseCoords = relMouseCoords;

function distance(start, end) {
  return Math.sqrt(Math.pow(end[0] - start[0], 2) + Math.pow(end[1] - start[1],2));
}

function midpoint(start, end) {
  return [
    (start[0] + end[0] / 2),
    (start[1] + end[1] / 2)
  ];
}


function Pen (garden) {
  this.garden = garden
  var ctx = garden.ctx;
   
  this.state = 'up';
  this.rakeState  =  'available';
  this.x = 0;
  this.y = 0;
  this.setCoords = function (coords) {
    this.x = coords.x;
    this.y = coords.y;
  };
  this.startSampler = function () {
    var self = this;
    setInterval(function () {
      if (self.state === 'down') {
        self.rakeState = 'available';
      }
    }, 10);  
  };
  this.lastTinePosition = null;
  this.reset = function () {
    this.state = 'up';
    this.lastTinePositions = null;
  };
  this.brushSize = 30;
  this.tineCount = 5;
}

Pen.prototype.rakeTo = function (endX,endY) {
  var pen = this;
  var ctx = this.garden.ctx;
  var slope = (this.y - endY) / (this.x - endX);
  var pSlope = -(1 / slope); //Perpendicular slope
  var brushSize = this.brushSize;
  var tineCount = 5;
  var brushSpacing = brushSize / tineCount;

  ymod = pSlope <= 1 ? pSlope : 1;
  ymod = ymod >= -1 ? ymod : -1;

  if (slope < 0) {
    ymod = Math.abs(ymod);
  }
 
  xmod = slope <= 1 ? slope : 1;
  xmod = xmod >= -1 ? xmod : -1;
   
  if (slope < 0) {
    xmod = Math.abs(xmod);
  }
  

  // Flip the tines over periodically so as to prevent path crossover
  // during rake flips
  var tinePositions = [];
  var tineIdxs = (slope < 0) ? _.range(0, tineCount) : _.range(tineCount-1, -1, -1);
  _.each(tineIdxs, function(i) {
    var xOffset = (brushSpacing * (i + 1) * xmod) + (-(brushSize * xmod / 2));
    var yOffset = (brushSpacing * (i + 1) * ymod) + (-(brushSize * ymod / 2));
    tinePositions[i] = [endX + xOffset, endY + yOffset];
  });

  // If this is the start, we have no origin
  if (!pen.lastTinePositions) {
    pen.lastTinePositions = tinePositions;
  }

  // Detect rake flips, and when found draw the first iteration linking
  // the tines in reverse

  // TODO: This can be detected smarter
  var justFlipped = (
      distance(pen.lastTinePositions[tineCount-1], tinePositions[0])
      <
      distance(pen.lastTinePositions[0], tinePositions[0]));

  var lastTineIdxMap = justFlipped ? _.range(tineCount-1, -1, -1) : _.range(0, tineCount);
  _.each(tinePositions, function (tinePos,i) {
    var lastTineIdx = lastTineIdxMap[i]
    var lastTinePos = pen.lastTinePositions[lastTineIdx];

    ctx.beginPath();
    ctx.strokeStyle = "#fff";
    ctx.moveTo(lastTinePos[0], lastTinePos[1]);

    // surrounding grains
    _.times(40, function () {
      ctx.fillStyle = 'rgba(80,80,80,' + (Math.random() - 0.65) + ')';
      var x = (lastTinePos[0] + tinePos[0]) / 2;
      var y = (lastTinePos[1] + lastTinePos[1]) / 2;
      var offBy = (Math.random() * 6) - 4;
      var size = Math.random() * 1.7;
      ctx.fillRect(x + offBy, y + offBy, size,size);
    });

    // Inner white
    ctx.lineWidth = (Math.random()*1.7) + 2.0;
    ctx.lineTo(tinePos[0], tinePos[1]);
    ctx.stroke();
  });

  pen.lastTinePositions = tinePositions;

  pen.rakeState = 'unavailable';
  pen.x = endX;
  pen.y = endY;
}

function Garden (selector) {
  var self = this;
  this.$el = $(selector);
  this.el  = this.$el[0];
  this.ctx = this.el.getContext('2d');
  this.pen = new Pen(this);

  this.upload = function () {
    var idata = self.ctx.getImageData(0,0,self.el.width,self.el.height);
    console.log("Data stored, uploading ", idata);
    var fdata = new FormData();
    postCanvasToURL("/upload", "garden-file", "garden.png", this.el, "image/png",
      function (response) {
        window.location = "/gardens/" + response;
      });
  };

  this.dumpSand = function () {
    self.ctx.fillStyle = "#FF0000";
    self.ctx.fillRect(img, 0, 0, self.width, self.height);
    var img = new Image();
    img.onload = function () {
      self.ctx.drawImage(img, 10, 10, img.width, img.height);
    }
    img.src = "/images/empty-sand-dark.png";
  };


  // This generates a random sand pattern, quite slow
  // Generally loading an image of the sand is much faster
  this.generateSand = function () {
    return
    w = this.el.width;
    h = this.el.height;
    
    var grainCount = (w * h) * 2;
    for (var i=0; i < grainCount; i++) {
      var x = Math.random() * w;
      var y = Math.random() * h;
      var size = Math.random() / 1.5;
      this.ctx.fillStyle = "rgba(190,190,190," + (Math.random()) + ")";
      this.ctx.fillRect(x,y, size, size); 
    }
  }
 //



  this.$el.bind('dragover', function (e) {
    e.preventDefault();
  });

  this.$el.droppable({
    drop: function (e,ui) {
      var relTop  = ui.helper.offset().top  - garden.$el.offset().top;
      var relLeft = ui.helper.offset().left - garden.$el.offset().left;
      var rockCanvas = ui.helper[0];
      garden.ctx.moveTo(0,0);
      garden.ctx.drawImage(rockCanvas,relLeft,relTop,rockCanvas.width,rockCanvas.height);
      ui.helper.remove();
    }
  });

  this.$el.mousedown(function (e) {
    var coords = garden.el.relMouseCoords(e);
    garden.pen.state = 'down';
    garden.pen.setCoords(coords);
    return false;
  });

  this.$el.bind('selectStart', function (e) {
    e.preventDefault();
    return false;
  });

  this.$el.disableTextSelect();

  this.$el.mouseup(function (e) {
    garden.pen.reset();
  });

  this.$el.mouseout(function (e) {
    garden.pen.reset();
  });

  this.$el.mousemove(function (e) {
    if (garden.pen.state === 'down') {
      var coords = garden.el.relMouseCoords(e);
      if (distance([garden.pen.x, garden.pen.y], [coords.x, coords.y]) > 5) {
        garden.pen.rakeTo(coords.x, coords.y);
      }
    }
  });
}

function Rock (url, width) {
  var self = this;
  this.img = $('<img>')[0];

  this.$canvas = $('<canvas></canvas>');
  this.canvas  = this.$canvas[0];

  this.$canvas.addClass('rock');

  this.img.onload = function () {
    var aspect = self.img.height / self.img.width;
    self.canvas.width  = width;
    self.canvas.height = width * aspect;
    self.ctx           = self.canvas.getContext('2d');
    self.ctx.drawImage(self.img, 0, 0, self.canvas.width, self.canvas.height);
    self.$canvas.draggable();
     
    if (self.onReady) {
      self.onReady();
    }
  };

  this.img.src = url;

  this.onReady = function () {};
}




$(function () {
  if (!$('#garden')[0]) {
    return;
  }
  touchInit();
  window.garden = new Garden('#garden');
  garden.dumpSand();
  garden.pen.startSampler();
  garden.rocks = [];
  // Filename of the rock + its width
  // it will be proportionally resized
  var rocksMeta = [
    ['0.png', 130],
    ['1.png', 80],
    ['2.png', 60],
    ['3.png', 120],
  ];
  _.each(rocksMeta, function (rockMeta) {
    var fn    = rockMeta[0];
    var width = rockMeta[1];
    var rock  = new Rock('/images/rocks/' + fn, width);
    garden.rocks.push(rock);
    rock.onReady = function () {
      $('#rock-caddy').append(rock.canvas);
    };
  });

  $('#complete').click(function (e) {
    if (confirm("This will save this garden permanently, you will no longer be able to edit it. Are you sure?")) {
        garden.upload();
        $('#main').text("Uploading...");
     }
     return false;
  });
});
