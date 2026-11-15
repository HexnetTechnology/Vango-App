"""Hold relay ON and swipe dimmer; confirm IO notify outDB/dDB echo."""
import json
import re
import subprocess
import time
from pathlib import Path

ADB = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")
S = "HA26XFVM"


def adb(*a):
    r = subprocess.run([ADB, "-s", S, *a], capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=40)
    return r.stdout + r.stderr


def tap(x, y, wait=0.4):
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(wait)


def swipe(x1, y1, x2, y2, ms=400):
    adb("shell", "input", "swipe", str(x1), str(y1), str(x2), str(y2), str(ms))
    time.sleep(0.5)


def parse_notifies(log: str):
    out = []
    for m in re.finditer(r'"outDB":\[([^\]]+)\][^}]*"dDB":\[([^\]]+)\]', log):
        try:
            outdb = [int(x) for x in m.group(1).split(",")]
            ddb = [int(x) for x in m.group(2).split(",")]
            out.append((outdb, ddb))
        except ValueError:
            pass
    return out


def main():
    # home
    tap(172, 1145, 0.8)
    adb("logcat", "-c")
    time.sleep(0.3)

    print("=== RELAY1 ON ===")
    tap(797, 309, 0.2)
    time.sleep(3.5)
    log = adb("logcat", "-d", "-t", "80")
    sent = [ln for ln in log.splitlines() if "Digital BLE sent" in ln or "Write Result" in ln or "data sent (BLE)" in ln]
    print("sent:", sent[-6:])
    ns = parse_notifies(log)
    print("unique outDB after ON:", list({tuple(a) for a, _ in ns[-8:]}))
    if ns:
        print("last outDB", ns[-1][0], "last dDB", ns[-1][1])

    print("=== RELAY1 OFF ===")
    tap(797, 309, 0.2)
    time.sleep(2.5)
    log2 = adb("logcat", "-d", "-t", "40")
    ns2 = parse_notifies(log2)
    if ns2:
        print("last outDB after OFF", ns2[-1][0])

    print("=== DIMMER1 SWIPE ===")
    adb("logcat", "-c")
    # slider roughly x 180-500, y 195
    swipe(180, 195, 480, 195, 500)
    time.sleep(3.0)
    log3 = adb("logcat", "-d", "-t", "80")
    dsent = [ln for ln in log3.splitlines() if "Dimmer BLE sent" in ln]
    print("dim sent:", dsent[-8:])
    ns3 = parse_notifies(log3)
    if ns3:
        print("last dDB", ns3[-1][1], "outDB", ns3[-1][0])
    else:
        print("no notify parsed")

    # zero dimmer via power button
    tap(629, 195, 0.8)

    print("=== WATER PUMP ===")
    tap(488, 1145, 1.0)
    adb("logcat", "-c")
    tap(1619, 927, 0.3)
    time.sleep(2.0)
    log4 = adb("logcat", "-d", "-t", "40")
    print("water sent:", [ln for ln in log4.splitlines() if "BLE sent" in ln or "skipped" in ln][-8:])

    print("=== CLIMATE FAN ICON ===")
    tap(803, 1145, 1.0)
    adb("logcat", "-c")
    tap(171, 860, 0.3)
    tap(653, 915, 0.3)
    time.sleep(2.0)
    log5 = adb("logcat", "-d", "-t", "50")
    print("climate sent:", [ln for ln in log5.splitlines() if "BLE sent" in ln or "skipped" in ln][-10:])

    print("=== MOTOR unlock/dir ===")
    tap(1118, 1145, 1.0)
    adb("logcat", "-c")
    # dump clickables
    adb("shell", "uiautomator", "dump", "/sdcard/uidump.xml")
    adb("pull", "/sdcard/uidump.xml", r"C:\Users\Smartup\AppData\Local\Temp\vango-uidump.xml")
    xml = Path(r"C:\Users\Smartup\AppData\Local\Temp\vango-uidump.xml").read_text(encoding="utf-8", errors="replace")
    import re as _re
    for n in _re.findall(r"<node[^>]*>", xml):
        if 'clickable="true"' not in n:
            continue
        b = _re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        t = _re.search(r'text="([^"]*)"', n)
        d = _re.search(r'content-desc="([^"]*)"', n)
        if b:
            x1,y1,x2,y2=map(int,b.groups())
            print("CLICK", (x1+x2)//2, (y1+y2)//2, t.group(1) if t else "", d.group(1) if d else "")

    tap(172, 1145, 0.4)


if __name__ == "__main__":
    main()
