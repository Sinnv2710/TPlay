#!/bin/zsh
# usage: perf-check.sh <serial> -> cold start, memory/leak loop over the player, jank, thermal
D=$1; P=app.tplay
echo "## Cold start (am start -W, 5 runs)"
for i in 1 2 3 4 5; do
  adb -s $D shell am force-stop $P; sleep 1
  adb -s $D shell am start -W -n $P/.MainActivity | grep -E "TotalTime" | tr -d '\r'
done
adb -s $D shell am start -n $P/.MainActivity >/dev/null; sleep 3
pss(){ adb -s $D shell dumpsys meminfo $P | grep -E "TOTAL PSS|TOTAL:" | head -1 | awk '{print $3}'; }
objs(){ adb -s $D shell dumpsys meminfo $P | grep -E "Activities:" | awk '{print $2}'; }
echo "## Memory before player loop: PSS=$(pss) KB, activities=$(objs)"
adb -s $D shell dumpsys gfxinfo $P reset >/dev/null
for i in $(seq 1 20); do
  scripts/tap-text.sh $D Library >/dev/null; sleep 1
  scripts/tap-text.sh $D "Big Buck Bunny" >/dev/null; sleep 3
  adb -s $D shell input keyevent KEYCODE_BACK; sleep 1
done
adb -s $D shell am force-stop com.android.chrome >/dev/null 2>&1
sleep 2
echo "## Memory after 20 open/close cycles: PSS=$(pss) KB, activities=$(objs)"
echo "## Jank (gfxinfo)"
adb -s $D shell dumpsys gfxinfo $P | grep -E "Total frames rendered|Janky frames|90th|95th|99th" | head -5
echo "## Thermal"
adb -s $D shell dumpsys thermalservice | grep -E "Thermal Status|Cached temperatures" -A2 | head -6
