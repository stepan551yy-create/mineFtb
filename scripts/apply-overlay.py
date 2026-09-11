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

# Add MineFTB's first-run helpers to the upstream launcher source tree.
java_dir = launcher / "app_pojavlauncher" / "src" / "main" / "java" / "net" / "kdt" / "pojavlaunch"
java_dir.mkdir(parents=True, exist_ok=True)
shutil.copy2(overlay / "android" / "MineFtbBootstrap.java", java_dir / "MineFtbBootstrap.java")
shutil.copy2(overlay / "android" / "MineFtbLocalAccount.java", java_dir / "MineFtbLocalAccount.java")

# Ask for a local username first, then start FTB setup. This runs after the account spinner
# has registered Amethyst's own local-account listener and after progress observers exist.
launcher_activity = java_dir / "LauncherActivity.java"
activity_text = launcher_activity.read_text(encoding="utf-8")
needle = "        mProgressLayout.observe(ProgressLayout.DOWNLOAD_VERSION_LIST);\n"
replacement = needle + "\n        MineFtbLocalAccount.ensure(this, () -> MineFtbBootstrap.maybeStart(this));\n"
if "MineFtbLocalAccount.ensure(this" not in activity_text:
    if needle not in activity_text:
        raise SystemExit("Could not find LauncherActivity MineFTB insertion point")
    activity_text = activity_text.replace(needle, replacement, 1)
launcher_activity.write_text(activity_text, encoding="utf-8")

# Rebrand only user-facing launcher strings. Package/application IDs stay upstream for
# this first build so native-library/resource assumptions are not broken.
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
print("Local username chooser + FTB bootstrapper installed")
