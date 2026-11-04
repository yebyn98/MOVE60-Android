#!/usr/bin/env python3
"""Publish only explicitly changed MOVE60 targets; preserve the other APK and checksum."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil

PHONE_URL = "https://raw.githubusercontent.com/yebyn98/MOVE60-Updates/main/MOVE60-latest.apk"
WATCH_URL = "https://raw.githubusercontent.com/yebyn98/MOVE60-Updates/main/MOVE60-Watch4-latest.apk"

def version(path):
    content = Path(path).read_text()
    code = re.search(r"versionCode\s+(\d+)", content)
    name = re.search(r"versionName\s+'([^']+)'", content)
    if not code or not name:
        raise ValueError("Invalid gradle version in " + str(path))
    return int(code.group(1)), name.group(1)

def checksum(path):
    h = hashlib.sha256()
    with open(path, "rb") as source:
        for chunk in iter(lambda: source.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()

def publish(updates, workspace, targets, sha, run_id, repo):
    updates, workspace = Path(updates), Path(workspace)
    previous_path = updates / "latest.json"
    if not previous_path.exists():
        raise ValueError("Missing previous release manifest; refuse to clobber existing APK state")
    meta = json.loads(previous_path.read_text(encoding="utf-8"))
    if set(targets) != {"phone", "watch"} or not all(type(v) is bool for v in targets.values()):
        raise ValueError("Release targets must be explicit booleans for phone and watch")
    phone_code, phone_name = version(workspace / "app/build.gradle")
    watch_code, watch_name = version(workspace / "wear/build.gradle")
    prior_phone_code = int(meta["versionCode"])
    prior_watch_code = int(meta.get("watchVersionCode", prior_phone_code))
    # Old releases had a single version field; preserve each installed target's history now.
    meta.setdefault("watchVersionCode", prior_watch_code)
    meta.setdefault("watchVersionName", meta.get("versionName", str(prior_watch_code)))
    meta.setdefault("watchSourceCommit", meta.get("sourceCommit", ""))
    meta.setdefault("watchValidationRun", meta.get("validationRun", ""))
    if targets["phone"]:
        if phone_code <= prior_phone_code:
            raise ValueError(f"Phone release version {phone_code} must exceed published {prior_phone_code}")
    if targets["watch"]:
        if watch_code <= prior_watch_code:
            raise ValueError(f"Watch release version {watch_code} must exceed published {prior_watch_code}")
    # All validation happens before a single binary is overwritten.
    if targets["phone"]:
        source = workspace / "app/build/outputs/apk/release/app-release.apk"
        if not source.is_file() or source.stat().st_size < 100:
            raise ValueError("Missing signed phone APK")
    if targets["watch"]:
        source = workspace / "wear/build/outputs/apk/release/wear-release.apk"
        if not source.is_file() or source.stat().st_size < 100:
            raise ValueError("Missing signed Watch4 APK")
    if not any(targets.values()):
        print("No target changed; release manifest and APKs remain untouched")
        return
    validation = f"https://github.com/{repo}/actions/runs/{run_id}"
    if targets["phone"]:
        dest = updates / "MOVE60-latest.apk"
        shutil.copyfile(workspace / "app/build/outputs/apk/release/app-release.apk", dest)
        meta.update(versionCode=phone_code, versionName=phone_name,
                    apkUrl=PHONE_URL, sha256=checksum(dest),
                    sourceCommit=sha, validationRun=validation)
    if targets["watch"]:
        dest = updates / "MOVE60-Watch4-latest.apk"
        shutil.copyfile(workspace / "wear/build/outputs/apk/release/wear-release.apk", dest)
        meta.update(watchVersionCode=watch_code, watchVersionName=watch_name,
                    watchApkUrl=WATCH_URL, watchSha256=checksum(dest),
                    watchSourceCommit=sha, watchValidationRun=validation)
    previous_path.write_text(json.dumps(meta, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("Published targets:", ", ".join(k for k, active in targets.items() if active))

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--updates", required=True)
    parser.add_argument("--workspace", required=True)
    parser.add_argument("--targets", required=True)
    args = parser.parse_args()
    publish(args.updates, args.workspace, json.loads(Path(args.targets).read_text()),
            os.environ["GITHUB_SHA"], os.environ["GITHUB_RUN_ID"], os.environ["GITHUB_REPOSITORY"])
