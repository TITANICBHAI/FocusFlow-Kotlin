# FocusFlow Import / Export Work Tracker

## Current status

- **Overall:** Batches 1–10 implementation and local unit verification complete; Android device-only checks remain deferred
- **Authorization:** Granted by the user on 2026-10-08 to restore `.focusflow` import/export according to the saved plan.
- **Last updated:** 2026-10-08
- **Scope note:** Batch 10 is limited to import robustness and clearer preview behavior found in the second source comparison; export behavior is out of scope.

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

## Batch 9 — Reference-source comparison and Kotlin improvements

**Status:** Complete — source comparison and portable-setting restore improvements are verified; device-rendered UI checks remain deferred.

**Scope**

- Compare the supplied TypeScript/hybrid and flattened feature bundles with the current native Kotlin import/export implementation.
- Use the reference behavior to close confirmed Kotlin import/export gaps without changing the v1 `.focusflow` envelope contract, weakening safety, or importing React Native code.
- Keep the existing Kotlin preview/confirmation flow, restore gate, no-touch list, and current v1 parser protections unless evidence supports a scoped improvement.

**Checklist**

- [x] Compare archive import/export behavior and Settings/confirmation-screen flows with the Kotlin source.
- [x] Restore current portable settings not covered by the legacy adapter, while keeping device-local settings out.
- [x] Refresh existing native setting side effects and reconcile task alarms when reminder settings are restored.
- [x] Verify the change with the configured local Android unit-test script for both product flavors.
- [ ] Render Settings, the system picker, and the restore-confirmation screen on a device/emulator; none is available in this environment.

**Initial notes**

- Baseline before app edits: branch `main` was already one commit ahead of `origin/main`; the two supplied ZIPs and the existing `allowance/` documentation were untracked. No tracked app changes were present.
- Both ZIPs were listed before extraction and their paths checked; contents were extracted only under `/tmp/focusflow-import-export-review/`.
- The feature-specific backup service, Settings screen, import-confirm screen, and pending-import handoff files in the flat bundle are byte-identical to their counterparts in the full hybrid archive.
- Initial comparison confirms the Kotlin exporter includes current portable fields, while `BackupRestoreEngine` restores only fields recognized by the one-time `LegacySettingsAdapter`. Existing acceptance notes already identify missing portable-setting restoration; the legacy migration adapter and policy are plan no-touch files and must not be broadened.
- The hybrid Settings flow allows merge/replace before selecting a file and restores immediately; the native Kotlin flow already previews the selected backup and requires confirmation, with replacement off by default. Preserve the safer Kotlin flow.

**Evidence / notes**

- Reference files reviewed: `backupService.ts`, `types.ts`, `AppContext.tsx`, TypeScript Settings, `import-confirm.tsx`, pending-import staging, and root external-file dispatch in the full archive; feature-specific duplicate files in the flat bundle were byte-compared.
- Current Kotlin files reviewed: `BackupSerializer`, `PortableSettingsPolicy`, `BackupRestoreEngine`, `BackupViewModel`, `SettingsRepository`, `SettingsViewModel`, `AppSettings`, `RestoreGate`, and focused backup tests.
- Confirmed comparisons: both formats use the v1 `FocusFlowBackupV1` envelope and SAF document flows; TypeScript shallow-validates kind/settings/tasks but does not enforce version or equivalent preflight bounds, and can hide task-read failure by exporting an empty task list. Kotlin already enforces the version/resource checks, fails task-read errors, shows confirmation after file selection, defaults replacement off, checks active sessions, and reconciles alarms after the batch.
- UI source comparison: both implementations expose import/export actions in Settings. Native Kotlin routes a picked backup to a dedicated full-screen summary/confirmation view, with replacement off by default, a destructive warning, progress, and outcome dialogs. The existing Kotlin flow is safer than the hybrid flow and remains unchanged.
- Changed files: `BackupSettingsAdapter.kt`, `BackupRestoreEngine.kt`, `PortableSettingsPolicy.kt`, `SettingsRepository.kt`, `BackupRestoreEngineTest.kt`, and this tracker. `TsSettingsAdapter.kt`, `LegacySettingsPolicy.kt`, and `LegacySettingsMigration.kt` remain untouched.
- Initial edit issue: the first multi-file patch did not apply its engine hunks because patch hunks were ordered incorrectly; those changes were reapplied in a separate patch and are included in the passing final test run.
- First verification attempt: `bash scripts/test-unit.sh` passed both product flavors (155 tests each, 0 failures/errors/skips), and `git diff --check` passed. Source review then identified native scheduling/enforcement side effects, which were added before the final verification.
- Follow-up implementation: settings restore refreshes the existing allowance, recurring-schedule, VPN, overlay-quote, and reflection-reminder effects as applicable; task alarms reconcile when the imported task-reminder preference is present. Failed refreshes are returned as restore warnings.
- Final checks and results: `bash scripts/test-unit.sh` passed `:app:testProductionDebugUnitTest` and `:app:testTbtechsdevDebugUnitTest`; each XML report contains 155 tests, 0 failures, 0 errors, and 0 skipped, including `BackupRestoreEngineTest.currentPortableSettingsRestoreBeyondLegacyMappingAndIgnoreLocalFields`. `git diff --check` passed after the final source edits.
- Environment side effect and cleanup: a Python-based XML summary command added `python-base-3.13` to `.replit`. The validated replacement helper restored the exact pre-test `.replit` content; the final diff is empty and the temporary file is absent.
- Decisions, blockers, and deferred work: leave the TypeScript app and both supplied ZIPs unchanged; no push or GitHub Actions were requested or performed. Preserve the current Kotlin confirmation flow and parser. Actual Android-rendered UI and SAF-provider behavior remain unverified because no device/emulator is available.

