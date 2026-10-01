# FocusFlow Backup / Import Work Tracker

## Source documents

- [Implementation plan](FocusFlow_Backup_Import_Implementation_Plan_v12.md) — authoritative contract, source audit, architecture, and acceptance criteria.
- [Incoming-agent prompt](AGENT_PRE_READ.md) — copy-ready instructions for an agent joining after work has started.

This tracker divides implementation into batches by the kind of change being made. The detailed contract and test requirements remain in the implementation plan; this file tracks execution and evidence.

## Tracking rules

- Tick an item only after its change is implemented **and** its stated behavior has been verified. A code edit alone is not completion.
- Update this tracker as each item or coherent sub-batch is completed; do not wait until the whole project is finished.
- Record the verification evidence or reason for deferral in the Notes column. Keep a batch `In progress` while any item is being worked on.
- Re-check the current source before acting on source-audit findings. The plan describes the audited archives; files may have changed since.
- Do not begin production implementation until the required reviewer decisions below are explicitly accepted.
- A deferred or blocked item stays unchecked. Never infer approval or completion.

## Setup

| Item | Status |
|---|---|
| Copy the uploaded plan into this stable `work/fixes/` location | [x] Done; byte-compared with the uploaded file |
| Create the batch tracker | [x] Done |
| Create the incoming-agent pre-read prompt | [x] Done |

## Batch 0 — Decision gate and source/data contract

**Plan references:** §§2A, 4, 65 Phase A, 69B  
**Status:** Blocked — required reviewer acceptance is not recorded here.

| Check | Status | Evidence / notes |
|---|---|---|
| Confirm the current implementation still matches the plan’s source/data audit; record any divergence | [ ] | |
| Reviewer accepts the TS ↔ Kotlin field-level matrix row by row | [ ] | |
| Reviewer accepts the frozen decisions in §4 with no open implementation choices | [ ] | |
| Reviewer explicitly accepts the EXACT-unavailable policy in §69B | [ ] | |
| Reviewer explicitly accepts the PREPARED → MUTATING crash boundary in §69B | [ ] | |
| Reviewer explicitly accepts the NotificationScheduler source/build verification requirement in §69B | [ ] | |

**Gate:** Do not start implementation batches until all required reviewer decisions are recorded. Phase A’s audit is documented in the plan; that does not mean this approval gate has passed.

## Batch 1 — Migration baseline and production wiring

**Plan references:** §1, §65 Phases A.1–B  
**Status:** Not started

| Check | Status | Evidence / notes |
|---|---|---|
| Verify and correct the legacy SQLite settings key (`app_settings`) | [ ] | |
| Verify and correct the SharedPreferences target namespace used by the live settings source of truth | [ ] | |
| Ensure a missing or invalid legacy setting does not permanently mark migration complete | [ ] | |
| Record the actual target SDK and build facts from the current project | [ ] | |
| Verify a concrete production `NotificationScheduler` exists and is wired; implement/wire it if absent | [ ] | |
| Verify the background reminder gateway is installed if the periodic worker owns re-arming | [ ] | |
| Consolidate restore behavior behind one canonical domain flow instead of extending duplicate backup layers | [ ] | |

## Batch 2 — Restore and import foundations

**Plan references:** §§5–13, §65 Phase C  
**Status:** Not started

| Check | Status | Evidence / notes |
|---|---|---|
| Implement the global `RestoreWriteGate` and ensure conflicting writers use it | [ ] | |
| Implement durable `RestoreSessionStore` state and the exact persisted `RestorePlan` | [ ] | |
| Implement durable `PendingImportStore` lifecycle | [ ] | |
| Implement `NormalizedBackup`, pure `RestorePlanner`, canonical serialization, and integrity hash | [ ] | |
| Implement startup recovery barrier before ordinary writers are admitted | [ ] | |
| Verify phase transitions, recovery behavior, and that unrecoverable post-mutation failure keeps writers blocked | [ ] | |

## Batch 3 — External file import

**Plan references:** §§26–30, §65 Phase D  
**Status:** Not started

| Check | Status | Evidence / notes |
|---|---|---|
| Route `ACTION_VIEW` imports on cold start and warm `onNewIntent` through the shared import flow | [ ] | |
| Enforce bounded reads, strict UTF-8 and JSON parsing, duplicate-key rejection, and V1 validation | [ ] | |
| Persist a validated import snapshot before showing confirmation | [ ] | |
| Use the shared import-confirmation UI for in-app and external entry points | [ ] | |
| Verify same-file retry after failure/cancel, and deterministic rejection of a second active import | [ ] | |
| Verify content URI/provider and `file://` behavior against the plan’s compatibility matrix | [ ] | |

