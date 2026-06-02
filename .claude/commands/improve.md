/im---
description: Run one incremental improvement pass (UI, security, perf) on changed or specified files, opening a small reviewable PR.
argument-hint: [path-or-area, optional]
allowed-tools: Read, Grep, Glob, Edit, Bash
---

Run a SINGLE incremental improvement pass. Target: $ARGUMENTS (if empty, use files from `git diff --name-only` plus staged changes; if none, ask which area to start with).

Do this in order, keeping each as its own small commit so changes stay reviewable:

1. Delegate to the `ui-polish` subagent for any Compose UI files in scope.
2. Delegate to the `security-hardening` subagent. Apply only its low-risk fixes; surface CRITICAL/HIGH as a summary for me to approve.
3. Delegate to the `perf-lightweight` subagent for one optimization.

Rules:
- Keep the total change set small and incremental — this is one pass, not a rewrite.
- Make a separate commit per area with a clear message (e.g. `ui: tokenize spacing in LoginScreen`).
- Do NOT modify business logic, state machines, or navigation.
- Run the build if available (`./gradlew assembleDebug` or the project's task) to confirm nothing breaks; if it fails, fix or revert that change.
- End with a concise summary: what changed per area, what was deferred, and any items needing my approval.
