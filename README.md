# parbench

# NOTE: There's a full-rewrite underway in the 'web_interface' branch. This will likely be split off into a separate repo soon (hopefully within the next day or two).


Visualization tool for webserver concurrency

![Screenshot](https://github.com/downloads/andrewvc/parbench/parbench-ss.png)

## Usage

  Download the JAR from the [downloads page](https://github.com/andrewvc/parbench/downloads).
  
  Then run:
    
    # Runs parbench with 50 workers given 100 requests each targeting localhost:9000
    java -jar parbench.jar -u http://localhost:9000

    # Full Usage: java -jar parbench.jar -h
    Usage: [OPTIONS] -u http://example.net
    Options
      --cli-only, -k           Command Line Only
      --concurrency, -c <arg>  Number of Workers              [default 100]
      --requests, -r <arg>     Number of requests per worker  [default 200]
      --scale, -s <arg>        Pixel Size of GUI Squares      [default 2]
      --url, -u <arg>          URL to benchmark

  Each horizontal line represents a worker thread. Each square represents an HTTP request.

  Square colors:

  * Light-gray: Scheduled, Not yet sent
  * Yellow:     Sent, Waiting for response
  * Dark-gray:  Complete, HTTP Status 200 - 299
  * Blue:       Complete, HTTP Status 300 - 399
  * White:      Complete, HTTP Status 400 - 499
  * Red:        Complete, HTTP Status 500 - 599
  * Black:      Internal Error, could not complete request

## Thanks!

YourKit is kindly supporting open source projects with its full-featured Java Profiler.

YourKit, LLC is the creator of innovative and intelligent tools for profiling

Java and .NET applications. Take a look at YourKit's leading software products:

[YourKit Java Profiler](http://www.yourkit.com/java/profiler/index.jsp") and

[YourKit .NET Profiler](http://www.yourkit.com/.net/profiler/index.jsp")

## License

Copyright (C) 2011 Andrew Cholakian

Distributed under the MIT Licensee, see LICENSE for details
