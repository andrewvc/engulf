# engulf (formerly parbench)

Visualization tool for webserver concurrency, written in Clojure and Javascript.

![Screenshot](https://img.skitch.com/20120221-eqssdyky47c8atq74tnen866xy.png)

## Get up and running

1. This only runs in google chrome at the moment
1. Download the jar from the [downloads page](https://github.com/andrewvc/engulf/downloads)
1. Run it like so `PORT=4000 java -jar engulf-VERSION.jar`
1. Visit http://localhost:4000 in your browser to use the GUI

# Todo:

* RESTful interface (partially complete)
* Distributed workers (in progress, on 'net' branch)

## Hacking

Engulf is a work in progresss and is rough around the edges in a number of places. Contributions are greatly appreciated, if you have any questions about contributing, just hit the message button on my github profile.

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