| 2026-10-08 | Replit Agent | Batch 9 — reference comparison and portable settings parity | Compared both archive source flows with Kotlin; added portable setting restoration and native side-effect refreshes without changing legacy migration or replacement safety. Both local product flavors passed (155 tests each, 0 failures/errors/skips); `git diff --check` passed. Restored the incidental `.replit` module addition. No push/Actions. Device-rendered UI and SAF checks remain deferred. |

## Batch 10 — Import behavior parity audit

**Status:** Complete for source and local-test scope — import parity improvements passed both product flavors; rendered device checks remain deferred.

**Scope**

- Compare only the TS and Kotlin import parser, restore path, and confirmation preview.
- Skip structurally malformed task rows without rejecting unrelated valid tasks/settings, and surface the skipped-row count before confirmation and after restore.
- Make merge and replacement effects explicit in the Kotlin preview. Preserve the replace-off default, explicit confirmation, active-session guard, version/size limits, and all plan no-touch rules.

**Checklist**

- [x] Add per-task structural parsing that tolerates malformed rows but retains strict envelope validation.
- [x] Propagate bounded parse warnings through preview, retry, and successful restore states.
- [x] Clarify merge vs replace behavior and show the settings-field count on the Kotlin confirmation screen.
- [x] Add regression tests for mixed valid/invalid rows and warning persistence.
- [x] Run the local Android unit-test script for both flavors and reconcile all evidence.
- [ ] Render the changed confirmation screen and exercise a real picker on an Android device/emulator; none is available here.

**Initial notes**

- At batch start, the worktree already contains uncommitted Batch 9 changes and both attached reference ZIPs are untracked. Preserve all of them; no app files were changed before this batch.
- The TS importer validates the top-level envelope, then skips malformed task entries individually and continues importing valid tasks. Kotlin validates the envelope by decoding its entire `List<Task>` at once, so one structurally malformed row rejects the complete file. Plan §4 explicitly says malformed task rows should be skipped.
- The TS confirmation screen states what merge keeps/adds and that settings are merged in both modes; it also shows the count of settings fields. Kotlin confirms replacement is destructive but does not explain merge semantics or that replacement affects tasks only.
- No changes to `BackupJsonLimits.kt`, `TsSettingsAdapter.kt`, `LegacySettingsPolicy.kt`, `LegacySettingsMigration.kt`, or any other no-touch path.

**Progress notes**

- Parser change and regression test verified in both flavors: envelope metadata remains strict, task rows are decoded independently under the existing task-count bound, valid rows are retained, and a single bounded warning reports malformed rows.
- ViewModel and preview changes verified in both flavors: parse warnings remain visible on the confirmation screen, survive a retryable restore failure, and join the final success warnings. The preview now explains merge versus replacement and shows the settings-field count.
- Final implementation files for this batch: `BackupEnvelope.kt`, `BackupSerializer.kt`, `BackupViewModel.kt`, `ImportConfirmScreen.kt`, `BackupSerializerTest.kt`, and `BackupViewModelTest.kt`. The source and tests compile in both variants.
- Final verification: `bash scripts/test-unit.sh` passed `:app:testProductionDebugUnitTest` and `:app:testTbtechsdevDebugUnitTest`. Each XML report set has 36 suites, 157 tests, 0 failures, 0 errors, and 0 skipped; both new regression tests appear in each variant. `git diff --check` passed. No-touch-path diff query returned no files.
- Verification-report issue: the first XML aggregation expression assumed a specific attribute order and printed zero suites/tests; it did not indicate a Gradle test failure. A corrected count of the `tests` attribute reported 36 suites and 157 tests per variant, and the expected test names were present.
- Device limitation: source-level confirmation copy was reviewed, but no rendered Compose interaction or real SAF picker run was possible without an Android device/emulator.
- No GitHub Actions or push was requested or performed; `.replit` is unchanged.

