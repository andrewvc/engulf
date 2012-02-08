# parbench

Visualization tool for webserver concurrency, written in Clojure and Javascript.

![Screenshot](https://img.skitch.com/20120131-ri4ru1rqkhtdpgwne4ps47mmis.jpg)

## Usage

1. You'll need leiningen for now. Clone the repo, then 'lein run'

# Todo:

* RESTful interface
* Cleaned up javascript
* Distributed workers
* Extraction of HTTP task distribution to a separate jar

## Hacking

Parbench is a work in progresss and is rough around the edges in a number of places. Contributions are greatly appreciated, if you have any questions about contributing, just hit the message button on my github profile.

## Legacy

There is a legacy version of parbench available on the downloads page and the `legacy` branch that runs quite differently, and looks like this:

![Screenshot](https://github.com/downloads/andrewvc/parbench/parbench-ss.png)

## Thanks!
I'd like to thank YourKit for providing this project with their Java profiler (which works excellently with Clojure).
It's great at spotting performance issues. More info below:

YourKit is kindly supporting open source projects with its full-featured Java Profiler.
YourKit, LLC is the creator of innovative and intelligent tools for profiling
Java and .NET applications. Take a look at YourKit's leading software products:
[YourKit Java Profiler](http://www.yourkit.com/java/profiler/index.jsp") and
[YourKit .NET Profiler](http://www.yourkit.com/.net/profiler/index.jsp"

## License

Copyright (C) 2011 and 2012 Andrew Cholakian

Distributed under the MIT Licensee, see LICENSE for details
