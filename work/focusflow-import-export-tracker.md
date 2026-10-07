# FocusFlow Import / Export Work Tracker

## Current status

- **Overall:** Batch 5 in progress — Batches 1, 2, 3, and 4 verified; Phase 5 UI underway
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
- Run local Android unit tests through the configured workflow or scripts under `scripts/`, as allowed by `AGENT_PRE_PROMPT.md`. Use remote GitHub Actions only when explicitly requested.

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
- Conflicts, missing references, or decisions: `removed.zip` is absent. The plan names `TsSettingsAdapter`, but the current object is `LegacySettingsAdapter` in `TsSettingsAdapter.kt`; the current migration still uses the adapter for the legacy database settings blob. No active `.focusflow` UI, route, or manifest entry point was found. The user has explicitly authorized Batch 1 and Batch 2; later batches remain out of scope until requested.

## Batch 1 — Data models and serialization

**Status:** Complete — implementation and local unit tests verified.

**Initial notes**

- `removed.zip` is unavailable; implementation must use the current Kotlin source and preserve existing legacy migration behavior.
- At the start of the batch, no app code had been changed. The data models, serializer, portable settings policy, and unit tests have since been added.
- At Batch 1 start, the project pre-prompt prohibited local tests; it was subsequently revised to allow the configured local test workflow or scripts. Remote CI or pushing still requires a separate explicit request.
- Current-source API review is complete. `Task` is serializable, while `AppSettings` is not; `LegacySettingsAdapter` is the current adapter object and its recognized fields cover only part of the current `AppSettings` model.
- Keep the current legacy migration path unchanged. The portable policy will map the adapter's supported aliases, preserve other portable current settings as JSON fields, and exclude the current model's device-local state separately from the legacy migration allowlist.
- `LegacySettingsPolicy` has a narrower historical TypeScript key list than the current native `AppSettings`; use it as an additional guard without changing its legacy migration behavior.

## Batch 1 — Data models and serialization

Plan phase: **Phase 1 — Data models and serialization**

- [x] Implement the versioned envelope and preset-section/summary models.
- [x] Implement the portable-settings policy, excluding device-local fields and preserving `focusMirrorVpnEnabled`.
- [x] Implement envelope creation, JSON serialization, suggested filenames, and parsing/validation.
- [x] Add tests for round-trip behavior, portable/device-local fields, summary values, and invalid `kind`.

**Evidence / notes**

- Changed files: `app/src/main/java/com/tbtechs/focusflow/data/backup/BackupEnvelope.kt`, `PortableSettingsPolicy.kt`, and `BackupSerializer.kt`; `app/src/test/java/com/tbtechs/focusflow/data/backup/BackupSerializerTest.kt`.
- Tests/checks and results: `bash scripts/test-unit.sh` passed `:app:testProductionDebugUnitTest` and `:app:testTbtechsdevDebugUnitTest`, each with 137 tests, 0 failures, 0 errors, and 0 skipped. Both `BackupSerializerTest` and `BackupRestoreEngineTest` are present in each variant's XML reports. Static whitespace checks passed; the committed v1 fixture parses as JSON.
- Decisions or blockers: The existing migration adapter remains unchanged; it recognizes only a subset of current `AppSettings` fields, so other portable JSON fields are preserved in the envelope but are not mapped by that legacy adapter.

## Batch 2 — Restore engine and write safety

Plan phase: **Phase 2 — Restore engine**

**Status:** Complete — local unit tests passed for both product flavors.

**Initial notes**

- The app module creates one `RestoreGate` and injects it into the settings, task, and alarm repositories; the restore engine must use that same gate.
- `SettingsRepository` currently exposes a gated `putString` but no typed boolean/integer preference writes; add gated typed methods for the adapter's existing preference values.
- Filter portability policy keys before mapping through `LegacySettingsAdapter`; its output keys are native preference names, not TypeScript keys for `LegacySettingsPolicy`.
- The current project pre-prompt allows local unit tests via the configured workflow or scripts; use `bash scripts/test-unit.sh`. Do not start GitHub Actions or push unless separately requested.
- Restore engine and gated boolean/integer settings writes were added, along with fake-access tests. The configured local test script has now passed for both product flavors.

- [x] Implement the active Focus Session guard for replace mode.
- [x] Route every restore write through the current restore/write gate.
- [x] Implement merge behavior using database task IDs and replace behavior with the required confirmation/guard.
- [x] Convert past scheduled tasks to skipped and reconcile alarms once after inserts.
- [x] Add tests for merge, replace, duplicates, past tasks, active-session protection, and write/reconcile behavior.

