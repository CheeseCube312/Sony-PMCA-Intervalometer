# Intervalometer for Sony cameras (PlayMemories Camera Apps)

A free, open-source built-in intervalometer for the Sony Alpha 7 II
(ILCE-7M2) and other cameras on Sony's PlayMemories Camera Apps (PMCA)
platform. These cameras ship no native interval shooting - only the paid,
closed Time-lapse app - so this fills the gap: a fixed-grid timed-still
engine that writes JPEGs straight to the card, with a per-shot session log
for drift and failure analysis.

**Status: 0.4.0e** (2026-11-15). Built and verified entirely on an A7 II
across many tagged on-camera test rounds; the current UI is a config grid +
dropdown menu with help cards and value editors. Other PMCA-era cameras
share the same Scalar platform, but are untested - reports welcome.

## Features

- **Fixed-grid engine** - one shot per grid slot; late shots fire and log
  their delta, slots are never skipped, an in-flight capture holds the grid,
  and trash stops the run with an on-screen summary.
- **Full exposure control** - shutter from the camera's stock timed list
  plus one custom long exposure, true Bulb (with a self-verifying exit that
  re-arms on firmware timeout), ISO walked from the camera's supported list,
  and aperture via the iris-unlock wrapper - every write read-back verified.
- **Power-saving runs** - the run starts dark (live view stopped, screen
  blank); any key wakes a 5 s HUD with taken/planned counts and battery.
- **In-camera UI** - persisted config grid (survives restarts and power
  cycles), dropdown menus with a help card on every item, value editors
  (ENTER saves, trash cancels), Shot-now single capture, count ladder
  5-600 + unlimited.
- **Session log** - every shot, delta, exposure echo, watchdog trip and
  failure lands in `/sdcard/INTVL/LOG.TXT` (pull it with
  `adb pull /sdcard/INTVL/LOG.TXT`); a persisted DEBUG flag toggled in-app
  adds verbose forensics for troubleshooting.

## Install on the camera (PMCA-RE)

[PMCA-RE](https://github.com/ma1co/Sony-PMCA-RE) is a free, open-source
desktop tool (Windows/macOS) that installs APKs on Sony's Android-based
cameras over USB. No Sony developer account or signing needed - the camera
accepts debug and release certificates alike.

1. Download `Intervalometer-<version>.apk` from
   [Releases](../../releases/latest).
2. Download `pmca-console` (or `pmca-gui`) for your OS from the
   [PMCA-RE releases](https://github.com/ma1co/Sony-PMCA-RE/releases).
3. Connect the camera to the PC via USB (its normal mass-storage/MTP mode,
   which Windows handles out of the box) and switch it on.
4. Test the connection, then install the APK:

        pmca-console info
        pmca-console install -f Intervalometer-<version>.apk

   (`-f` installs a local APK file; `pmca-console install -i` instead picks
   interactively from the app list, and `pmca-gui` has an *Install app* tab
   for the same. For a downloaded APK, `-f` is the direct route.)
5. Launch Intervalometer from the camera's application list.

Alternative: **adb over Wi-Fi**, if your camera has it enabled:
`adb connect <camera-ip>:5555`, then
`adb install -r Intervalometer-<version>.apk`.

Operation is key-only (PMCA-era cameras have no touchscreen): the config grid
edits with UP/DOWN/LEFT/RIGHT + ENTER, S1/S2 arm and fire, trash stops a run.

## Build from source

- JDK 17 and an Android SDK (compileSdk 34); Gradle wrapper 8.9 and AGP
  8.5.2 come with the repo.
- The OpenMemories framework and the Sony Scalar API stubs are **vendored**
  under [`intervalometer/third_party/OpenMemories-Framework/`](intervalometer/third_party/OpenMemories-Framework)
  with build files patched for modern Gradle/JDK (a pristine upstream
  checkout does not build), so a fresh clone needs nothing else.

        cd intervalometer
        ./gradlew assembleDebug        # gradlew.bat on Windows
        # -> app/build/outputs/apk/debug/Intervalometer-<version>.apk

min/target SDK is API 10 (Gingerbread-era firmware), no AndroidX.

## Documentation

- [`docs/DEVNOTES.md`](docs/DEVNOTES.md) - engineering notes: measured
  device facts, the verified Scalar SDK surface, session-log format, and
  the full per-version history.

## Credits & third-party

- [ma1co/OpenMemories-Framework](https://github.com/ma1co/OpenMemories-Framework)
  - the framework and Scalar stubs this is built on (vendored under
  `third_party/`, MIT).
- [ma1co/Sony-PMCA-RE](https://github.com/ma1co/Sony-PMCA-RE) - APK
  sideloading for PMCA-era cameras.

## License

[MIT](LICENSE) (c) 2026 JPC. The vendored
`third_party/OpenMemories-Framework` code remains MIT (c) 2016 ma1co.
