# Intervalometer workspace

Version-controlled workspace for the INTVL app: a built-in intervalometer for
the Sony Alpha 7 II (PlayMemories Camera Apps platform).

## What's tracked

- `intervalometer/` - the app project (Gradle, Java). See its `README.md` for
  the full development history and current status.
- `.clinerules` - assistant working conventions for this workspace.

## What's deliberately NOT tracked

- `recipe-lab-sony-pmca/` - third-party Recipe Lab reference app (upstream
  clone; git can't embed nested repos, and its content doesn't change).
- `Blank/` - PMCA framework demo clones and the original starting package.
- `LOG.TXT` - pulled camera session logs (the empirical evidence behind
  every engine decision). They churn every pull, so they stay untracked;
  committed snapshots through `5103f9f` remain in history.
- Build artifacts, `.gradle/`, `local.properties` - regenerated per machine.

## Checkpoint: v0.2.15

Phase 1 (0.2.0-0.2.5: exposure control + true bulb) and Phase 2 trust work
(0.2.6-0.2.10: persistence, custom slots, failure abort, hold warning) are
shipped and camera-gated. Since: 0.2.11 single shot + count ladder, 0.2.12
ISO control (read-back verified), 0.2.13 aperture control (event-driven,
iris unlock wrapper), 0.2.14 aperture display fix for the confirmed x100
encoding, 0.2.15 the S2 abort (a press after the one that fired quits an
in-flight capture or stops a run; the firing press's own release is
exempt). Still owed on camera: the ISO echo filter
(no ISO writes since the fix), ISO in Bulb, the bulb-exit self-check, the
bare-edit aperture A/B, and the 3-strike abort - a 2026-11-14 11:11 run
logged six consecutive `takePicture` throws and still finished
`done, stored 1 of 10`, so the abort has never fired (the hold warning HAS
been seen live: shots 8-10 held at 19:35 the same day). Full history and
status: `intervalometer/README.md`; living plan with open items:
`roadmap.md`. Next: the still-owed on-camera verification list, then an
optional PWR long-run battery proof (%/h vs display-on). (Previous
checkpoint: `v0.2.15` - S2 abort. `v0.3.0` tagged 2026-11-15.)
