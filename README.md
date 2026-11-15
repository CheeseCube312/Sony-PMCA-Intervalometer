# Intervalometer — PMCA timed-sequence shooting app (Sony A7 II)

Built-in intervalometer for the Sony Alpha 7 II (ILCE-7M2, PlayMemories Camera
Apps platform, API 2.10). The A7 II has no native interval shooting (only the
paid Time-lapse app), so this app fills that gap: fixed-grid timed stills
straight to the card, with a session log for drift/failure analysis.

**Status: 0.3.1 shipped and camera-gated 2026-11-15 (DEBUG log gate passed:
OFF → skeleton-only log, ON → verbose forensics, the flag persists across
restart; zero errors on camera; in-app ISO writes accepted and the echo
filter held). Everything through 0.3.1 is verified. Open work - owed
camera runs and deferred hygiene - is tracked in roadmap.md.**

## Repository layout

Tracked: this document (covers the whole workspace), `intervalometer/`
(the app project - Gradle, Java), `roadmap.md` (living plan: owed
on-camera runs, deferred hygiene) and `.clinerules/` (assistant working
conventions).

Deliberately NOT tracked: `recipe-lab-sony-pmca/` (third-party Recipe Lab
reference app - upstream clone), `Blank/` (PMCA framework demo clones and
the original starting package), `LOG.TXT` / `logcat-INTVL.txt` /
`session-logs/` (per-session camera pulls - the evidence behind every
engine decision; they churn, so committed snapshots through `5103f9f`
remain in history), and build artifacts / `.gradle/` / `local.properties`
(regenerated per machine).

## Version history (one line each)

- 0.1.0 - starter smoke test retargeted to INTVL (preview + S2 still on camera).
- 0.1.1 - live view switched to the plain HAL path; NDK/M-JPEG decode retired.
- 0.1.3 - mono root cause: Creative Style stuck on `mono`; force `standard` + `picture-effect=off` at startPreview (confirmed on camera).
- 0.1.4 - drop the color-depth A/B toggle (the mono look sits above the display pipeline).
- 0.2.0 - Phase 1: fixed-grid engine (late shots fire + log their delta, slots never skipped), config grid, APO-off + keep-screen-on, trash-stop summary.
- 0.2.2-0.2.4 - trustworthy multi-shot: full shutter cycle per frame, interval as minimum spacing (slots hold behind a live capture), summary waits out an in-flight last shot, 90 s never-stored watchdog (tag `v0.2.4-mvp`).
- 0.2.5 - exposure stepping + event-read, true Bulb (65535 sentinel confirmed), `cancelExposure()` ends exposures (tag `v0.2.5-mvp`).
- 0.2.6 - Phase 2: persisted config (restarts + power cycles), extended presets + Custom slots, 3-strike reject abort, hold warning.
- 0.2.7 - UX: run-end gap owned + config polish; Exposure field = stock timed list + one custom long exposure.
- 0.2.8 - Bulb exit via incrementShutterSpeed (decrement is a firmware no-op) + probe drift fix.
- 0.2.9 - decimal seconds for the slow fraction speeds.
- 0.2.10 - self-verifying Bulb exit: confirm the timed callback within 1.5 s, re-arm Bulb on timeout.
- 0.2.11 - Shot now (single capture at current exposure); count ladder 5..600 + unlimited.
- 0.2.12 - ISO field walks the camera's supported ISO list, every write read-back verified; auto-ISO echo filter (ffef940).
- 0.2.13 - aperture control: increment/decrement + ApertureChangeListener mirror, edits wrapped in the iris unlock wrapper.
- 0.2.14 - encoding round settled: raw = f-number x100; grid display fixed (tag `v0.2.14`).
- 0.2.15 - S2 abort tightened: a press after the firing one quits an in-flight capture early and stops the run at its current stage (summary prints); the firing press's own release is exempt, so taps can't eat a delay-0 first shot or a mid-store single.
- 0.3.0 - power-saving run mode (PWR): runs start dark (live view stopped, `Display.NONE`, review off), any key wakes a 5 s HUD (taken/planned, battery %, storage estimate); pre-run display restored before the summary; stored shots log battery %. Plus the key-dispatch crash shield (a handler throw logs instead of killing the main looper). Camera-gated (G1-G5).
- 0.3.1 - file-by-file review round: two-tier debug log gate - config-grid DEBUG field (persisted, default OFF); OFF = essential log (session header, device, config, run start/end, per-shot fired/stored, aborts, wake chain, errors), ON adds camera forensics, arms live, the session header names the mode - plus audit fixes: held-slot tick polls at 250 ms instead of spinning delay-0 through camera-busy holds, the in-flight tick no longer runs after a 3-strike abort, "aperture step rejected" bumped to ERROR; dead code dropped (`currentDisplay` mirror, `Logger.isDebug`).

## Key findings

