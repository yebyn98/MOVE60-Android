#!/usr/bin/env python3
"""Exercise the fixed dock after scrolling, while paused to keep UIAutomator idle."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

OUT = Path("smoke-debug")
OUT.mkdir(exist_ok=True)

def adb(*args):
    return subprocess.run(["adb", *args], check=True, capture_output=True, timeout=35).stdout

def bounds(node):
    return tuple(map(int, re.findall(r"\d+", node.attrib["bounds"])))

def find(root, description=None, text=None):
    matches = [n for n in root.iter("node") if
               (description is None or n.get("content-desc") == description) and
               (text is None or n.get("text") == text)]
    assert len(matches) == 1, (description, text, len(matches))
    return matches[0]

def tap(node):
    x1, y1, x2, y2 = bounds(node)
    assert x2 > x1 and y2 > y1
    adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))
    time.sleep(1)

def snapshot(name):
    for attempt in range(6):
        try:
            adb("shell", "uiautomator", "dump", "/sdcard/fixed-controls.xml")
            adb("pull", "/sdcard/fixed-controls.xml", str(OUT / (name+".xml")))
            return ET.parse(OUT / (name+".xml")).getroot()
        except (subprocess.SubprocessError, ET.ParseError) as error:
            if attempt == 5:
                raise RuntimeError("Could not verify fixed controls after 6 attempts") from error
            # Android emulator UiAutomator can briefly return before the dump is on disk.
            time.sleep(2)

# The parent smoke test captured this hierarchy immediately before starting.
initial = ET.parse(OUT / "window.xml").getroot()
toggle = find(initial, description="운동 시작 또는 일시정지")
tap(toggle)
before = snapshot("fixed-controls-before")
play = find(before, description="운동 시작 또는 일시정지")
assert "운동 시작" in play.get("text", ""), "Pause did not reach the session"
dock = find(before, description="고정 운동 컨트롤")
scroll = find(before, description="운동 내용 스크롤")
dock_bounds, play_bounds = bounds(dock), bounds(play)
sx1, sy1, sx2, sy2 = bounds(scroll)
assert sy2 <= dock_bounds[1], "Scroll content overlaps the dock"
assert bounds(find(before, text="← 이전"))[3] <= dock_bounds[3]
assert bounds(find(before, text="다음 →"))[3] <= dock_bounds[3]
for _ in range(3):
    adb("shell", "input", "swipe", str((sx1+sx2)//2), str(sy2-30),
        str((sx1+sx2)//2), str(sy1+30), "400")
    time.sleep(0.4)
after = snapshot("fixed-controls-after")
assert bounds(find(after, description="고정 운동 컨트롤")) == dock_bounds, "Dock moved on scroll"
assert bounds(find(after, description="운동 시작 또는 일시정지")) == play_bounds, "Play moved"
def visible_text(root):
    return {n.get("text") for n in find(root, description="운동 내용 스크롤").iter("node") if n.get("text")}
assert visible_text(before) != visible_text(after), "Test did not actually scroll the content"
# Stage buttons must remain usable after scrolling.
def stage(root):
    return next(n.get("text") for n in find(root, description="고정 운동 컨트롤").iter("node") if n.get("text"))
original_stage = stage(after)
tap(find(after, text="다음 →"))
advanced = snapshot("fixed-controls-next")
assert stage(advanced) != original_stage, "Next stage did not work from dock"
tap(find(advanced, text="← 이전"))
restored = snapshot("fixed-controls-previous")
assert stage(restored) == original_stage, "Previous stage did not work from dock"
adb("shell", "screencap", "-p", "/sdcard/fixed-controls.png")
adb("pull", "/sdcard/fixed-controls.png", str(OUT / "fixed-controls-scrolled.png"))
tap(find(restored, description="운동 시작 또는 일시정지"))
# The rest of the smoke test verifies the resumed foreground notification.
print("PASS: dock stays fixed after actual scrolling; no content overlap; pause, next, previous and resume exercised")
