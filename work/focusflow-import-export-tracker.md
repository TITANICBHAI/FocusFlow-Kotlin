# FocusFlow Import / Export Work Tracker

## Current status

- **Overall:** Batch 1 in progress
- **Authorization:** Granted by the user on 2026-10-08 to restore `.focusflow` import/export according to the saved plan.
- **Last updated:** 2026-10-08
- **Scope note:** This tracker organizes the supplied plan; creating it does not authorize implementation. The feature is currently removed from the app.

Read [`AGENT_PRE_PROMPT.md`](AGENT_PRE_PROMPT.md) and [`focusflow-import-export-plan.md`](focusflow-import-export-plan.md) before acting on this tracker.

## Tracking rules

- Update this tracker as each batch starts and finishes; do not wait until the end of the project.
- Tick a checkbox only after the work is complete and verified. Record evidence or notes in that batch immediately.
- Evidence should identify changed files, the exact check or test performed, its result, and a GitHub Actions link when applicable.
- If work is blocked, leave its completion boxes unchecked and record the blocker and next decision needed.
- If an item is skipped or its scope changes, document why rather than silently removing it.
- Keep unrelated changes and user data out of the implementation.
- Android Gradle/build/test commands must not run on Replit. Use GitHub Actions only when the user explicitly requests remote verification.

## Batch 0 — Authorization and current-state review

- [x] Receive explicit user authorization to implement or restore this feature.
- [x] Read the pre-prompt, this tracker, and the complete plan.
- [x] Read applicable project instructions and inspect current source, repository status, and relevant paths before editing.
- [x] Check whether plan references such as `removed.zip` exist; verify against the current code instead of assuming old paths or APIs still apply.
- [x] Record the agreed scope and any plan conflicts before implementation.

**Evidence / notes**

- Authorization and scope: User explicitly authorized reimplementation according to the saved plan on 2026-10-08. Scope is the supplied `.focusflow` plan, adapted to the current Kotlin source.
- Baseline repository state: Branch `main`, commit `80bc745`; working tree was clean when checked before Batch 1.
- Relevant files inspected: `replit.md`; all three `work/` documents; `LegacySettingsPolicy.kt`, `LegacySettingsMigration.kt`, `TsSettingsAdapter.kt`, `RestoreGate.kt`; legacy migration call in `FocusFlowDatabase.kt`; targeted `.focusflow` entry-point search in Settings, navigation, and AndroidManifest files.
- Conflicts, missing references, or decisions: `removed.zip` is absent. The plan names `TsSettingsAdapter`, but the current object is `LegacySettingsAdapter` in `TsSettingsAdapter.kt`; the current migration still uses the adapter for the legacy database settings blob. No active `.focusflow` UI, route, or manifest entry point was found. Await explicit authorization and agreed implementation scope before app changes.

## Batch 1 — Data models and serialization

**Status:** In progress — current-source API review before implementation.

**Initial notes**

- `removed.zip` is unavailable; implementation must use the current Kotlin source and preserve existing legacy migration behavior.
- No app code has been changed in this batch yet.
- Do not run Android Gradle/build/test commands on Replit. Remote CI or pushing still requires a separate explicit request.

## Batch 1 — Data models and serialization

Plan phase: **Phase 1 — Data models and serialization**

- [ ] Implement the versioned envelope and preset-section/summary models.
- [ ] Implement the portable-settings policy, excluding device-local fields and preserving `focusMirrorVpnEnabled`.
- [ ] Implement envelope creation, JSON serialization, suggested filenames, and parsing/validation.
- [ ] Add tests for round-trip behavior, portable/device-local fields, summary values, and invalid `kind`.

**Evidence / notes**

- Changed files:
- Tests/checks and results:
- Decisions or blockers:

## Batch 2 — Restore engine and write safety

Plan phase: **Phase 2 — Restore engine**

- [ ] Implement the active Focus Session guard for replace mode.
- [ ] Route every restore write through the current restore/write gate.
- [ ] Implement merge behavior using database task IDs and replace behavior with the required confirmation/guard.
- [ ] Convert past scheduled tasks to skipped and reconcile alarms once after inserts.
- [ ] Add tests for merge, replace, duplicates, past tasks, active-session protection, and write/reconcile behavior.