**Look / mono (0.1.x).** Recipe Lab drives the Sony look keys over
`Camera.Parameters` right after startPreview; on this body `color-mode=mono`
(Creative Style) or a mono `picture-effect` renders preview AND firmware
stills monochrome (only inside the app's JPEG pipeline - RAW never shows
it). The setting survives power cycles in the camera's settings store and
never self-repairs, so the app re-forces `color-mode=standard`,
`picture-effect=off`, `rgb-matrix-mode=false` on every open and logs
`look before/after`. The mono look sits above the display pipeline (color
depth is irrelevant).

**Engine (0.2.0).** Fixed grid: every tick recomputes shot n at
t0 + delay + n*interval; late shots still fire and log their delta - slots
are never skipped, so the log shows the real interval floor. Runs hold the
camera awake (APO-off broadcast renewed per minute + keep-screen-on); trash
or a second S2 press (0.2.15) stops with a summary, second trash exits;
onPause stops the run. Summary logs stored-vs-fired and capture min/avg/max.

**Multi-shot trust (0.2.2-0.2.4).** The interval is a MINIMUM spacing: a
slot holds while the previous capture is in flight (firing into a busy
camera silently loses both shots - v0.2.3 lost 8/10; v0.2.4 verified 10/10).
Full shutter cycle per frame: takePicture, then cancelTakePicture +
startPreview ~2.5 s later - skipping the release bricks shot 2+. Summary
waits out an in-flight last capture; a 90 s cap marks a never-storing
capture lost. The overlay warns `camera busy - shot N waits` while held.

**Exposure + true Bulb (0.2.5+).** The SDK steps shutter speed
(`incrementShutterSpeed`/`decrementShutterSpeed` - software state; the dials
drive the same value) and can only READ it via `ShutterSpeedChangeListener`
(no getter). Bulb is the 65535 (0xFFFF) sentinel; UP/DOWN at the Bulb end
stop edit the app-side bulb duration, DOWN below the shortest timed step
exits back into the timed list. Bulb exit goes via incrementShutterSpeed
(decrement is a firmware no-op) and is self-verifying: confirm the timed
callback within 1.5 s, re-arm Bulb on timeout. Field-proven 2026-11-15: the
first on-camera Bulb cycle needed the re-arm and it worked. Bulb exposures end with
`cancelExposure()` (the SDK's exposure-end primitive; `cancelTakePicture`
is the timed release, ignored mid-exposure), and `takePicture` returns
immediately at Bulb (~700 ms), so the app timer owns the exposure length.
In-flight watchdog + summary deadline scale with the bulb duration;
ShutterListener / CaptureStatusListener / ExposureCompleteListener are
armed log-only as ground truth. Unverified: whether stepping is accepted
in P/A. A one-time `getSupportedParameters()` dump per session catalogs
every parameter key this HAL accepts.

**Persistence (0.2.6).** `getPreferences(MODE_PRIVATE)` (Recipe-Lab-proven):
survives app restarts and camera power cycles, dies on uninstall; lists are
append-only and indices clamp on load, so old persisted choices stay valid.
Exposure position is deliberately NOT persisted - the camera owns its
shutter-speed store.

**3-strike abort + hold warning (0.2.6).** 3 consecutive `takePicture`
throws stop the run with reason `failed` (once the HAL starts rejecting,
every following slot rejects too); any success resets the counter. Neither
mechanism has ever fired on camera - see roadmap.md.

**ISO (0.2.12).** String param `iso-sensitivity-values` (0 = AUTO), read at
open; writes are verified by read-back only - a dropped value throws
nothing. Auto-ISO listener echoes land 100-330 ms after each write, so
events inside a 600 ms post-write window are skipped (echo filter ffef940).
Field-proven 2026-11-15: five in-app writes, all kept, no echo escaped.

**Aperture (0.2.13/0.2.14).** `increment/decrementAperture` +
`ApertureChangeListener` mirror (event-only, no getter; open probe pair +
bounded corrective steps - the 0.2.5 shutter pattern). Edits wrap the step
in `shooting-preview=iris_ss_iso_aeunlock` (set, read-back verified,
restored ~1 s after the last edit or on pause) so the iris moves even when
the body owns it. Raw encoding = f-number x100 (560..3600 = f/5.6..f/36,
1/3-stop steps); the open probe netted zero; the wrapper stays until a
bare-edit A/B says otherwise.

**Mid-run input lock (verified 2026-11-15).** During a run every direction
key gates on `busy()` (engine running, summary shown, run ending, capture in
flight), so the field cursor cannot reach ISO/F/Exposure and UP/DOWN cannot
reach `adjust()` - the only route to any exposure write. ENTER is a no-op;
PWR-asleep presses only wake the HUD. The per-shot capture path
(`takePicture`, `startBulb` → `cancelExposure`) touches no exposure
parameter, and the run anchors (bulb duration, PWR display state) freeze at
run start; the bulb-exit confirm only flips the app-side field. S2 and trash
stay live deliberately - they are the abort controls. The body's
ISO/shutter/aperture wheels are continuous encoders (only the mode dial has
detents) and the HAL offers no input lock (`ae-lock` is moot - the app never
writes exposure mode), so a physical nudge mid-run is unblockable; it
surfaces only at DEBUG tier, so EXIF is the evidence in a skeleton-only log.

## Building