| 2026-10-08 | Replit Agent | Batch 10 — import behavior parity | Made Kotlin task parsing tolerant of malformed individual rows while preserving strict envelope checks; propagated a bounded skip warning through confirmation, retry, and success. Clarified merge/replacement effects and added the settings-field count. Both local variants passed (157 tests each, 0 failures/errors/skips); `git diff --check` passed. Device UI/picker checks remain deferred. No push/Actions. |

## Batch 11 — Import UI parity
**Status:** In progress — comparing the complete TS settings entry and import-confirm flow with Kotlin before making UI-only changes.
**Scope**
- Compare import/export placement within Settings, file review hierarchy, merge/replace choice presentation, warnings, progress, and completion UI across the supplied TS references.
- Update Kotlin Compose UI to follow the TS hierarchy and compact sizing while preserving the existing platform picker, data summary, confirmation, restore behavior, and safety guards.
- Do not alter backup format, import semantics, persistence, or unrelated Settings sections.
**Checklist**
- [x] Audit both supplied TS UI sources and the current Kotlin Settings/import screen entry paths.
- [ ] Implement matching Settings placement and import-confirm screen hierarchy/states in Kotlin.
- [ ] Add/update UI-level tests where supported; run the local Android unit-test script for both flavors.
- [ ] Reconcile the tracker with actual code, tests, and explicitly deferred device rendering.
**Initial notes**
- Preserve Batch 9/10 uncommitted changes, both attached ZIPs, and current `.replit`; no push or GitHub Actions without explicit request.
- Existing Kotlin Settings places Backup & Restore directly after Profile and before Appearance; TS Settings must be located across both supplied extracts before deciding whether this placement matches.
- Device/emulator rendering and SAF picker interaction are not available in this workspace; do not claim visual runtime verification.

**Progress notes**
- Reference audit complete: the flat TS Settings places “Backup & Data” after Pomodoro and before Permissions; the TS hybrid Settings omits the entry, but its import route uses the same confirmation layout as the flat screen.
- Both TS confirmation screens use a centered cloud-download hero, “This file contains” icon rows, an explicit merge/replace choice, a red destructive warning and red replacement action, then an outlined Cancel action. Kotlin currently uses an uncentered title, summary-first layout, a persistent switch label, and a primary-colored Restore action.
- Kotlin currently places Backup & Restore directly after Profile, so it will move to the TS location between Pomodoro and Permissions. The existing list-based summary, parser warnings, and native document picker remain.
- Search found no Compose UI test setup or existing `ImportConfirmScreen` tests; local Android tests will compile the changed screen and cover the existing backup ViewModel behavior.
- An initial patch application failed because its hunks were not in file order; it made no source changes. Reapplying with ordered hunks.
- Settings section moved to match the flat TS position after Pomodoro and before Permissions; changed the rows to cloud upload/download icons and clarified that import opens a file for review. Picker behavior is unchanged; UI build verification is pending.
- Import confirmation now follows the TS hierarchy: centered cloud-download hero, icon-based summary rows, metadata line, merge/replace choice, red replacement warning/action, and outlined Cancel button. Existing task/settings counts, parser warnings, confirmation default, and session guard are retained; compilation and tests are pending.
- Layout pass keeps long warning copy within the card width and avoids duplicate Reading status text; verification remains pending.
- Verification failure: the compiler rejected `Icons.AutoMirrored.Outlined.FormatListBulleted` in both variants. The earlier `Icons.Outlined.FormatListBulleted` compiled but emitted a deprecation warning. Keep the test item open, use a supported non-directional icon, and record the successful rerun; no tests completed on this failed run.
- Replaced the directional task-list icon with the existing non-directional `Assignment` vector to avoid both the unsupported AutoMirrored reference and the deprecation warning; final rerun is pending.
- Follow-up test run passed both variants but confirmed `Assignment` also has a directional deprecation warning. Replace it with the existing non-directional `Description` vector and rerun before marking verification complete.
- The summary task row now uses the existing non-directional `Description` icon; final source/test verification is pending.
