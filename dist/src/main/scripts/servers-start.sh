#!/bin/sh
set-elk-ver.sh

nohup ./elasticsearch-${elk_ver}/bin/elasticsearch.sh &
nohup ./kibana-${elk_ver}-windows-x86/bin/kibana.sh &
