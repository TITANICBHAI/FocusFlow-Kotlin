# FocusFlow Backup / Import / Alarm Implementation Tracker

## Reference files

- [Implementation contract](FOCUSFLOW_IMPLEMENTATION_PLAN_FINAL_v14.md) — authoritative behavior, data boundaries, work order, and tests.
- Review notes: `REVIEW_NOTES_FINAL_v14.txt` — reviewed earlier; the workspace copy is currently absent. Do not reconstruct it from memory.
- [Agent pre-read prompt](AGENT_PRE_READ_PROMPT.md) — context and working rules for agents without prior chat history.

Use the implementation contract as the product specification. Re-check code before
implementing each item; the contract describes intended behavior and may be newer than
the current implementation. Do not treat a checked plan item as proof that code already
has that behavior.

## Review-note decisions

| Finding | Decision | Evidence / action |
|---|---|---|
| Recovery failure releases the gate and deletes corrupt journals | **No plan edit needed; implementation remains outstanding.** | The supplied v14 contract already requires `RECOVERY_BLOCKED`, a closed gate, retained journal, and quarantine of corrupt journals (§6.5). The current app still uses the legacy callback-based restore path; it has no durable journal/recovery coordinator. Implement and test the contract rather than reverting the corrected text. |
| Reminder differences are excluded from duplicate identity | **Accepted; corrected in the plan.** | `Task.reminders` is persisted and serialized (`data/model/Task.kt`, `data/repository/TaskRepository.kt`); task insertion ignores an existing ID (`TaskDao`/`TaskRepository`). The contract now explicitly says differences are ignored and the existing reminder array wins. |
| Filename says v14, internal heading says v13-FINAL | **Already corrected; no edit needed.** | The supplied Markdown already says `Final Plan (v14)` and supersedes `v13-FINAL` and earlier plans. |

## Baseline confirmed by source inspection

These are observations, not completed work:

- `BackupCoordinator` delegates to the existing `BackupManager` and supplies a no-op
  `scheduleTasks` callback. The restore path is not the journaled flow in the contract.
- `BackupManager` currently applies imported live-state toggles, combines
  `focusMirrorVpnEnabled` with logical OR, and skips same-ID tasks without comparing
  their content.
- `Routes.fromPath` can resolve `IMPORT_CONFIRM`; the content URI filters in
  `AndroidManifest.xml` include `BROWSABLE`.
- Task write paths use `Instant.toString()`; the contract's shared canonical timestamp
  formatter is not in place.
- `AlarmRepository` currently posts an alarm immediately for a past trigger, permits
  an inexact third-tier fallback for task-end alarms, and uses request-code hashes for
  PendingIntent identity. Notification dismissal also uses a global ID.
- `Task.reminders` is persisted on the task. The new reminder scheduler must use task
  times as specified, not reinterpret this array.

Re-inspect these paths before implementation. They can change between milestones.

## Status key

- **Not started** — no implementation work verified.
- **In progress** — work is underway; leave unchecked until its completion criteria pass.
- **Blocked** — record the exact blocker and what is needed to resolve it.
- **Verified** — implementation and relevant checks have passed; tick the associated boxes and record evidence below.

## Milestones

All milestones start **Not started**. Keep the contract's order; do not silently skip
security or recovery work.

### M0 — Security and routing hotfix

Status: **Blocked** — implementation criteria are complete; Gradle build/unit-test verification cannot start because Java and the Android SDK are unavailable.

- [x] Never apply the 21 live-state keys from either old or current backup files.
- [x] Use an explicit external-route allowlist; never expose `IMPORT_CONFIRM` to arbitrary links.
- [x] Remove `BROWSABLE` from both content URI filters while retaining the specified MIME support.
- [x] Introduce canonical UTC millisecond timestamps for imports and every Kotlin task write.
- [x] Verify merge/replace settings behavior and ensure the import cannot weaken protection without the required PIN flow.
- [x] Add regression tests for old exports, route rejection, and timestamp formatting.

### M1 — Wire model, parser, validation, settings adapter, and legacy migration

Status: **Blocked** — implementation and fixtures are in place; Gradle unit-test verification cannot start because Java and the Android SDK are unavailable.

- [x] Model the V1 envelope and TS wire names in one shared adapter.
- [x] Enforce bounded, strict parsing; reject duplicate JSON keys and duplicate task IDs before mutation.
- [x] Validate task/settings records and preserve the null-versus-empty package-list semantics.
- [x] Route legacy settings migration through the adapter and correct the key, preference store, and marker behavior.
- [x] Verify new model/settings fields against current repositories and enforcement consumers before adding storage.
- [x] Add parser, validation, migration, and compatibility fixtures from contract §11.

Verification notes:

- `git diff --check` and source-level assertions passed for bounded import parsing,
  duplicate-ID validation ordering, adapter wiring, migration key/store/marker behavior,
  and daily-allowance persistence. Gradle unit tests were not run.
- The current container has no `java`, `kotlinc`, `JAVA_HOME`, or Android SDK variables,
  so M0 and M1 remain **Blocked** pending a runnable Android toolchain.
