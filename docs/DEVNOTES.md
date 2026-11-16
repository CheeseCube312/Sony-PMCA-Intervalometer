# Intervalometer — development notes

Engineering companion to the project `README.md`: camera and platform
facts, the Sony Scalar SDK surface as verified against the compiled stubs,
the session log format, and the full one-line-per-version history. Open
work lives in `roadmap.md` (kept local, untracked). Latest shipped:
0.4.0h (tag `v0.4.0h`).

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
- 0.4.0 - UI pass: the config screen is an auto-collapsing dropdown menu - a tab bar (RUN PLAN / EXPOSURE / SINGLE SHOT / SETTINGS) along the top picked with LEFT/RIGHT, DOWN opens a category's items and walks them, LEFT/RIGHT adjusts the value, UP backs out to the tabs, and the contextual hint + key legend pin to the screen's bottom edge instead of riding under the walked items; after ~4 s of key silence only the tab bar stays, keeping the live view clear for framing and focus. Hold-ENTER help cards for every item and tab (any key closes, the closing press's release is swallowed), `BULB n s` exposure wording with honest custom-interval formatting, guard-windowed breakover transitions in both directions (RIGHT crosses the 30 s end stop onto BULB 5 s and climbs the 5/30 s steps, LEFT walks back and exits at the floor, and held keys can no longer bounce 30 s <-> BULB while a callback is in flight), VERSION info in SETTINGS instead of a title row, and a camera-open-failure card instead of a dead grid.
- 0.4.0b - interval naming: the custom slot's 30 s steps above 2 min read 2.5 min, 3 min, 3.5 min... instead of flipping between seconds and minutes; INTERVAL help card states the custom rule. Version letter-bumped per UI tweak (0.4.0b, 0.4.0c...) so the VERSION field identifies the installed build.
- 0.4.0c - value editors: CUSTOM became a plain entry in both lists (no more in-place 5 s/30 s stepping - LEFT/RIGHT only selects it); an ENTER tap on it opens a full-screen editor, h/m/s units for the interval (cap raised to 99:59:59) and a four-digit wheel for a new CUSTOM shot count (1-9999, persisted under a fresh `countSlot2` key, sitting before `all`); a dim `series ~` line on RUN PLAN estimates the total run time (delay + shots x cadence, cadence floored at exposure + ~3 s of card store - the engine holds a slot behind a live capture, so long exposures stretch the spacing), also logged at run start; off-grid custom values read `3 min 17 s` instead of raw seconds, and fmtDur learned half hours (1.5 h).
- 0.4.0d - linger fixes: the open dropdown waits 10 s of key silence before collapsing (was 4 s - it folded away mid-thought); a selected CUSTOM interval reads `INTERVAL custom 45 s` instead of a bare value so the slot is unmistakable; the value editor never auto-collapses (the idle timer armed before it opened used to repaint the grid over it mid-edit) - ENTER saves or trash cancels, nothing else closes it.
- 0.4.0e - naming: the PWR field reads `POWER SAVING OFF/ON` instead of `PWR OFF` - the old label said nothing about what it switched; the field's help card and the SETTINGS tab card use the same name.
- 0.4.0f - bulb border rework (kills the residual 30 s <-> BULB ping-pong): BULB is a slot, not a value - LEFT/RIGHT only cross the timed<->BULB border (one blind increment per crossing, optimistic claim + matching-callback confirm; a stale echo can no longer overwrite a pending claim mid-window), ENTER opens a m/s editor on the BULB position exactly like the CUSTOM slots (no hours; 5 s - 59 min 59 s cap - the body sets no bulb ceiling, battery/heat are the real limits), the 5 s floor handshake and its value reset/restore are gone (the stored duration rides across the border untouched), run start and shot now adopt any still-unconfirmed claim before freezing `bulbShotMs`, and the display formats like the interval grid (`BULB 4 min 30 s`).
- 0.4.0g - exposure direction flip: RIGHT always lengthens and LEFT shortens, like every other field (mid-list the timed walk runs opposite the body's dial - decrementShutterSpeed steps toward longer, incrementShutterSpeed toward faster, as the probe-correction path already assumed), so the same keypress now walks through the 30 s end stop onto BULB (RIGHT) and back off it (LEFT); the border toggle itself stays incrementShutterSpeed both ways, unchanged and field-proven.
- 0.4.0h - bulb entry fixed, superseding 0.4.0g's border belief: the shutter list is one straight line walked by decrementShutterSpeed toward longer, breakover included - at the 30 s end stop incrementShutterSpeed steps BACK into the list (30 s -> 25 s) and Bulb never echoes (0.4.0g field log: 6/6 entry attempts landed on 25 s and self-reverted ~2 s later; the Nov 15 "two-way increment border" was a misread - those Bulb echoes came from the body's dial and a plain-branch decrement leak), so the entry toggle is now decrementShutterSpeed (the SAME longer-direction step as the whole timed walk) and RIGHT walks 30 s onto BULB coherently; exit stays incrementShutterSpeed (Bulb -> 30 s, field-proven since 0.2.8) and the optimistic-claim + 1.5 s self-check machinery is unchanged. Field-verified: BULB entry sticks.
- 0.4.0i - tab-bar rule only while the dropdown is open: the labelled rule under the tabs read as a divider between tabs and items, but once the menu collapsed it floated alone after SETTINGS - a 2 px gray stub that looked like a stray underscore in the top right corner over the live view. Collapsed the bare tab bar now draws no rule.
- 0.4.0j - tab-bar rule dropped entirely: 0.4.0i kept it while the dropdown was open as a tabs/items divider, but field check said the stub from SETTINGS to the screen edge still read as a stray underscore there too - the top row is rule-free in every state.

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
(no getter). Bulb is the 65535 (0xFFFF) sentinel. The shutter list is one
straight line walked by decrementShutterSpeed toward longer: mid-list it
lengthens (the probe corrections knew all along), and at the 30 s end stop
it crosses ONTO Bulb; incrementShutterSpeed walks back toward faster,
landing on 30 s from Bulb (field-proven 0.2.8/0.2.10 exits; decrement from
Bulb is a firmware no-op - Bulb is the terminus). The 0.4.0-era "two-way
increment breakover" was a misread of the Nov 15 session: the 0.4.0g field
log disproved it - increment at 30 s stepped back to 25 s 6/6 times and
Bulb never echoed (those old Bulb echoes came from the body's dial and a
plain-branch decrement leak, not the entry call). 0.4.0h aligned the border
with the line: entry decrement, exit increment.
0.4.0f design: BULB is a slot, not a value -
LEFT/RIGHT only cross the border (one blind border step per crossing, claimed
optimistically, confirmed only by the MATCHING callback - a stale echo of
the side being left is ignored mid-window; the 1.5 s timeout settles a
swallowed step), and the duration lives behind ENTER's m/s editor exactly
like the CUSTOM slots (5 s - 59 min 59 s; the body sets no bulb ceiling,
battery/heat are the real limits). The 0.4.0 in-place stepping + 5 s floor
handshake is what still ping-ponged the border: the entry reset the value
to 5 s while stale HAL echoes repainted the optimistic claim, so the
display and the mirror fought across the break. Run start and shot now
adopt any still-unconfirmed claim before freezing `bulbShotMs` (no getter
to verify against). Field-proven 2026-11-15: the first on-camera Bulb
cycle needed the exit re-arm and it worked. Bulb exposures end with
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
flight), so the item walk cannot reach ISO/F/Exposure and LEFT/RIGHT cannot
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

