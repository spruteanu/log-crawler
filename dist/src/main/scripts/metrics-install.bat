@echo off
call set-elk-ver.bat

wget https://artifacts.elastic.co/downloads/beats/packetbeat/packetbeat-%elk_ver%-windows-x86_64.zip
echo Unpacking PacketBeat...
unzip -o packetbeat-%elk_ver%-windows-x86_64.zip -d .. >> install.log
del packetbeat-%elk_ver%-windows-x86_64.zip >> install.log

wget https://artifacts.elastic.co/downloads/beats/metricbeat/metricbeat-%elk_ver%-windows-x86_64.zip
echo Unpacking MetricBeat...
unzip -o metricbeat-%elk_ver%-windows-x86_64.zip -d .. >> install.log
del metricbeat-%elk_ver%-windows-x86_64.zip >> install.log

wget https://artifacts.elastic.co/downloads/beats/filebeat/filebeat-%elk_ver%-windows-x86_64.zip
echo Unpacking FileBeat...
unzip -o filebeat-%elk_ver%-windows-x86_64.zip -d .. >> install.log
del filebeat-%elk_ver%-windows-x86_64.zip >> install.log

echo Staring Elastic and Kibana to load metrics dashboards.
@start cmd /c call ..\elasticsearch-%elk_ver%/bin/elasticsearch.bat
@start cmd /c call ..\kibana-%elk_ver%-windows-x86/bin/kibana.bat
echo Continue once Elastic and Kibana are started.
pause

echo Importing PacketBeat dashboards...
cmd /c ..\packetbeat-%elk_ver%-windows-x86_64\scripts\import_dashboards.exe >> install.log
echo Importing MetricBeat dashboards...
cmd /c ..\metricbeat-%elk_ver%-windows-x86_64\scripts\import_dashboards.exe >> install.log
echo Importing FileBeat dashboards...
cmd /c ..\filebeat-%elk_ver%-windows-x86_64\scripts\import_dashboards.exe >> install.log
