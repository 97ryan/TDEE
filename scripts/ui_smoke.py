#!/usr/bin/env python3
"""Drives the installed release APK through its main screens with adb, to catch crashes that only
happen in minified builds (R8): launch, log twelve days of entries, then open every screen.

Usage: ui_smoke.py <package> <screenshot-dir>
Exits non-zero if the app crashes or a key screen never shows up.
"""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = sys.argv[1]
OUT = sys.argv[2]
BOUNDS = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


def adb(*args, binary=False):
    result = subprocess.run(["adb", *args], capture_output=True, check=False)
    return result.stdout if binary else result.stdout.decode("utf-8", "replace")


def dump():
    for _ in range(4):
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
        text = adb("exec-out", "cat", "/sdcard/ui.xml")
        if text.strip().startswith("<?xml"):
            return ET.fromstring(text)
        time.sleep(1)
    raise RuntimeError("could not dump the UI")


def centre(node):
    m = BOUNDS.search(node.get("bounds", ""))
    x1, y1, x2, y2 = map(int, m.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def matches(node, label, contains):
    for attr in ("text", "content-desc"):
        value = node.get(attr, "")
        if value and (label in value if contains else value == label):
            return True
    return False


def find(root, label, contains=False):
    return [n for n in root.iter("node") if matches(n, label, contains)]


def wait_for(label, contains=False, timeout=25):
    end = time.time() + timeout
    while time.time() < end:
        found = find(dump(), label, contains)
        if found:
            return found[0]
        time.sleep(1)
    shot("failure-waiting-for-" + re.sub(r"\W+", "-", label))
    fail(f"timed out waiting for {label!r}")


def tap_node(node):
    x, y = centre(node)
    adb("shell", "input", "tap", str(x), str(y))


def tap(label, contains=False, timeout=25):
    tap_node(wait_for(label, contains, timeout))
    time.sleep(0.8)


def shot(name):
    with open(f"{OUT}/{name}.png", "wb") as f:
        f.write(adb("exec-out", "screencap", "-p", binary=True))


def crashed():
    return bool(adb("logcat", "-d", "-b", "crash").strip())


def alive():
    return bool(adb("shell", "pidof", PKG).strip())


def fail(message):
    print(f"SMOKE FAILED: {message}")
    print(adb("logcat", "-d", "-b", "crash")[-4000:])
    sys.exit(1)


def check(step):
    if crashed() or not alive():
        shot("crash-after-" + re.sub(r"\W+", "-", step))
        fail(f"app crashed during: {step}")
    print(f"ok: {step}")


def text_fields(root):
    fields = [n for n in root.iter("node") if n.get("class") == "android.widget.EditText"]
    return sorted(fields, key=lambda n: centre(n)[0])


adb("logcat", "-c")
adb("shell", "monkey", "-p", PKG, "-c", "android.intent.category.LAUNCHER", "1")
wait_for("Need to eat")
shot("r01-home-empty")
check("launch")

# Log twelve days ending today, going backwards. Enter on the calories box saves.
for i in range(12):
    fields = text_fields(dump())
    if len(fields) < 2:
        fail("could not find the weight and calories boxes")
    tap_node(fields[0])
    adb("shell", "input", "text", f"{80.0 + (i % 4) * 0.1 - i * 0.02:.2f}")
    tap_node(fields[1])
    adb("shell", "input", "text", str(2800 + (i % 3) * 100))
    adb("shell", "input", "keyevent", "66")
    time.sleep(1.2)
    if i == 0:
        shot("r02-home-after-first-save")
    tap("Previous day")
check("logging twelve days")
shot("r03-home-seeded")

tap("Progress")
wait_for("Progress graph", contains=True)
time.sleep(1.5)
shot("r04-graph")
tap("Line + dots")
shot("r05-graph-weight-dots")
tap("3M")
shot("r06-graph-3m")
tap("Table")
wait_for("Calories")
time.sleep(1)
shot("r07-table")
rows = [n for n in find(dump(), "20", contains=True) if re.fullmatch(r"\d{1,2} \w{3} \d{4}", n.get("text", ""))]
if rows:
    tap_node(rows[0])
    wait_for("Delete")
    shot("r08-edit-dialog")
    tap("Cancel")
check("progress screens")

adb("shell", "input", "keyevent", "4")
wait_for("Need to eat")
tap("Settings")
wait_for("Days used for calculations")
shot("r09-settings")
tap("Algorithm")
wait_for("Smoothed")
shot("r10-algorithm-dialog")
tap("Smoothed")
tap("How TDEE is calculated")
wait_for("Got it")
tap("Got it")
adb("shell", "input", "swipe", "540", "1700", "540", "500")
time.sleep(1)
shot("r11-settings-scrolled")
tap("Graph settings")
wait_for("Weight trend line smoothing")
shot("r12-graph-settings")
check("settings screens")

adb("shell", "input", "keyevent", "4")
adb("shell", "input", "keyevent", "4")
wait_for("Need to eat")
time.sleep(1)
shot("r13-home-end")
check("navigating back")
print("SMOKE PASSED")
