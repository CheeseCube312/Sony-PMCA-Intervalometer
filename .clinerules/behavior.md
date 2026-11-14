## Communication
- Skip praise and hedging. State problems and plans directly.
- If an approach is flawed, say so and don't just implement it

## Camera address
- Connection with camera possible over ADB over WIFI
- Camera IP 192.168.1.110 Port 5555
- if it fails, camera wifi might be disabled, notify user
- don't run on-camera tests for the user unless explicitly instructed otherwise. ask

## Scope
- Only read/touch files relevant to the current task.
- Before multi-file edits, list the files you'll change.
- Prefer minimal diffs over full rewrites unless explicitly told otherwise

## Verification
- After changes: 1-3 sentences on what changed and why. No more.
- Run tests/build if available. Report pass/fail plainly — no speculation.