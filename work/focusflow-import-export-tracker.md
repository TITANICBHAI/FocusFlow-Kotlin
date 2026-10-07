# FocusFlow Import / Export Work Tracker

## Current status

- **Overall:** Batches 1–6 complete and locally verified; Batch 7 in progress
- **Authorization:** Granted by the user on 2026-10-08 to restore `.focusflow` import/export according to the saved plan.
- **Last updated:** 2026-10-08
- **Scope note:** Implementation is batch-specific. Batch 6 is complete; later acceptance/stability work remains pending.

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

**Status:** Complete — both local unit-test variants passed.

- Phase 5 scope is the Settings backup section and confirmation composable. The route constant, NavHost destination, external-intent dispatch, and manifest filters remain in Phase 6.
- The existing BackupViewModel already holds validated import envelopes and provides progress/result states; the UI must preserve that pending envelope until explicit confirm/cancel and must not reset state when the document picker is dismissed.

**Initial notes — resumed 2026-10-08**

- Baseline at review start: `main`; no tracked or staged code changes. The only untracked file was the user's attached read-first instruction; no app files had been modified yet.
- The current Settings screen already includes SAF export/import launchers, a Backup & Restore section after Profile, and the inline confirmation fallback. The confirmation UI includes all four summary counts, defaults replacement off, warns on replacement, exposes progress/results/errors, and has accessible labels/state semantics.
- Keep this in-screen confirmation for Batch 5. Do not add `IMPORT_CONFIRM`, a NavHost destination, file-open dispatch, or manifest filters; those stay in Batch 6.
- State review found that a cancelled document picker leaves the ViewModel state untouched, explicit cancellation clears the pending envelope, and retryable restore errors preserve it. Local JVM ViewModel tests cover these state transitions. `adb` is unavailable, so device-based Compose UI tests are not currently runnable.

- [x] Add the Settings export/import entry points in the agreed location.
- [x] Add the import confirmation screen and all plan-required summary fields.
- [x] Keep task replacement off by default and show the deletion warning when enabled.
- [x] Handle progress, success, and errors without losing pending state or changing data on cancellation.
- [x] Verify accessibility, navigation, and screen states affected by the UI.

**Evidence / notes**

- Verification history: the first `bash scripts/test-unit.sh` run failed KSP parsing on invalid trailing commas after final `else` entries in two UI `when` expressions. The second run passed KSP but failed Kotlin compilation because the two progress `AlertDialog` calls lacked the required `confirmButton` slot. Both issues were fixed.
- Changed files: `app/src/main/java/com/tbtechs/focusflow/ui/backup/ImportConfirmScreen.kt`, `app/src/main/java/com/tbtechs/focusflow/ui/settings/SettingsScreen.kt`, and `work/focusflow-import-export-tracker.md`; the Backup & Restore UI was already present and was retained.
- Tests/checks and results: final `bash scripts/test-unit.sh` passed `:app:testProductionDebugUnitTest` and `:app:testTbtechsdevDebugUnitTest`, each with 150 tests, 0 failures, 0 errors, and 0 skipped. `git diff --check` passed.
- UI checks and results: reviewed the Settings entry-point placement and inline confirmation flow, including back/cancel/done callbacks, switch role and state description, warning live region, progress descriptions, and result/error handling. Existing `BackupViewModelTest` state tests passed in both variants. `adb` is unavailable, so device-based Compose UI testing was not run.
- Data-safety review: replacement starts off; the permanent-deletion warning appears only when selected. Dismissing the document picker does not mutate import state, explicit cancellation clears pending import without restoring, and a retryable restore error returns to the pending confirmation.
- Decisions or blockers: Batch 5 uses the in-screen confirmation. `IMPORT_CONFIRM`, a NavHost destination, external file-open routing, and manifest filters remain in Phase 6. No current Batch 5 blocker.

## Batch 6 — Navigation, external intents, and manifest

Plan phase: **Phase 6 — Navigation and plumbing**

**Status:** Complete — both local test variants passed; navigation and packaged manifests reviewed.

- [x] Add the confirmation route and navigation destination.
- [x] Route staged external file-open intents through the in-app confirmation flow.
- [x] Add only the necessary manifest intent filters; do not add a FileProvider unless new evidence requires it.
- [x] Verify cancellation and completion return to the expected destination.

**Evidence / notes**

