#!/usr/bin/env python3
"""Reject drift between canonical MOVE60 tokens, Android, Wear and resource themes."""
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path

root = Path(__file__).resolve().parents[1]
tokens = json.loads((root / "design/tokens.json").read_text())
files = {
    "phone": root / "app/src/main/java/com/move60/workout/Move60Theme.kt",
    "watch": root / "wear/src/main/java/com/move60/watch/Move60Theme.kt",
}
for target, file in files.items():
    source = file.read_text()
    for key, value in tokens["colors"].items():
        assert f'val {key} = Color.parseColor("{value}")' in source, (target, key)
    for key, value in tokens["space"].items():
        assert f"const val {key} = {value}" in source, (target, "space", key)
    for key, value in tokens["radius"].items():
        assert f"const val {key} = {value}" in source, (target, "radius", key)
    for category in tokens["type"].values():
        for key, value in category.items():
            # Android Type constants are shared across targets with named Wear slots.
            name = "wear" + key[0].upper() + key[1:] if category is tokens["type"]["wear"] else key
            assert f"const val {name} = {value}f" in source, (target, "type", name)
for name in ("app", "wear"):
    path = root / name / "src/main/res/values/colors.xml"
    colors = {e.get("name"): e.text.upper() for e in ET.parse(path).getroot() if e.tag=="color"}
    for semantic, resource in (
        ("background", "move60_bg"), ("surface", "move60_surface"),
        ("surfaceElevated", "move60_surface_elevated"), ("outline", "move60_outline"),
        ("primary", "move60_accent"), ("textHigh", "move60_text"),
        ("textMedium", "move60_text_medium"),
    ):
        assert colors.get(resource) == tokens["colors"][semantic], (name, resource)
for path in (
    root / "app/src/main/java/com/move60/workout/MainActivity.kt",
    root / "app/src/main/java/com/move60/workout/SettingsActivity.kt",
    root / "wear/src/main/java/com/move60/watch/WatchActivity.kt",
    root / "wear/src/main/java/com/move60/watch/WorkoutTileService.kt",
):
    source = path.read_text()
    assert "Move60Theme as Theme" in source, path
    assert not re.search(r"Color\.rgb\(|0xFFB4F56E|#B4F56E", source), path
    assert "Theme.Colors.primary" in source or "val lime = Theme.Colors.primary" in source, path
main=(root/"app/src/main/java/com/move60/workout/MainActivity.kt").read_text()
settings=(root/"app/src/main/java/com/move60/workout/SettingsActivity.kt").read_text()
wear=(root/"wear/src/main/java/com/move60/watch/WatchActivity.kt").read_text()
tile=(root/"wear/src/main/java/com/move60/watch/WorkoutTileService.kt").read_text()
assert 'val block = card().apply' in main and 'phone_dashboard_summary' in main
assert '지난 기록 불러오기' not in main
assert 'tag = "set-' not in main or '-rpe"' not in main
assert '"오늘 누적 ' in main
assert 'phone_dashboard_progress' in main
assert 'tag = "settings_sound_toggle"' in settings and 'settings_about_card' in settings and 'settings_help_card' in settings
assert 'watch_progress_ring' in wear and 'WorkoutProgressRing' in wear
assert 'Theme.Colors.surfaceElevated' in wear and 'ring(360f * ratio, Theme.Colors.primary)' in tile
print("PASS: canonical design tokens, phone/Wear theme parity, dashboard and round UI links")
