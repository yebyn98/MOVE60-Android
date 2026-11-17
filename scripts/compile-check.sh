#!/bin/bash
# Compiles main + test code of :app and :wear BEFORE pushing, so compile errors are caught locally instead of in a 15-25 minute CI run.
# Maven Central answers 429 (too many requests) when many dependencies are downloaded at once; the script retries, and downloaded files
# stay cached, so every retry gets further. Needs the Android SDK (ANDROID_HOME, e.g. /opt/android-sdk).
# Usage: scripts/compile-check.sh            (exit 0 = everything compiled)
set -uo pipefail
: "${ANDROID_HOME:?set ANDROID_HOME to the Android SDK (see docs/v67-po-p0p1-decisions.md)}"
cd "$(dirname "$0")/.."
LOG=$(mktemp)
for attempt in 1 2 3 4 5 6; do
  if gradle --no-daemon -q --max-workers=2 :app:compileDebugKotlin :wear:compileDebugKotlin \
        :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin :wear:compileDebugAndroidTestKotlin >"$LOG" 2>&1; then
    echo "compile OK (attempt $attempt)"; exit 0
  fi
  if ! grep -q "429" "$LOG"; then break; fi          # a real error, not rate limiting: stop and show it
  echo "attempt $attempt hit the Maven Central rate limit, retrying"; sleep 30
done
grep -vE "Picked up|^w: " "$LOG" | grep -E "^e: |What went wrong" -A3 | head -40
echo "compile FAILED"; exit 1
