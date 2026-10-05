package com.tbtechs.focusflow.ui.settings

internal const val FOCUSFLOW_FILE_FORMAT_URL =
    "https://focusflowapp.pages.dev/focusflow-file-format/"

internal val FOCUSFLOW_CODING_AGENT_PROMPT = """
# FocusFlow coding-agent instructions

You are an AI coding agent working in the existing FocusFlow Android repository.
FocusFlow is a native Android focus and digital-wellbeing app written in Kotlin
with Jetpack Compose. Its application ID is `com.tbtechs.focusflow`.

## Read the relevant references first

- Read `README.md` and `replit.md` for the project overview and workspace rules.
- The human-readable `.focusflow` file guide is
  $FOCUSFLOW_FILE_FORMAT_URL. Use the website's Docs option for other product
  documentation.
- For backup, import, restore, or alarm work, read
  `fixes/AGENT_PRE_READ_PROMPT.md`, `fixes/IMPLEMENTATION_TRACKER.md`, and
  `fixes/FOCUSFLOW_IMPLEMENTATION_PLAN_FINAL_v14.md`. The v14 plan is the
  authority for that contract. Do not invent undocumented backup behavior.
- For backup investigations, check `docs/focus-bugs.md` and
  `docs/focus-bugs-tracker.md`, but verify each report against current code and
  tests before treating it as open.
- For Stats and behavioral-intelligence work, read
  `docs/behavioral-intelligence-agent-prompt.md`,
  `docs/behavioral-intelligence-implementation-tracker.md`, and the relevant
  phase brief from `docs/IMPL_1A.md` through `docs/IMPL_5.md`.
- For the offline backup editor/checker, see the backup section in `README.md`
  and `docs/focusflow-backup-builder.html`.

## Project map

- `app/src/main/java/com/tbtechs/focusflow/ui/` — Compose screens and UI state.
- `app/src/main/java/com/tbtechs/focusflow/data/` — Room entities, DAOs, and
  repositories.
- `app/src/main/java/com/tbtechs/focusflow/domain/` — scheduling and business rules.
- `app/src/main/java/com/tbtechs/focusflow/enforcement/` — app blocking and
  enforcement services.
- `app/src/test/` and `app/src/androidTest/` — unit and Android tests.

## Working rules

1. Inspect the current implementation, callers, persistence path, and tests
   before editing. Confirm documented behavior in code and follow the designated
   feature contract when one exists.
2. Keep changes focused and use the existing Kotlin, Compose, repository, and
   manual dependency-injection patterns. Do not replace the app architecture or
   broaden the request into unrelated refactoring.
3. Preserve the Room database and preferences. Handle task deletion, backup
   replacement, and other destructive changes carefully; do not widen their
   scope or silently discard unrelated data.
4. Add or update tests for changed behavior. Report checks that were not run
   instead of implying they passed.
5. Do not build the Android project inside Replit. Use the project's GitHub
   Actions workflow for Android compilation and APK verification.
6. Confirm Android platform behavior from current documentation or device tests
   before claiming it is verified.
7. Never place credentials, tokens, or other secrets in source files, prompts,
   logs, or documentation.

## Current request

[Add the specific change, expected behavior, and any constraints here.]

When finished, summarize the changes, the checks that actually ran, and any
remaining limitations.
""".trimIndent()