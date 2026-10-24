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


def shot(name, limit=25):
    with open(f"{OUT}/{name}.png", "wb") as f:
        f.write(adb("exec-out", "screencap", "-p", binary=True))
    print(f"screenshot {name}:")
    describe(limit=limit)


def crashed():
    return bool(adb("logcat", "-d", "-b", "crash").strip())


def alive():
    return bool(adb("shell", "pidof", PKG).strip())


def describe(root=None, limit=70):
    """Prints what is on screen as text: one line per node that has text, a description or input."""
    try:
        root = root if root is not None else dump()
    except Exception as error:  # the app may be gone
        print(f"  (could not read the screen: {error})")
        return
    shown = 0
    for n in root.iter("node"):
        text, desc = n.get("text", ""), n.get("content-desc", "")
        if text or desc or n.get("class") == "android.widget.EditText":
            print(f'  {n.get("class", "").split(".")[-1]:<10} text={text!r} desc={desc!r} {n.get("bounds")}')
            shown += 1
            if shown >= limit:
                break


SOFT = False


class SoftFail(Exception):
    pass


def fail(message):
    if SOFT:
        raise SoftFail(message)
    print(f"SMOKE FAILED: {message}")
    print("alive:", alive(), "| keyboard shown:", keyboard_shown())
    print("screen:")
    describe()
    print("crash log:", adb("logcat", "-d", "-b", "crash")[-4000:] or "(empty)")
    sys.exit(1)


def check(step):
    if crashed() or not alive():
        shot("crash-after-" + re.sub(r"\W+", "-", step))
        fail(f"app crashed during: {step}")
    print(f"ok: {step}")


def keyboard_shown():
    return "mInputShown=true" in adb("shell", "dumpsys", "input_method")


def hide_keyboard():
    # Back closes the keyboard; only press it when the keyboard is up, or it would leave the screen.
    if keyboard_shown():
        adb("shell", "input", "keyevent", "4")
        time.sleep(1)


def scroll_to(label, contains=False, attempts=8):
    for _ in range(attempts):
        found = find(dump(), label, contains)
        if found:
            return found[0]
        adb("shell", "input", "swipe", "540", "1800", "540", "900", "300")
        time.sleep(0.8)
    shot("failure-scrolling-to-" + re.sub(r"\W+", "-", label))
    fail(f"could not scroll to {label!r}")


def text_fields(root):
    fields = [n for n in root.iter("node") if n.get("class") == "android.widget.EditText"]
    return sorted(fields, key=lambda n: centre(n)[0])


adb("logcat", "-c")
adb("shell", "monkey", "-p", PKG, "-c", "android.intent.category.LAUNCHER", "1")
wait_for("Need to eat")
shot("r01-home-empty")
check("launch")

# Log twelve days ending today, going backwards.
for i in range(12):
    fields = text_fields(dump())
    if len(fields) < 2:
        fail("could not find the weight and calories boxes")
    tap_node(fields[0])
    time.sleep(0.6)
    adb("shell", "input", "text", f"{80.0 + (i % 4) * 0.1 - i * 0.02:.2f}")
    fields = text_fields(dump())  # the keyboard may have moved things
    tap_node(fields[1])
    time.sleep(0.6)
    adb("shell", "input", "text", str(2800 + (i % 3) * 100))
    hide_keyboard()
    tap("Save")
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
shot("r07-table", limit=80)
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
tap_node(scroll_to("Graph settings"))
time.sleep(0.8)
shot("r11-settings-scrolled")
wait_for("Weight trend line smoothing")
shot("r12-graph-settings")
check("settings screens")

adb("shell", "input", "keyevent", "4")
adb("shell", "input", "keyevent", "4")
wait_for("Need to eat")
time.sleep(1)
shot("r13-home-end")
check("navigating back")


# ---- persistence: everything must survive the app being killed ----
def device_today():
    return adb("shell", "date", "+%Y-%m-%d").strip()


def table_date_label(iso):
    year, month, day = iso.split("-")
    months = "Jan Feb Mar Apr May Jun Jul Aug Sep Oct Nov Dec".split()
    return f"{int(day)} {months[int(month) - 1]} {year}"


def restart_app():
    adb("shell", "am", "force-stop", PKG)
    time.sleep(1)
    adb("shell", "monkey", "-p", PKG, "-c", "android.intent.category.LAUNCHER", "1")
    wait_for("Need to eat")


restart_app()
tap("Settings")
wait_for("Smoothed: weights recent days more")  # the algorithm chosen earlier is remembered
adb("shell", "input", "keyevent", "4")
wait_for("Need to eat")
tap("Progress")
tap("Table")
wait_for(table_date_label(device_today()))  # today's entry is still there
shot("r14-after-restart", limit=40)
adb("shell", "input", "keyevent", "4")
wait_for("Need to eat")
check("settings and entries survive a restart")


