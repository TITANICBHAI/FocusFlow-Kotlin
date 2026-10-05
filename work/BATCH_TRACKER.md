# Always-on status notification — batch tracker

**Implementation plan:** [ALWAYS_ON_STATUS_NOTIFICATION_IMPLEMENTATION_PLAN.md](ALWAYS_ON_STATUS_NOTIFICATION_IMPLEMENTATION_PLAN.md)  
**Agent instructions:** [AGENT_PRE_PROMPT.md](AGENT_PRE_PROMPT.md)  
**Overall status:** Not started  
**Last updated:** 2026-10-05

## Tracking rules

1. Keep this tracker current as work happens. Do not reconstruct progress later from memory.
2. Leave every box unchecked until its work is actually done. A checked box must have supporting evidence in that batch's work log.
3. Before changing code, complete Batch 0 and record its read-only findings. Re-verify plan statements against the current repository; the source plan was based on an uploaded source snapshot.
4. Record each batch's status, date, changed files, commands/checks, results, evidence, and blockers/decisions. Update the log before stopping or handing work off.
5. Do not mark a batch complete while its required checks or acceptance criteria are unverified. If a check cannot run, record why and leave the affected item incomplete or blocked.
6. Respect decision gates. Record the owner's exact decision and date before doing work that depends on it. Do not treat a recommendation as approval.
7. Keep work within the plan's scope and constraints. Parked items stay untouched. Do not push changes or start/poll GitHub Actions unless the user explicitly asks.
8. The plan says each phase is a separate commit or PR. Record any phase boundary commit/PR if one is made; this tracker does not authorize a push.
9. If implementation changes a durable project rule or reveals a non-obvious repository constraint, update the relevant project memory entry as well as this tracker. Do not use memory as a substitute for the work log.

**Status values:** Not started · In progress · Blocked · Complete

## Batch 0 — Phase 0: read-only verification

**Status:** Not started  
**Gate:** Complete and report findings before coding.

- [ ] Confirm whether `startIdleService()` has callers.
- [ ] Confirm whether `ACTION_STOP` has senders.
- [ ] Find every `notify` / `startForeground` site and record notification IDs.
- [ ] Record `minSdk`, `targetSdk`, and `compileSdk` from the current Gradle files.
- [ ] Compare `ForegroundTaskService.isAccessibilityServiceEnabled()` with `UsageStatsRepository.hasAccessibilityPermission()`.
- [ ] List `PermissionDefinition` entries where `optional = false`.
- [ ] Identify existing tests and the available test setup.
- [ ] Record the repository/environment build prerequisites relevant to verification.
- [ ] Write a concise findings report below; note any difference from the plan.

### Batch 0 work log

| Date | Status / work performed | Files inspected or changed | Commands and checks | Findings / evidence / blockers |
|---|---|---|---|---|
|  |  |  |  |  |

**Findings report:**  
_Not yet recorded._

## Batch 1 — Phase 1: always-on lifecycle

**Status:** Not started  
**Gate:** Complete Batch 0 first. Resolve Decision Q1 before implementing the off switch.

- [ ] Add a side-effect-free ensure-running service action and command branch; do not route through `startIdleService()` / `ACTION_SET_IDLE`.
- [ ] Add `ForegroundServiceController.ensureRunning()` with failure logging and no thrown exception.
- [ ] Add consent- and onboarding-gated calls from `MainActivity.onStart` and the onboarding completion step.
- [ ] Leave `BootReceiver` unchanged.
- [ ] Record the owner's Q1 decision about switching the service off while enforcement is active.
- [ ] Add the consent setting/off switch using the existing key and `ACTION_STOP`, with the approved PIN/consequence behavior.
- [ ] Verify idle notification wording and count-up timer remain unchanged.
- [ ] Verify all Phase 1 acceptance items from plan section 5, or clearly record device-only items that could not be run.
- [ ] Verify no Phase 1 enforcement behavior changed and no prohibited keys, permissions, polling loops, or manifest changes were added.
- [ ] Record build/test results and evidence.

### Decision record

**Q1: May the background service be switched off while a focus session, standalone block, or always-on block is active?**  
Owner decision: _Not yet provided_  
Date / evidence: _Not recorded_

### Batch 1 work log

| Date | Status / work performed | Files inspected or changed | Commands and checks | Findings / evidence / blockers |
|---|---|---|---|---|
|  |  |  |  |  |

## Batch 2 — Phase 2: extract notification building

**Status:** Not started  
**Gate:** Complete Batch 1 first.