- Changed files: `app/src/main/AndroidManifest.xml`, `app/src/main/java/com/tbtechs/focusflow/MainActivity.kt`, `ExternalBackupIntentPolicy.kt`, `ui/navigation/FocusFlowNavGraph.kt`, `ui/navigation/Routes.kt`, `ui/settings/SettingsScreen.kt`, `app/src/test/java/com/tbtechs/focusflow/ExternalBackupIntentPolicyTest.kt`, `ui/navigation/RoutesTest.kt`, and this tracker. The existing Batch 5 confirmation screen changes and the user's untracked read-first attachment were preserved.
- Tests/checks and results: `bash scripts/test-unit.sh` passed `:app:testProductionDebugUnitTest` and `:app:testTbtechsdevDebugUnitTest`. Both XML report sets contain 154 tests, 0 failures, 0 errors, and 0 skipped; the new `ExternalBackupIntentPolicyTest` and updated `RoutesTest` appear in both. `git diff --check` passed.
- Intent/manifest verification: both packaged flavor manifests include the new octet-stream and `.focusflow` `ACTION_VIEW` filters on `MainActivity`; neither contains a `FileProvider` entry.
- Navigation review: Settings consumes the staged URI and navigates only after validation reaches `PendingConfirm`. Cancel clears the pending import and pops back to Settings; successful completion resets state and navigates to Settings. External reopens reset the back stack to Settings so an older confirmation cannot remain underneath.
- Device checks and limitations: `adb` is unavailable, so file-open, cancel, and completion interactions were not exercised on a device. The routing, callback wiring, manifest merge, and corresponding JVM policy/route tests were verified locally.
- Decisions or blockers: Accept only `ACTION_VIEW` content/file URIs with `application/octet-stream` or a `.focusflow` path suffix; preserve existing app deep links. No FileProvider, push, or GitHub Actions run was added or requested. Batch 7 remains out of scope.
- Local environment side effect: The post-test Python XML-report summary added `python-base-3.13` to `.replit`. The validated replacement flow restored the tracked baseline; the final `.replit` diff is empty, and the temporary restore file was absent after replacement.

## Batch 7 — Acceptance and stability

**Status:** Complete — Section 8 reconciled; known gaps and device-only checks are recorded.

**Initial notes — started 2026-10-08**

- At audit start, there were no tracked code changes; the user's attached read-first instruction was the only untracked file. Batch 6 is recorded complete; this batch is limited to Section 8 acceptance verification, no-touch review, and recording evidence or gaps.
- The existing device-test limitation (`adb` unavailable) may prevent interactive SAF and Compose verification; confirm tooling and distinguish code/test evidence from device-only checks.
- Reconcile acceptance against current source and test reports rather than relying on earlier summaries. Do not push or start GitHub Actions unless explicitly requested.

- [x] Reconcile each item in section 8 of the plan against implementation and evidence.
- [x] Verify export content and cancellation behavior from source and JVM evidence; record the unavailable device check.
- [x] Verify import merge/replace behavior, invalid-file handling, active-session protection, and settings preservation limits.
- [x] Verify external file-open behavior and stability requirements from source, manifest merges, and JVM evidence; record the unavailable device check.
- [x] Review the plan's no-touch list and confirm unrelated protected areas were not changed.
- [x] Record any acceptance item that is deferred, unsupported, or needs a user decision.

**Evidence / notes**

