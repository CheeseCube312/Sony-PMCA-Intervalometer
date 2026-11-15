# Intervalometer Feature Roadmap

_Created 2026-11-14 after the 0.2.10 no-bug verdict. Base: 0.2.10, versionCode 16,
tree clean at `5ee6842`. Order agreed: 0.2.11 → 0.2.12 → 0.2.13 → 0.3.0, one commit +
one camera-test round per version (house cadence). Delete this file when done._

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

## 0.2.11 (versionCode 17) — Capture UX: single shot + count rework — SHIPPED

**Outcome:** shipped and camera-verified (single shot, the 5-step count
ladder, trash-stop of unlimited runs, count-slot persistence). The
bulb-exit self-check carry-over in the test plan below was NOT closed here -
it has still never logged.

**Goal:** standalone one-shot capture at current exposure (dark frame / preview tool),
and shot-count setting in 5-shot increments + an unlimited (∞) mode.

### What we already know
- **Single shot needs zero new camera code.** The per-shot capture path exists and is
  proven: `takePicture` (S2 down) → ~`SHUTTER_MS` later `cancelTakePicture` + preview
  resume (see the shutter-release code at `IntervalActivity.java:181-200` and
  `resumePreview()` at :623). Bulb shots instead end via `cancelExposure()` at the
  bulb duration (`startRun()` freezes `bulbShotMs`, :935). A single shot = the same
  routine, triggered outside a run. Full logging (`exposure complete`, `storeImage
  done - seq …`) comes free via the existing listeners.
- **Engine already supports unlimited.** `IntervalEngine.java:54`
  `shotCount <= 0 = unlimited`; `start()` logs "count unlimited" (:86); the done-check
  `fired >= shotCount` is skipped for `shotCount <= 0` (:136). **No engine change
  needed** — HUD/summary rendering of `x/∞` is activity-side only.
- **Config screen is a 4-field grid** (`showConfig()`, `IntervalActivity.java:635-651`):
  `Interval | Count` / `Exposure | Delay`, `selField` moved by LEFT/RIGHT, values by
  UP/DOWN, S2 starts a run, trash exits, contextual hint line. Adding fields means
  extending this grid + the `mark(field, …)` indexing.
- **Count editing has a slot-list precedent:** the Interval field already uses a
  slot list + CUSTOM slot pattern (`config.intervalIsCustom()`, :637). Count can
  mirror that: slots stepping in 5s plus a final ∞ slot (persisted as 0).
- S2 during a run is ignored (mid-run guard at :865-866); summary is dismissed by S2
  (:867-872). The single-shot trigger must slot into these guards, not around them.

### Implementation sketch
- Files: `IntervalActivity.java` (grid + key handlers + single-shot routine —
  refactor the engine listener's fire path so run shots and the single shot share
  one `fireShot`-style method), `IntervalConfig.java` (count slots/unlimited),
  `app/build.gradle` (0.2.11 / versionCode 17), `README.md` changelog.
- UI proposal (finalize at implementation): grid grows to 6 fields —
  `Interval | Count` / `Exposure | Delay` / `ISO placeholder | Shot now` — or
  "Shot now" as a dedicated selectable line under the grid. Selecting it + S2 =
  one capture, stay in config. Rejected (with hint) while a run is in flight,
  in the run-ending gap, or while a summary is up. Honors Bulb-custom seconds
  (app-side `cancelExposure` at the frozen bulb duration, same as engine shots).
- Count field: UP/DOWN steps ±5 through 5,10,15,… ; last slot = `∞` (stored 0).
  HUD `23/120`, unlimited `23/∞`; trash still stops unlimited runs;
  `REASON_DONE` never fires for unlimited.

### Camera-test round (also close the carry-over!)
- **Bulb-exit self-check (0.2.10 carry-over, still unverified):** UP into Bulb,
  DOWN out → expect `bulb exit confirmed: 30 s` in the log.
- Single shot in timed mode: one `takePicture`, one store, preview resumes.
- Single shot in Bulb-custom: exposure ends at the configured seconds (`cancelExposure`).
- Single-shot rejection paths: press during run, during summary.
- Count ∞: run 5-10 shots, trash-stop, summary shows `x/∞`.
- Count 5-steps: values survive app restart (config persistence).

---

## 0.2.12 (versionCode 18) — ISO control — SHIPPED

**Outcome:** shipped; the timed ISO walk verified the write path (read-back
"wrote X - camera kept Y" lines, AUTO picked 400 at open and the listener
reported it). Post-test fix ffef940 added the echo filter (exactly one
"kept" line per write) - unverified since, no ISO writes happened in the
0.2.13/0.2.14 rounds. Bulb never exercised, so the "in Bulb?" question
below stays open.

**Goal:** new config field "ISO"; UP/DOWN walks the camera's supported ISO list
(incl. AUTO); display always reflects what the camera actually kept.

### What we already know
- **There is NO ISO step method in the SDK** (unlike shutter speed). ISO is a
  `Camera.Parameters` string key: **`iso-sensitivity`**. Your own LOG.TXT param dump
  confirms the HAL's value list:
  `iso-sensitivity-values=0,50,64,80,100,125,160,200,250,320,400,500,640,800,1000,
  1250,1600,2000,2500,3200,4000,5000,6400,8000,10000,12800,16000,20000,25600`
  — `0` is presumably AUTO (confirm on camera).
