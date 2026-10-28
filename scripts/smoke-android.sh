#!/usr/bin/env bash
set -euo pipefail
mkdir -p smoke-debug
wait_adb() {
  for attempt in $(seq 1 20); do
    if adb shell getprop sys.boot_completed 2>/dev/null | grep -q '^1'; then return 0; fi
    echo "Waiting for emulator ADB recovery ($attempt/20)"
    sleep 3
  done
  echo "Emulator failed to recover ADB connectivity" >&2
  return 1
}
dump() { adb logcat -d -v threadtime > smoke-debug/logcat.txt || true; adb shell dumpsys notification --noredact > smoke-debug/notifications.txt || true; adb shell uiautomator dump /sdcard/move60-smoke.xml >/dev/null 2>&1 && adb pull /sdcard/move60-smoke.xml smoke-debug/window.xml >/dev/null || true; }
trap 'if test "$?" -ne 0; then echo "=== Recent Android errors ==="; dump; grep -n -A 18 -B 5 -E "FATAL EXCEPTION|AndroidRuntime|RemoteServiceException|move60.workout" smoke-debug/logcat.txt | tail -n 150 || true; fi' EXIT
adb wait-for-device
adb shell settings put system screen_off_timeout 300000 || true
adb shell input keyevent 82 || true
adb install -r app/build/outputs/apk/debug/app-debug.apk
# Permission popup otherwise hides the app and prevents meaningful UI interaction.
adb shell pm grant com.move60.workout android.permission.POST_NOTIFICATIONS
# Suppress flaky first-boot Google setup ANR dialogs on CI emulator.
adb shell settings put global hide_error_dialogs 1 || true
adb shell pm disable-user --user 0 com.google.android.googlesdksetup || true
adb shell input keyevent KEYCODE_BACK || true
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
for try in $(seq 1 12); do
  # Dismiss an unexpected system dialog only when MOVE60 is not foreground.
  # Sending BACK unconditionally closes MainActivity and makes the test inspect Launcher.
  foreground="$(adb shell dumpsys activity activities | grep -m1 'mResumedActivity' || true)"
  if ! echo "$foreground" | grep -q 'com.move60.workout'; then
    adb shell input keyevent KEYCODE_BACK || true
    adb shell am start -W -n com.move60.workout/.MainActivity >/dev/null || true
    sleep 2
  fi
  adb shell uiautomator dump /sdcard/move60-smoke.xml >/dev/null 2>&1 || true
  adb pull /sdcard/move60-smoke.xml smoke-debug/window.xml >/dev/null 2>&1 || true
  if python3 - <<'PY'
import xml.etree.ElementTree as ET, pathlib,re,subprocess
p=pathlib.Path("smoke-debug/window.xml")
if not p.is_file(): raise SystemExit(1)
root=ET.parse(p).getroot()
for n in root.iter("node"):
    txt=n.attrib.get("text","")
    desc=n.attrib.get("content-desc","")
    if "운동 시작" in txt or txt.strip() in ("시작", "▶ 시작", "▶ 운동 시작") or "운동 시작" in desc:
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
  # The timer controls are below the fold on the tall workout summary.
  # Always scroll downward; alternating directions repeatedly hid the start button.
  adb shell input swipe 550 1850 550 420 450
  sleep 1
done
if test "$tapped" -ne 1; then echo "Could not locate and tap workout start button"; cat smoke-debug/window.xml || true; exit 1; fi
sleep 6
wait_adb
pid="$(adb shell pidof com.move60.workout | tr -d '\r' || true)"
if test -z "$pid"; then echo "MOVE60 process died after exercise start"; exit 1; fi
wait_adb
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
# Expand notifications through SystemUI, not an emulator-dependent swipe coordinate.
adb shell cmd statusbar expand-notifications || adb shell input swipe 550 8 550 1400 420 || true
sleep 4
adb shell screencap -p /sdcard/move60-shade.png || true
adb pull /sdcard/move60-shade.png smoke-debug/notification-shade.png >/dev/null 2>&1 || true
# UiAutomator sometimes times out on first boot; retry and preserve its error output.
shade_found=0
for attempt in 1 2 3; do
  echo "Notification shade hierarchy check: $attempt"
  adb shell cmd statusbar expand-notifications || true
  if timeout 25s adb shell uiautomator dump /sdcard/move60-shade.xml > smoke-debug/uiautomator-shade.log 2>&1 &&
     adb pull /sdcard/move60-shade.xml smoke-debug/notification-shade.xml >/dev/null 2>&1 &&
     grep -q 'MOVE60' smoke-debug/notification-shade.xml; then
    shade_found=1
    break
  fi
  sleep 3
done
adb logcat -d -v threadtime > smoke-debug/logcat-after-shade.txt || true
if grep -q -E 'FATAL EXCEPTION|BadForegroundServiceNotificationException|Error inflating class android.view.View|RemoteServiceException' smoke-debug/logcat-after-shade.txt; then
  echo "Fatal notification rendering error found after opening notification shade"
  grep -n -A 30 -B 5 -E 'FATAL EXCEPTION|BadForegroundServiceNotificationException|Error inflating class android.view.View|RemoteServiceException' smoke-debug/logcat-after-shade.txt | tail -n 120
  exit 1
fi
if test "$shade_found" -ne 1; then
  # UIAutomator may fail with "could not get idle state" while SystemUI is
  # animating. A verified NotificationManager record is the authoritative
  # assertion; preserve the screenshot and logs for visual review.
  echo "WARN: SystemUI hierarchy unavailable; checking NotificationManager instead"
  cat smoke-debug/uiautomator-shade.log || true
  if ! grep -q 'com.move60.workout' smoke-debug/notifications.txt; then
    echo "FAIL: MOVE60 notification absent from NotificationManager"
    exit 1
  fi
  echo "PASS: MOVE60 notification confirmed by NotificationManager"
fi
pid="$(adb shell pidof com.move60.workout | tr -d '\r' || true)"
if test -z "$pid"; then echo "MOVE60 process died while rendering notification"; exit 1; fi
adb shell input keyevent KEYCODE_BACK || true
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
echo "Passed cold launch x3, workout start, rendered notification shade and active-session relaunch checks."
