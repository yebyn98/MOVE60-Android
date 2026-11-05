#!/usr/bin/env bash
set -euo pipefail
mkdir -p smoke-debug/features
adb install -r -g app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb logcat -c
set +e
timeout 360s adb shell am instrument -w -r -e timeout_msec 45000 com.move60.workout.test/androidx.test.runner.AndroidJUnitRunner | tee smoke-debug/features/instrumentation.txt
result=${PIPESTATUS[0]}
set -e
adb logcat -d -v threadtime > smoke-debug/features/logcat.txt
adb pull /sdcard/Android/data/com.move60.workout/files/. smoke-debug/features/ >/dev/null 2>&1 || true
test "$result" -eq 0
# Require an instrumentation success result and at least the baseline suite size.
# Exact-count checks break whenever a valid new test is added.
passed_tests="$(sed -nE 's/^OK \(([0-9]+) tests?\).*$/\1/p' smoke-debug/features/instrumentation.txt | tail -n 1)"
test -n "$passed_tests"
test "$passed_tests" -ge 27
! grep -q -E 'FAILURES!!!|INSTRUMENTATION_FAILED' smoke-debug/features/instrumentation.txt
! grep -q 'FATAL EXCEPTION' smoke-debug/features/logcat.txt
echo 'PASS: settings navigation, finish action, inline per-set persistence, calendar dots, stair limits, daily energy and session isolation'

