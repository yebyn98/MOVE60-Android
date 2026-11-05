#!/usr/bin/env python3
import importlib.util
from pathlib import Path

spec = importlib.util.spec_from_file_location("v", Path(__file__).with_name("verify-release-version.py"))
v = importlib.util.module_from_spec(spec)
spec.loader.exec_module(v)

meta = {"versionCode": 37, "watchVersionCode": 35}
both = {"phone": True, "watch": True}
assert v.problems(38, 36, both, meta) == []
assert len(v.problems(37, 36, both, meta)) == 1 and "phone" in v.problems(37, 36, both, meta)[0]
assert len(v.problems(38, 35, both, meta)) == 1 and "watch" in v.problems(38, 35, both, meta)[0]
assert len(v.problems(37, 35, both, meta)) == 2
assert v.problems(37, 36, {"phone": False, "watch": True}, meta) == []   # untouched target is not checked
assert v.problems(38, 35, {"phone": True, "watch": False}, meta) == []
# Old manifest without a watch field: publish compares the watch against the phone's published code.
assert v.problems(40, 38, both, {"versionCode": 37}) == []
assert len(v.problems(40, 36, both, {"versionCode": 37})) == 1
print("PASS: release version rules match publish_selected")
