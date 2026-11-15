import re
xml=open(r"C:\Users\Smartup\Desktop\ARGE\Hexnet\HexnetWorkspace\VanGO\VanGO-v0.91-001\tools\ui3.xml",encoding="utf-8").read()
# clickable rows with meaningful width
for m in re.finditer(r'clickable="true"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
 l,t,r,b=map(int,m.groups())
 w=r-l
 if w>600:
  print(f"clickable L={l} R={r} W={w} Y={t}-{b}")
