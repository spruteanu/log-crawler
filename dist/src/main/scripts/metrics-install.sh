#!/bin/sh
set-elk-ver.sh

wget https://artifacts.elastic.co/downloads/beats/packetbeat/packetbeat-${elk_ver}-linux-x86_64.tar.gz
echo Unpacking PacketBeat...
tar xvf ./packetbeat-${elk_ver}-linux-x86_64.tar.gz -C ./.. >> install.log
rm ./packetbeat-${elk_ver}-linux-x86_64.tar.gz >> install.log

wget https://artifacts.elastic.co/downloads/beats/metricbeat/metricbeat-${elk_ver}-linux-x86_64.tar.gz
echo Unpacking MetricBeat...
tar xvf ./metricbeat-${elk_ver}-linux-x86_64.tar.gz -C ./.. >> install.log
rm ./metricbeat-${elk_ver}-linux-x86_64.tar.gz >> install.log

wget https://artifacts.elastic.co/downloads/beats/filebeat/filebeat-${elk_ver}-linux-x86_64.tar.gz
echo Unpacking FileBeat...
tar xvf ./filebeat-${elk_ver}-linux-x86_64.tar.gz -C ./.. >> install.log
rm ./filebeat-${elk_ver}-linux-x86_64.tar.gz >> install.log

echo Staring Elastic and Kibana to load metrics dashboards.
. ../servers-start.sh
read -p "Press [Enter] to continue once Elastic and Kibana are started."

echo Importing PacketBeat dashboards...
. ../packetbeat-${elk_ver}-linux-x86_64/scripts/import_dashboards.sh >> install.log
echo Importing MetricBeat dashboards...
. ../metricbeat-${elk_ver}-linux-x86_64/scripts/import_dashboards.sh >> install.log
echo Importing FileBeat dashboards...
. ../filebeat-${elk_ver}-linux-x86_64/scripts/import_dashboards.sh >> install.log
