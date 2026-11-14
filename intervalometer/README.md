# Intervalometer — PMCA timed-sequence shooting app (Sony A7 II)

Built-in intervalometer for the Sony Alpha 7 II (ILCE-7M2, PlayMemories Camera
Apps platform, API 2.10). The A7 II has no native interval shooting (only the
paid Time-lapse app), so this app fills that gap: fixed-grid timed stills
straight to the card, with a session log for drift/failure analysis.

**Status: Phase 0 done, Phase 1 next.** 0.1.0 was the `Blank/starting_package`
smoke test retargeted to `com.jpc.intervalometer` / INTVL logging (preview +
S2 still verified on camera). 0.1.1 retires the starter's M-JPEG decode
engine: live view is the plain HAL path (PMCADemo CameraActivity pattern) -
no NDK, no native code. 0.1.3 attacks the monochrome live view/stills at the
real cause: a camera LOOK state. Recipe Lab (recipe-lab-sony-pmca) opens the
camera the same way (`CameraEx.open(0, null)` + HAL SurfaceView) yet gets
color, because it drives the Sony runtime look keys over `Camera.Parameters`
after startPreview (`applyPreview()` / `Params.preview()`: `color-mode`,
`saturation`, `contrast`, `sharpness`, `picture-effect`, ...). `color-mode`
(Creative Style) has a `"mono"` value and `picture-effect` has
rough-mono/richtone-mono - either one renders preview AND firmware stills
monochrome while normal-mode shooting stays color. 0.1.3 logs the look keys
the session opens with, forces `color-mode=standard`, `picture-effect=off`,
`rgb-matrix-mode=false`, and logs what the camera kept.
**Root cause CONFIRMED on camera (2026-11-14 log):** the session opens with
`look before: color-mode=mono` (Creative Style stuck on mono; every other key
already neutral) and the camera kept `standard` - fix held on the first try,
S2 still wrote normally (DSC07291). The mono style only bites inside the
app's JPEG pipeline (RAW shooting never shows it), and since the camera may
copy live params back into the settings store on exit, next run's
`look before:` shows whether the store self-repaired or the app session
re-injects mono per open; forcing at startPreview covers both.
0.1.4 removes the UP color-depth A/B toggle - the 11-14 log shows the mono
look sat above the display pipeline, identical under both depths.
**0.2.0 is Phase 1:** `IntervalEngine` fixed-grid scheduler (elapsedRealtime
anchor; every tick recomputes shot n at t0 + delay + n*interval, ticks at the
deadline or 1 s; late shots still fire and are logged with their delta -
slots are never skipped, so the log shows the real interval floor). Config
screen on the camera: interval 2/5/10/15/30/60 s, count 10/60/inf, start
delay 0/2/5/10 s (UP/DOWN value, LEFT/RIGHT field, S2 starts). Runs hold the
camera awake: APO-off broadcast (`DAConnectionManagerService.apo`, renewed
per minute - the PMCADemo WifiActivity / Recipe Lab route) + keep-screen-on;
trash stops with a summary, second trash exits; onPause stops the run.
Summary logs stored-vs-fired and capture min/avg/max. `DemoActivity` renamed
`IntervalActivity`.
0.2.2-0.2.4 made multi-shot runs trustworthy: the full shutter-key cycle per
frame (takePicture, then cancelTakePicture + startPreview ~2.5 s later -
skipping the release bricks shot 2+), and the interval became a MINIMUM
spacing - a slot holds while the previous capture is in flight (firing into a
busy camera silently loses both shots: v0.2.3 lost 8 of 10 at a 5 s interval
vs 30 s exposures; v0.2.4 verified 10/10 stored), the summary waits out an
in-flight last capture, and a 90 s cap marks a never-storing capture lost.
**0.2.5 adds exposure control + true bulb:** the SDK steps the camera's
shutter speed (`incrementShutterSpeed`/`decrementShutterSpeed` - software
state; the dials are just input devices for the same value) and can only READ
it via `ShutterSpeedChangeListener` (no getter). The config screen gains an
Exposure field mirroring the camera's current value and stepping it live; at
the Bulb end stop UP/DOWN adjust the app-side bulb duration (5-300 s)
instead, and DOWN below the shortest steps back out into the timed list.
Bulb runs end each exposure with `cancelExposure()` - the SDK's exposure-end
primitive, while `cancelTakePicture` is the timed-shot release and is ignored
mid-exposure (the v0.2.4 side finding). The in-flight watchdog and summary
deadline scale with the bulb duration, and `ShutterListener` /
`CaptureStatusListener` / `ExposureCompleteListener` are armed as log-only
ground truth (shutter-open anchor, real in-flight state). First session findings: the Bulb encoding is CONFIRMED as n=65535
(0xFFFF sentinel), d=1 - not the n==0/d==0 guess (the Exposure field
briefly showed "65535 s" at the Bulb position until detection was fixed).
The timed ladder steps cleanly (30/25/20/15/13/10 s in the test mode) and
one step down from Bulb returns to 30 s. The shutter-open anchor lands
~700 ms after takePicture (205 ms on a session's first shot),
`CaptureStatusListener`'s "mode" argument counts shots rather than naming
a capture mode, and the timed regression held under 0.2.5 with all
listeners armed - 10/10 stored at a 20 s exposure vs 15 s interval (every
shot held until the previous capture stored, spacing ~23.3 s). Still
unverified: whether stepping is accepted in P/A, and whether `takePicture`
returns immediately at Bulb so the app's `cancelExposure` timer owns the
exposure length - the short-bulb run is the next gate. A one-time
`getSupportedParameters()` dump per session catalogs every parameter key
this HAL accepts.

## Phase roadmap

| Phase | Scope | Gate (on-camera, A7 II) |
|---|---|---|
| 0 (this) | scaffold + rename + `.so` rebuild | preview + S2 still behave exactly like the starter demo |
| 1 | MVP: `IntervalEngine` fixed-grid scheduler, config menu (interval/count/delay), run overlay, S2 start / trash stop, APO-off | 30 min @ 30 s, zero misses; measure the real interval floor |
| 2 | trust: skip-on-overrun, failure abort, card guard, pause/resume, persistence, summary, rear-dial UI | 1 h endurance + card-full behavior |
| 3 | specialize (likely: battery/screen-off time-lapse track) | scenario end-to-end |

## Building

APK (Gradle 8.9 / AGP 8.5.2, JDK 17 pinned in `gradle.properties`):

    gradlew assembleDebug

Consume the sibling patched framework checkout (already carries the
modernized `framework`/`stubs` build.gradle - see `_deps-patches/`):

    settings.gradle points ':framework'/':stubs' at ../Blank/OpenMemories-Framework

No native code, no NDK step: the app never touches preview pixels, so the
starter's libjpeg-turbo decoder (`starter_jni.c` / `libstarter.so`) is
retired. The pristine pixel-path engine stays in `Blank/starting_package`
(reference if a per-pixel feature like focus peaking is ever wanted).

## Logging

`Logger` writes `/sdcard/INTVL/LOG.TXT` (rotation attempt to LOG.OLD.TXT;
this firmware rejects renames, sessions append). Pull the SD card to read it.
Camera open/preview lifecycle, S2 stills (timing + card path) and any errors
land there.

## Engine facts (do not fight them)

Full details: `Blank/starting_package/README.md`. The load-bearing ones:
Android 4.1.2 / API 10, no AndroidX; exposure is app-visible and -steppable
since 0.2.5 (`incrementShutterSpeed`/`decrementShutterSpeed` +
`ShutterSpeedChangeListener` for reading; whether steps are accepted still
depends on the camera's exposure mode), true bulb via step-past-30 s ended by
the app with `cancelExposure()`; before 0.2.5 the user set P/A/S/M before
launching. The SDK also has `setIntervalRecMode` + `startIntervalRec`/
`stopIntervalRec` (the firmware's native interval recording - what the paid
Time-lapse app wraps; future spike, not used by this app). **`Camera.Parameters` is NOT hollow** - the
starter README overgeneralized: only the LIST getters throw NPE; string-key
`get`/`set`/`flatten` work, and Recipe Lab drives the whole look pipeline
through them on this platform (its "monochrome" values in `color-mode` /
`picture-effect` are the likely cause of the B&W live view this app had).
Live view is the plain
HAL path (`CameraEx.open -> getNormalCamera().setPreviewDisplay ->
startPreview`, PMCADemo's CameraActivity pattern): the firmware renders into
the app's SurfaceView and the app never touches preview bytes. The
`CameraSequence` M-JPEG poll is only needed for pixel ACCESS (per-pixel
processing like IRSwap's channel swap) - the starter's "backdrop-only HAL
preview" claim was wrong; HAL is the display path and the JPEG->Bitmap decode
was pure overhead for this app.

Measured on A7 II (ILCE-7M2, 2026-11-13 session log):
- CPU CXD90014 reports no NEON (`simd: 0x0`) - irrelevant now that nothing
  decodes JPEG; noted for any future pixel work (libjpeg-turbo C fallback,
  ~94-126 ms per full-res frame).
- EVF<->LCD flip mid-preview survives (frames keep flowing, fps dips briefly).
- Stills are plain `takePicture` straight to the card (~1-2 s capture window;
  `setStoreImageCompleteListener` armed for shot logging: seq counter, elapsed
  ms, card path).
- Screen wake: `SCREEN_BRIGHTNESS_WAKE_LOCK` in BaseActivity.
- No touchscreen - keys only (UP/DOWN/LEFT/RIGHT/ENTER, S1/S2, trash, rear/
  front dials, FN/AEL/C1 via PMCADemo-style hooks when needed).

