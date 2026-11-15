import re
from pathlib import Path

p = Path(r"C:\Users\Smartup\AppData\Local\Temp\vango-uidump.xml")
t = p.read_text(encoding="utf-8")
nodes = re.findall(r"<node[^>]*>", t)
print("nodes", len(nodes))
for n in nodes:
    m = re.search(r'text="([^"]*)"', n)
    d = re.search(r'content-desc="([^"]*)"', n)
    c = re.search(r'clickable="(true|false)"', n)
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    txt = m.group(1) if m else ""
    desc = d.group(1) if d else ""
    clk = c.group(1) if c else ""
    if txt or desc or clk == "true":
        bounds = b.groups() if b else None
        print(f"click={clk:5} text={txt!r:40} desc={desc!r:30} bounds={bounds}")
