"""End-to-end VanGO tablet UI + BLE logcat smoke test."""
from __future__ import annotations

import json
import re
import subprocess
import time
from pathlib import Path

ADB = Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe"
SERIAL = "HA26XFVM"
PKG = "com.hextech.vango.v090"
DUMP = Path(r"C:\Users\Smartup\AppData\Local\Temp\vango-uidump.xml")
REPORT = Path(__file__).resolve().parent / "_e2e_report.json"

# Home Kontroller tiles (centers from uiautomator dump, landscape 1920x1200)
RELAYS = [
    (797, 309), (997, 309), (1197, 309), (1397, 309), (1597, 309), (1797, 309),
    (797, 618), (997, 618), (1197, 618), (1397, 618), (1597, 618), (1797, 618),
]
DIMMER_PLUS = [
    (629, 195), (629, 271), (629, 347), (629, 424),
    (629, 501), (629, 578), (629, 655), (629, 733),
]
NAV = {
    "home": (172, 1145),
    "water": (488, 1145),
    "climate": (803, 1145),
    "motor": (1118, 1145),
    "energy": (1433, 1145),
    "settings": (1748, 1145),
}


def adb(*args: str, timeout: int = 30) -> str:
    r = subprocess.run(
        [str(ADB), "-s", SERIAL, *args],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        timeout=timeout,
    )
    return (r.stdout or "") + (r.stderr or "")


def tap(x: int, y: int) -> None:
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(0.35)


def logcat_since(marker: str) -> str:
    return adb("logcat", "-d", "-t", "250")


def last_notify(log: str) -> dict | None:
    matches = re.findall(r'notify 227B: (\{.*?\})', log)
    if not matches:
        matches = re.findall(r'Raw data: (\{.*?\})', log)
    if not matches:
        return None
    raw = matches[-1]
    # truncated lines sometimes; try to parse last full-looking json
    for m in reversed(matches):
        try:
            return json.loads(m)
        except json.JSONDecodeError:
            continue
    return None


def collect_sent(log: str) -> list[str]:
    keys = (
        "Digital BLE sent",
        "Dimmer BLE sent",
        "RGB BLE sent",
        "writeDigitalPort skipped",
        "writeDimmerPort skipped",
        "data sent (BLE)",
    )
    lines = []
    for line in log.splitlines():
        if any(k in line for k in keys):
            lines.append(line.strip()[-220:])
    return lines[-40:]


def dump_ui() -> str:
    adb("shell", "uiautomator", "dump", "/sdcard/uidump.xml")
    adb("pull", "/sdcard/uidump.xml", str(DUMP))
    return DUMP.read_text(encoding="utf-8", errors="replace") if DUMP.exists() else ""


def texts_from_xml(xml: str) -> list[str]:
    texts = re.findall(r'text="([^"]+)"', xml)
    descs = re.findall(r'content-desc="([^"]+)"', xml)
    return [t for t in texts + descs if t]


def clickables(xml: str) -> list[tuple[int, int, str, str]]:
    out = []
    for n in re.findall(r"<node[^>]*>", xml):
        c = re.search(r'clickable="true"', n)
        if not c:
            continue
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        if not b:
            continue
        x1, y1, x2, y2 = map(int, b.groups())
        t = re.search(r'text="([^"]*)"', n)
        d = re.search(r'content-desc="([^"]*)"', n)
        cx, cy = (x1 + x2) // 2, (y1 + y2) // 2
        if cy > 1080:  # skip footer nav
            continue
        if cy < 90:  # skip header
            continue
        out.append((cx, cy, t.group(1) if t else "", d.group(1) if d else ""))
    return out


def main() -> None:
    report: dict = {"steps": [], "ok": True}
    adb("logcat", "-c")
    time.sleep(0.4)
    focus = adb("shell", "dumpsys", "window")
    report["focus"] = "MainActivity" in focus

    # Baseline notify
    time.sleep(1.2)
    base_log = logcat_since("")
    notify = last_notify(base_log)
    report["ble_notify_before"] = notify
    report["ble_link"] = bool(notify and notify.get("slvConn") == "Yes")

    # Relays: toggle ON then OFF
    relay_results = []
    for i, (x, y) in enumerate(RELAYS, start=1):
        adb("logcat", "-c")
        tap(x, y)
        time.sleep(1.1)
        log = logcat_since("")
        sent = [ln for ln in collect_sent(log) if "Digital BLE sent" in ln or "data sent (BLE)" in ln]
        n = last_notify(log)
        out = (n or {}).get("outDB")
        relay_results.append({
            "relay": i,
            "sent": sent[-2:],
            "outDB": out,
            "pass": bool(sent) and isinstance(out, list),
        })
        tap(x, y)  # restore OFF
        time.sleep(0.7)
    report["relays"] = relay_results

    # Dimmers + (first 3 only to limit time, then rest)
    dim_results = []
    for i, (x, y) in enumerate(DIMMER_PLUS, start=1):
        adb("logcat", "-c")
        tap(x, y)
        time.sleep(0.9)
        log = logcat_since("")
        sent = [ln for ln in collect_sent(log) if "Dimmer BLE sent" in ln or "Dim" in ln]
        n = last_notify(log)
        ddb = (n or {}).get("dDB")
        dim_results.append({
            "dimmer": i,
            "sent": sent[-2:],
            "dDB": ddb,
            "pass": bool(sent) or (isinstance(ddb, list) and any(v for v in ddb)),
        })
        # tap + again might go 0->step; leave as-is then zero via a second later pass if needed
    report["dimmers"] = dim_results

    # Footer pages: dump + tap unique clickables (max 8 per page)
    pages = {}
    for name, (nx, ny) in NAV.items():
        tap(nx, ny)
        time.sleep(1.0)
        xml = dump_ui()
        labels = texts_from_xml(xml)[:40]
        taps = clickables(xml)
        adb("logcat", "-c")
        tapped = []
        # Settings has logout / language — dump only.
        max_taps = 0 if name == "settings" else 6
        for cx, cy, t, d in taps[:max_taps]:
            tap(cx, cy)
            tapped.append({"xy": [cx, cy], "text": t, "desc": d})
            time.sleep(0.55)
        log = logcat_since("")
        pages[name] = {
            "labels": labels,
            "tapped": tapped,
            "sent": collect_sent(log),
            "notify": last_notify(log),
        }
        if name == "settings":
            # go back home via footer
            tap(*NAV["home"])
            time.sleep(0.5)
    report["pages"] = {k: {kk: vv for kk, vv in v.items() if kk != "notify" or vv} for k, v in pages.items()}
    # keep notify compact
    for k, v in pages.items():
        n = v.get("notify")
        if n:
            report["pages"][k]["outDB"] = n.get("outDB")
            report["pages"][k]["dDB"] = n.get("dDB")
            report["pages"][k]["slvConn"] = n.get("slvConn")

    tap(*NAV["home"])
    report["ok"] = report["ble_link"] and any(r["pass"] for r in relay_results)
    REPORT.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps({
        "ble_link": report["ble_link"],
        "relays_pass": [r["relay"] for r in relay_results if r["pass"]],
        "relays_fail": [r["relay"] for r in relay_results if not r["pass"]],
        "dimmers_pass": [d["dimmer"] for d in dim_results if d["pass"]],
        "pages": {k: {"labels": v["labels"][:12], "sent_n": len(v["sent"])} for k, v in pages.items()},
    }, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