- [ ] Create the status-card model, pure mapper, Android renderer, and shared task-action intent builder described in plan section 6.
- [ ] Keep notification output equivalent: strings/emoji, action order, priority, ongoing/only-alert-once flags, and chronometer behavior.
- [ ] Update the service and publisher to delegate to the extracted code.
- [ ] Add JVM mapper tests for 12-hour label edge cases, progress clamping, and chronometer base math.
- [ ] Verify the service file shrinks and new files meet the plan's size guidance.
- [ ] Record before/after idle, active, and break notification evidence where available.
- [ ] Record build/test results and evidence.

### Batch 2 work log

| Date | Status / work performed | Files inspected or changed | Commands and checks | Findings / evidence / blockers |
|---|---|---|---|---|
|  |  |  |  |  |

## Batch 3 — Phase 3: enforcement health state

**Status:** Not started  
**Gate:** Requires explicit owner approval; this phase is recommended, not already decided.

- [ ] Record the owner's approval or deferral before implementation.
- [ ] If approved, derive required permissions from `PermissionDefinition.optional`; do not hard-code the list.
- [ ] Add the health model/reader and "Needs attention — tap to fix" idle state using existing permission checks and deep-link routes.
- [ ] Refresh only at the plan's stated existing lifecycle/check points; add no polling loop.
- [ ] Verify the state and refresh behavior, then record build/test results and evidence.

### Decision record

**Q2: Does the owner want the health state (Phase 3)?**  
Owner decision: _Not yet provided_  
Date / evidence: _Not recorded_

### Batch 3 work log

| Date | Status / work performed | Files inspected or changed | Commands and checks | Findings / evidence / blockers |
|---|---|---|---|---|
|  |  |  |  |  |

## Batch 4 — Phase 4: scheduled-task card

**Status:** Not started  
**Gate:** Complete Batch 2 first. After Phase 3, record whether it was completed or explicitly deferred. Then reproduce the possible duplicate during a break and record the owner's A/B decision before changing card behavior.

- [ ] Reproduce or disprove the duplicate card during a break; record device/API level and evidence.
- [ ] If reproduced, fix the publisher's break-active check and verify the result.
- [ ] Record the owner's choice: A — keep the publisher card separate; or B — route scheduled-task state through the service when consented, with publisher fallback otherwise.
- [ ] Implement only the selected option and verify its behavior.
- [ ] Record build/test results and evidence.

### Decision record

**Q3: Scheduled-task card — A (leave separate) or B (fold into the service card when consented)?**  
Owner decision: _Not yet provided_  
Date / evidence: _Not recorded_

### Batch 4 work log

| Date | Status / work performed | Files inspected or changed | Commands and checks | Findings / evidence / blockers |
|---|---|---|---|---|
|  |  |  |  |  |

## Batch 5 — Phase 5: allowance ownership refactor

**Status:** Not started  
**Gate:** Separate effort; do not begin without explicit owner approval.

- [ ] Record the owner's decision: now, later, or never.
- [ ] If approved for now, add characterization tests for the existing handoff rules before extraction.
- [ ] Preserve the current 15-second, 60-second, and 2-minute intervals and the live-session real-time behavior.
- [ ] Extract only according to plan section 9; stop after the ledger if accessibility-service extraction is too risky.
- [ ] Keep notification code out of this phase.
- [ ] Record build/test results and evidence.

### Decision record

**Q4: Allowance refactor — now, later, or never?**  
Owner decision: _Not yet provided_  
Date / evidence: _Not recorded_

### Batch 5 work log

| Date | Status / work performed | Files inspected or changed | Commands and checks | Findings / evidence / blockers |
|---|---|---|---|---|
|  |  |  |  |  |

## Batch 6 — final verification and handoff

**Status:** Not started  
**Gate:** Run after all approved implementation batches are complete.

- [ ] Complete applicable device/API checks from plan section 12, recording unavailable devices/tests explicitly.
- [ ] Complete the review gates in plan section 13.
- [ ] Confirm parked work and out-of-scope behavior stayed untouched.
- [ ] Confirm every completed batch has work-log evidence and every unresolved item is marked blocked or deferred.
- [ ] Summarize completed batches, test/build results, known gaps, and decisions still needed.

### Batch 6 work log

| Date | Status / work performed | Files inspected or changed | Commands and checks | Findings / evidence / blockers |
|---|---|---|---|---|
|  |  |  |  |  |

## Deferred / blocked items

| Item | Reason / required decision | Revisit condition | Status |
|---|---|---|---|
| Phase 3 health state | Requires owner approval | Owner chooses yes | Not started |
| Phase 4 card consolidation | Requires reproduction and A/B owner choice | Owner chooses A or B after reproduction | Not started |
| Phase 5 allowance refactor | Separate effort; requires owner approval | Owner chooses now | Not started |
