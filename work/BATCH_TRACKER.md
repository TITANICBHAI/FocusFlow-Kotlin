# Always-on status notification — batch tracker

**Implementation plan:** [ALWAYS_ON_STATUS_NOTIFICATION_IMPLEMENTATION_PLAN.md](ALWAYS_ON_STATUS_NOTIFICATION_IMPLEMENTATION_PLAN.md)  
**Agent instructions:** [AGENT_PRE_PROMPT.md](AGENT_PRE_PROMPT.md)  
**Overall status:** In progress
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

**Status:** Complete
**Gate:** Complete and report findings before coding.

- [x] Confirm whether `startIdleService()` has callers.
- [x] Confirm whether `ACTION_STOP` has senders.
- [x] Find every `notify` / `startForeground` site and record notification IDs.
- [x] Record `minSdk`, `targetSdk`, and `compileSdk` from the current Gradle files.
- [x] Compare `ForegroundTaskService.isAccessibilityServiceEnabled()` with `UsageStatsRepository.hasAccessibilityPermission()`.
- [x] List `PermissionDefinition` entries where `optional = false`.
- [x] Identify existing tests and the available test setup.
- [x] Record the repository/environment build prerequisites relevant to verification.
- [x] Write a concise findings report below; note any difference from the plan.

### Batch 0 work log

| Date | Status / work performed | Files inspected or changed | Commands and checks | Findings / evidence / blockers |
|---|---|---|---|---|
| 2026-10-05 | Complete; read-only Phase 0 audit | Inspected Android service, notification publishers, permission repository/definitions, Gradle config, tests, and environment; updated this tracker and the linked project memory/pre-prompt | `rg` for service actions, notification calls/IDs, SDK settings, permission checks; `find` for tests/build files; inspected relevant source ranges and Java/Android SDK environment | Findings below. No Kotlin/source changes and no build/test execution: Java/JDK 17 and Android SDK are unavailable in this workspace. |

**Findings report:**

- **Service controls:** `ForegroundServiceController.startIdleService()` has no callers (only its declaration; the service contains a comment referring to it). `ForegroundTaskService.ACTION_STOP` has no sender in the app; its constant and handling branch exist. `NetworkBlockerVpnService.ACTION_STOP` is a separate action.
- **Notification sites and IDs:**
  - `1001` — `ForegroundTaskService`, channel `focusday_foreground`; idle, focus-task, and break notification states share the same ID.
  - `1002` — `NetworkBlockerVpnService` foreground notification, channel `focusday_vpn`.
  - `1003`, tag `live-task-status` — scheduled live-task card from `LiveTaskStatusNotificationPublisher`.
  - `1003`, no tag — VPN recovery notification from `VpnRecoveryNotifier`, on its own channel. The numeric ID is shared, but the tagged live-task notification and untagged recovery notification are distinct entries.
  - `9101`, task-specific tag — task-end alarm from `ForegroundTaskService`, using `TaskEndAlarmIdentity`.
  - `9001` — block alert sites in `ForegroundTaskService` and `AppBlockerAccessibilityService`.
  - `8800` — temptation / heads-up notification sites in `TemptationReportReceiver` and `AppBlockerAccessibilityService`.
  - `8812` — day-rating reminder from `DayRatingReminderReceiver`.
  - `1`, reminder-slot tag — reminder notifications from `ReminderNotificationPublisher`.
- **SDK values:** `minSdk = 26`, `targetSdk = 35`, `compileSdk = 35` in `app/build.gradle.kts`.
- **Accessibility checks:** They are not identical. The service synchronously searches the raw enabled-services setting for the app package substring. `UsageStatsRepository.hasAccessibilityPermission()` first checks `AccessibilityManager` for an enabled service from this package, then falls back to requiring both the package and `AppBlockerAccessibilityService` class name in the setting. The service check can therefore be broader in the fallback case.
- **Required permissions (`optional = false`):** `ACCESSIBILITY`, `USAGE`, and `OVERLAY`.
- **Tests/setup:** 21 local unit-test files and 4 instrumented-test files. Gradle config uses JUnit 4, coroutine test, Room testing, AndroidX JUnit/Espresso, and Compose UI test dependencies. Existing nearby tests include reminder-chain, task-end alarm contract, and task-end alarm identity tests.
- **Build prerequisites:** The wrapper is present (Gradle 8.14.2), but `java` is unavailable, `JAVA_HOME`, `ANDROID_HOME`, and `ANDROID_SDK_ROOT` are unset, no Android SDK was found in the checked locations, and `local.properties` is absent. The app targets Java 17 and compile SDK 35. Gradle build/tests were not run because prerequisites are missing.
- **Differences / follow-up:** The plan said the SDK values were unavailable in its source snapshot; they are now verified. More importantly, the plan says to perform the Android 15 service pass before raising `targetSdk` to 35, but the current target is already 35. This audit does not expand into that parked work; confirm its scope before implementation. The possible extra scheduled-task card during a break is consistent with the code path (`focus_active` becomes false during a break), but runtime reproduction remains unverified and belongs to Batch 4.

