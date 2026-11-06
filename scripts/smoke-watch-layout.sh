#!/usr/bin/env bash
set -euo pipefail
mkdir -p smoke-debug/watch
adb wait-for-device
adb shell settings put global device_provisioned 1
adb shell settings put secure user_setup_complete 1
adb shell settings put system screen_off_timeout 300000
adb shell pm disable-user --user 0 com.google.android.wearable.setupwizard || true
adb shell am force-stop com.google.android.wearable.setupwizard || true
adb shell input keyevent KEYCODE_WAKEUP
# The emulator boots "charging". Wear SysUI then shows its own charging screen on top of the app once it
# finishes initialising, which stops WatchActivity mid-test (isShown=false / launch timeouts).
# Reporting an unplugged battery removes that system overlay without touching any assertion.
adb shell dumpsys battery unplug || true
adb shell dumpsys battery set level 80 || true
# Give first-boot Wear services and the startup surface time to settle.
sleep 20
adb shell wm size 396x396
adb shell wm density 330
adb install -r -g wear/build/outputs/apk/debug/wear-debug.apk
adb install -r -g wear/build/outputs/apk/androidTest/debug/wear-debug-androidTest.apk
adb logcat -c
# Fail early and clearly if a system overlay (e.g. the charging screen) still covers the app before the tests start.
for i in $(seq 1 15); do
  if adb shell dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity' | grep -q 'ChargingActivity'; then
    echo "System charging screen is on top; waiting ($i/15)"; adb shell input keyevent KEYCODE_WAKEUP; sleep 2
  else break; fi
done
set +e
timeout 240s adb shell am instrument -w -r -e timeout_msec 45000 com.move60.workout.test/androidx.test.runner.AndroidJUnitRunner | tee smoke-debug/watch/instrumentation.txt
result=${PIPESTATUS[0]}
set -e
adb logcat -d -v threadtime > smoke-debug/watch/logcat.txt
adb shell dumpsys notification --noredact > smoke-debug/watch/notifications.txt
adb pull /sdcard/Android/data/com.move60.workout/files/. smoke-debug/watch/ >/dev/null 2>&1 || true
test "$result" -eq 0
# Require an instrumentation success result and at least the baseline suite size.
# Exact-count checks break whenever a valid new test is added.
passed_tests="$(sed -nE 's/^OK \(([0-9]+) tests?\).*$/\1/p' smoke-debug/watch/instrumentation.txt | tail -n 1)"
test -n "$passed_tests"
test "$passed_tests" -ge 9
! grep -q -E 'FAILURES!!!|INSTRUMENTATION_FAILED' smoke-debug/watch/instrumentation.txt
if grep -q 'FATAL EXCEPTION' smoke-debug/watch/logcat.txt; then
  echo "Wear OS fatal runtime exception"
  exit 1
fi
echo "PASS: small round Wear OS stage/buttons, ambient layout/restore, stable countdown and ongoing return"

