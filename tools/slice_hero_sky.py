"""Slice 4x4 full-day empty landscape sheet into phone_sky_00..15."""
from __future__ import annotations

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SRC = Path(
    r"C:\Users\Smartup\.cursor\projects\c-Users-Smartup-Desktop-ARGE-Hexnet-HexnetWorkspace-VanGO-VanGO-v0-90-001\assets\c__Users_Smartup_AppData_Roaming_Cursor_User_workspaceStorage_empty-window_images_00-00--12-00-1c99afd6-3594-4a57-8230-e14ea1ed4878.png"
)
OUT = ROOT / "composeApp" / "src" / "commonMain" / "composeResources" / "drawable"


def main() -> None:
    im = Image.open(SRC).convert("RGB")
    w, h = im.size
    cols, rows = 4, 4
    for old in OUT.glob("phone_sky_*.png"):
        old.unlink()
    cell_w = w / cols
    cell_h = h / rows
    idx = 0
    for r in range(rows):
        for c in range(cols):
            left = int(round(c * cell_w))
            top = int(round(r * cell_h))
            right = int(round((c + 1) * cell_w))
            bottom = int(round((r + 1) * cell_h))
            panel = im.crop((left + 1, top + 1, right - 1, bottom - 1))
            tw = 720
            scale = tw / panel.width
            panel = panel.resize((tw, max(1, int(panel.height * scale))), Image.Resampling.LANCZOS)
            dest = OUT / f"phone_sky_{idx:02d}.png"
            panel.save(dest, optimize=True)
            print("wrote", dest.name, panel.size)
            idx += 1


if __name__ == "__main__":
    main()
