# Intervalometer Feature Roadmap

_Created 2026-11-14 after the 0.2.10 no-bug verdict. Stripped 2026-11-14
(0.2.x) and again 2026-11-15 (0.3.0): every shipped section deleted -
one-line summaries per version live in intervalometer/README.md, full
detail in git history. What remains: the live progress block, the pending
0.3.1 section, the deferred-hygiene list, and the SDK re-derivation notes._

_Progress: 0.2.11-0.2.15, 0.3.0 and 0.3.1 shipped and camera-tested
(0.3.0: G1-G5 passed; the G6 %/h long run still owed). 0.3.1 closed the
file-by-file review round (all five files have verdicts, one defect
fixed - "aperture step rejected" tier), its DEBUG gate passed on the
A7 II 2026-11-15 (details in the 0.3.1 section), and Logger.isDebug()
was dropped as dead code (zero callers - the gate runs on setDebug()
plus the internal flag). The bulb-exit self-check debt is also closed:
"bulb exit unconfirmed ... re-arming" fired and "bulb exit confirmed:
30 s" logged - the re-arm fallback was needed on the first Bulb cycle
and worked. The ISO echo filter debt is closed too: five in-app writes
at 15:33:50-51 (400->320->250->200->160, one step back to 200) were all
accepted ("camera kept" matched every write, no REJECTED), and no
"ISO auto" echo followed - the lastIsoWriteMs window suppressed them.
Still owed on camera, oldest first: ISO writes in Bulb, the bare-edit
A/B for the aperture unlock wrapper, the 3-strike abort (a 2026-11-14 11:11 run
logged six consecutive takePicture throws and still finished "done,
stored 1 of 10" - re-verify deliberately), and the 0.3.0 G6 %/h long
run (PWR, count=all, trash-stop at end - the %/h delta vs a display-on
run is the proof). Next: the owed camera runs and the deferred-hygiene
list; the 0.3.1 section gets stripped next round per convention._

All SDK facts below were verified against the compiled stubs — **not assumed**.
See "Re-deriving the SDK surface" at the bottom for how to re-check any of this.

---

## 0.3.1 (versionCode 23) - code-review round, all five files closed

**Goal:** post-tag file-by-file code review. First file: Logger. The
production build should not pay for the verbose session log, but a third
party hitting an issue must be able to turn it back on without adb.

**Shipped:**
- Two-tier Logger: ERROR (crash shields, watchdog, failures) and the INFO
  skeleton (session header, device, config, run start/end, per-shot
  fired/stored, aborts, wake chain, apo restore) always log; 41 forensic
  call sites (params dump ~30 lines, probes, listener mirrors, ISO/aperture
  step echoes, review/iris internals, cpu line) moved to a new
  `Logger.debug()` that writes only while the gate is ON.
- The gate is the config grid's DEBUG field (field 8, between PWR and Shot
  now): persisted in SharedPreferences like every other choice - it must
  survive app kills and camera power cycles to catch intermittent issues -
  default OFF, arms live on toggle (no restart), session header names the
  mode. Grid: `PWR OFF  DEBUG OFF` on its own line.
- Review verdicts: the per-shot `fired`/`stored` lines stay always-on
  (they ARE the %/h + drift measurement, ~2 file writes per shot is noise
  against seconds-long spacing); the per-line open/append/close FileWriter
  stays (fine at these rates - a writer-per-line is only worth revisiting
  if a future feature logs per-tick). Not yet camera-gated.