**Evidence / notes**

- Changed files: `app/src/main/java/com/tbtechs/focusflow/data/backup/BackupRestoreEngine.kt`, `app/src/main/java/com/tbtechs/focusflow/data/repository/SettingsRepository.kt`, and `app/src/test/java/com/tbtechs/focusflow/data/backup/BackupRestoreEngineTest.kt`.
- Tests/checks and results: `bash scripts/test-unit.sh` passed `:app:testProductionDebugUnitTest` and `:app:testTbtechsdevDebugUnitTest`, each with 137 tests, 0 failures, 0 errors, and 0 skipped. XML reports include `BackupRestoreEngineTest` and `BackupSerializerTest` for both flavors. `git diff --check` and equivalent whitespace checks for new files passed.
- Data-safety review: Replacement checks for an active session before any writes while holding the shared gate; settings are validated before writes; task IDs are read from the database under the gate; all settings/task/alarm mutations use the gate. Replace mode reconciles once even when no future scheduled task was imported, so alarms belonging to deleted tasks can be removed.
- Decisions or blockers: No blockers remain in Batch 2. No remote CI was requested or run. `LegacySettingsPolicy` is applied to source backup keys before conversion because the adapter's output keys are native preference names. Replace mode reconciles once even without future scheduled imports to clear alarms for deleted tasks.

## Batch 3 — File I/O and import validation

Plan phase: **Phase 3 — File I/O**

**Status:** Complete — implementation and local unit tests verified.

**Initial notes**

- `BackupFileManager.kt` and its focused tests do not exist yet. Reuse `BackupJsonPreflight.readUtf8Bounded` for byte-cap and UTF-8 enforcement; `BackupSerializer.parseAndValidate` already delegates JSON bounds/syntax checks to `validateAndStripBom`.
- The tracker’s previous prohibition on Replit tests conflicts with `AGENT_PRE_PROMPT.md` and the preceding batch evidence. Follow the current pre-prompt and use the bootstrap-backed `bash scripts/test-unit.sh`; do not invoke remote CI or push.
- No mocking library is configured for local JVM tests. Keep stream-level helpers internal and test them with in-memory streams, while the public methods remain direct `ContentResolver` adapters.

- [x] Implement ContentResolver reads and writes for document URIs.
- [x] Enforce the existing byte limit, UTF-8 validation, and JSON preflight behavior; do not duplicate an existing preflight implementation.
- [x] Add tests for size rejection, malformed input, and read/write round-trip behavior.

**Evidence / notes**

- Changed files: Added `app/src/main/java/com/tbtechs/focusflow/data/backup/BackupFileManager.kt` and `app/src/test/java/com/tbtechs/focusflow/data/backup/BackupFileManagerTest.kt`.
- Tests/checks and results: `bash scripts/test-unit.sh` passed `:app:testProductionDebugUnitTest` and `:app:testTbtechsdevDebugUnitTest`. XML reports show 141 tests per flavor, with 0 failures, 0 errors, and 0 skipped; `BackupFileManagerTest` appears with all 4 tests in each flavor. `git diff --check` passed.
- Decisions or blockers: The public methods directly adapt `ContentResolver` streams; internal stream helpers allow deterministic JVM tests without introducing a mocking dependency. Bounds and UTF-8 decoding delegate to `BackupJsonPreflight`; JSON scanning remains in `BackupSerializer.parseAndValidate`. No blockers. No remote CI or push was requested or run.

## Batch 4 — Intent relay and ViewModel

Plan phase: **Phase 4 — ViewModel**

**Status:** Complete — implementation and expanded local unit tests verified.

**Initial notes**

- Batch 4 is limited to the pending-URI relay and backup state orchestration. Settings entry points, confirmation UI, routes, MainActivity intent dispatch, and manifest filters remain for their later batches.
- The existing `BackupRestoreEngine` owns validated restore mutations; the new ViewModel will call it off the main thread and refresh the existing `SettingsViewModel` only after a successful restore.
- The app has no dedicated ViewModel test rule. Use an injected test dispatcher sharing `runTest`'s scheduler, and keep file/repository operations behind focused internal seams for deterministic state-transition tests.

