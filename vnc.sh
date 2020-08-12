#!/bin/bash

set -x

[[ -z $(which vncviewer) ]] && echo "make sure to have vncviewer installed - can be downloaded here: https://www.realvnc.com/en/connect/download/viewer/" && exit 1
[[ -z $(which vncpasswd) ]] && echo "make sure to have package 'tigervnc-common' installed" && exit 1

VNC_PORTS=$(docker ps | grep -oP '([[:digit:]]+)(?=\->5900)')
declare -a children
for p in $VNC_PORTS
do
	(vncviewer -passwd <(vncpasswd -f <<<"secret") 0.0.0.0:$p) &
	children+=($!)
done

quit_children() { 
  echo "Caught SIGTERM signal!" 
  kill -TERM "$children" 2>/dev/null
}

trap quit_children SIGTERM

for pid in "${children[@]}"; do wait $pid; done