- `alwaysOnVpnPackages` already has model and SharedPreferences read/write paths. No direct
  enforcement consumer was found in `VpnRepository` or the accessibility service; no new
  enforcement behavior was added without contract evidence.

### M2 — Export and round-trip compatibility

Status: **Blocked** — marked complete per user; local Android test execution remains
unavailable because this environment has no `java` command or `JAVA_HOME`.

- [x] Export the V1 envelope using TS field names and the portable settings boundary.
- [x] Export tasks with canonical timestamps and reminders preserved opaquely.
- [x] Export user and recurring greyout data according to contract §10.
- [x] Verify TS-compatible and Kotlin round trips with golden fixtures.

Verification notes:

- The user confirmed M2 is done. Source inspection found the V1 exporter, golden
  fixture, and Kotlin/TypeScript round-trip test coverage.
- Local execution is still blocked: `bash ./gradlew :app:testDebugUnitTest --no-daemon`
  failed with `JAVA_HOME is not set and no 'java' command could be found in your PATH`.
  No Kotlin compilation or unit-test result is available in this environment; this
  status reflects the user's completion report, not a locally verified Android build.

### M3 — Durable import and recovery engine

Status: **In progress** — implementation and focused test sources are present, but
source review is not complete and the relevant Android tests were not run, per the
user's instruction not to run Gradle/builds.

- [ ] Add the process-wide restore gate and route every relevant persistent writer through it.
- [ ] Persist validated `PendingImport` before confirmation and handle process death, cancel, replacement, and startup recovery.
- [ ] Build a pure restore plan; detect divergent same-ID conflicts before writing a journal or mutating data.
- [ ] Test a same-ID Merge duplicate with differing reminders: it remains identical and preserves the local reminder array.
- [ ] Apply tasks transactionally and settings with checked, idempotent commits.
- [ ] Rebuild derived state only in the reconciliation phase.
- [ ] Retain the journal and keep the gate closed after post-mutation recovery failure; provide explicit Retry/Discard behavior.
- [ ] Quarantine unreadable/unknown-version journals instead of silently deleting them.
- [ ] Test every crash point, partial settings commit, retry path, and discard path from §6.6.

Progress notes:

- Current source includes the gate, atomic PendingImport and journal stores, pure restore
  planning, phased restore coordination, recovery, and Retry/Discard UI.
- Focused tests now cover gate drain/suspension, PendingImport recreation/replacement/cancel,
  reminder-different Merge duplicates, divergent-ID rejection, failed PendingImport and
  journal writes, closed-gate admission, phase-boundary replay, partial settings replay,
  pending cleanup retry, unknown-version quarantine, and discard/retry behavior.
- Source review still needs to close the full §6.6 crash matrix, including failure inside
  the actual Room restore transaction, and confirm every relevant writer path. No Gradle,
  build, or test task was run per user instruction; M3 remains unchecked pending that work.

### M4 — Task-end alarms and reconciliation

Status: **In progress** — M4 behavior and current Android exact-alarm guidance
have been source-reviewed. Cross-talk instrumentation is present but unexecuted;
Java/Android SDK and an emulator/device are unavailable in this workspace.

- [x] Give all task-end PendingIntents collision-resistant per-task data URI identity and notifications per-task identity.
- [x] Make past-trigger scheduling a no-op and remove inexact task-end fallback.
- [x] Persist and reconcile alarm registry state; defer exact-alarm-unavailable entries and retry on capability changes.
- [x] Validate task state at fire time, use receiver time budgets, and deduplicate notifications.
- [x] Add startup/boot/permission/time/task-change reconciliation and the overdue sweep.
- [x] Keep restore, alarm cancellation, and reconciliation serialized with the restore gate.
- [x] Verify exact-alarm behavior against current Android documentation.
- [ ] Execute the multi-alarm cross-talk instrumentation cases on an Android emulator/device.

Progress notes:

- Source audit confirmed per-task alarm/show PendingIntent URIs and notification tags,
  exact-only task-end scheduling, past-trigger no-op behavior, durable registry,
  exact-access deferral/retry triggers, receiver validation/budgets/deduplication,
  boot/startup/permission/wall-clock/task reconciliation, overdue sweeping, and
  shared restore-gate serialization.
- `FocusSessionViewModel.startFocusMode` can adjust a task's start/end times when
  starting immediately. It now reconciles task-end alarms after that update.
- The existing instrumentation test already covered distinct alarm/activity
  PendingIntents and per-task deduplication. It now also checks per-task PendingIntent
  cancellation isolation, UI replacement via `onNewIntent`, and task-specific
  dismissal isolation. The added instrumentation has not been executed.
