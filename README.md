# engulf

Visualization tool for webserver concurrency, written in Clojure and Javascript.

![Screenshot](https://img.skitch.com/20120221-eqssdyky47c8atq74tnen866xy.png)

## Get up and running

1. This only runs in google chrome at the moment
1. Download the jar from the [downloads page](https://github.com/andrewvc/engulf/downloads)
1. Run it like so `PORT=4000 java -jar engulf-VERSION.jar`
1. Visit http://localhost:4000 in your browser to use the GUI

# Todo:

* Embedded JS Engine
* Reworked UI

## Hacking

Engulf is a work in progresss. The *currently released version is the 2.x branch.*
Master represents the upcoming 3.0 version, which will feature distributed operation,
 saved results, a REST API, and numerous other improvements. It's currently only 70% done
and barely runnable (with no UI). If you're interested in helping out, just ping me!

## Legacy

There is a legacy version of engulf available on the downloads page and the `legacy` branch that runs quite differently.

## Thanks!
I'd like to thank YourKit for providing this project with their Java profiler (which works excellently with Clojure).
It's great at spotting performance issues. More info below:

YourKit is kindly supporting open source projects with its full-featured Java Profiler.
YourKit, LLC is the creator of innovative and intelligent tools for profiling
Java and .NET applications. Take a look at YourKit's leading software products:
[YourKit Java Profiler](http://www.yourkit.com/java/profiler/index.jsp) and
[YourKit .NET Profiler](http://www.yourkit.com/.net/profiler/index.jsp)

## License

Copyright (C) 2011 and 2012 Andrew Cholakian

Distributed under the MIT Licensee, see LICENSE for details
