#!/bin/bash
# Reduce fin to 7s from default 60
echo 7 | sudo tee /proc/sys/net/ipv4/tcp_fin_timeout

INST_DIR=/home/ubuntu
curl -L http://engulf-project.org/latest.jar > $INST_DIR/engulf.jar
curl http://169.254.169.254/latest/user-data | head -n1 > $INST_DIR/engulf-opts
ENGULF_OPTS=`cat $INST_DIR/engulf-opts`
MEMTOT=`free -m | grep '^Mem: ' | awk '{print $2}'`
MEMUNIT=m
MEMMIN=`expr $MEMTOT - 400`$MEMUNIT
MEMMAX=`expr $MEMTOT - 200`$MEMUNIT
CMD="java -Xms$MEMMIN -Xmx$MEMMAX -jar $INST_DIR/engulf.jar --http-port 8080 $ENGULF_OPTS"
echo Will run: $CMD
$CMD
