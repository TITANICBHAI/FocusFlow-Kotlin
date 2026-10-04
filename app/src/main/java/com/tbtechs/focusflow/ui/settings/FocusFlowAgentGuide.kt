package com.tbtechs.focusflow.ui.settings

internal const val FOCUSFLOW_WEBSITE_URL = "https://focusflowapp.pages.dev"

internal val FOCUSFLOW_AGENT_GUIDE = """
# FocusFlow project and file guide

You are an AI coding agent working in the existing FocusFlow Android repository.
FocusFlow is a native Android focus and digital-wellbeing app. The app is written
in Kotlin with Jetpack Compose and uses application ID `com.tbtechs.focusflow`.

## Start with the right references

- Read `README.md` and `replit.md` for the project overview and workspace rules.
- Visit $FOCUSFLOW_WEBSITE_URL and use its Docs option for product-facing
  documentation. Check the specific page that matches the requested feature.
- Read the relevant files in `docs/` before changing documented behavior.
  `docs/focus-bugs.md` and `docs/focus-bugs-tracker.md` contain bug investigations;
  verify each issue against the current code and tests before treating it as open.
- For Stats and behavioral-intelligence work, read
  `docs/behavioral-intelligence-agent-prompt.md`,
  `docs/behavioral-intelligence-implementation-tracker.md`, and the relevant
  phase brief from `docs/IMPL_1A.md` through `docs/IMPL_5.md`.
- For `.focusflow` backup, import, restore, or alarm work, read
  `fixes/AGENT_PRE_READ_PROMPT.md`, `fixes/IMPLEMENTATION_TRACKER.md`, and
  `fixes/FOCUSFLOW_IMPLEMENTATION_PLAN_FINAL_v14.md`. The v14 plan is the authority
  for that contract; do not invent undocumented backup behavior.
- For backup-file fields and the offline editor/checker, see the backup section in
  `README.md` and `docs/focusflow-backup-builder.html`.

## Project map

- `app/src/main/java/com/tbtechs/focusflow/ui/` — Compose screens and UI state.
- `app/src/main/java/com/tbtechs/focusflow/data/` — Room entities, DAOs, and
  repositories.
- `app/src/main/java/com/tbtechs/focusflow/domain/` — scheduling and business rules.
- `app/src/main/java/com/tbtechs/focusflow/enforcement/` — app-blocking and
  enforcement services.
- `app/src/test/` and `app/src/androidTest/` — unit and Android tests.

## Working rules

1. Inspect the current implementation, callers, persistence path, and tests before
   editing. Documentation is important context, but confirm behavior in the code;
   follow an explicitly designated feature contract when one exists.
2. Keep changes focused and use the existing Kotlin, Compose, repository, and manual
   dependency-injection patterns. Do not replace the app architecture or broaden the
   request into unrelated refactoring.
3. Preserve the existing Room database and preferences. Treat task deletion, backup
   replacement, and other destructive data changes carefully; do not widen their
   scope or silently discard unrelated data.
4. Add or update tests for changed behavior. Report checks that were not run instead
   of implying they passed.
5. Do not build the Android project inside Replit. Use the project's GitHub Actions
   workflow for Android compilation and APK verification.
6. Confirm Android platform behavior from current documentation or device tests
   before claiming it is verified.
7. Never place credentials, tokens, or other secrets in source files, prompts, logs,
   or documentation.

## Current request

[Add the specific change, expected behavior, and any constraints here.]

When finished, summarize the changes, the checks that actually ran, and any
remaining limitations.
""".trimIndent()
