# Incremental improvement agents

Three scoped subagents + one orchestrator command for making small, reviewable
changes to a Kotlin/Compose codebase.

## Files
- `.claude/agents/ui-polish.md` — Compose UI: tokens, a11y, recomposition-safe refactors
- `.claude/agents/security-hardening.md` — secrets, storage, network/TLS, manifest, validation
- `.claude/agents/perf-lightweight.md` — recomposition hotspots, allocations, dependency/resource weight
- `.claude/commands/improve.md` — `/improve` runs one pass across all three

## Setup
1. Copy the `.claude/` folder into the root of your repo.
2. Commit and push to GitHub.

## Trigger from claude.ai/code
1. Open https://claude.ai/code and select this repo.
2. Type `/improve` to run a full pass on changed files,
   or `/improve app/src/main/.../LoginScreen.kt` to target a file/area.
3. Or invoke one agent directly, e.g.:
   "Use the security-hardening subagent on the auth module."

Each pass is intentionally small: one concern, separate commits, build-checked,
risky changes surfaced for your approval rather than auto-applied.
