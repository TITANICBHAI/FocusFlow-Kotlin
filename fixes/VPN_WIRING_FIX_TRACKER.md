# FocusFlow VPN wiring fix — batch tracker

**Implementation plan:** [VPN_WIRING_FIX_PLAN.md](VPN_WIRING_FIX_PLAN.md)
**Agent instructions:** [VPN_WIRING_AGENT_PRE_PROMPT.md](VPN_WIRING_AGENT_PRE_PROMPT.md)
**Overall status:** Not started — implementation has not been authorized or begun
**Last updated:** 2026-10-05

## Tracking rules

1. Update this tracker while work is happening. Do not reconstruct progress later from memory.
2. Leave checklist items unchecked until completed. Every checked item needs evidence in its batch work log.
3. Complete Batch 0 and report its read-only findings before changing code. The plan is a static audit of an uploaded source snapshot; verify its findings against the current repository.
4. Search by symbol and behavior rather than relying on the plan's snapshot line numbers.
5. Keep the plan's order and scope. Test P0 findings first; if a repro test passes on current code, stop that task and report the mismatch.
6. Stop at unanswered owner-decision gates. A plan recommendation or default is not approval. Record the owner's exact decision and date before dependent implementation.
7. Record each batch's status, date, changed files, commands/checks, results, evidence, and blockers or decisions. Update the log before stopping or handing work off.
8. Do not mark work complete when required checks are unavailable. Record what could not run and why.
9. Route every VPN start/stop through `VpnPolicyCoordinator.requestSync` or `requestRecoverySync`. Keep its lock, generation counter, and debounce behavior intact.
10. Route writes through `restoreGate.write(...)` as the existing repositories do. Preserve preferences and backup data during migrations.
11. Do not touch the Launcher, Linux app, or backup file-format versions. Avoid UI, navigation, icon, and copy changes except where the plan explicitly requires them.
12. Do not push changes, start or poll GitHub Actions, or use the GitHub workflow unless the owner explicitly asks.
13. Keep project memory for durable constraints, not this work's progress log.

**Status values:** Not started · In progress · Blocked · Complete · Deferred

## Batch 0 — read-only baseline verification

**Status:** Not started
**Gate:** Finish and report this batch before any implementation.

- [ ] Confirm current writers, readers, and call sites for every VPN preference listed in plan §1.
- [ ] Re-verify the T1–T11 evidence against the current source; record any mismatches or findings already fixed.
- [ ] Locate existing tests and identify the narrowest test seams for each approved task.
- [ ] Check JDK 17, `JAVA_HOME`, Android SDK variables, `local.properties`, and available device/emulator tooling.
- [ ] Review the relevant `SettingsRepository`, `VpnRepository`, coordinator, service, screen, schedule, backup, and permission code before proposing implementation.
- [ ] Record the baseline findings and any owner decisions needed before coding.

### Batch 0 work log

| Date | Status / work performed | Files inspected or changed | Commands and checks | Findings / evidence / blockers |
|---|---|---|---|---|
|  |  |  |  |  |

## Batch 1 — P0 T1: explicit VPN targets and derived snapshots

**Status:** Not started
**Gate:** Batch 0 complete; confirm the T1 evidence and failing repro test first.

- [ ] Add and run the T1-repro test before changing implementation.
- [ ] Add and run T1-migrate and T1-migrate-2 tests.
- [ ] Make `net_block_explicit_packages` the only explicit-list input and implement the one-time, idempotent migration without losing user data.
- [ ] Remove any seeding of explicit packages from derived effective targets.
- [ ] Preserve `net_block_packages` as diagnostic output only.
- [ ] Run the relevant tests and record verification evidence.

### Batch 1 work log

| Date | Status / work performed | Files inspected or changed | Commands and checks | Findings / evidence / blockers |
|---|---|---|---|---|
|  |  |  |  |  |

## Batch 2 — P0 T2: Defense self-healing toggle

**Status:** Not started
**Gate:** Batch 1 complete.

- [ ] Verify the current Defense toggle and all self-heal preference call sites.
- [ ] Connect the Defense preference to the native self-heal key with a safe one-time migration.
- [ ] Ensure the Defense toggle owns disabling; list screens may enable self-healing when needed but must not turn it off.
- [ ] Preserve the backup import protection rule for this setting.
- [ ] Add/run focused tests for off/on state, watchdog cancellation, and recovery scheduling.
- [ ] Record results and any unavailable checks.

