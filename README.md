# Intervalometer workspace

Version-controlled workspace for the INTVL app: a built-in intervalometer for
the Sony Alpha 7 II (PlayMemories Camera Apps platform).

## What's tracked

- `intervalometer/` - the app project (Gradle, Java). See its `README.md` for
  the full development history and current status.
- `LOG.TXT` - pulled session logs from the camera; the empirical evidence
  behind every engine decision.
- `.clinerules` - assistant working conventions for this workspace.

## What's deliberately NOT tracked

- `recipe-lab-sony-pmca/` - third-party Recipe Lab reference app (upstream
  clone; git can't embed nested repos, and its content doesn't change).
- `Blank/` - PMCA framework demo clones and the original starting package.
- Build artifacts, `.gradle/`, `local.properties` - regenerated per machine.

## Checkpoint: v0.2.6

Everything in v0.2.5-mvp (exposure control + true bulb, interval-as-minimum
grid, 90 s never-stored watchdog, summary wait), plus the first Phase 2
trust work: config persists across restarts and power cycles, custom
interval/bulb slots edit in place and persist their value, presets run out
to 1 h interval / 30 min bulb, a run stops as `failed` after 3 consecutive
`takePicture` rejections, and the running overlay warns when a shot is
held behind a live capture. Gated on camera 2026-11-14: persistence incl.
a power cycle, custom-slot bounds, long presets, 10/10 on-grid at the
custom 5 s interval. Hold warning + reject abort are code-shipped but not
yet exercised on camera - tag only after both have been seen live
(Previous checkpoint: `v0.2.5-mvp` - exposure control + true bulb.)
