# Current status

## Implemented
- Reproducible GitHub Actions build from Amethyst Android `v3_openjdk`.
- Java 8 ARM64 runtime included by upstream runtime artifact.
- MineFTB launcher branding overlay.
- FTB Infinity Evolved 3.1.0 target metadata bundled in APK.
- 5120 MB / G1GC JVM preset bundled in APK.
- Forge splash disable preset bundled in APK.
- Xiaomi 14 Ultra touch-control preset bundled in APK.

## Next engineering step
- First-run bootstrapper that creates/selects a 1.7.10 profile, installs Forge 10.13.4.1614, obtains the FTB Infinity Evolved files from an authorized source, then copies the bundled splash/control presets into the launcher/game directories.

No Minecraft or FTB binaries are committed to this repository.
