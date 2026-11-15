# Intervalometer Feature Roadmap

_Created 2026-11-14 after the 0.2.10 no-bug verdict. Stripped 2026-11-14:
every shipped 0.2.x section deleted - one-line summaries per version live
in intervalometer/README.md, full detail in git history. What remains: the
live progress block, the next version (0.3.0 power saving), and the SDK
re-derivation notes._

_Progress: 0.2.11, 0.2.12 and 0.2.13 shipped and camera-tested, plus an
out-of-plan 0.2.14 (versionCode 20) fixing the aperture display once the
encoding round landed. Still owed on camera: the ISO echo filter (no ISO
writes since ffef940), ISO writes in Bulb, the bulb-exit self-check
("bulb exit confirmed" has never logged), and the bare-edit A/B for the
aperture unlock wrapper. Also never exercised: the 3-strike abort - a
2026-11-14 11:11 run logged six consecutive takePicture throws and still
finished `done, stored 1 of 10` (either a pre-0.2.6 build or the abort
never fires under real consecutive failures - re-verify deliberately).
Next: 0.3.0 power saving - versionCode 21 (20 was consumed by 0.2.14)._

All SDK facts below were verified against the compiled stubs — **not assumed**.
See "Re-deriving the SDK surface" at the bottom for how to re-check any of this.

---

## 0.3.0 (versionCode 21) — Power-saving run mode

**Goal:** make very long sessions (hours) viable on one battery. During a
power run everything not needed for capture goes dark: panel off, no live
view, no per-shot review flash. Any keypress wakes a minimal HUD for a few
seconds — `35/100` (or `152/∞`), battery %, and a warning when the card
can't hold all remaining shots — then the screen sleeps again. Trash still
stops the run while asleep; S2 still starts one.

### Design (outlined 2026-11-14)
- **Enable:** new config field `PWR` (ON/OFF, persisted like the other
  fields, default OFF). One start path (S2); the toggle decides the run
  flavor. Rejected alternative: a second start key — grid fields are
  visible persistent state, and two start buttons invite "which mode am I
  in?" bugs.
- **Run start (PWR ON):** log the current display state (the
  DisplayManager.Listener data BaseActivity already receives), stop
  preview, `setActiveDisplay(Display.NONE)`, then set the auto-picture
  review control once (probe `getPictureReviewSupportedTimes()` first,
  log what sticks). APO-off renewal stays exactly as today — the system's
  own display-off/APO must never fight the dark panel either.
- **During the run:** overlay suppressed; no per-tick UI work while
  asleep. Timed shots skip the `resumePreview()` between captures IF gate
  G2 passes (see below) — otherwise keep the normal cycle and bank the
  backlight savings alone. Every stored shot logs its battery % (sticky
  `ACTION_BATTERY_CHANGED`) — the session log becomes the power-curve
  measurement for free.
- **Wake HUD:** first keypress wakes only (pocket-press protection);
  auto-sleep after 5 s (POWER_WAKE_MS constant, tunable); while the HUD
  is awake keys act normally — trash stops (second press), other keys
  refresh the 5 s timer. S2 stays ignored during the run, as today.
- **Storage warning:** computed at wake only (cheap: one StatFs + one
  File.length() per wake, not per tick): free bytes / avg shot size
  (last stored file, path from the existing storeImage log) = `full in
  ~N shots`; HUD warns when N < remaining shots, or under an absolute
  floor (< 10) for `∞` runs.
- **Run end / stop / pause:** display restored to the pre-run state
  BEFORE the summary shows. Review control: log whether it survives
  pause (the camera may reset per session) — don't fight it if so.

### What we already know
- **Real LCD-off exists:** framework `DisplayManager.setActiveDisplay(Display.NONE)`
  (ma1co wrapper over `com.sony.scalar.hardware.avio.DisplayManager`,
  `DEVICE_ID_NONE`). The app already depends on this class — `BaseActivity`
  implements `DisplayManager.Listener` (display-change events arrive today,
  e.g. the `Display changed: FINDER` log lines). `Display` enum includes
  SCREEN / FINDER / HDMI / NONE.