# ---- a small phone with large text: nothing may crash or run off the screen ----
def overlaps(a, b):
    ax1, ay1, ax2, ay2 = map(int, BOUNDS.search(a.get("bounds")).groups())
    bx1, by1, bx2, by2 = map(int, BOUNDS.search(b.get("bounds")).groups())
    w = min(ax2, bx2) - max(ax1, bx1)
    h = min(ay2, by2) - max(ay1, by1)
    if w <= 0 or h <= 0:
        return False
    smaller = min((ax2 - ax1) * (ay2 - ay1), (bx2 - bx1) * (by2 - by1))
    return w * h > 0.25 * smaller


def layout_problems(screen):
    root = dump()
    width = int(adb("shell", "wm", "size").split()[-1].split("x")[0])
    texts = [n for n in root.iter("node") if (n.get("text") or n.get("content-desc")) and n.get("class") != "android.widget.EditText"]
    problems = []
    for n in texts:
        x1, _, x2, _ = map(int, BOUNDS.search(n.get("bounds")).groups())
        if x2 > width + 2 or x1 < -2:
            problems.append(f"{screen}: off screen {n.get('text') or n.get('content-desc')!r} {n.get('bounds')}")
    for i, a in enumerate(texts):
        for b in texts[i + 1:]:
            if overlaps(a, b):
                problems.append(f"{screen}: overlap {a.get('text') or a.get('content-desc')!r} / {b.get('text') or b.get('content-desc')!r}")
    return problems


def small_screen_pass():
    adb("shell", "wm", "size", "720x1280")
    adb("shell", "wm", "density", "320")
    adb("shell", "settings", "put", "system", "font_scale", "1.6")
    time.sleep(4)
    problems = []
    wait_for("Need to eat")
    shot("r15-small-home", limit=60)
    problems += layout_problems("home")
    tap("Progress")
    wait_for("Progress graph", contains=True)
    time.sleep(1.5)
    shot("r16-small-graph", limit=60)
    problems += layout_problems("graph")
    tap("Table")
    time.sleep(1)
    shot("r17-small-table", limit=60)
    problems += layout_problems("table")
    adb("shell", "input", "keyevent", "4")
    wait_for("Need to eat")
    tap("Settings")
    wait_for("Days used for calculations")
    shot("r18-small-settings", limit=60)
    problems += layout_problems("settings")
    adb("shell", "input", "keyevent", "4")
    wait_for("Need to eat")
    for p in problems:
        print("LAYOUT WARNING:", p)
    print(f"layout warnings: {len(problems)}")


def stage(name, function):
    """Runs a check whose failure is reported but does not block the release (yet)."""
    global SOFT
    SOFT = True
    try:
        function()
        print(f"STAGE OK: {name}")
    except SoftFail as error:
        print(f"STAGE FAILED (not blocking): {name}: {error}")
    except Exception as error:  # noqa: BLE001
        print(f"STAGE ERROR (not blocking): {name}: {error!r}")
    finally:
        SOFT = False


try:
    stage("small screen with large text", small_screen_pass)
finally:
    adb("shell", "wm", "size", "reset")
    adb("shell", "wm", "density", "reset")
    adb("shell", "settings", "put", "system", "font_scale", "1.0")
    time.sleep(3)
check("small screen with large text")


# ---- the new day: at midnight the boxes must be empty again, without restarting the app ----
def midnight_rollover():
    adb("root")
    adb("wait-for-device")
    time.sleep(2)
    adb("shell", "settings", "put", "global", "auto_time", "0")
    adb("shell", "settings", "put", "global", "auto_time_zone", "0")
    iso = device_today()
    year, month, day = iso.split("-")
    adb("shell", "date", f"{month}{day}2359{year}.20")  # 23:59:20 today
    print("device time now:", adb("shell", "date").strip())
    restart_app()
    fields = text_fields(dump())
    tap_node(fields[0])
    adb("shell", "input", "text", "81")
    tap_node(text_fields(dump())[1])
    adb("shell", "input", "text", "2900")
    hide_keyboard()
    tap("Save")
    time.sleep(1)
    before = [f.get("text") for f in text_fields(dump())]
    print("boxes before midnight:", before)
    if before[0] != "81":
        fail(f"the entry was not saved before midnight: {before}")
    time.sleep(55)  # past 00:00:00 plus the 1 s margin
    print("device time now:", adb("shell", "date").strip())
    after = [f.get("text") for f in text_fields(dump())]
    print("boxes after midnight:", after)
    shot("r19-after-midnight", limit=30)
    if any(after):
        fail(f"the boxes were not emptied at midnight: {after}")


stage("midnight rollover", midnight_rollover)
check("midnight")
print("SMOKE PASSED")
