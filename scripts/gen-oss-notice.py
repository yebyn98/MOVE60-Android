#!/usr/bin/env python3
"""Build a visible *metadata inventory* from the reviewed v70 transitive dependency audit.

The v70 Markdown is a historical releaseRuntimeClasspath snapshot, NOT a fresh
Gradle dependency resolution, source-license audit, or legal NOTICE clearance.
This generator deliberately never sets any release-approval flags to true.
After a dependency/version change, update the v70 report from Gradle and redo
individual license/NOTICE/copyright inspections before release approval.
"""
from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
AUDIT = ROOT / "docs/v70-oss-transitive-licenses.md"
DEST = ROOT / "app/src/main/assets/oss_licenses.json"
LICENSE_URLS = {
    "apache": "https://www.apache.org/licenses/LICENSE-2.0",
    "bsd": "https://opensource.org/license/bsd-3-clause",
    "mit": "https://opensource.org/license/mit",
    "sdk": "https://developer.android.com/studio/terms",
}
ROW = re.compile(
    r"^\|\s*([A-Za-z0-9_.-]+):([A-Za-z0-9_.-]+)\s*"
    r"\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|$"
)
audit_text = AUDIT.read_text(encoding="utf-8")
assert "## 전체 목록" in audit_text, "Missing v70 transitive inventory"
lines = audit_text.split("## 전체 목록", 1)[1].splitlines()

previous = json.loads(DEST.read_text(encoding="utf-8")) if DEST.exists() else {}
pom_sources = {
    (x.get("group"), x.get("artifact"), x.get("version")): x.get("pomSource") or x.get("source")
    for x in previous.get("libraries", [])
}
entries, unresolved, seen = [], [], set()
for line in lines:
    m = ROW.match(line)
    if not m:
        continue
    group, artifact, version, modules, license_name = (v.strip() for v in m.groups())
    identity = f"{group}:{artifact}:{version}"
    assert identity not in seen, f"Duplicate dependency: {identity}"
    seen.add(identity)
    external_jsr330 = identity == "javax.inject:javax.inject:1"
    if license_name == "미확인" and not external_jsr330:
        unresolved.append(identity)
        continue
    # The historical POM scan had no license. The upstream JSR-330 source
    # project README and Android JSR330 source POM independently state Apache-2.0.
    # This is a metadata classification, NOT a review of binary NOTICE/copyright.
    if external_jsr330:
        license_name = "Apache License, Version 2.0"
    kind = (
        "apache" if "Apache" in license_name else
        "bsd" if license_name == "BSD-3-Clause" else
        "mit" if "MIT" in license_name else
        "sdk" if license_name == "Android Software Development Kit License" else None
    )
    assert kind is not None, f"Unrecognized license: {identity} / {license_name}"
    entry = {
        "group": group,
        "artifact": artifact,
        "version": version,
        "modules": [s.strip() for s in modules.split(",")],
        "licenses": [{"name": license_name, "url": LICENSE_URLS[kind]}],
        "licenseEvidence": (
            "https://github.com/javax-inject/javax-inject/blob/master/README.md ; "
            "https://android.googlesource.com/platform/external/jsr330/+/refs/tags/android-17.0.0_r1/pom.xml"
            if external_jsr330 else
            "docs/v70-oss-transitive-licenses.md (historical POM report, not per-artifact NOTICE)"
        ),
    }
    if external_jsr330:
        entry["copyrightEvidence"] = "https://github.com/javax-inject/javax-inject/blob/master/src/javax/inject/Inject.java"
        entry["copyrightSummary"] = "Copyright (C) 2009 The JSR-330 Expert Group (source evidence, not entire artifact audit)"
    if pom_sources.get((group, artifact, version)):
        entry["pomSource"] = pom_sources[(group, artifact, version)]
    entry["classification"] = "SDK terms" if kind == "sdk" else "OSS metadata"
    entries.append(entry)

assert len(seen) == 188 and len(entries) == 188, "Historical v70 dependency count changed; re-audit first"
assert not unresolved, "Unresolved dependency; re-audit before release"
result = {
    "scope": "phone+Wear releaseRuntimeClasspath transitive POM audit snapshot (2026-11-17); incomplete third-party legal notices, NOT release-approved",
    "audit": {
        "expectedRuntimeModules": len(seen),
        "resolvedModules": len(entries),
        "unresolved": unresolved,
        "releaseGraphReverified": False,
        "licenseTextsVerified": False,
        "copyrightVerified": False,
        "noticesVerified": False,
        "operatorApproved": False,
        "report": "docs/v70-oss-transitive-licenses.md",
    },
    "libraries": entries,
}
DEST.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"Generated {len(entries)} license metadata entries; {len(unresolved)} unresolved. Release NOT authorized.")