- **Shot preview can be suppressed:** `camera.setAutoPictureReviewControl(
  CameraEx$AutoPictureReviewControl)` (CameraEx.j:279); the control object offers
  `cancelAutoPictureReview()`, `setPictureReviewTime(I)`, `getPictureReviewTime()`,
  `getPictureReviewSupportedTimes()[I`, `getReviewType()I`, and constants
  `PICTURE_REVIEW_TYPE_SINGLE=0`, `PICTURE_REVIEW_TYPE_10=2`,
  `PICTURE_REVIEW_TYPE_INVALID=-1`.
- **Live view off is one existing call:** `stopPreview()`
  (`IntervalActivity.java:606`); the only change is *skipping* the automatic
  `resumePreview()` after each shot (shutter-release path currently always
  restarts it).
- **Battery:** no SDK percent getter. Percent via standard Android sticky
  `ACTION_BATTERY_CHANGED` (`LEVEL`/`SCALE`) — the camera is Android 4.1.2.
  Bonus: `com.sony.scalar.sysutil.didep.Power` — `getInstance()`,
  `setBatteryRemainTimeListener(BatteryRemainTimeListener)` with
  `onChanged(I)` (remaining **time**, `-1 = INVALID_BATTERY_REMAIN`), `release()`.
- **Storage:** plain `android.os.StatFs` on the external-storage root (GB free).
- **Auto-power-off plumbing already exists** (`apoOff()/apoRestore()` around
  runs; `Settings.getAutoPowerOffTime()/setAutoPowerOffTime(I)`,
  `getDispMode(I)` in Settings.j) — power mode should keep APO off during runs
  and NOT let the system's own display-off fight ours.
- Engine is untouched by this feature: absolute grid, hold-when-busy, overlay
  lines keep updating; in power mode the overlay is simply hidden while the
  panel is off.

### Implementation sketch
- Files: `IntervalActivity.java` (PWR toggle wiring, display set/restore
  around the run, wake HUD + 5 s timer, skip-preview variant of the shot
  cycle per G2, per-shot battery log line), `IntervalConfig.java`
  (persist PWR), `app/build.gradle` (0.3.0 / versionCode 22 - 21 was 0.2.15), `README.md`.
- `IntervalEngine` untouched — power mode is activity-side.
- Native interval recording (`startIntervalRec` — firmware owns the loop,
  README spike notes) stays the separate battery-friendly track it
  already was; NOT part of 0.3.0 (no per-shot logging or control there).
- Minor version bump justified: changes run-time behavior, not just config.

### Camera-test gates (risk order; must pass before shipping)
- **G1 keys-while-dark:** hardware keys still reach the activity with
  Display.NONE active — the whole wake interaction hangs on this.
  Fallbacks if not: wake via S1 half-press, or minimum panel brightness
  instead of NONE (less saving, still something).
- **G2 capture-without-preview:** timed shots complete without the
  cancelTakePicture + startPreview cycle between them (v0.2.4 showed
  skipping the *release* bricks shot 2+; is resume equally required?).
  Bulb runs don't resume preview anyway (cancelExposure ends the shot).
- **G3 display restore:** NONE → remembered state, no stuck EVF/HDMI
  (Display.Listener events already arrive — watch them during the test).
- **G4 review suppression:** stills still store + seq increments with the
  review cancelled (cancel must not drop the file).
- **G5 perturbation:** does display-off or the review control disturb the
  exposure listeners / Bulb position detection (the 0.2.12 interaction
  question, now with a second writer in the mix)?
- **G6 battery numbers:** sticky % values sane on this firmware (optional
  Sony Power remain-time listener as a bonus readout); StatFs path
  correct on-device.

### Camera-test round
- G1..G6 in order; then one real long run (60-shot Bulb or 30 s x 60) in
  PWR mode: wake HUD correct (shots/battery/storage), trash-stop works
  from dark after a wake press, summary + display restore, log shows the
  per-shot battery curve (the %/h delta vs a display-on run is the proof
  the feature works).

Results (2026-11-15, shipped build): G1-G5 passed, G6 partial.
- G1/G3: two clean 5-shot PWR runs - wake gate consumed the key,
  wake/sleep at exactly 5 s, display restored to SCREEN, review never
  armed (the S2 cancel NPE is cosmetic).