APK from `intervalometer/` (Gradle 8.9 / AGP 8.5.2, JDK 17 pinned in its
`gradle.properties`):

    gradlew assembleDebug

The sibling patched framework checkout (already carries the modernized
`framework`/`stubs` build.gradle - see `intervalometer/_deps-patches/`):

    settings.gradle points ':framework'/':stubs' at ../Blank/OpenMemories-Framework

No native code, no NDK step: the app never touches preview pixels, so the
starter's libjpeg-turbo decoder (`starter_jni.c` / `libstarter.so`) is
retired. The pristine pixel-path engine stays in `Blank/starting_package`
(reference if a per-pixel feature like focus peaking is ever wanted).

## Logging

`Logger` writes `/sdcard/INTVL/LOG.TXT` (rotation attempt to LOG.OLD.TXT;
this firmware rejects renames, sessions append). Pull the SD card to read it.
The device copy was purged 2026-11-15 - pulls are fresh-only now; historical
cumulative snapshots live in git through `5103f9f` (untracked since `2b2e9a4`).
Camera open/preview lifecycle, S2 stills (timing + card path) and any errors
land there. The camera's wall clock resets to 1970-01-01 on a power cycle,
so absolute LOG.TXT timestamps are only comparable within one powered
session - the run-relative times (`fired @ +mm:ss`, planned) are unaffected.

Two tiers (0.3.1): ERROR (crash shields, watchdog, failures) and the INFO
skeleton always log; the DEBUG tier (params dump, probes, listener mirrors,
per-shot capture values, step echoes, review/iris internals) only while the
config grid's DEBUG field is ON. The flag persists across restarts and
power cycles like every config choice and arms live on toggle; the session
header names the mode. To troubleshoot a failure: DEBUG ON, reproduce,
pull LOG.TXT, DEBUG OFF. Gate sanity check (0.3.1): a 10-shot PWR run logs
~37 lines with DEBUG off, 250+ with it on.

## Device and platform facts (do not fight them)

Full details: `Blank/starting_package/README.md`. The load-bearing ones:
Android 4.1.2 / API 10, no AndroidX. `Camera.Parameters` is NOT hollow -
only the LIST getters throw NPE; string-key `get`/`set`/`flatten` work.
Live view is the plain HAL path (`CameraEx.open ->
getNormalCamera().setPreviewDisplay -> startPreview`): the firmware renders
into the app's SurfaceView and the app never touches preview bytes; the
`CameraSequence` M-JPEG poll is only needed for pixel ACCESS (per-pixel
processing). Native `startIntervalRec` exists but stays unused (see the
spike notes below).

Measured on A7 II (ILCE-7M2, 2026-11-13 session log):
- CPU CXD90014 reports no NEON (`simd: 0x0`) - irrelevant now that nothing
  decodes JPEG; noted for any future pixel work (libjpeg-turbo C fallback,
  ~94-126 ms per full-res frame).
- EVF<->LCD flip mid-preview survives (frames keep flowing, fps dips briefly).
- Stills are plain `takePicture` straight to the card (~1-2 s capture window;
  `setStoreImageCompleteListener` armed for shot logging: seq counter, elapsed
  ms, card path).
- Screen wake: `FLAG_KEEP_SCREEN_ON` on the activity window during runs - no wake lock anywhere.
- No touchscreen - keys only (UP/DOWN/LEFT/RIGHT/ENTER, S1/S2, trash, rear/
  front dials, FN/AEL/C1 via PMCADemo-style hooks when needed).
- Native interval recording (separate spike APK, 11-14): needs
  scene-mode=M; `startIntervalRec` snaps the period into firmware units
  (10 s sent -> 10000 ms confirmed), count is ignored - it runs until S2;
  the supported-intervals getter returns null. Firmware owns shutter +
  storage: zero app work per frame, but no per-shot logging or control.
  Candidate for a future battery-friendly track.

## Re-deriving the SDK surface

All SDK facts in this file were verified against the compiled stubs - not
assumed. To re-check any of it:

- Stubs: `Blank/OpenMemories-Framework/stubs/`. The **sources are jasmin
  assembly text** (readable signatures):
  `stubs/src/main/jasmin/com/sony/scalar/...` - e.g. `hardware/CameraEx.j`,
  `sysutil/didep/Power.j`, `sysutil/didep/Settings.j`.
- No `javap` on this machine (JRE 1.8 only, no JAVA_HOME). Use PowerShell:
  `Select-String -Path <file>.j -Pattern '^\.method'` lists every method with
  its exact JNI signature; drop the `^` anchor for method bodies/strings.
- Compiled jar (what the app actually links):
  `Blank/OpenMemories-Framework/stubs/build/libs/stubs.jar` (320 KB); list
  classes via `System.IO.Compression.ZipFile::OpenRead(...).Entries.FullName`.
- `CameraEx` has 119 public methods; inner classes (listeners + info
  holders) are the read-back mechanism for anything without a getter.
- Runtime ground truth for param keys: the per-session
  `getSupportedParameters()` dump in LOG.TXT (search `params[` -
  `iso-sensitivity-values`, `shooting-preview-values`, ...).