- Acceptance reconciliation (source/unit evidence unless explicitly marked device-deferred):
  - **Export E1 — Settings row:** Present under Backup & Restore in `SettingsScreen`.
  - **Export E2 — Suggested Save name/dialog:** `CreateDocument("application/octet-stream")` receives `BackupSerializer.buildSuggestedFilename()`; `BackupSerializerTest.suggestedFilenameUsesTimestampAndFocusflowExtension` checks the required filename shape. Actual system dialog interaction is device-deferred.
  - **Export E3 — SAF destinations:** Writes use the `CreateDocument` URI and `ContentResolver.openOutputStream`; provider interaction with Downloads/Drive is device-deferred.
  - **Export E4 — JSON envelope:** Serializer round-trip and committed V1 fixture tests verify `FocusFlowBackupV1` and version 1.
  - **Export E5 — Device-local fields:** `PortableSettingsPolicy` filters current local enforcement fields; serializer policy test checks the current-model local fields and confirms `focusMirrorVpnEnabled` remains portable.
  - **Export E6 — Tasks/settings/sections/summary:** Export reads `TaskRepository.getAllTasks()` and current settings. Tests cover a task round-trip, seven section IDs, and all four summary counts. Exhaustive serialization of every portable `AppSettings` field is not independently asserted.
  - **Export E7 — Save cancellation:** The result callback calls export only for non-null URIs; therefore a cancelled picker does not enter the export state machine. Actual system-picker cancellation was not exercised on a device.
  - **Settings import I1 — Import row:** Present under Backup & Restore.
  - **Settings import I2 — All file types:** `OpenDocument` launches with `*/*`; actual picker display is device-deferred.
  - **Settings import I3 — Valid file confirmation:** ViewModel reaches `PendingConfirm`; Settings routes to the confirmation destination. Serializer/ViewModel/route tests cover the state and route pieces; end-to-end device selection is deferred.
  - **Settings import I4 — Summary:** Confirmation UI displays exported date, app version, tasks, blocked words, schedule windows, and daily allowances.
  - **Settings import I5 — Replacement warning:** Switch state starts false for a new envelope; selecting replacement shows the permanent-deletion warning. UI source was reviewed; no Compose/device interaction test ran.
  - **Settings import I6 — Merge:** `BackupRestoreEngineTest.mergeUsesDatabaseIdsAndSkipsDuplicatesWithinBackup` verifies database-ID collision handling and duplicate skipping.
  - **Settings import I7 — Replace:** `replaceDeletesBeforeInsertingAndReconcilesOnceAfterTheBatch` verifies delete-before-insert behavior.
  - **Settings import I8 — Past schedules:** `pastScheduledTasksAreImportedAsSkippedWithCanonicalUpdateTime` verifies conversion to `skipped`.
  - **Settings import I9 — Future schedule alarms:** Merge and replace tests verify one `backup-restore` reconcile after inserts; actual alarm delivery is not device-verified.
  - **Settings import I10 — Portable/local settings:** Restore test verifies supported portable values and rejects device-local writes. **Partial acceptance gap:** backups include current portable fields such as text scales and modern notification preferences that `LegacySettingsAdapter.toSharedPreferencesValues` does not map, so those fields are silently ignored on import. A dedicated backup mapping or explicit contract decision is needed; keep the one-time migration adapter unchanged.
  - **Settings import I11 — Active session replacement guard:** `activeFocusSessionBlocksReplaceBeforeAnyRestoreWrites` verifies failure before settings or task mutation.
  - **Settings import I12 — Confirmation cancellation:** `cancelingPendingImportDiscardsEnvelopeWithoutRestoring` verifies no restore call after cancellation. Actual back/button interaction is device-deferred.
  - **Settings import I13 — Invalid files:** Serializer tests verify invalid JSON, wrong kind, missing settings, and missing tasks return failures; file-manager tests cover size and malformed UTF-8.
  - **External X1 — File-manager/open filters:** Both production and tbtechsdev merged manifests contain octet-stream and `.focusflow` VIEW filters; neither merged manifest contains a FileProvider. Policy tests accept intended content/file URIs and reject unrelated files, schemes, and actions. Actual Files/Gmail/Drive launch is device-deferred.
  - **External X2 — Land on Settings and open confirmation:** `MainActivity` stages eligible URIs, Settings consumes the relay, and navigation routes validated imports to confirmation; relay and route policy tests are present. End-to-end Android dispatch is device-deferred.
  - **External X3 — Same import behavior:** External and picker imports share `BackupViewModel.beginImport` and the same confirmation/restore path.
  - **Stability S1 — No restore hang:** JVM engine tests exercise gated write ordering, task replacement, inserts, and reconciliation; no Android/Room device run was possible.
  - **Stability S2 — No recovery dialogs:** Code/tests verify one post-batch reconciliation rather than per-task calls. Runtime recovery-dialog absence is not observable without a device run.
  - **Stability S3 — Merge during active session:** The merge test uses an active session and succeeds without invoking replacement; actual running-session integration is device-deferred.
