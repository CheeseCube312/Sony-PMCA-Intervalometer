# Intervalometer Roadmap

_Shipped sections are stripped every round - one-line summaries per version
live in intervalometer/README.md, full detail in git history (0.2.x stripped
2026-11-14; 0.3.0 and 0.3.1 stripped 2026-11-15). The SDK re-derivation
notes moved to intervalometer/README.md - reference, not plans._

---

## Owed on camera (oldest first)

- **ISO writes mid-Bulb** - the write path (list-walk + read-back verify)
  has never been exercised during an open Bulb exposure.
- **Bare-edit aperture A/B** - the `iris_ss_iso_aeunlock` wrapper stays
  until an A/B proves a bare edit moves the iris (or proves it can't).
- **3-strike abort re-verify** - a 2026-11-14 11:11 run logged six
  consecutive takePicture throws and still finished "done, stored 1 of
  10"; re-verify deliberately that the abort trips at 3.
- **0.3.0 G6 %/h long run** - PWR, count=all, trash-stop at the end; the
  %/h delta vs a display-on run is the battery proof.
- Optional micro-check anytime: DEBUG toggle OFF then restart
  (OFF-after-ON persistence - both other directions already shown).

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
