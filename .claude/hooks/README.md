# Agent hooks & local controls

This repo's only **committed** Claude Code hook is intentionally minimal, because of a hard
environment constraint discovered on the maintainer's box (Windows + the packaged
`WindowsApps\Claude.exe`):

- `node` ships **inside** the app and is **not on PATH** → `node .claude/hooks/*.mjs`-style
  hooks are silently inert here.
- `bash` resolves to `C:\WINDOWS\system32\bash.exe` (**WSL**), not Git Bash, and Git's
  `bin/` is not on the system PATH → `bash …` hook commands are wrong/fragile.
- Only `git`, `powershell`, and cmd builtins are reliably resolvable from a hook command.

So the harness uses **portable, interpreter-free** controls and pushes the rest to CI:

| Control | Where | Why here |
| --- | --- | --- |
| Stop hook `git diff --check` | `.claude/settings.json` | Pure `git` (on PATH everywhere); exits 2 on whitespace / conflict-marker errors → blocks the turn end. No interpreter needed. |
| TaskCreated hook `high-risk-task-guard.ps1` | `.claude/settings.json` + `.claude/hooks/` | Bounded-autonomy gate for Agent Teams: blocks creating a task whose payload matches the constitution's high-risk surface (schema/migration/auth/billing/public-API/deploy/destructive) until a human approves. Pure `powershell`, reads hook JSON from stdin, schema-agnostic keyword match. Self-test commands are in the script header. Only fires when `CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS=1`. |
| Schema-digest drift | `.github/workflows/schema-digest-drift.yml` | The migration→digest sync is enforced on the Linux runner (Docker + the script), not a fragile local hook. |
| Protected-push guard | (none — server-side) | Direct push to `main` / force-push of `develop`/`main` is already blocked by GitHub branch protection (ADR-0020), the real control. A local guard would only duplicate it. |

If you add a hook, make the `command` resolvable by `git` / `powershell` / cmd alone, or
wrap a guaranteed interpreter — do **not** assume `node`/`bash` on PATH. Test it by piping
sample hook JSON to the command before committing.