- Engine review (file 2): its four lines (run start/finish, shot fired,
  slot held) ARE the skeleton - all stay always-on INFO. The audit fixed
  two tick-loop warts, no grid-math changes: a held slot re-posted its own
  tick with delay 0 (deadline already past), spinning the main looper for
  the whole camera-busy hold (tens of seconds on bulb) - now a fixed 250 ms
  poll; and after a 3-strike abort (stop() from inside onShoot) the
  in-flight tick still ran one onTick + re-post - the engine now returns
  (the activity's onTick guard stays as belt-and-braces). Verified while
  auditing: fireCapture() marks the capture in-flight before takePicture
  returns, so the engine's canShoot() re-check cannot double-fire into a
  busy camera.
- BaseActivity review (file 3): all five lines stay - Resume/Pause (the
  wake chain) and Display changed as INFO, both key-dispatch shield
  catches as ERROR. Dropped a dead mirror: currentDisplay tracked
  display-change events under a comment claiming it was the PWR restore
  target, but the restore uses the live pre-run snapshot
  (displayBeforeRun) and the firmware never echoes the app's own
  Display.NONE switches - the field had no readers. The Display changed
  line itself stays; it is the wake-chain and panel-state evidence.
- IntervalConfig review (file 4): closed, zero defects. Arming order is
  right: onCreate reads the flag straight from the store (readDebugLog)
  and arms the Logger BEFORE beginSession, so the header names the mode;
  the config object is constructed later in onCreate, so the
  "config loaded ... debug log" line lands inside the session. One key,
  one default: load() delegates to readDebugLog(), save() persists the
  same flag after every grid edit - the toggle survives kills and the
  pre-header read and the load can never disagree (both use
  getPreferences(MODE_PRIVATE), one cached store). adjustDebugLog()
  mirrors adjustPwr() (flip, return the new state); the caller arms the
  Logger live and logs the transition as always-on INFO, so a pulled
  log shows exactly when verbose armed/disarmed mid-session. Grid
  wiring checked: NEXT_FIELD/PREV_FIELD cover field 8 with correct wrap
  (PWR -> DEBUG -> Shot now -> Interval), UP/DOWN toggles, hint line
  and the "PWR x  DEBUG x" grid line all present.
- IntervalActivity review (file 5): closed with one fix - "aperture step
  rejected" (adjustF's catch) was INFO while its sibling "shutter step"
  is ERROR; same event class (the camera's step call threw, the field
  keeps the camera's value), and it fires after irisUnlock() whose own
  failure is ERROR - now ERROR, so an "F key does nothing" report is
  diagnosable from a skeleton-only log. Tier counts shift to 27 info /
  41 debug / 26 error. Everything else verified in place: the 27 INFO
  lines are the README skeleton (run-start exposure identity, per-shot
  stored + battery, summary stored-vs-fired, late-store correction,
  watchdog give-up, open/release/preview lifecycle, the bulb exit
  confirmed/unconfirmed pair, PWR display before/after + wake chain,
  S2 aborts, ISO-step-skipped user feedback); every ERROR is a genuine
  failure path (HAL rejects, 3-strike abort, crash handler, parameter
  write failures, PWR display failures); all 41 DEBUG are forensic
  (listener mirrors, probes + inert probe failures, params dump,
  review + iris + ISO internals, cpu line). Kept as-is on purpose:
  ISO probe failed is ERROR while the step-pair probes are DEBUG (an
  ISO probe failure kills the field permanently - isoValues stays
  empty - a failed pair just leaves the mirror logic inert);
  "params dump failed" is ERROR though the dump is DEBUG (one-time,
  and a throw there means the HAL connection is degraded); "review
  cancel rejected" stays DEBUG (the documented on-camera stub NPE
  never blocks a run).

**Camera gate PASSED (A7 II, 2026-11-15, 3 sessions):** debug OFF ->
skeleton-only (zero [DEBUG] lines in either OFF session; a 10-shot PWR
run logs 37 lines where 0.3.0 logged 250+), the in-app toggle arms
verbose mid-session (one [INFO] "debug log ON - verbose forensics on"),
and a restart came up ON from the first line - probes, params dump and
per-shot internals at [DEBUG], skeleton lines [INFO]. Zero [ERROR]
lines and zero step rejections across all 0.3.1 sessions. Bonus: five
in-app ISO writes ("ISO: wrote X - camera kept X", zero REJECTED)
proved the echo filter - no "ISO auto" echo after the writes, the
lastIsoWriteMs window swallowed them. Optional micro-check next
session: toggle OFF and restart (persistence has been shown in both
directions, just not OFF-after-ON).
**Shipped as 0.3.1:** Logger.isDebug() dropped before commit (zero
callers - the gate runs on setDebug() plus the internal flag).

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
