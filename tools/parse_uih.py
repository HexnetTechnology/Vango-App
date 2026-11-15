import re
xml=open(r"C:\Users\Smartup\Desktop\ARGE\Hexnet\HexnetWorkspace\VanGO\VanGO-v0.91-001\tools\uih2.xml",encoding="utf-8").read()
print("len", len(xml))
texts=re.findall(r'text="([^"]+)"', xml)
print("texts", texts[:40])
sv=re.findall(r'ScrollView"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
print("scroll", sv)