- [x] Implement pending external-import URI relay behavior.
- [x] Implement export/import state transitions, confirmation, cancellation, reset, and error handling.
- [x] Refresh settings after a successful restore.
- [x] Add focused tests for state transitions and failure/cancel paths.

**Evidence / notes**

- Changed files: Added `app/src/main/java/com/tbtechs/focusflow/BackupImportIntentRelay.kt`, `app/src/main/java/com/tbtechs/focusflow/ui/backup/BackupViewModel.kt`, `app/src/test/java/com/tbtechs/focusflow/BackupImportIntentRelayTest.kt`, and `app/src/test/java/com/tbtechs/focusflow/ui/backup/BackupViewModelTest.kt`.
- Tests/checks and results: Final `bash scripts/test-unit.sh` passed `:app:testProductionDebugUnitTest` and `:app:testTbtechsdevDebugUnitTest`. Each XML report shows 150 tests, 0 failures, 0 errors, and 0 skipped; `BackupImportIntentRelayTest` has 2 passing tests and `BackupViewModelTest` has 7 passing tests in both flavors. `git diff --check` passed.
- Decisions or blockers: The relay is a synchronized single-slot handoff. The ViewModel cancels stale reads, refuses cancellation while a restore is already mutating data, and keeps a validated envelope available for retry after restore failure; parse/read errors reset to Idle. A settings-refresh failure is reported as a warning after a successful restore. Internal operation seams keep state tests deterministic without new dependencies. No blockers; no remote CI or push was requested or run.

## Batch 5 — Settings and confirmation UI

Plan phase: **Phase 5 — UI**

**Status:** In progress.

- Phase 5 scope is the Settings backup section and confirmation composable. The route constant, NavHost destination, external-intent dispatch, and manifest filters remain in Phase 6.
- The existing BackupViewModel already holds validated import envelopes and provides progress/result states; the UI must preserve that pending envelope until explicit confirm/cancel and must not reset state when the document picker is dismissed.

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
- [ ] Confirm no unrequested remote CI or push ran, and record any authorized local test/build commands.

**Evidence / notes**

- Final commit / remote status (if requested):
- Final verification:
- Remaining risks or follow-up:

## Session notes

| Date | Agent | Batch / scope | Evidence, notes, decisions, or blockers |
|---|---|---|---|
| 2026-10-08 | Replit Agent | Created this tracker and organized the supplied plan; no feature implementation performed | Feature remains removed. All implementation checkboxes are intentionally unchecked pending explicit authorization. |
| 2026-10-08 | Replit Agent | Batch 0 current-state review | Read the required docs and current project context. `removed.zip` is missing; current legacy database migration uses `LegacySettingsAdapter`. User authorized implementation according to the saved plan; Batch 1 is now in progress. |
| 2026-10-08 | Replit Agent | Batch 1 — data models and serialization | Added envelope, portable settings mapping, serialization/validation, and focused tests. At the end of Batch 1 implementation, Android/Gradle tests and remote CI had not been run; a later authorized local test run verified Batch 1 and Batch 2. |
| 2026-10-08 | Replit Agent | Batch 1 and Batch 2 verification | `bash scripts/test-unit.sh` passed both product-flavor JVM unit-test tasks (137 tests each, no failures/errors/skips). Both backup test classes appear in both XML reports. Remote CI and pushing were not requested or run. |
| 2026-10-08 | Replit Agent | Batch 3 start — file I/O | Current pre-prompt permits local tests through the configured workflow or `scripts/`; tracker wording that prohibited them was inconsistent with that instruction and prior batch evidence. Batch 3 will use the local bootstrap-backed test script only. |
| 2026-10-08 | Replit Agent | Batch 3 implementation | Added the ContentResolver adapter and stream tests for byte limit, UTF-8, round-trip, and parser preflight. Local verification is pending; no app code outside Batch 3 was changed. |
| 2026-10-08 | Replit Agent | Batch 3 verification | `bash scripts/test-unit.sh` passed both product-flavor JVM test tasks; each XML report contains 141 tests with 0 failures/errors/skips, including all 4 `BackupFileManagerTest` cases. `git diff --check` passed. No remote CI or push ran. |
| 2026-10-08 | Replit Agent | Batch 4 verification | Added the pending import URI relay and export/import ViewModel state orchestration. Final `bash scripts/test-unit.sh` passed both flavor tasks; each XML report has 150 tests with 0 failures/errors/skips, including 2 relay and 7 ViewModel tests. `git diff --check` passed. No remote CI or push ran. |
