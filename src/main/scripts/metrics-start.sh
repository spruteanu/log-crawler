#!/bin/sh
./set-elk-ver.sh

. nohup ./metricbeat-${elk_ver}-linux-x86_64/scripts/metricbeat.sh &
. nohup ./filebeat-${elk_ver}-linux-x86_64/scripts/filebeat.sh &
echo Packet requires configured sniffer
. nohup ./packetbeat-${elk_ver}-linux-x86_64/scripts/packetbeat.sh &
