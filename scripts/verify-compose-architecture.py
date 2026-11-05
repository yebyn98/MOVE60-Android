#!/usr/bin/env python3
"""Block a v35 rollout if Compose/MVVM/Hilt/Room/WorkManager wiring drifts."""
from pathlib import Path
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
def read(path): return (root/path).read_text(encoding="utf-8")
gradle=read("app/build.gradle")
for marker in ("org.jetbrains.kotlin.plugin.compose","com.google.devtools.ksp",
               "com.google.dagger.hilt.android","compose true","navigation-compose",
               "room-runtime","room-compiler","hilt-android","work-runtime-ktx"):
    assert marker in gradle,marker
manifest=ET.fromstring(read("app/src/main/AndroidManifest.xml"))
uri="{http://schemas.android.com/apk/res/android}"
application=manifest.find("application")
assert application is not None
assert application.attrib[uri+"name"]==".Move60Application"
entry=[node for node in application.findall("activity")
       if node.attrib.get(uri+"name")==".architecture.ComposeActivity"]
assert len(entry)==1
assert any(f.find("action") is not None and
           f.find("action").attrib.get(uri+"name")=="android.intent.action.MAIN"
           for f in entry[0].findall("intent-filter"))
app=read("app/src/main/java/com/move60/workout/Move60Application.kt")
assert "@HiltAndroidApp" in app and "HiltWorkerFactory" in app
assert "workManagerConfiguration" in app
styles=read("app/src/main/java/com/move60/workout/architecture/ComposeActivity.kt")
assert "NavHost(" in styles and "Move60ComposeTheme" in styles
theme=read("app/src/main/java/com/move60/workout/ui/Move60ComposeTheme.kt")
assert "MaterialTheme(" in theme and "Move60Theme.Colors" in theme
components=read("app/src/main/java/com/move60/workout/ui/Move60Components.kt")
assert "fun Move60Card(" in components and "fun Move60Button(" in components
assert "com.google.android.material:material" in gradle
screens=read("app/src/main/java/com/move60/workout/architecture/ComposeScreens.kt")
for marker in ("@Composable","WorkoutDashboard(","WorkoutCalendar(","WorkoutSettings(",
               "collectAsStateWithLifecycle()","오늘 누적","세트","hiltViewModel()"):
    assert marker in screens,marker
vm=read("app/src/main/java/com/move60/workout/architecture/WorkoutViewModel.kt")
assert "@HiltViewModel" in vm and "MutableSharedFlow" in vm and "viewModelScope" in vm
repo=read("app/src/main/java/com/move60/workout/architecture/WorkoutRepository.kt")
for marker in ("interface WorkoutRepository","StateFlow","Dispatchers.IO","SessionStore",
               "WatchStatePublisher","WorkoutService","Room"):
    if marker=="Room": continue
    assert marker in repo,marker
assert "SessionStore" in repo and "startForegroundService" in repo
dao=read("app/src/main/java/com/move60/workout/architecture/WorkoutDao.kt")
assert "@Dao" in dao and "@Upsert" in dao and "Flow<List<DailyWorkoutEntity>>" in dao
assert "@Database" in read("app/src/main/java/com/move60/workout/architecture/WorkoutDatabase.kt")
worker=read("app/src/main/java/com/move60/workout/architecture/WorkoutHistorySyncWorker.kt")
assert "@HiltWorker" in worker and "CoroutineWorker" in worker
assert "EnergyLedger" in worker and "WorkoutHistory" in worker
assert "WorkManagerInitializer" in read("app/src/main/AndroidManifest.xml")
targets=read("scripts/release-targets.json")
assert '"phone": true' in targets and '"watch": true' in targets

# Launcher icon + first-screen intro (approved logo: 60-tick plate + dumbbell).
import subprocess,sys
manifest_text=read("app/src/main/AndroidManifest.xml")
assert 'android:icon="@mipmap/ic_launcher"' in manifest_text and 'android:roundIcon="@mipmap/ic_launcher_round"' in manifest_text
assert 'android:icon="@mipmap/ic_launcher"' in read("wear/src/main/AndroidManifest.xml")
for module in ("app","wear"):
    for res in ("drawable/ic_launcher_foreground.xml","drawable/ic_launcher_monochrome.xml",
                "mipmap-anydpi-v26/ic_launcher.xml","mipmap-anydpi-v26/ic_launcher_round.xml",
                "values/ic_launcher.xml"):
        assert (root/module/"src/main/res"/res).exists(),(module,res)
assert "<monochrome" in read("app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml")
subprocess.run([sys.executable,str(root/"scripts/generate-logo-assets.py"),"--check"],check=True)
logo=read("app/src/main/java/com/move60/workout/ui/Move60Logo.kt")
assert "LOGO_TICKS = 60" in logo and "MaterialTheme.colorScheme" in logo
splash=read("app/src/main/java/com/move60/workout/ui/Move60Splash.kt")
assert "fun Move60Splash(" in splash and "detectTapGestures" in splash
activity=read("app/src/main/java/com/move60/workout/architecture/ComposeActivity.kt")
assert "Move60Splash(" in activity and "ANIMATOR_DURATION_SCALE" in activity
assert "savedInstanceState == null" in activity, "Intro must not replay on rotation/recreation"
print("PASS: Compose Navigation, StateFlow/SharedFlow, Hilt, Room, WorkManager and phone-only release")