### Batch 2 work log

| Date | Status / work performed | Files inspected or changed | Commands and checks | Findings / evidence / blockers |
|---|---|---|---|---|
|  |  |  |  |  |

## Batch 3 — P0 T3: VPN consent result handling

**Status:** Not started
**Gate:** Batch 2 complete.

- [ ] Re-verify every consent request caller named in plan §2, T3.
- [ ] Apply enabled-state changes only after system VPN consent is confirmed.
- [ ] Preserve each caller's existing behavior; run the cancellation test and related tests.
- [ ] Remove the legacy request-code flow only after all callers are migrated.
- [ ] Record results and any unavailable device verification.

### Batch 3 work log

| Date | Status / work performed | Files inspected or changed | Commands and checks | Findings / evidence / blockers |
|---|---|---|---|---|
|  |  |  |  |  |

## Batch 4 — P1 T4 and T8-a: VPN list persistence and master-switch ownership

**Status:** Not started
**Gate:** Batch 3 complete. Q3 is decided; resolve Q4 before implementing the VPN-list persistence/migration.

- [ ] Read the relevant backup contract and verify current import/export wiring.
- [x] Record the owner's Q3 decision about restoring a non-empty list while Network Blocking is off (see the owner decision record and dated work-log entry).
- [ ] Record the owner's Q4 decision about the single source of truth for the VPN list.
- [ ] Implement the approved VPN-list persistence and migration without overwriting or dropping user data.
- [ ] During Import, request VPN consent for a non-empty restored list when Network Blocking is off; only after an actual grant, enable Network Blocking (VPN), VPN Self-Healing, and the matching persisted VPN settings.
- [ ] If VPN consent is denied/canceled, continue the selected restore, preserve the list as dormant, and do not newly enable either switch.
- [ ] Add one informational post-import summary with collapsible cards for supported imported VPN, Always-On, daily allowance, keyword, and Greyout/block schedule settings; show truthful active/inactive status without another confirmation prompt.
- [ ] Ensure saving an empty list does not disable the master Network Blocking switch.
- [ ] Add/run export-import round-trip, empty-list, consent-grant/cancel, and summary coverage tests.
- [ ] Record results and any unavailable checks.

### Batch 4 work log

| Date | Status / work performed | Files inspected or changed | Commands and checks | Findings / evidence / blockers |
|---|---|---|---|---|
| 2026-10-05 | Owner decision recorded; implementation not started. | `VPN_WIRING_FIX_PLAN.md`, this tracker, `VpnBlockListScreen`, `AlwaysOnScreen`, `VpnRepository`, `DefenseScreen` | Reviewed the user's answer and compared list-save/consent behavior with the current code. | Q3: request Android VPN consent during Import; on grant activate Network Blocking (VPN), VPN Self-Healing, and the imported list; on denial/cancel continue restore with the list dormant. Use one informational post-import summary with collapsible cards for imported protection categories. Read-only check found list-save/native and Defense UI self-heal state are not fully synchronized; T2/T8-a must verify and fix that. Q4 remains pending. |

## Batch 5 — P1 T5 and T6: standalone and schedule VPN enforcement

**Status:** Not started
**Gate:** Batch 4 complete. Resolve Q1 before implementing schedule VPN scope. T5/T6 share the boundary scheduler and must be coordinated.

- [ ] Re-verify standalone-block VPN selection, persistence, expiry clearing, and all relevant callers.
- [ ] Record the owner's Q1 decision on which schedule apps are VPN-blocked and when.
- [ ] Add/run fixed-time tests for schedule windows, overnight windows, and week boundaries.
- [ ] Implement shared boundary start/stop scheduling and verify it is re-armed on the required lifecycle events.
- [ ] Add/run standalone expiry and schedule boundary tests, including the no-user-action stop case.
- [ ] Record results and unavailable device checks.

### Batch 5 work log

| Date | Status / work performed | Files inspected or changed | Commands and checks | Findings / evidence / blockers |
|---|---|---|---|---|
|  |  |  |  |  |

## Batch 6 — P1 T7: permission-lost recovery banner

**Status:** Not started
**Gate:** Batch 5 complete.

- [ ] Re-verify all effective VPN target sources and current permission-loss banner conditions.
- [ ] Ensure the banner covers each approved VPN source and recovery uses the coordinator without changing the explicit list.
- [ ] Add/run focused tests for focus-mirror-only configuration and revoked VPN permission.
- [ ] Record results and any unavailable device verification.

