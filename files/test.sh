#!/bin/bash
echo ""
echo "==========### Just for test run shell script ###----------"
HN=$(cat /proc/sys/kernel/hostname )
echo "Hostname: $HN"

echo "List of root file system:"
ls / -ls

echo "Disk free:"
df
echo '=========THE END==========='
echo ""

