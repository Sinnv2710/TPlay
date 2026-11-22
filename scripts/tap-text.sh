#!/bin/zsh
# usage: tap-text.sh <serial> <exact text or prefix> -> taps the center of the first matching element
adb -s "$1" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
adb -s "$1" shell cat /sdcard/ui.xml | python3 -I -c "
import re,sys,subprocess
s=sys.stdin.read(); want=sys.argv[2]; dev=sys.argv[1]
items=list(re.finditer(r'(?:text|content-desc)=\"([^\"]*)\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"',s))
hit=next((m for m in items if m.group(1)==want),None) or next((m for m in items if m.group(1).startswith(want)),None)
if hit:
    x=(int(hit.group(2))+int(hit.group(4)))//2; y=(int(hit.group(3))+int(hit.group(5)))//2
    subprocess.run(['adb','-s',dev,'shell','input','tap',str(x),str(y)]); print('tapped',want,x,y)
else: print('NOT FOUND',want)" "$1" "$2"
