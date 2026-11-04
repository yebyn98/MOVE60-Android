#!/usr/bin/env python3
"""Prevent mobile, Wear, widgets and notification UI from drifting apart."""
import re
from pathlib import Path
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
phone=(root/"app/src/main/java/com/move60/workout/Move60Theme.kt").read_text()
watch=(root/"wear/src/main/java/com/move60/watch/Move60Theme.kt").read_text()
expected={
    "background":"FF000000","surface":"FF121212","elevated":"FF1C1C1C",
    "divider":"FF303030","accent":"FF00E676","accentDim":"FF103722",
    "textHigh":"FFFFFFFF","textMedium":"FFB0B0B0",
    "onAccent":"FF002A15","error":"FFFF6B6B",
}
for key, hex_value in expected.items():
    pattern=rf"val {key}: Int = 0x{hex_value}\.toInt\(\)"
    assert re.search(pattern,phone),f"Phone token missing: {key}"
    assert re.search(pattern,watch),f"Watch token missing: {key}"
xml=ET.parse(root/"app/src/main/res/values/colors.xml").getroot()
palette={node.attrib["name"]:node.text.upper() for node in xml.findall("color")}
assert palette["move60_bg"]=="#000000"
assert palette["move60_surface"]=="#121212"
assert palette["move60_accent"]=="#00E676"
assert palette["move60_text"]=="#FFFFFF"
assert palette["move60_text_medium"]=="#B0B0B0"
for target in [
    "app/src/main/java/com/move60/workout/MainActivity.kt",
    "app/src/main/java/com/move60/workout/SettingsActivity.kt",
    "wear/src/main/java/com/move60/watch/WatchActivity.kt",
    "wear/src/main/java/com/move60/watch/WorkoutTileService.kt",
]:
    text=(root/target).read_text()
    assert "Move60Theme" in text,f"Theme is not imported by {target}"
    assert "Color.rgb(" not in text,f"Ad-hoc palette leaked into {target}"
main=(root/"app/src/main/java/com/move60/workout/MainActivity.kt").read_text()
settings=(root/"app/src/main/java/com/move60/workout/SettingsActivity.kt").read_text()
assert "지난 기록 불러오기" not in main
assert 'val effort = input(' not in main
assert "summary.addView(label(routine.focus" not in main
assert 'tag = "phone_strength_progress"' in main
assert 'tag = "settings_back"' in settings
assert 'tag = "settings_sound_toggle"' in settings
assert 'tag = "settings_version"' in settings
assert 'support.addView(actionButton("오류 기록 확인' not in settings
assert 'label("도움말"' in settings
assert 'label("앱 정보"' in settings
assert '오늘 누적' in main
widget=(root/"app/src/main/res/layout/widget_workout.xml").read_text()
assert "@color/move60_accent" in widget
assert "@color/move60_surface" in widget
for name in ("notification_workout_compact.xml","notification_workout_expanded.xml"):
    item=(root/"app/src/main/res/layout"/name).read_text()
    assert "@color/move60_accent" in item or "@drawable/bg_notification_brand" in item
    assert "RPE" not in item
print("PASS: MOVE60 phone, Wear, tile, widget, notifications and settings share fixed design tokens")
