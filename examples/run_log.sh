#!/bin/sh
curl -XPOST http://localhost:4000/jobs/current -H 'Content-Type: application/json' -d '{
"title": "A Test of a relay", "_stream":"true",
"params": {"formula-name":"http-benchmark", "concurrency":5, "limit":300,
    "target": {"type": "replay", "url": "/Users/andrewcholakian/projects/engulf/examples/log"}}}'
