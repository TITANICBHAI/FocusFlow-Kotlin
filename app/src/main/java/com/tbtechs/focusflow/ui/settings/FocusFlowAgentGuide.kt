package com.tbtechs.focusflow.ui.settings

internal const val FOCUSFLOW_FILE_FORMAT_URL =
    "https://focusflowapp.pages.dev/focusflow-file-format/"

internal val FOCUSFLOW_CODING_AGENT_PROMPT = """
# FocusFlow coding-agent instructions

You are an AI coding agent working in the existing FocusFlow Android
repository. FocusFlow is a native Android focus and digital-wellbeing app
written in Kotlin with Jetpack Compose. Its application ID is
`com.tbtechs.focusflow`.

This prompt is self-contained: do not require the user or another agent to find
a separate Markdown file, website, or prior conversation for the project or
`.focusflow` format context. The format notes below are the practical reference
for this request. For existing implementation details, inspect the current
Kotlin source and tests. If a required behavior is not specified here or by the
current implementation, explain what is unknown instead of guessing.

## Standalone `.focusflow` file reference

- A `.focusflow` backup is a plain UTF-8 JSON document, not a ZIP archive.
- The top-level JSON object must contain `kind: "FocusFlowBackupV1"`,
  `settings` (an object), and `tasks` (an array). Newly generated files should
  include `version: 1`; the current importer also accepts a missing version.
- A task is an object with a unique, non-empty `id`, a non-blank `title`,
  ISO-8601 `startTime` and `endTime` instants with explicit offsets, integer
  `durationMinutes`, a supported `status` (`scheduled`, `active`, `completed`,
  `skipped`, or `overdue`), and a supported `priority` (`low`, `medium`, `high`,
  or `critical`). `endTime` must not be earlier than `startTime`.
- Common task fields are `description`, `tags`, `reminders`, `color`,
  `focusMode`, `focusAllowedPackages`, `createdAt`, and `updatedAt`. Reminders
  are objects attached to a task, not bare integer offsets. Canonical generated
  timestamps use UTC `Z`. Missing `description` imports as empty text; missing
  `color` defaults to `#6366f1`; missing `focusMode` defaults to `false`.
- For `focusAllowedPackages`, missing or null means use the global allow-list;
  an explicit empty array means all apps are allowed for that task; a non-empty
  array names the task-specific Android package IDs.
- Portable settings can include focus preferences, app package lists, blocked
  words, greyout windows, recurring block schedules, daily allowances, presets,
  launcher preferences, profile data, and protection preferences. Use exact
  setting names found in the current Kotlin implementation. Omitted settings
  remain local; a backup must not restore active sessions, live enforcement,
  PIN/device security state, or raw alarm registrations.
- `greyoutSchedule` and `recurringBlockSchedules` are separate data. Summary
  counts are informational and recalculated from the payload. `presetSections`
  is descriptive inventory, not a command to activate behavior. A backup cannot
  override FocusFlow's protected-app list.
- Import validates and presents a review before applying changes. Merge keeps
  existing tasks, adds new IDs, treats identical same-ID tasks as duplicates,
  and reports different same-ID content as a conflict rather than overwriting.
  Replace applies to task rows only; it does not replace all app data, and task
  replacement is refused during an active Focus session. Portable settings use
  their own field-specific rules.

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