#!/usr/bin/env python3
"""Hard gates for MOVE60 v36 phone clean-up and non-fabricated Watch4 health UI."""
from pathlib import Path
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
read=lambda p:(root/p).read_text(encoding="utf-8")
main=read("app/src/main/java/com/move60/workout/MainActivity.kt")
compose=read("app/src/main/java/com/move60/workout/architecture/ComposeScreens.kt")
health=read("app/src/main/java/com/move60/workout/health/HealthConnectGateway.kt")
consent=read("app/src/main/java/com/move60/workout/health/HealthConsentActivity.kt")
wear=read("wear/src/main/java/com/move60/watch/health/WearHealthActivity.kt")
old=read("wear/src/main/java/com/move60/watch/WatchActivity.kt")
for phrase in ("버추얼메이트Me 열기", "별도 앱으로 이동합니다.", "오늘 운동량", "ExercisePhotos.addTo", "openVirtualMate"):
    assert phrase not in main, f"Deleted phone UI came back: {phrase}"
assert not (root/"app/src/main/java/com/move60/workout/ExercisePhotos.kt").exists()
assert 'label("MOVE60", 24' in main and 'label("MOVE", 24' not in main
assert 'tag = "phone_calendar"' in main and 'contentDescription = "운동 달력 열기"' in main
assert 'tag = "phone_settings"' in main and 'contentDescription = "앱 설정 열기"' in main
assert 'Icon(Icons.Outlined.CalendarMonth' in compose and 'Icon(Icons.Outlined.Settings' in compose
assert "동작 영상" not in compose
assert "PermissionController.createRequestPermissionResultContract()" in consent
assert "granted.containsAll(HealthConnectGateway.readPermissions)" in consent
assert 'putBoolean("enabled", false)' in health and 'putBoolean("consented", consented)' in health
assert "getGrantedPermissions().containsAll(readPermissions)" in health
assert "HeartRateRecord::class" in health and "SleepSessionRecord::class" in health
assert 'DataOrigin("com.sec.android.app.shealth")' in health
assert health.count("dataOriginFilter = samsungOrigin") == 2, "Do not read other apps' health records"
assert "now - lastSynced in 0L..86_400_000L" in wear, "Stale shared health data must expire"
assert "unsupported_watch4" in health
assert "fillMaxRectangle()" in wear and "ScalingLazyColumn(" in wear
assert "워치4 미지원" in wear and "추정값을 만들지 않아요." in wear
assert "consented" in wear and "startForegroundService" in wear
assert "건강 대시보드" in old
assert 'horologist-compose-layout:0.6.23' in read("wear/build.gradle")
targets=read("scripts/release-targets.json")
assert '"phone": true' in targets and '"watch": true' in targets
assert "versionCode 36" in read("app/build.gradle") and "versionCode 34" in read("wear/build.gradle")
for manifest_path, activity in (
    ("app/src/main/AndroidManifest.xml", ".health.HealthConsentActivity"),
    ("wear/src/main/AndroidManifest.xml", ".health.WearHealthActivity"),
):
    xml=ET.fromstring(read(manifest_path))
    android="{http://schemas.android.com/apk/res/android}"
    assert any(x.get(android+"name")==activity for x in xml.findall(".//activity"))
print("PASS: phone clean-up, accessible nav icons, Wear Compose Horologist, consent checks, no fabricated vascular values and both-target release")