- Tests/checks and results: `bash scripts/test-unit.sh` completed successfully in 4m17s. `:app:testProductionDebugUnitTest` and `:app:testTbtechsdevDebugUnitTest` each report 154 tests, 0 failures, 0 errors, and 0 skipped. Both XML report sets contain `BackupSerializerTest`, `BackupRestoreEngineTest`, `BackupFileManagerTest`, `BackupViewModelTest`, `BackupImportIntentRelayTest`, `ExternalBackupIntentPolicyTest`, `RoutesTest`, and `LegacySettingsMigrationTest`. `git diff --check` passed after the final tracker update.
- Verification note: An initial report-name lookup searched for unqualified class names in XML suite attributes and falsely reported the classes missing. Corrected lookup by report filename and fully qualified suite name confirmed all eight expected report files in both variants; aggregate totals above are from the XML reports.
- Changed files for Batch 7: `work/focusflow-import-export-tracker.md` only. No app source was changed.
- No-touch review: `git diff --name-only` was empty for every path in the plan's no-touch list at the end of Batch 7. At that point, the only non-tracker workspace change was the user's untracked attached instruction; the later UI QA changes are recorded separately in Session notes and are outside the no-touch list.
- Deferred items and rationale: Interactive SAF save/open/cancel, external provider launch, actual scheduled-alarm delivery, and runtime recovery-dialog checks require Android device execution. The SDK-provided `adb` lists no attached devices, and this environment has no emulator binary or system image. Full portable-settings restoration remains an acceptance gap pending a mapping/contract decision. `removed.zip` is absent; audit used current source and tests.
- Remote CI/push: GitHub Actions was not explicitly requested and was not run; no push was made.

## Batch 8 — Final handoff

- [x] Ensure all completed work is ticked and has evidence; leave blocked or unverified work unchecked.
- [x] Summarize changed behavior, validation, known limitations, and remaining decisions.
- [x] Push or trigger GitHub Actions only if the user explicitly requested it.
- [x] Confirm no unrequested remote CI or push ran, and record any authorized local test/build commands.

**Evidence / notes**

- Changed behavior: Adds versioned `.focusflow` backup export/import through Android document pickers, import preview/confirmation, merge or replacement restore with duplicate/invalid-task handling, active-session replacement protection, alarm reconciliation, and external file opening into the Settings confirmation flow. Recent UI hardening bounds backup metadata and scrolls large warning/error content. Portable settings not covered by the legacy adapter remain incomplete on restore.
- Final commit / remote status: No commit or push was requested or made. Local branch is `main` tracking `origin/main`, with no ahead/behind marker. The current tracked diff contains the two UI-hardening files and this tracker; the user's attached instruction remains untracked. `.replit` is unchanged.
- Final verification: The latest code-changing run was `bash scripts/test-unit.sh`, which passed `:app:testProductionDebugUnitTest` and `:app:testTbtechsdevDebugUnitTest` (154 tests each; 0 failures, errors, or skipped). `git diff --check` passed after the final tracker edits. No APK or device test was run.
- Remaining risks or follow-up: Some portable settings included in backups are still ignored by import because the backup restore mapping only applies the legacy adapter's recognized keys; a separate backup-specific mapping/contract decision is needed, without changing the one-time database migration. SAF picker, external-provider launch, actual alarm delivery, and device-rendered UI remain unverified because there is no connected device or installed emulator. No remote CI or push ran; no GitHub token was accessed.

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
| 2026-10-08 | Replit Agent | Batch 6 — navigation, external intents, and manifest | Added the confirmation route, shared ViewModel destination, staged external-file handoff, and manifest filters. Both local flavor test tasks passed with 154 tests each; merged manifests contain no FileProvider. Device interactions remain untested because `adb` is unavailable. No remote CI or push ran. |
| 2026-10-08 | Replit Agent | Batch 7 — acceptance and stability | Reconciled all Section 8 items against current source, focused tests, both merged manifests, and fresh test XML. Both local variants passed (154 tests each, no failures/errors/skips). Recorded the portable-settings restore gap, device-only checks, and no-touch review. No app source changes, remote CI, or push. |
| 2026-10-08 | Replit Agent | Backup UI and error-display QA | Reviewed Settings export/import dialogs, restore confirmation, ViewModel transitions, navigation, and external-intent handoff. Bounded long backup metadata in summary rows, made large restore-warning lists lazy and scrollable, and made long error details scrollable. Both local test variants passed (154 each, 0 failures/errors/skips); `git diff --check` passed. `adb devices -l` reports no device, and no emulator is installed, so rendered device verification remains deferred. |
