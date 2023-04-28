#!/bin/bash
echo "==========### Just for test run shell script ###----------"
HN=$(cat /proc/sys/kernel/hostname )
echo "Hostname: $HN"

pwd
ls / -ls
df
echo '=========THE END==========='