- G2/G4: every dark-run shot stored with seq increments.
- G5: run armed at a dialed 1/15 s, again at 1/15 s after a full app
  restart, and the UP/DOWN shutter cascade echoed 2.5 s -> 1/60 s before
  a final run armed at 1/60 s.
- Trash-stop from the wake HUD: four abort runs stopped mid-stage,
  summary + restore correct each time, app survived every wake press
  (the wake key's own release stays inert).
- G6: per-shot battery % sane (94 -> 93% over ~13 min); the real %/h
  long run (count=all, trash-stop at end) is still owed.
- The round also caught a freeze: with the panel dark, a key handler
  threw and killed the main looper (app alive, keys dead) - fixed with a
  catch-and-log shield on the key dispatch; zero shield hits in every
  run after.

---

## Deferred hygiene (audit 2026-11-15)

The 2026-11-15 hygiene pass landed the mechanical items (volatile HAL
mirror fields, monotonic clocks for stillT0 + probe windows, onResume
split into registerCameraListeners + finishCameraOpen) as a no-bump
commit. Deferred from that audit, as too big or on-camera-dependent:

- **Split IntervalActivity (~1.4k lines, six subsystems in one class).**
  The real structural debt: extract the ISO control (list + read-back
  writes), the aperture control (unlock wrapper + probe restore), the
  exposure mirror + probe restore, and the capture runner (fireCapture +
  release runnables + watchdog + stats) into their own classes; the
  activity keeps key routing, the config grid, the run lifecycle.
  Behavior-preserving in principle, but it reshapes every log line's
  context — do it in 0.2.x-style camera-verified rounds, not blind.
- **Camera-open failure UX.** onResume's open-failure catch is log-only:
  the config grid stays painted with the camera-side fields silently
  dead (stepping them logs caught NPEs). Needs an overlay error line +
  a decision on which keys stay live — on-camera round required (the
  realistic trigger is the stock camera app holding the device).
- **Small dedups (only if a round touches the code anyway):**
  probeRestore/apertureProbeRestore are algorithm twins (~45 lines
  each; unifying needs generics/lambdas this codebase avoids), one
  write-verified helper for irisUnlock/irisRestore, a hudTitle helper
  for the 4 "Intervalometer v..." overlay builds, the shared
  bulb-freeze line in startRun/shotNow.

---

## Re-deriving the SDK surface (for future sessions)

- Stubs live in `Blank/OpenMemories-Framework/stubs/`. The **sources are jasmin
  assembly text** (readable signatures): `stubs/src/main/jasmin/com/sony/scalar/...`
  — e.g. `hardware/CameraEx.j`, `sysutil/didep/Power.j`, `sysutil/didep/Settings.j`.
- No `javap` on this machine (only a JRE 1.8 installed — `C:\Program Files\Java\
  jre1.8.0_481`; no JAVA_HOME). Use PowerShell instead:
  `Select-String -Path <file>.j -Pattern '^\.method'` lists every method with its
  exact JNI signature; drop the `^` anchor to search method bodies/strings.
- Compiled jar (what the app actually links):
  `Blank/OpenMemories-Framework/stubs/build/libs/stubs.jar` (320 KB). List classes
  via `System.IO.Compression.ZipFile::OpenRead(...).Entries.FullName`.
- `CameraEx` has 119 public methods total; inner classes (listeners + info
  holders) are the read-back mechanism for anything without a getter.
- Runtime ground truth for param keys: the per-session `getSupportedParameters()`
  dump in LOG.TXT (search `params[` — `iso-sensitivity-values`,
  `shooting-preview-values`, etc.).
- LOG.TXT used to be cumulative (append-on-rotation-failure) — purged from
  the device 2026-11-15 once its conclusions were banked; future pulls are
  fresh-only. Historical snapshots live in git history through `5103f9f`
  (untracked since `2b2e9a4`). The 0.2.10 "late 8303 ms" snowball was ruled
  user-caused (UI fields 2 s/2 s vs saved 5 s config), engine correct —
  see 0.2.10 session notes.
