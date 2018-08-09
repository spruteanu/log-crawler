@echo off
call set-elk-ver.bat

@start cmd /c call elasticsearch-%elk_ver%/bin/elasticsearch.bat
@start cmd /c call kibana-%elk_ver%-windows-x86/bin/kibana.bat