**Evidence / notes**

- Changed files:
- Tests/checks and results:
- Data-safety review:
- Decisions or blockers:

## Batch 3 — File I/O and import validation

Plan phase: **Phase 3 — File I/O**

- [ ] Implement ContentResolver reads and writes for document URIs.
- [ ] Enforce the existing byte limit, UTF-8 validation, and JSON preflight behavior; do not duplicate an existing preflight implementation.
- [ ] Add tests for size rejection, malformed input, and read/write round-trip behavior.

**Evidence / notes**

- Changed files:
- Tests/checks and results:
- Decisions or blockers:

## Batch 4 — Intent relay and ViewModel

Plan phase: **Phase 4 — ViewModel**

- [ ] Implement pending external-import URI relay behavior.
- [ ] Implement export/import state transitions, confirmation, cancellation, reset, and error handling.
- [ ] Refresh settings after a successful restore.
- [ ] Add focused tests for state transitions and failure/cancel paths.

**Evidence / notes**

- Changed files:
- Tests/checks and results:
- Decisions or blockers:

## Batch 5 — Settings and confirmation UI

Plan phase: **Phase 5 — UI**

- [ ] Add the Settings export/import entry points in the agreed location.
- [ ] Add the import confirmation screen and all plan-required summary fields.
- [ ] Keep task replacement off by default and show the deletion warning when enabled.
- [ ] Handle progress, success, and errors without losing pending state or changing data on cancellation.
- [ ] Verify accessibility, navigation, and screen states affected by the UI.

**Evidence / notes**

- Changed files:
- UI checks and results:
- Data-safety review:
- Decisions or blockers:

## Batch 6 — Navigation, external intents, and manifest

Plan phase: **Phase 6 — Navigation and plumbing**

- [ ] Add the confirmation route and navigation destination.
- [ ] Route staged external file-open intents through the in-app confirmation flow.
- [ ] Add only the necessary manifest intent filters; do not add a FileProvider unless new evidence requires it.
- [ ] Verify cancellation and completion return to the expected destination.

**Evidence / notes**

- Changed files:
- Checks and results:
- Decisions or blockers:

## Batch 7 — Acceptance and stability

- [ ] Reconcile each item in section 8 of the plan against implementation and evidence.
- [ ] Verify export content and cancellation behavior.
- [ ] Verify import merge/replace behavior, invalid-file handling, active-session protection, and settings preservation.
- [ ] Verify external file-open behavior and stability requirements.
- [ ] Review the plan's no-touch list and confirm unrelated protected areas were not changed.
- [ ] Record any acceptance item that is deferred, unsupported, or needs a user decision.

**Evidence / notes**

- Acceptance items verified:
- Tests/checks and results:
- Deferred items and rationale:
- GitHub Actions run links (only if explicitly requested):

## Batch 8 — Final handoff

- [ ] Ensure all completed work is ticked and has evidence; leave blocked or unverified work unchecked.
- [ ] Summarize changed behavior, validation, known limitations, and remaining decisions.
- [ ] Push or trigger GitHub Actions only if the user explicitly requested it.
- [ ] Confirm no Gradle/build/test command ran on Replit.

**Evidence / notes**

- Final commit / remote status (if requested):
- Final verification:
- Remaining risks or follow-up:

## Session notes

| Date | Agent | Batch / scope | Evidence, notes, decisions, or blockers |
|---|---|---|---|
| 2026-10-08 | Replit Agent | Created this tracker and organized the supplied plan; no feature implementation performed | Feature remains removed. All implementation checkboxes are intentionally unchecked pending explicit authorization. |
| 2026-10-08 | Replit Agent | Batch 0 current-state review | Read the required docs and current project context. `removed.zip` is missing; current legacy database migration uses `LegacySettingsAdapter`. User authorized implementation according to the saved plan; Batch 1 is now in progress. |
