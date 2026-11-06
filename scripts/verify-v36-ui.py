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
assert 'detail_back' in main and 'detail_title' in main and 'leaveToOverview' in main, "Detail screen needs back button + title"
assert 'label("MOVE60", 24' not in main, "Detail screen must not keep the old app bar"
assert 'ExerciseGuide.create' in main and (root/"app/src/main/assets/exercise/legPress_0.jpg").exists(), "Guide must use the two-frame loop"
assert 'backgroundDimEnabled' in read("app/src/main/res/values/styles.xml") and 'DialogDim()' in compose, "Popups must dim the background"
assert 'phone_calendar' not in main and 'showWorkoutCalendar' not in main, "Calendar lives in Compose only"
for marker in ("MonthGrid(", "calendar-dot-", "calendar_prev", "calendar_next", "운동 기록 있음"):
    assert marker in compose, f"Compose calendar lost: {marker}"
assert 'phone_settings' not in main and 'SettingsActivity' not in main, "Settings live in Compose only"
assert not (root/"app/src/main/java/com/move60/workout/SettingsActivity.kt").exists()
assert "body_focus_" not in main and "renderDayStrip" not in main, "Day/body selection lives in the Compose overview only"
assert "WorkoutDock(" in compose and "workout_dock" in compose, "Figma: overview has the fixed bottom dock"
assert "오늘의 운동 구성" in main and "상세 ›" in main and "phone_current_guide" in main, "Figma: detail accordion + set-entry card"
assert "fun buildSetTable" in main and "phone_current_exercise" in main and "buildSetTable(task)" in main.split("fun showDetails")[1], "Set entry: current-exercise card + exercise popup"
for feature in ("운동 알림 진단", "notificationDiagnostics(", "updateManager.check(true)", "peekAvailability", "settings_ratio", "오늘 컨디션 루틴", "칼로리 계산 체중"):
    assert feature in compose, f"Compose settings lost: {feature}"
assert 'Icon(Icons.Outlined.Today' in compose and 'Icon(Icons.Outlined.Settings' in compose
assert "동작 영상" not in compose
assert "PermissionController.createRequestPermissionResultContract()" in consent
assert "granted.containsAll(HealthConnectGateway.readPermissions)" in consent
assert 'putBoolean("enabled", false)' in health and 'putBoolean("consented", consented)' in health
assert "getGrantedPermissions().containsAll(readPermissions)" in health
assert "HeartRateRecord::class" in health and "SleepSessionRecord::class" not in health
assert "sleepMinutes" not in health and "sleepEndedAt" not in health
assert "android.permission.health.READ_SLEEP" not in read("app/src/main/AndroidManifest.xml")
# Consent gates all reads; Health Connect may expose synced samples from many
# writer packages. Restricting to Samsung origin or one day silently hides valid data.
assert "check(isOptedIn())" in health and "check(permissionsGranted())" in health
assert "Duration.ofDays(7)" in health and "pageToken = nextPage" in health
assert "dataOriginFilter = samsungOrigin" not in health
assert "metadata.dataOrigin.packageName" in health
assert "WindowCompat.setDecorFitsSystemWindows(window, false)" in read("app/src/main/java/com/move60/workout/architecture/ComposeActivity.kt")
assert "statusBarsPadding()" in compose and "navigationBarsPadding()" in compose
assert "statusBarsPadding()" in consent
assert "now - lastSynced in 0L..86_400_000L" in wear, "Stale shared health data must expire"
assert "vascularLoad" not in health
assert "sleepMinutes" not in wear and "최근 수면" not in wear
assert "혈관 스트레스" not in wear and "워치4 미지원" not in wear
assert "fillMaxRectangle()" in wear and "ScalingLazyColumn(" in wear
assert "현재 심박수" in wear and "심박수 측정 시작" in wear
assert "consented" in wear and "startForegroundService" in wear
assert "건강 대시보드" in old
assert 'horologist-compose-layout:0.6.23' in read("wear/build.gradle")
targets=read("scripts/release-targets.json")
assert '"phone": true' in targets and '"watch": true' in targets
assert "versionCode 44" in read("app/build.gradle") and "versionCode 42" in read("wear/build.gradle")
for manifest_path, activity in (
    ("app/src/main/AndroidManifest.xml", ".health.HealthConsentActivity"),
    ("wear/src/main/AndroidManifest.xml", ".health.WearHealthActivity"),
):
    xml=ET.fromstring(read(manifest_path))
    android="{http://schemas.android.com/apk/res/android}"
    assert any(x.get(android+"name")==activity for x in xml.findall(".//activity"))
print("PASS: phone clean-up, accessible nav icons, Wear Compose Horologist, heart-rate-only consent/data, no sleep or vascular load and both-target release")