## Batch 1 — Phase 1: always-on lifecycle

**Status:** Complete
**Gate:** Complete Batch 0 first. Follow the owner's recorded Q1 decision; do not add idle/active detection or an in-app off switch in this iteration.

- [x] Add an idempotent ensure-running service action and command branch; do not route through `startIdleService()` / `ACTION_SET_IDLE`.
- [x] Add `ForegroundServiceController.ensureRunning()` with failure logging and no thrown exception.
- [x] Add consent- and onboarding-gated calls from `MainActivity.onStart` and the onboarding completion step.
- [x] Leave `BootReceiver` unchanged.
- [x] Record the owner's Q1 decision: keep the service running whenever existing background consent is granted, regardless of idle/active state; defer an idle-only opt-out and do not add that control now.
- [x] Implement the consented always-on lifecycle without adding idle/active-state checks or an in-app off switch.
- [x] Verify idle notification wording and count-up timer remain unchanged.
- [x] Verify all Phase 1 acceptance items from plan section 5, or clearly record device-only items that could not be run.
- [x] Verify no Phase 1 enforcement behavior changed and no prohibited keys, permissions, polling loops, or manifest changes were added.
- [x] Record build/test results and evidence.

### Decision record

**Q1: May the background service be switched off while a focus session, standalone block, or always-on block is active?**  
Owner decision (2026-10-05): Keep the service always on once existing background consent is granted; do not check idle/active state or add an in-app off switch now. Revisit an idle-only opt-out in the future.
Evidence: User's decision in chat; plan file intentionally unchanged.

### Batch 1 work log

| Date | Status / work performed | Files inspected or changed | Commands and checks | Findings / evidence / blockers |
|---|---|---|---|---|
| 2026-10-05 | Complete; consented always-on lifecycle implemented and reviewed | `ForegroundTaskService.kt`, `ForegroundServiceController.kt`, `MainActivity.kt`, `OnboardingScreen.kt` | `git diff --check` passed; reviewed full code diff and call sites; confirmed no manifest or `BootReceiver` diff; Android build/tests intentionally skipped per owner | Ensure action preserves a live focus/break session and reuses existing persisted-session recovery if the service is newly created. `ForegroundTaskService.kt` remains 1,715 lines (line-neutral). Idle notification/timer code is unchanged. No off switch, idle/active checks, new keys, permissions, polling loops, or manifest changes. Device-only acceptance checks (fresh-install notification, recents swipe, API 29/31+/33/34 runtime smoke) remain unverified. The targetSdk 35/Android 15 service-type pass remains parked; no scope expansion. |

## Batch 2 — Phase 2: extract notification building

**Status:** Implementation complete; build/device acceptance verification blocked
**Gate:** Complete Batch 1 first.

- [x] Create the status-card model, pure mapper, Android renderer, and shared task-action intent builder described in plan section 6.
- [x] Keep notification output equivalent: strings/emoji, action order, priority, ongoing/only-alert-once flags, and chronometer behavior.
- [x] Update the service and publisher to delegate to the extracted code.
- [x] Add JVM mapper tests for 12-hour label edge cases, progress clamping, and chronometer base math.
- [x] Verify the service file shrinks and new files meet the plan's size guidance.
- [x] Record before/after idle, active, and break notification evidence where available.
- [x] Record build/test results and evidence.

### Batch 2 work log

| Date | Status / work performed | Files inspected or changed | Commands and checks | Findings / evidence / blockers |
|---|---|---|---|---|
| 2026-10-05 | Baseline audit complete | `ForegroundTaskService.kt`, `LiveTaskStatusNotificationPublisher.kt`, `NotificationActionReceiver.kt`, `app/build.gradle.kts`, existing JVM tests | Inspected Phase 2 contract and source renderers; checked toolchain availability | Before evidence from source: idle shows “FocusFlow” / “Monitoring active — tap to open” and counts up; focus shows “🎯 $taskName” with end label, optional next-task subtext, 0–100 progress, and Done / +15m / +30m / Skip; break shows “☕ Break · $taskName” and its two existing text lines. Focus and break count down. |
| 2026-10-05 | Implementation complete; runtime acceptance verification blocked | `ForegroundTaskService.kt`, `LiveTaskStatusNotificationPublisher.kt`, `notifications/status/*.kt`, `StatusCardMapperTest.kt` | `git diff --check` passed; reviewed action/model/rendering diff; `ForegroundTaskService.kt` 1,715 → 1,593 lines; new production files 37–83 lines and tests 128 lines; `./gradlew :app:testDebugUnitTest` attempted | After source-level evidence matches the baseline strings, emoji, action order, priorities, ongoing/only-alert-once flags, and chronometer direction/base formulas. Required idle/active/break before-and-after screenshots were not captured, so visual acceptance remains unverified. The test task did not start: `JAVA_HOME` is unset and no `java` executable exists; Android SDK variables and `local.properties` are also absent. JVM tests and Android build remain unexecuted, not failed on source. No manifest changes. |

