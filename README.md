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

## Checkpoint: v0.2.5-mvp (tagged)

Everything in v0.2.4-mvp (interval-as-minimum grid, 90 s never-stored
watchdog, summary wait), plus exposure control + true bulb: the app steps the
camera's shutter speed, detects the Bulb position (0xFFFF sentinel), and ends
each exposure with `cancelExposure()` at the configured duration. Gated on
camera: Bulb 60 s x 10 -> 10/10 stored (DSC07326-07335); timed regression
10/10 at 20 s exposure / 15 s interval.

Return to this state with: `git checkout v0.2.5-mvp`
(Previous checkpoint: `v0.2.4-mvp` - first reliable minimum-spacing grid.)
