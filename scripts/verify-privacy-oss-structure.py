#!/usr/bin/env python3
"""Static acceptance checks for backup scope and incomplete OSS notice metadata.

This is a PR regression test, not the public-release legal gate. A passing
result does not imply that privacy, live backup, individual licenses/NOTICE or
the Play Console declarations have been legally verified.
"""
import json
import xml.etree.ElementTree as ET
from pathlib import Path

root = Path(__file__).resolve().parents[1]
android = "{http://schemas.android.com/apk/res/android}"
app = ET.parse(root / "app/src/main/AndroidManifest.xml").getroot().find("application")
assert app is not None
assert app.get(android+"allowBackup") == "true", "Historical workout backup behavior changed without migration review"
assert app.get(android+"fullBackupContent") == "@xml/routive_full_backup_content"
assert app.get(android+"dataExtractionRules") == "@xml/routive_data_extraction_rules"

excluded = {
    "move60_health_opt_in_v1.xml",
    "move60_widget_heart_v1.xml",
    "preferences.xml",
    "move60_health_refresh_v1.xml",
    "watch_publish_v26.xml",
}
legacy = ET.parse(root / "app/src/main/res/xml/routive_full_backup_content.xml").getroot()
modern = ET.parse(root / "app/src/main/res/xml/routive_data_extraction_rules.xml").getroot()
assert legacy.tag == "full-backup-content"
assert modern.tag == "data-extraction-rules"
def names(node):
    return {n.get("path") for n in node.findall("exclude") if n.get("domain") == "sharedpref"}
assert excluded <= names(legacy), "Sensitive preferences must be excluded for Android 11 and earlier"
cloud = modern.find("cloud-backup")
transfer = modern.find("device-transfer")
assert cloud is not None and transfer is not None, "Android 12+ must cover both cloud and D2D"
assert cloud.get("disableIfNoEncryptionCapabilities") == "true"
assert excluded <= names(cloud) and excluded <= names(transfer)
for node in (legacy, cloud, transfer):
    assert not node.findall("include"), "Do not silently narrow workout history backup/restore"
    assert not any(e.get("domain") == "database" for e in node.findall("exclude")), "Do not silently remove historical workout DB from restore"

oss = json.loads((root/"app/src/main/assets/oss_licenses.json").read_text(encoding="utf-8"))
entries = oss["libraries"]
unresolved = oss["audit"]["unresolved"]
assert oss["audit"]["expectedRuntimeModules"] == len(entries) + len(unresolved) == 188
assert len(entries) == len({(x["group"],x["artifact"],x["version"]) for x in entries}), "Duplicate dependencies"
assert len(entries) >= 187, "The v70 transitive graph must not regress to direct-only metadata"
assert all(x.get("licenses") and x.get("licenseEvidence") for x in entries)
# Incomplete notices must remain visibly incomplete, not accidentally represented as approved.
if unresolved:
    assert not oss["audit"].get("operatorApproved"), "Unresolved OSS entries cannot be approved"
if not oss["audit"].get("licenseTextsVerified") or not oss["audit"].get("noticesVerified"):
    assert "not release-approved" in oss["scope"].lower()
print("PASS: selective privacy backup exclusions, preserved record backup intent, and auditable OSS metadata. Release gate remains separate.")