- Current Android documentation confirms that PendingIntent matching uses
  `Intent.filterEquals` (including data URI), and that `setAlarmClock()` and
  `setExactAndAllowWhileIdle()` require exact-alarm access. The app checks
  `canScheduleExactAlarms()` and handles the grant broadcast. Android does not
  send that broadcast on revocation; `MainActivity.onResume` requests reconciliation
  after returning from Settings. Fresh installs targeting API 33+ are denied
  `SCHEDULE_EXACT_ALARM` by default. References:
  [PendingIntent](https://developer.android.com/reference/android/app/PendingIntent),
  [AlarmManager](https://developer.android.com/develop/background-work/services/alarms),
  [Android 14 exact-alarm changes](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms).
- No Gradle/build/test command was run per user instruction. Device-matrix checks
  remain outside this milestone's source-only verification. This shell has no
  `java`, `JAVA_HOME`, Android SDK variables, `adb`, or emulator executable.
- Inline source assertions passed for exact-only scheduling, the past-trigger
  guard, per-task identities, reconcile triggers, receiver budgets, and the
  existing cross-talk cases; `git diff --check` passed. These checks do not
  establish Kotlin compilation or Android runtime behavior.

### M5 — Reminder chain

Status: **In progress** — reminder-chain implementation and focused test sources are
present; unit and Android test execution remains unverified.

- [x] Generate reminder slots from task IDs and task times, not `Task.reminders`.
- [x] Enforce the 450-slot budget and skip ineligible or expired task slots.
- [x] Schedule one reminder-chain alarm and replan after each fire.
- [x] Gate reminders using the device-local reminder preference and persist a bounded dedupe ledger.
- [ ] Test chain delivery, rescheduling, cancellation, and budget behavior.

Progress notes:

- Extracted the existing task-time reminder rules into a pure planner. It emits
  the seven reminder slot types from §TS.8; task-end alarms remain separate, and
  the planner never reads `Task.reminders`.
- The Android path now uses one fixed-identity AlarmManager PendingIntent at
  `focusflow-internal://reminder-chain`, selects the next future slot, and
  replans after a fire. It uses the exact-alarm tiers when available and
  `setAndAllowWhileIdle()` as the inexact reminder fallback.
- Task reconciliation now rearms or cancels the chain from the device-local
  `taskRemindersEnabled` preference. The settings toggle requests reconciliation
  after its preference write completes; restore and task-change reconciliation
  use the same path.
- Added a SharedPreferences ledger keyed by slot ID, retained for 48 hours and
  capped at 450 entries, and task-reminder notifications use `(tag = slotId,
  id = 1)`. The 450-entry cap is an implementation bound aligned with the
  planner budget; v14 specifies 48-hour pruning but no separate ledger count.
- Added unit coverage for derived slots, eligibility/expiry, the 450-slot cap,
  due delivery and deduplication, rescheduling, cancellation, disabled
  preferences, and ledger retention/bounds. Tests have not been executed.

### M6 — Alarm presentation and diagnostics

Status: **Not started**

- [ ] Consolidate task-alarm notification channel creation.
- [ ] Capture and display the capability snapshot described in §8.8.
- [ ] Add the API-34+ full-screen-intent capability prompt/diagnostic behavior.
- [ ] Restrict any background `startActivity` fallback as specified and do not treat a non-throwing call as proof of visibility.
- [ ] Add runtime timestamps and complete the required device-state matrix; attach actual results before sign-off.

## Verification and handoff record

Do not mark a milestone **Verified** until the corresponding tests/checks have run.
Separate source/build failures from missing SDK/device capabilities.

| Date | Milestone | Checks and evidence | Remaining blockers / device-only checks |
|---|---|---|---|
| 2026-10-03 | Initial source review | Reviewed backup coordinator/manager, task model/repository, alarm repository, routes, manifest, and task timestamp write sites. Confirmed review-note dispositions above. | At that point, no implementation milestone had started. Android build and device matrix had not been run. |
| 2026-10-03 | M0 | Implemented the live-state import denylist, external-route allowlist, content-filter changes, canonical task timestamp boundary, and weakening-import PIN gate. `git diff --check` and static source/fixture assertions passed. Source review confirms settings restore remains independent of task Merge/Replace. | `bash ./gradlew :app:testDebugUnitTest --no-daemon` could not start: no `java` command or `JAVA_HOME`. `ANDROID_HOME` and `ANDROID_SDK_ROOT` are unset; no build or unit-test result is available. Review-notes file is absent from the current workspace and was not recreated. |
| 2026-10-03 | M2 | User confirmed M2 is done. Source inspection found the V1 exporter and golden compatibility/round-trip tests; all M2 tracker items are checked. | Local `bash ./gradlew :app:testDebugUnitTest --no-daemon` failed because Java/`JAVA_HOME` is unavailable; no local Android build or unit-test result. |
| 2026-10-03 | M3 | Added focused gate, PendingImport, conflict/reminder, journal-write, retry, discard, quarantine, and phase-replay test sources; recovery routing and writer gates were extended. | Full §6.6 coverage—especially failure inside the Room restore transaction—and final writer-path audit remain; tests were intentionally not run per user instruction. |
| 2026-10-03 | M4 follow-up audit | Source assertions passed for the task-end invariants and current cross-talk test source; `git diff --check` passed. Current Android docs and task-change/recovery triggers were reviewed. | Instrumentation and device-matrix checks remain unrun; local Java/Android SDK and emulator/device are unavailable. M4 stays in progress. |

Final handoff must list changed files, completed tracker items, exact checks run, source
assumptions that differed from the contract, and all unresolved or device-only items.