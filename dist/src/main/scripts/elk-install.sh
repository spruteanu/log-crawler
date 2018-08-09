#!/bin/sh
set-elk-ver.sh

wget https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-${elk_ver}.tar.gz
echo Unpacking Elastic Search...
tar xvf ./elasticsearch-${elk_ver}.tar.gz -C ./.. >> install.log
rm ./elasticsearch-${elk_ver}.tar.gz >> install.log

wget https://artifacts.elastic.co/downloads/kibana/kibana-${elk_ver}-linux-x86_64.tar.gz
echo Unpacking Kibana...
tar xvf ./kibana-${elk_ver}-linux-x86_64.tar.gz -C ./.. >> install.log
rm ./kibana-${elk_ver}-linux-x86_64.tar.gz >> install.log

wget https://artifacts.elastic.co/downloads/logstash/logstash-${elk_ver}.tar.gz
echo Unpacking Logstash...
tar xvf ./logstash-${elk_ver}.tar.gz -C ./.. >> install.log
rm ./logstash-${elk_ver}.tar.gz >> install.log

echo Finished ELK installation, check install.log for details.