APK from `intervalometer/` (Gradle 8.9 / AGP 8.5.2, JDK 17 via JAVA_HOME):

    gradlew assembleDebug

The OpenMemories framework/stubs modules are vendored under
`intervalometer/third_party/OpenMemories-Framework/`, with build.gradle
files already patched for this toolchain (see the vendored README):

    settings.gradle points ':framework'/':stubs' at third_party/OpenMemories-Framework

No native code, no NDK step: the app never touches preview pixels, so the
starter's libjpeg-turbo decoder (`starter_jni.c` / `libstarter.so`) is
retired. The pristine pixel-path engine stays in the dev workspace's
`Blank/starting_package` (untracked; reference if a per-pixel feature like
focus peaking is ever wanted).

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

Full details: the dev-workspace starting-package README (untracked here).
The load-bearing ones:
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

- Stubs: `intervalometer/third_party/OpenMemories-Framework/stubs/`
  (vendored from ma1co/OpenMemories-Framework). The **sources are jasmin
  assembly text** (readable signatures):
  `stubs/src/main/jasmin/com/sony/scalar/...` - e.g. `hardware/CameraEx.j`,
  `sysutil/didep/Power.j`, `sysutil/didep/Settings.j`.
- No `javap` on this machine (JRE 1.8 only, no JAVA_HOME). Use PowerShell:
  `Select-String -Path <file>.j -Pattern '^\.method'` lists every method with
  its exact JNI signature; drop the `^` anchor for method bodies/strings.
- Compiled jar (what the app actually links):
  `intervalometer/third_party/OpenMemories-Framework/stubs/build/libs/stubs.jar`
  (built locally, 320 KB); list
  classes via `System.IO.Compression.ZipFile::OpenRead(...).Entries.FullName`.
- `CameraEx` has 119 public methods; inner classes (listeners + info
  holders) are the read-back mechanism for anything without a getter.
- Runtime ground truth for param keys: the per-session
  `getSupportedParameters()` dump in LOG.TXT (search `params[` -
  `iso-sensitivity-values`, `shooting-preview-values`, ...).

