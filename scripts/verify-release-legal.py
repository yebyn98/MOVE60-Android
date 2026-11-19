#!/usr/bin/env python3
"""Fail-closed *public-release* privacy URL + transitive license evidence gate.

This does not constitute a legal opinion or attest that the published page or
installed app is compliant. Those require human verification and device QA.
The script is deliberately excluded from ordinary feature PR checks until the
operator's real privacy-policy URL and full OSS notices are ready.
"""
import json
import pathlib
import re
import sys

root = pathlib.Path(__file__).resolve().parent.parent
xml = (root / "app/src/main/res/values/legal.xml").read_text(encoding="utf-8")
m = re.search(r'<string name="privacy_policy_url"[^>]*>([^<]*)</string>', xml)
url = m.group(1).strip() if m else ""
blocked = ("example.", "localhost", ".invalid", ".test", "yourdomain", "domain.com")
parts = url.split("/")
host = parts[2] if url.startswith("https://") and len(parts) > 2 else ""
if not host or "." not in host or any(x in host for x in blocked):
    print("BLOCKED: No approved public HTTPS privacy policy URL; do not release.")
    sys.exit(1)
print("Policy URL format looks valid (publication, content and Play Console must be independently verified):", url)

try:
    oss = json.loads((root / "app/src/main/assets/oss_licenses.json").read_text(encoding="utf-8"))
    scope = str(oss.get("scope", "")).strip().lower()
    libs = oss.get("libraries", [])
    audit = oss.get("audit", {})
except (OSError, ValueError, TypeError) as exc:
    print("BLOCKED: OSS metadata missing or unreadable:", exc)
    sys.exit(1)

# Retain the older release-graph verifier's anti-bypass check ("not transitive" in scope).
if not isinstance(libs, list) or not libs or not scope or "not transitive" in scope or "direct runtime dependencies" in scope:
    print("BLOCKED: OSS inventory is missing or direct-only.")
    sys.exit(1)

ids = []
for lib in libs:
    if not isinstance(lib, dict) or not lib.get("group") or not lib.get("artifact") or not lib.get("version"):
        print("BLOCKED: Incomplete OSS dependency identity.")
        sys.exit(1)
    if not isinstance(lib.get("licenses"), list) or not lib["licenses"]:
        print("BLOCKED: OSS dependency without confirmed license metadata:", lib)
        sys.exit(1)
    ids.append((lib["group"], lib["artifact"], lib["version"]))
if len(ids) != len(set(ids)):
    print("BLOCKED: Duplicate OSS dependency entries.")
    sys.exit(1)

expected = audit.get("expectedRuntimeModules", 0)
unresolved = audit.get("unresolved", [])
if not isinstance(expected, int) or expected < 188 or len(libs) != expected or unresolved:
    print(f"BLOCKED: OSS closure incomplete: {len(libs)}/{expected} identified; unresolved={unresolved}")
    sys.exit(1)

required = ("releaseGraphReverified", "licenseTextsVerified", "copyrightVerified", "noticesVerified", "operatorApproved")
missing = [key for key in required if audit.get(key) is not True]
if missing:
    print("BLOCKED: Source/license/NOTICE/copyright/operator evidence still missing:", ", ".join(missing))
    sys.exit(1)

print("Metadata gate passed ONLY; verify bundled license copies, individual NOTICE and rights evidence, live privacy URL, Data Safety, and phone+Wear release QA manually.")
