@echo off
call set-elk-ver.bat

wget https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-%elk_ver%.zip
echo Unpacking Elastic Search...
unzip -o elasticsearch-%elk_ver%.zip -d .. >> install.log
del elasticsearch-%elk_ver%.zip >> install.log

wget https://artifacts.elastic.co/downloads/kibana/kibana-%elk_ver%-windows-x86.zip
echo Unpacking Kibana...
unzip -o kibana-%elk_ver%-windows-x86.zip -d .. >> install.log
del kibana-%elk_ver%-windows-x86.zip >> install.log

wget https://artifacts.elastic.co/downloads/logstash/logstash-%elk_ver%.zip
echo Unpacking Logstash...
unzip -o logstash-%elk_ver%.zip -d .. >> install.log
del logstash-%elk_ver%.zip >> install.log

echo Finished ELK installation, check install.log for details.
