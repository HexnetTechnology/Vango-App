import re
xml=open(r"C:\Users\Smartup\Desktop\ARGE\Hexnet\HexnetWorkspace\VanGO\VanGO-v0.91-001\tools\uih.xml",encoding="utf-8").read()
print("root", re.search(r'bounds="\[0,0\]\[(\d+),(\d+)\]"', xml).groups())
# scrollview
for m in re.finditer(r'class="[^"]*ScrollView"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
 print("ScrollView", m.groups())
for m in re.finditer(r'text="(AYDINLATMALAR|BUTONLAR|SU TANKLARI|DIMMERLER|RÖLELER)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
 print("section", m.group(1), m.groups()[1:])
