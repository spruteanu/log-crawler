@echo off
call set-elk-ver.bat

pushd metricbeat-%elk_ver%-windows-x86_64
cmd /c metricbeat.exe
popd

pushd filebeat-%elk_ver%-windows-x86_64
cmd /c filebeat.exe
popd

@echo PacketBeat requires WpCap sniffer installed(wpcap.dll)
pushd packetbeat-%elk_ver%-windows-x86_64
cmd /c packetbeat.exe
popd