### Batch 6 work log

| Date | Status / work performed | Files inspected or changed | Commands and checks | Findings / evidence / blockers |
|---|---|---|---|---|
|  |  |  |  |  |

## Batch 7 — P2 T8–T12: hardening and cleanup

**Status:** Not started
**Gate:** Batch 6 complete. Resolve Q2 before T8. T12 is optional and must not expand scope without a clear need.

- [ ] Record the owner's Q2 decision on Wi-Fi/mobile-data side effects before changing T8 behavior.
- [ ] Fix the `vpn_failed_packages` clobbering issue with a distinct, accurately exposed invalid-package state.
- [ ] Remove dead code only after verifying zero call sites and completing dependent tasks.
- [ ] Add the active-block guard to the Defense master toggle where approved by the plan.
- [ ] Decide whether the optional pure policy calculator is needed; do not implement it by default.
- [ ] Run relevant tests and record results.

### Batch 7 work log

| Date | Status / work performed | Files inspected or changed | Commands and checks | Findings / evidence / blockers |
|---|---|---|---|---|
|  |  |  |  |  |

## Owner decision record

Record each decision, date, and evidence before dependent work. Pending questions remain implementation gates.

| Plan question | Decision needed | Status | Owner answer / date / evidence |
|---|---|---|---|
| Q1 | Schedule VPN scope and active-window behavior | Pending |  |
| Q2 | Remove Wi-Fi/mobile-data behavior, or expose it with safe defaults and a working restore path | Pending |  |
| Q3 | Behavior when import includes a VPN list but Network Blocking is off | Decided (2026-10-05) | Request VPN consent during Import; on grant turn on Network Blocking and VPN Self-Healing and activate the imported list. On denial/cancel, continue restore with the list dormant. Use one informational expandable summary for imported protection categories; do not ask for another in-app confirmation. |
| Q4 | Single source of truth for the explicit VPN list | Pending |  |

## Required test scenarios

- [ ] T1-repro: focus-mirror targets stop blocking after focus ends when there is no explicit VPN list.
- [ ] T1-migrate: missing explicit key plus generation 0 migrates legacy snapshot once.
- [ ] T1-migrate-2: missing explicit key plus positive generation does not treat derived targets as user picks.
- [ ] T2: Defense self-heal on/off updates native state and watchdog/recovery scheduling.
- [ ] T3: cancelling VPN consent leaves the setting unchanged.
- [ ] T4: backup round-trip preserves the approved VPN list source of truth.
- [ ] T4-consent-granted: restore with Network Blocking off; actual Android consent grant activates both switches and the list.
- [ ] T4-consent-cancelled: cancellation leaves the list stored but inactive and does not newly enable either switch.
- [ ] T4-vpn-permission-pregranted: existing VPN permission skips the system prompt but still activates the imported list and both switches.
- [ ] T4-summary: one summary shows collapsible cards for imported supported categories and truthful status.
- [ ] T5: standalone VPN targets stop at expiry without user interaction.
- [ ] T6: schedule VPN targets apply only inside approved windows, including overnight and week-wrap cases.
- [ ] T7: permission-lost recovery appears for approved effective VPN sources.
- [ ] T8-a: saving an empty list leaves the master switch unchanged.

## Final verification

- [ ] Build and run unit tests for the completed scope; identify any unavailable checks as blocked, not passed.
- [ ] Run all applicable verification greps from plan §6 and record exact output.
- [ ] Complete feasible manual device checks from plan §6; record device/API and results.
- [ ] Review the final diff for data loss, unauthorized scope, and changes outside the plan.
- [ ] Confirm every completed batch has evidence and every unresolved item remains blocked, pending, or deferred.
- [ ] Record a final handoff summary with completed scope, test/build results, blockers, and the next authorized batch.

## Final work log

| Date | Status / work performed | Files inspected or changed | Commands and checks | Findings / evidence / blockers |
|---|---|---|---|---|
| 2026-10-05 | Handoff documents prepared; no implementation started | `fixes/VPN_WIRING_FIX_PLAN.md`, `fixes/VPN_WIRING_FIX_TRACKER.md`, `fixes/VPN_WIRING_AGENT_PRE_PROMPT.md` | Read the full attached plan and the existing `work/BATCH_TRACKER.md` and `work/AGENT_PRE_PROMPT.md`; no code/build/test changes made | Plan moved under `fixes/`; implementation status remains Not started. Batch 0 and all owner decisions remain open. |
