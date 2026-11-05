#!/usr/bin/env python3
"""Fail a pull request early when its versionCode would be rejected by the publish step.

The release publish job (scripts/publish_selected.py) refuses a phone/watch APK whose versionCode does
not exceed the one already published. That job only runs after merge, so a missing bump used to surface
one target at a time on main. This gate applies the same rule to the pull request using the public
manifest the app itself updates from.
"""
import json
import sys
import time
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from publish_selected import version  # noqa: E402

MANIFEST_URL = "https://raw.githubusercontent.com/yebyn98/MOVE60-Updates/main/latest.json"

def problems(phone_code, watch_code, targets, meta):
    published_phone = int(meta["versionCode"])
    published_watch = int(meta.get("watchVersionCode", published_phone))
    found = []
    if targets["phone"] and phone_code <= published_phone:
        found.append(f"phone versionCode {phone_code} must exceed published {published_phone} (app/build.gradle)")
    if targets["watch"] and watch_code <= published_watch:
        found.append(f"watch versionCode {watch_code} must exceed published {published_watch} (wear/build.gradle)")
    return found

def fetch(url=MANIFEST_URL, attempts=3):
    last = None
    for attempt in range(attempts):
        try:
            with urllib.request.urlopen(url, timeout=20) as response:
                return json.load(response)
        except Exception as error:  # network or JSON problems are retried, then reported
            last = error
            time.sleep(2 * (attempt + 1))
    raise SystemExit(f"Cannot read the published manifest {url}: {last}")

def main():
    root = Path(__file__).resolve().parent.parent
    phone_code, _ = version(root / "app/build.gradle")
    watch_code, _ = version(root / "wear/build.gradle")
    targets = json.loads((root / "scripts/release-targets.json").read_text())
    found = problems(phone_code, watch_code, targets, fetch())
    if found:
        raise SystemExit("Release version check failed:\n- " + "\n- ".join(found))
    print(f"PASS: phone {phone_code} / watch {watch_code} exceed the published versions for the selected targets")

if __name__ == "__main__":
    main()
