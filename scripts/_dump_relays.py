from pathlib import Path
import json, re, html, subprocess

adb = r"C:\Users\Smartup\AppData\Local\Android\Sdk\platform-tools\adb.exe"
raw = subprocess.check_output(
    [adb, "-s", "HA26XFVM", "shell", "run-as", "com.hextech.vango.v090", "cat", "shared_prefs/vango_login_prefs.xml"],
    stderr=subprocess.STDOUT,
).decode("utf-8", errors="replace")
m = re.search(r'name="cached_panel_config_json"[^>]*>([^<]*)</string>', raw)
val = html.unescape(m.group(1))
cfg = json.loads(val)
for i, r in enumerate(cfg["channels"]["relays"]):
    print(f"idx={i} id={r.get('id', '(missing→0)')} enabled={r.get('enabled', True)} name={r.get('name')} icon={r.get('icon')} sort={r.get('sort_order')}")
