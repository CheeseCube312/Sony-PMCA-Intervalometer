# Intervalometer workspace

Version-controlled workspace for the INTVL app: a built-in intervalometer for
the Sony Alpha 7 II (PlayMemories Camera Apps platform).

## What's tracked

- `intervalometer/` - the app project (Gradle, Java). Its `README.md` has the
  full version history, key findings, build/logging docs and SDK notes.
- `roadmap.md` - the living plan: owed on-camera runs, deferred hygiene.
- `.clinerules` - assistant working conventions for this workspace.

## What's deliberately NOT tracked

- `recipe-lab-sony-pmca/` - third-party Recipe Lab reference app (upstream
  clone; git can't embed nested repos, and its content doesn't change).
- `Blank/` - PMCA framework demo clones and the original starting package.
- `LOG.TXT` / `session-logs/` - pulled camera session logs (the empirical
  evidence behind every engine decision). They churn every pull, so they
  stay untracked; committed snapshots through `5103f9f` remain in history.
- Build artifacts, `.gradle/`, `local.properties` - regenerated per machine.

## Status

0.3.1 shipped and camera-gated 2026-11-15 (v0.3.0 PWR power-saving runs,
v0.3.1 debug log gate). Full history and details: `intervalometer/README.md`;
open items - owed camera runs and deferred hygiene: `roadmap.md`.
