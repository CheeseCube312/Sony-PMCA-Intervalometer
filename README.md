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

## Checkpoint: v0.2.4-mvp (tagged)

First reliable version: fixed-grid scheduler where the interval is a minimum
spacing - slots hold while a capture is in flight, so long exposures stretch
the grid instead of silently losing shots. 90 s never-stored watchdog, summary
waits out an in-flight last capture. Verified on camera: 5 s interval vs ~30 s
shutter -> 10/10 stored, zero silent losses.

Return to this state with: `git checkout v0.2.4-mvp`