## Batch 4 — Persistent restore and conflict handling

**Plan references:** §§8–25, §§50–52, §65 Phase E  
**Status:** Not started

| Check | Status | Evidence / notes |
|---|---|---|
| Commit Room changes using the planned transaction and cross-store recovery protocol | [ ] | |
| Restore SharedPreferences through a dedicated persistence path, not asynchronous ViewModel field-by-field updates | [ ] | |
| Implement task merge/replace behavior and pre-mutation conflict planning | [ ] | |
| Verify equivalent task IDs are skipped and divergent same-ID tasks fail before mutation | [ ] | |
| Preserve reminder identity and correctly map task/reminder relationships | [ ] | |
| Verify replace scope does not silently delete unrelated or historical local data | [ ] | |
| Apply the field-level portable/local/runtime settings policy, including `focusMirrorVpnEnabled` and setup fields | [ ] | |
| Apply package and external-resource actions explicitly; report unresolved or cleared values in plan/result | [ ] | |

## Batch 5 — Scheduler registry, reminders, and task-end alarms

**Plan references:** §§32–45, §49, §65 Phase F  
**Status:** Not started

| Check | Status | Evidence / notes |
|---|---|---|
| Add the persistent desired-state scheduler registry and crash-safe write/apply ordering | [ ] | |
| Implement and wire `FocusFlowNotificationScheduler` and task reminder persistence/reconstruction | [ ] | |
| Give task-end alarms deterministic per-task identity, including per-task PendingIntent and notification identity | [ ] | |
| Validate stale callbacks against current task state before surfacing an alarm | [ ] | |
| Reconcile desired scheduler state after restore and cancel known obsolete FocusFlow-owned identities | [ ] | |
| Verify normal task/reminder create, edit, completion, skip, and delete paths keep scheduler ownership consistent | [ ] | |
| Verify multi-alarm isolation and do not treat `FLAG_NO_CREATE` as proof an alarm is registered | [ ] | |

## Batch 6 — Reboot and capability recovery

**Plan references:** §§46–48, §65 Phase G  
**Status:** Not started

| Check | Status | Evidence / notes |
|---|---|---|
| Reconcile alarms on the required boot/unlock lifecycle events | [ ] | |
| Detect exact-alarm grant and revocation changes and reconcile at the required lifecycle points | [ ] | |
| Keep EXACT task-end semantics when exact-alarm capability is unavailable; persist the planned deferred result rather than silently downgrading | [ ] | |
| Diagnose notification permission, channel state, and full-screen-intent capability separately | [ ] | |

## Batch 7 — Runtime alarm investigation

**Plan references:** §§40–45, §65 Phase H  
**Status:** Not started

| Check | Status | Evidence / notes |
|---|---|---|
| Run the runtime matrix for the available TS baseline, current Kotlin behavior, and Kotlin after changes | [ ] | |
| Record active-device, locked/screen-off, permission-denied, channel-blocked, and FSI-unavailable outcomes separately | [ ] | |
| Verify no user tap is required to surface an alarm where platform capability permits; measure actual activity visibility/resume rather than inferring it from `startActivity()` | [ ] | |
| Record device, OS/build, SDK, permission/capability state, and observed results | [ ] | |

## Batch 8 — Regression suite and final sign-off

**Plan references:** §§54–66, §65 Phase I  
**Status:** Not started

| Check | Status | Evidence / notes |
|---|---|---|
| Run golden fixtures and TS V1 ↔ Kotlin semantic compatibility tests | [ ] | |
| Run parser, boundary-limit, UTF-8, duplicate-key, unknown-field/enum, and wire-format tests | [ ] | |
| Run task conflict, duplicate projection, settings policy, and relationship tests | [ ] | |
| Run restore lifecycle, crash recovery, cross-store failure, and zero-mutation-on-invalid-input tests | [ ] | |
| Run provider/URI compatibility tests | [ ] | |
| Run scheduler registry, reminder, multi-alarm cross-talk, stale-callback, and reboot tests | [ ] | |
| Run alarm-fronting instrumentation where device/platform capabilities allow; record limitations | [ ] | |
| Complete every applicable reviewer sign-off item in §66; explicitly document any platform-only exception | [ ] | |
| Run the strongest available project build and test checks; record environment limitations | [ ] | |

## Progress log

| Date | Batch / item | Change and verification evidence | Agent |
|---|---|---|---|
| 2026-10-02 | Setup | Filed and byte-verified plan copy; created tracker and incoming-agent prompt. No application code changed. | Replit Agent |