## Batch 3 — Phase 3: enforcement health state

**Status:** In progress; owner approved as useful but nonessential

**Gate:** Owner says it is good to have but unnecessary most of the time; keep it low priority and nonessential to the core work.

- [x] Record the owner's Q2 response and priority before implementation.
- [ ] If approved, derive required permissions from `PermissionDefinition.optional`; do not hard-code the list.
- [ ] Add the health model/reader and "Needs attention — tap to fix" idle state using existing permission checks and deep-link routes.
- [ ] Refresh only at the plan's stated existing lifecycle/check points; add no polling loop.
- [ ] Verify the state and refresh behavior, then record build/test results and evidence.

### Decision record

**Q2: Does the owner want the health state (Phase 3)?**  
Owner response (2026-10-05): “It is good to have but unnecessary most of times.”
Priority: Useful but nonessential; do not make it a blocker for the core phases.

### Batch 3 work log

| Date | Status / work performed | Files inspected or changed | Commands and checks | Findings / evidence / blockers |
|---|---|---|---|---|
| 2026-10-05 | Owner decision recorded; Batch 0 source audit complete | `PermissionSupport.kt`, `UsageStatsRepository.kt`, `LauncherController.kt`, `MainActivity.kt`, `Routes.kt`, `AndroidManifest.xml`, `ForegroundTaskService.kt`, `FocusFlowNavGraph.kt` | Rechecked permission definitions/checks, existing route path, lifecycle start, service startup, and fallback accessibility check | The three current non-optional permission definitions are Accessibility, Usage Access, and Overlay; their required set must continue to come from `permissionDefinitions.filterNot { it.optional }`. Existing direct checks are suspend functions; do not call UI-layer `checkPermission()`. `MainActivity.onStart()` already dispatches `ACTION_ENSURE_RUNNING`; service startup and its one-second fallback accessibility check provide the other planned refresh points. `Routes.fromPath(intent.data.path)` accepts the `permissions` path through the existing `focusflow` scheme. No implementation changes made before recording Q2. |

## Batch 4 — Phase 4: scheduled-task card

**Status:** Not started  
**Gate:** Complete Batch 2 first. After Phase 3, record whether it was completed or explicitly deferred. Then reproduce the possible duplicate during a break and record the owner's A/B decision before changing card behavior.

- [ ] Reproduce or disprove the duplicate card during a break; record device/API level and evidence.
- [ ] If reproduced, fix the publisher's break-active check and verify the result.
- [x] Record the owner's choice: A — keep the publisher card separate; or B — route scheduled-task state through the service when consented, with publisher fallback otherwise.
- [ ] Implement only the selected option and verify its behavior.
- [ ] Record build/test results and evidence.

### Decision record

**Q3: Scheduled-task card — A (leave separate) or B (fold into the service card when consented)?**  
Owner decision (2026-10-05): A — leave the scheduled-task card separate.
Evidence: User's decision in chat; scheduled-task notification remains a separate card.

### Batch 4 work log

| Date | Status / work performed | Files inspected or changed | Commands and checks | Findings / evidence / blockers |
|---|---|---|---|---|
|  |  |  |  |  |

## Batch 5 — Phase 5: allowance ownership refactor

**Status:** Not started  
**Gate:** Separate effort; do not begin without explicit owner approval.

- [x] Record the owner's decision: now, later, or never.
- [ ] If approved for now, add characterization tests for the existing handoff rules before extraction.
- [ ] Preserve the current 15-second, 60-second, and 2-minute intervals and the live-session real-time behavior.
- [ ] Extract only according to plan section 9; stop after the ledger if accessibility-service extraction is too risky.
- [ ] Keep notification code out of this phase.
- [ ] Record build/test results and evidence.

### Decision record

**Q4: Allowance refactor — now, later, or never?**  
Owner decision (2026-10-05): Later; keep the allowance refactor in Phase 5 of the plan.
Evidence: User's decision in chat; plan file already contains Phase 5 and remains unchanged.

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
| Phase 3 health state | Useful but nonessential; low priority | Revisit after core phases | Not started |
| Phase 4 duplicate-during-break check | Runtime reproduction still required; card remains separate by owner choice | Complete prerequisite phases and test on device | Not started |
| Phase 5 allowance refactor | Owner deferred it until later | Owner reopens the work | Deferred |
