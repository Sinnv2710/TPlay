#!/bin/zsh
# usage: ui-text.sh <serial> -> prints visible texts of the current screen (no screenshot needed)
adb -s "$1" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
adb -s "$1" shell cat /sdcard/ui.xml | python3 -I -c "
import re,sys
s=sys.stdin.read()
print(' | '.join(t for t in re.findall(r'text=\"([^\"]+)\"',s) if t))"
