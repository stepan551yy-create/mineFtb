# MineFTB Android

Android launcher build project aimed at **FTB Infinity Evolved 3.1.0** on **Minecraft 1.7.10 / Forge 10.13.4.1614**, tuned first for Xiaomi 14 Ultra.

The launcher base is built from the open-source Amethyst/PojavLauncher lineage. This repository contains only the MineFTB overlay, presets and build workflow. It does **not** contain Minecraft game files, paid/proprietary assets, Microsoft/Mojang credentials, or a copied CubixWorld application.

## Target profile

- Minecraft: 1.7.10
- Forge: 10.13.4.1614
- FTB Infinity Evolved: 3.1.0
- Runtime: Java 8 ARM64
- RAM preset: 5120 MB
- JVM: `-Xms1024M -Xmx5120M -XX:+UseG1GC -XX:MaxGCPauseMillis=75 -XX:+DisableExplicitGC -Dfile.encoding=UTF-8`
- Renderer starting point: MobileGlues / Auto
- Resolution scale: 75%
- Forge splash: disabled

## Build

GitHub Actions checks out `AngelAuraMC/Amethyst-Android` at `v3_openjdk`, downloads its Java 8 runtime, applies this overlay, and builds a debug APK.

The Xiaomi 14 Ultra control map and FTB target profile are bundled inside the APK under `assets/mineftb/`. Automatic first-run installation/bootstrap of the modpack is a separate next step; this build intentionally does not redistribute Minecraft or FTB binaries.

## Legal / attribution

Amethyst/PojavLauncher and third-party components remain under their respective licenses. FTB, Minecraft, Forge and all mod names belong to their respective owners.
