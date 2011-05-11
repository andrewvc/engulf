# parbench

Visualization tool for webserver concurrency

![Screenshot](https://github.com/downloads/andrewvc/parbench/parbench-ss.png)

## Usage

  Download the [JAR](https://github.com/downloads/andrewvc/parbench/parbench-1.0.0-SNAPSHOT-standalone.jar)
  
  Then run:
    
    # Runs parbench with 50 workers given 100 requests each targeting localhost:9000
    java -jar parbench-1.0.0-SNAPSHOT-standalone.jar http://localhost:9000

    # Full Usage: java -jar parbench.jar -h
    Usage: [-k,-c NUM_WORKERS,-r NUM_REQUESTS] http://example.net 
    Options
      --cli-only, -k           Command Line Only                           
      --concurrency, -c <arg>  Number of Workers              [default 50] 
      --requests, -r <arg>     Number of requests per worker  [default 100]

  
  Each horizontal line represents a worker thread. Each square represents an HTTP request.

  Square colors:

  * Light-gray: pending/future requests
  * Dark-gray:  200/OK
  * Yellow:     Incomplete requests
  * Red:        Complete, but not 200/OK requests

## License

Copyright (C) 2011 Andrew Cholakian

Distributed under the MIT Licensee, see LICENSE for details
