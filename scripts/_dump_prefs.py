from pathlib import Path
import re
import html
import subprocess

adb = r"C:\Users\Smartup\AppData\Local\Android\Sdk\platform-tools\adb.exe"
raw = subprocess.check_output(
    [adb, "-s", "HA26XFVM", "shell", "run-as", "com.hextech.vango.v090", "cat", "shared_prefs/vango_login_prefs.xml"],
    stderr=subprocess.STDOUT,
)
t = raw.decode("utf-8", errors="replace")
Path(r"c:\Users\Smartup\Desktop\ARGE\Hexnet\HexnetWorkspace\VanGO\VanGO-v0.90-001\scripts\_prefs_dump.xml").write_text(t, encoding="utf-8")
for m in re.finditer(r'name="([^"]+)"[^>]*>([^<]*)</string>', t):
    name, val = m.group(1), html.unescape(m.group(2))
    if any(k in name.lower() for k in ("panel", "relay", "io_", "profile", "ble", "mac")):
        print(name, "=", val[:800])
        print("---")
for m in re.finditer(r'name="([^"]+)"\s+value="([^"]+)"', t):
    name = m.group(1)
    if any(k in name.lower() for k in ("panel", "ble", "mac", "pending")):
        print("kv", name, m.group(2))
