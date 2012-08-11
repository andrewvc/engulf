# engulf

Distributed HTTP load tester, written in Clojure and Javascript.

engulf lets you stress-test websites, scaling outwards with multiple worker nodes. engulf is fully self-contained, all you need is java 7 and the jar. It can also run in a standalone configuration.

Your tests can be controlled by either the web interface (seen in the screenshot below), or by the RESTful/Streaming [HTTP API](https://github.com/andrewvc/engulf/wiki/HTTP-API).

Load can be generated in one of two, ways. Either a single URL can be tested, or a sequence of URLs can be given. engulf will build the sequence into a markov chain and follow it in a pattern commensurate with that.

All jobs are stored in a local sqlite database by the master node, and are accessible through the HTTP API (a web interface for past jobs is on the way).

![Screenshot](https://img.skitch.com/20120811-qf81tgw9pg51mnbjnidq4axmgf.png)

## Get up and running

1. This only runs in google chrome at the moment
1. Download the jar from the [downloads page](https://github.com/andrewvc/engulf/downloads)
1. Run it like so `PORT=4000 java -jar engulf-VERSION.jar`
1. Visit http://localhost:4000 in your browser to use the GUI
1. See the wiki page on [usage](https://github.com/andrewvc/engulf/wiki/Usage) for more details.

# Todo:

* Embedded JS Engine
* Browsing past jobs in the UI
* Targetting subsets of nodes

## Hacking

Engulf is a work in progresss. The 3.0.0 branch is currently alpha quality but is rapidly approaching beta.

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
