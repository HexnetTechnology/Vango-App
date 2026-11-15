import re
xml=open(r"C:\Users\Smartup\Desktop\ARGE\Hexnet\HexnetWorkspace\VanGO\VanGO-v0.91-001\tools\ui3.xml",encoding="utf-8").read()
print("screen W from root", re.search(r'bounds="\[0,0\]\[(\d+),(\d+)\]"', xml).groups())
for m in re.finditer(r'text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
 t,l,ty,r,b=m.groups()
 if t.strip() and int(r)>int(l):
  print(f"{t[:36]:36} L={l:>4} R={r:>4}")
