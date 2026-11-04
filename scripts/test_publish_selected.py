#!/usr/bin/env python3
"""Offline selective-release contract regression tests (no GitHub token/network)."""
import json
import tempfile
from pathlib import Path
from publish_selected import publish

def setup(root):
    root = Path(root)
    workspace, updates = root / "workspace", root / "updates"
    (workspace / "app/build/outputs/apk/release").mkdir(parents=True)
    (workspace / "wear/build/outputs/apk/release").mkdir(parents=True)
    (workspace / "app/build.gradle").write_text("versionCode 33\nversionName '33.0'\n")
    (workspace / "wear/build.gradle").write_text("versionCode 32\nversionName '32.0'\n")
    (workspace / "app/build/outputs/apk/release/app-release.apk").write_bytes(b"updated-phone" * 100)
    (workspace / "wear/build/outputs/apk/release/wear-release.apk").write_bytes(b"updated-watch" * 100)
    updates.mkdir()
    (updates / "MOVE60-latest.apk").write_bytes(b"prior-phone")
    (updates / "MOVE60-Watch4-latest.apk").write_bytes(b"prior-watch")
    (updates / "latest.json").write_text(json.dumps({
        "versionCode": 31, "versionName": "31.0", "sha256": "old_phone",
        "watchSha256": "old_watch", "sourceCommit": "old_sha",
        "validationRun": "old_run", "apkUrl": "https://example.org/phone",
        "watchApkUrl": "https://example.org/watch"
    }))
    return workspace, updates

def manifest(updates):
    return json.loads((updates / "latest.json").read_text())

with tempfile.TemporaryDirectory() as temp:
    workspace, updates = setup(temp)
    original_watch = (updates / "MOVE60-Watch4-latest.apk").read_bytes()
    publish(updates, workspace, {"phone": True, "watch": False}, "phone_sha", "1", "owner/repo")
    result = manifest(updates)
    assert result["versionCode"] == 33 and result["versionName"] == "33.0"
    assert result["watchVersionCode"] == 31
    assert result["watchSha256"] == "old_watch"
    assert result["watchSourceCommit"] == "old_sha"
    assert (updates / "MOVE60-Watch4-latest.apk").read_bytes() == original_watch
    assert (updates / "MOVE60-latest.apk").read_bytes().startswith(b"updated-phone")
    before_phone = (updates / "MOVE60-latest.apk").read_bytes()
    (workspace / "wear/build.gradle").write_text("versionCode 34\nversionName '34.0'\n")
    publish(updates, workspace, {"phone": False, "watch": True}, "watch_sha", "2", "owner/repo")
    result = manifest(updates)
    assert result["watchVersionCode"] == 34 and result["watchSourceCommit"] == "watch_sha"
    assert result["versionCode"] == 33 and result["sourceCommit"] == "phone_sha"
    assert (updates / "MOVE60-latest.apk").read_bytes() == before_phone
    before = (updates / "latest.json").read_bytes()
    publish(updates, workspace, {"phone": False, "watch": False}, "none", "3", "owner/repo")
    assert (updates / "latest.json").read_bytes() == before

with tempfile.TemporaryDirectory() as temp:
    workspace, updates = setup(temp)
    snapshot = (updates / "latest.json").read_bytes()
    # Deliberately keep the watch at the ALREADY PUBLISHED version (31).
    # setup() normally uses 32 to simulate a valid upgrade.
    (workspace / "wear/build.gradle").write_text("versionCode 31\\nversionName '31.0'\\n")
    try:
        publish(updates, workspace, {"phone": False, "watch": True}, "wrong", "4", "owner/repo")
        raise AssertionError("Watch release without version bump must fail")
    except ValueError:
        assert (updates / "latest.json").read_bytes() == snapshot
    (workspace / "wear/build.gradle").write_text("versionCode 32\\nversionName '32.0'\\n")
    publish(updates, workspace, {"phone": True, "watch": True}, "both_sha", "5", "owner/repo")
    result = manifest(updates)
    assert result["versionCode"] == 33 and result["watchVersionCode"] == 32
    assert result["sourceCommit"] == result["watchSourceCommit"] == "both_sha"
    assert result["validationRun"] == result["watchValidationRun"]
print("PASS: phone-only, watch-only, both, neither, stale-version rejection")