- **The write path is already proven in this very app:** `forceColorLook()`
  (`IntervalActivity.java:580-604`) does `p.set(key, value)` → `cam.setParameters(p)`
  → `getParameters()` read-back and logs "what the camera kept". On this HAL a
  dropped value throws nothing, so **the read-back is the ground truth** — same
  lesson as Recipe Lab's applyPreview (issue #104). Only the *list getters* of
  `Camera.Parameters` are hollow (starter README warning); string `get/set` works.
- Supported-values list should be read at runtime from the `iso-sensitivity-values`
  param (via `camera.getSupportedParameters().flatten()` — already logged each
  session at :797-801), not hardcoded.
- There is also `setAutoISOSensitivityListener(CameraEx$AutoISOSensitivityListener)`
  (CameraEx.j:276) — arm it **log-only** in v1 to learn what AUTO picks.

### Implementation sketch
- Files: `IntervalActivity.java` (ISO field in grid, UP/DOWN walks list, set +
  read-back verify + display), `IntervalConfig.java` (persist last ISO choice?),
  `app/build.gradle` (0.2.12/18), `README.md`.
- Display: `AUTO` when read-back is `0`, otherwise the number.
- Define behavior when the camera rejects the write (read-back unchanged): show the
  kept value + log; do not fight it (same philosophy as exposure stepping in P).

### Camera-test round
- Does the write stick in the current exposure mode(s)? In Bulb?
- Read-back value after mode-dial changes (does the body reset ISO?).
- Interaction with the exposure probe: does a params write disturb the
  shutter-speed listener anchor / Bulb position detection?
- AUTO: does `0` read back as AUTO, and does the listener report the chosen ISO?

---

## 0.2.13 (versionCode 19) — Aperture control (non-manual lenses) — SHIPPED

**Outcome:** shipped; camera round 1 proved the encoding guess below wrong -
raw = f-number x100, not x10 (the lens walked 560..3600 = f/5.6..f/36 in
1/3-stop steps). The open probe netted zero (one coalesced callback, the
anchor guard stayed inert). The `iris_ss_iso_aeunlock` wrapper ran a clean
f/5.6 -> f/36 -> f/5.6 round trip with no preview perturbation, so it stays
until a bare-edit A/B per mode position (own open item). 0.2.14
(versionCode 20, commit 87b0446) fixed the F-field display for x100.
`enableIrisRing`/`disableIrisRing` remains unexercised - the log-only plan
stands.

**Goal:** new config field "F"; UP/DOWN steps the iris even when the lens/body
owns the aperture (mode dial not in A/M where required).

### What we already know
- **The SDK has a real aperture API** (CameraEx.j):
  - `adjustAperture(I)V`, `incrementAperture()V`, `decrementAperture()V`
  - `setApertureChangeListener(CameraEx$ApertureChangeListener)` → callback
    `onApertureChange(ApertureInfo, CameraEx)`
  - `ApertureInfo` fields: `int currentAperture`, `int currentAvailableMin`,
    `int currentAvailableMax`
  - `enableIrisRing()V` / `disableIrisRing()V` (body-iris unlock candidates; log first)
- **Architecture mirrors shutter speed exactly**: steppable, event-read-only (no
  getter), change events drive the display. Port the 0.2.5 pattern as-is:
  probe pair to anchor the value, bounded corrective steps, deadline-expiry when
  the camera rejects steps (never fight the mode).
- **Non-manual unlock exists:** the HAL supports the param
  **`shooting-preview` = `iris_ss_iso_aeunlock`** — confirmed in LOG.TXT params
  (`shooting-preview-values=off,iris_ss_iso_aeunlock`). This is the body's
  mechanism to change iris/SS/ISO without capturing. Set it via the
  same params path as ISO; restore `off` afterwards (an `apertureRestore()`
  mirroring the existing `apoRestore()` pattern).
- **F-number encoding unknown:** exposure-complete lines log `F100 ISO 800` —
  likely f/10.0 encoded ×10, but `currentAperture`'s scale must be confirmed on
  the first camera test before formatting `f/x.y`.

### Implementation sketch
- Files: `IntervalActivity.java` (F field, ApertureChangeListener wiring with
  probe-anchor + corrections, unlock set/restore around edits),
  `IntervalConfig.java` if persistence wanted, `app/build.gradle` (0.2.13/19),
  `README.md`.
- Probe order on camera: (1) do raw steps work in M and A without the unlock?
  (2) only add `iris_ss_iso_aeunlock` if steps are rejected outside A/M.
- Boundary handling from `currentAvailableMin/Max` (event-driven clamp display).

### Camera-test round
- Encoding of `currentAperture` (expected ×10).
- Step acceptance per mode-dial position: P / S / A / M / Bulb.
- Whether the `shooting-preview` unlock is needed, and whether setting it
  perturbs preview/Bulb detection (restore path).
- `enableIrisRing/disableIrisRing` — only if the above shows a ring-dependent
  behavior; log-only first.

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
  (persist PWR), `app/build.gradle` (0.3.0 / versionCode 21), `README.md`.
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



