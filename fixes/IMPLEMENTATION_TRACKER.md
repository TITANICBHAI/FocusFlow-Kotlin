# FocusFlow Backup / Import / Alarm Implementation Tracker

## Reference files

- [Implementation contract](FOCUSFLOW_IMPLEMENTATION_PLAN_FINAL_v14.md) — authoritative behavior, data boundaries, work order, and tests.
- [Review notes](REVIEW_NOTES_FINAL_v14.txt) — uploaded review of the plan.
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

Status: **Not started**

- [ ] Never apply the 21 live-state keys from either old or current backup files.
- [ ] Use an explicit external-route allowlist; never expose `IMPORT_CONFIRM` to arbitrary links.
- [ ] Remove `BROWSABLE` from both content URI filters while retaining the specified MIME support.
- [ ] Introduce canonical UTC millisecond timestamps for imports and every Kotlin task write.
- [ ] Verify merge/replace settings behavior and ensure the import cannot weaken protection without the required PIN flow.
- [ ] Add regression tests for old exports, route rejection, and timestamp formatting.

### M1 — Wire model, parser, validation, settings adapter, and legacy migration

Status: **Not started**

- [ ] Model the V1 envelope and TS wire names in one shared adapter.
- [ ] Enforce bounded, strict parsing; reject duplicate JSON keys and duplicate task IDs before mutation.
- [ ] Validate task/settings records and preserve the null-versus-empty package-list semantics.
- [ ] Route legacy settings migration through the adapter and correct the key, preference store, and marker behavior.
- [ ] Verify new model/settings fields against current repositories and enforcement consumers before adding storage.
- [ ] Add parser, validation, migration, and compatibility fixtures from contract §11.

### M2 — Export and round-trip compatibility

Status: **Not started**

- [ ] Export the V1 envelope using TS field names and the portable settings boundary.
- [ ] Export tasks with canonical timestamps and reminders preserved opaquely.
- [ ] Export user and recurring greyout data according to contract §10.
- [ ] Verify TS-compatible and Kotlin round trips with golden fixtures.

### M3 — Durable import and recovery engine

Status: **Not started**

- [ ] Add the process-wide restore gate and route every relevant persistent writer through it.
- [ ] Persist validated `PendingImport` before confirmation and handle process death, cancel, replacement, and startup recovery.
- [ ] Build a pure restore plan; detect divergent same-ID conflicts before writing a journal or mutating data.
- [ ] Test a same-ID Merge duplicate with differing reminders: it remains identical and preserves the local reminder array.
- [ ] Apply tasks transactionally and settings with checked, idempotent commits.
- [ ] Rebuild derived state only in the reconciliation phase.
- [ ] Retain the journal and keep the gate closed after post-mutation recovery failure; provide explicit Retry/Discard behavior.
- [ ] Quarantine unreadable/unknown-version journals instead of silently deleting them.
- [ ] Test every crash point, partial settings commit, retry path, and discard path from §6.6.

### M4 — Task-end alarms and reconciliation

Status: **Not started**

- [ ] Give all task-end PendingIntents collision-resistant per-task data URI identity and notifications per-task identity.
- [ ] Make past-trigger scheduling a no-op and remove inexact task-end fallback.
- [ ] Persist and reconcile alarm registry state; defer exact-alarm-unavailable entries and retry on capability changes.
- [ ] Validate task state at fire time, use receiver time budgets, and deduplicate notifications.
- [ ] Add startup/boot/permission/time/task-change reconciliation and the overdue sweep.
- [ ] Keep restore, alarm cancellation, and reconciliation serialized with the restore gate.
- [ ] Verify exact-alarm behavior against current Android documentation and test the multi-alarm cross-talk cases.

### M5 — Reminder chain

Status: **Not started**

- [ ] Generate reminder slots from task IDs and task times, not `Task.reminders`.
- [ ] Enforce the 450-slot budget and skip ineligible or expired task slots.
- [ ] Schedule one reminder-chain alarm and replan after each fire.
- [ ] Gate reminders using the device-local reminder preference and persist a bounded dedupe ledger.
- [ ] Test chain delivery, rescheduling, cancellation, and budget behavior.

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
| 2026-10-03 | Initial source review | Reviewed backup coordinator/manager, task model/repository, alarm repository, routes, manifest, and task timestamp write sites. Confirmed review-note dispositions above. | No implementation milestone started. Android build and device matrix not run. |

Final handoff must list changed files, completed tracker items, exact checks run, source
assumptions that differed from the contract, and all unresolved or device-only items.