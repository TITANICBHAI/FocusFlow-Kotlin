package com.tbtechs.focusflow.ui.settings

internal const val FOCUSFLOW_FILE_FORMAT_URL =
    "https://focusflowapp.pages.dev/focusflow-file-format/"

internal const val FOCUSFLOW_SCHEDULE_GENERATOR_PROMPT = """
# FocusFlow schedule-to-backup generator

You are an AI schedule generator. Turn my natural-language schedule into a
valid, downloadable `.focusflow` backup that FocusFlow can import as tasks.
This is a data-generation request: do not edit the FocusFlow app or write
application code.

This prompt is self-contained and contains the format requirements you need.
Do not ask me to find project files, separate documentation, links, or earlier
conversation.
If you can inspect FocusFlow's source, check the actual backup exporter and
importer before creating the file; if they disagree with this reference, explain
the mismatch instead of guessing.

## Understand my schedule

- Ask only for information that is necessary to make the schedule accurate.
  If missing, ask in one concise batch for the time zone, first date, date range
  or number of weeks, task durations, and any important constraints.
- A FocusFlow task is one dated occurrence; task records do not have a
  recurrence rule. Expand a repeating personal routine into one task for each
  occurrence over the finite date range I specify. If I ask for a weekly routine
  but give no end date or number of weeks, ask rather than inventing one.
- Personal plans belong in the `tasks` array. App-blocking time windows are a
  different feature, represented by `settings.recurringBlockSchedules` or
  `settings.greyoutSchedule`; do not use those for ordinary calendar tasks.
  This prompt generates task schedules only, so always set `settings` to `{}`.
  If I ask for app-blocking or other settings changes, explain that they need a
  separate request rather than inventing settings data here.
- Interpret local times in the time zone I provide, including daylight-saving
  changes. Convert each occurrence to an ISO-8601 UTC instant ending in `Z`.
  Never label a local time as UTC without converting it.
- Check for overlaps, impossible durations, past dates, and conflicts with my
  stated constraints. Ask before silently resolving a conflict.

## `.focusflow` V1 format

A `.focusflow` file is plain UTF-8 JSON, not a ZIP. Keep it under 8 MiB with no
more than 20,000 tasks. Create one JSON object with:

- `kind`: exactly `"FocusFlowBackupV1"`
- `version`: integer `1`
- `settings`: `{}` for a task-only schedule
- `tasks`: an array of task objects
- `exportedAt`: the file-generation time as an ISO-8601 UTC instant
- `platform`: `{"os":"android"}`

Leave optional `summary`, `presetSections`, and `appVersion` metadata out.
They are not needed to import tasks.

Each task must include all of these fields:

- `id`: unique, non-empty string, at most 128 characters, with no control characters
- `title`: non-blank string, at most 1,000 characters
- `description`: string, at most 20,000 characters; use `""` if none was requested
- `startTime`, `endTime`: ISO-8601 instants; `endTime` must be after `startTime`
- `durationMinutes`: positive integer up to 100,000, equal to the elapsed time
  between the two instants
- `status`: use `"scheduled"` for future plans
- `priority`: exactly `"low"`, `"medium"`, `"high"`, or `"critical"`; default to
  `"medium"` if I do not specify one
- `tags`: array of up to 100 strings, each at most 100 characters; use `[]` if none
- `reminders`: array of up to 100 reminder objects; use `[]` if none
- `color`: a string of at most 32 characters; use `"#6366f1"` if none was requested
- `focusMode`: boolean; use `false` unless I request Focus mode for the task
- `createdAt`, `updatedAt`: ISO-8601 UTC instants; use the file-generation time
  for both unless I specify otherwise

For a reminder, use an object with a unique, non-empty `id` (at most 128
characters), the owning task's `taskId`, integer `offsetMinutes`, and `type`
equal to `"pre-start"`, `"at-start"`, or `"post-start"`. Negative offsets are
before the task, zero is at its start, and positive offsets are after it. Do not
invent Android notification IDs.

Omit `focusAllowedPackages` by default so FocusFlow uses the device's existing
global allow-list. If I explicitly request a task-specific app list, use exact
Android package IDs only when I provide or verify them. Missing or `null` means
use the global allow-list; `[]` means allow all apps for that task.

Do not add unknown fields, duplicate JSON keys, comments, or trailing commas.
Do not include active-session data, alarms, PIN/security data, or live
enforcement state.

## Deliver the file

Validate the JSON and every task against the requirements above. Create and
attach a downloadable file named `schedule.focusflow`. If you cannot create
files, return the complete raw JSON so I can save it with that extension; keep
any explanation outside the JSON.

After the file, briefly report the number of tasks, covered dates, time zone,
and any assumptions. FocusFlow shows an import review before applying anything;
do not claim the file was imported or tested in the app unless that actually
happened.

## My schedule request

[Describe the routine, dates, time zone, task durations, breaks, reminders, and
constraints here. You can describe it naturally.]
""".trimIndent()
