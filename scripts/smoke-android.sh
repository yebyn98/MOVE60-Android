#!/usr/bin/env bash
set -euo pipefail
mkdir -p smoke-debug
dump() { adb logcat -d -v threadtime > smoke-debug/logcat.txt || true; adb shell dumpsys notification --noredact > smoke-debug/notifications.txt || true; adb shell uiautomator dump /sdcard/move60-smoke.xml >/dev/null 2>&1 && adb pull /sdcard/move60-smoke.xml smoke-debug/window.xml >/dev/null || true; }
trap 'if test "$?" -ne 0; then echo "=== Recent Android errors ==="; dump; grep -n -A 18 -B 5 -E "FATAL EXCEPTION|AndroidRuntime|RemoteServiceException|move60.workout" smoke-debug/logcat.txt | tail -n 150 || true; fi' EXIT
adb wait-for-device
adb shell settings put system screen_off_timeout 300000 || true
adb shell input keyevent 82 || true
adb install -r app/build/outputs/apk/debug/app-debug.apk
# Permission popup otherwise hides the app and prevents meaningful UI interaction.
adb shell pm grant com.move60.workout android.permission.POST_NOTIFICATIONS
adb logcat -c
for i in 1 2 3; do
  echo "Cold launch check: $i"
  adb shell am force-stop com.move60.workout
  adb shell am start -W -n com.move60.workout/.MainActivity
  sleep 4
  pid="$(adb shell pidof com.move60.workout | tr -d '\r' || true)"
  if test -z "$pid"; then echo "MOVE60 died on cold launch $i"; exit 1; fi
done
echo "Finding workout start button..."
tapped=0
for try in 1 2 3 4 5 6; do
  adb shell uiautomator dump /sdcard/move60-smoke.xml >/dev/null 2>&1 || true
  adb pull /sdcard/move60-smoke.xml smoke-debug/window.xml >/dev/null 2>&1 || true
  if python3 - <<'PY'
import xml.etree.ElementTree as ET, pathlib,re,subprocess
p=pathlib.Path("smoke-debug/window.xml")
if not p.is_file(): raise SystemExit(1)
root=ET.parse(p).getroot()
for n in root.iter("node"):
    txt=n.attrib.get("text","")
    if "운동 시작" in txt:
        coords=list(map(int,re.findall(r"\d+",n.attrib.get("bounds",""))))
        if len(coords)==4:
            x=(coords[0]+coords[2])//2
            y=(coords[1]+coords[3])//2
            subprocess.run(["adb","shell","input","tap",str(x),str(y)],check=True)
            print("Tapped "+txt)
            raise SystemExit(0)
raise SystemExit(1)
PY
  then tapped=1; break; fi
  adb shell input swipe 550 1580 550 520 350
done
if test "$tapped" -ne 1; then echo "Could not locate and tap workout start button"; exit 1; fi
sleep 6
pid="$(adb shell pidof com.move60.workout | tr -d '\r' || true)"
if test -z "$pid"; then echo "MOVE60 process died after exercise start"; exit 1; fi
dump
if grep -q 'FATAL EXCEPTION' smoke-debug/logcat.txt; then
  echo "AndroidRuntime fatal error found"
  grep -n -A 30 'FATAL EXCEPTION' smoke-debug/logcat.txt | tail -n 80
  exit 1
fi
if ! grep -q 'com.move60.workout' smoke-debug/notifications.txt; then
  echo "MOVE60 ongoing notification missing from NotificationManager"
  exit 1
fi
echo "Testing app relaunch with persisted active timer..."
adb shell am force-stop com.move60.workout
adb shell am start -W -n com.move60.workout/.MainActivity
sleep 5
pid="$(adb shell pidof com.move60.workout | tr -d '\r' || true)"
if test -z "$pid"; then echo "MOVE60 died on relaunch with active timer"; exit 1; fi
dump
if grep -q 'FATAL EXCEPTION' smoke-debug/logcat.txt; then
  echo "AndroidRuntime fatal error after relaunch"
  grep -n -A 30 'FATAL EXCEPTION' smoke-debug/logcat.txt | tail -n 100
  exit 1
fi
echo "Passed cold launch x3, workout start, ongoing notification and relaunch checks."
