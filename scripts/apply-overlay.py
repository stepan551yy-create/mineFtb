#!/usr/bin/env python3
from pathlib import Path
import shutil
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply-overlay.py <Amethyst-Android checkout>")

launcher = Path(sys.argv[1]).resolve()
overlay = Path(__file__).resolve().parents[1]
if not launcher.is_dir():
    raise SystemExit(f"launcher checkout not found: {launcher}")

# Bundle MineFTB presets in the APK without redistributing Minecraft/FTB binaries.
assets = launcher / "app_pojavlauncher" / "src" / "main" / "assets" / "mineftb"
assets.mkdir(parents=True, exist_ok=True)
for name in (
    "ftb-infinity-evolved.json",
    "jvm_args.txt",
    "splash.properties",
    "FTB_IE_Xiaomi14Ultra_controls.json",
):
    shutil.copy2(overlay / "config" / name, assets / name)

# Rebrand only user-facing launcher strings. Package/application IDs stay upstream for
# this first build so we do not break native-library/resource assumptions.
strings = launcher / "app_pojavlauncher" / "src" / "main" / "res" / "values" / "strings.xml"
text = strings.read_text(encoding="utf-8")
text = text.replace(
    '<string name="app_name" translatable="false">Amethyst</string>',
    '<string name="app_name" translatable="false">MineFTB</string>',
)
text = text.replace("Amethyst has unexpectedly crashed", "MineFTB has unexpectedly crashed")
strings.write_text(text, encoding="utf-8")

print(f"MineFTB overlay applied to {launcher}")
print(f"Bundled presets: {assets}")
