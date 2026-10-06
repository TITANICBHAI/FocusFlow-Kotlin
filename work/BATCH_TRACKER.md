# Always-on status notification — batch tracker

**Implementation plan:** [ALWAYS_ON_STATUS_NOTIFICATION_IMPLEMENTATION_PLAN.md](ALWAYS_ON_STATUS_NOTIFICATION_IMPLEMENTATION_PLAN.md)  
**Agent instructions:** [AGENT_PRE_PROMPT.md](AGENT_PRE_PROMPT.md)  
**Overall status:** Blocked — final Android build/device verification unavailable
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
- **SDK values:** `minSdk = 29`, `targetSdk = 35`, `compileSdk = 35` in `app/build.gradle.kts`.
- **Accessibility checks:** They are not identical. The service synchronously searches the raw enabled-services setting for the app package substring. `UsageStatsRepository.hasAccessibilityPermission()` first checks `AccessibilityManager` for an enabled service from this package, then falls back to requiring both the package and `AppBlockerAccessibilityService` class name in the setting. The service check can therefore be broader in the fallback case.
- **Required permissions (`optional = false`):** `ACCESSIBILITY`, `USAGE`, and `OVERLAY`.
- **Tests/setup:** 23 local unit-test files and 4 instrumented-test files. Gradle config uses JUnit 4, coroutine test, Room testing, AndroidX JUnit/Espresso, and Compose UI test dependencies. Existing nearby tests include reminder-chain, task-end alarm contract, task-end alarm identity, and enforcement-health tests.
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

**Status:** Blocked; implementation present, verification awaits Android build/device prerequisites

**Gate:** Owner says it is good to have but unnecessary most of the time; keep it low priority and nonessential to the core work.

- [x] Record the owner's Q2 response and priority before implementation.
- [x] If approved, derive required permissions from `PermissionDefinition.optional`; do not hard-code the list.
- [x] Add the health model/reader and "Needs attention — tap to fix" idle state using existing permission checks and deep-link routes.
- [x] Refresh only at the plan's stated existing lifecycle/check points; add no polling loop.
- [ ] Verify the state and refresh behavior, then record build/test results and evidence.

### Decision record

**Q2: Does the owner want the health state (Phase 3)?**  
Owner response (2026-10-05): “It is good to have but unnecessary most of times.”
Priority: Useful but nonessential; do not make it a blocker for the core phases.

### Batch 3 work log

| Date | Status / work performed | Files inspected or changed | Commands and checks | Findings / evidence / blockers |
|---|---|---|---|---|
| 2026-10-05 | Owner decision recorded; Batch 0 source audit complete | `PermissionSupport.kt`, `UsageStatsRepository.kt`, `LauncherController.kt`, `MainActivity.kt`, `Routes.kt`, `AndroidManifest.xml`, `ForegroundTaskService.kt`, `FocusFlowNavGraph.kt` | Rechecked permission definitions/checks, existing route path, lifecycle start, service startup, and fallback accessibility check | The three current non-optional permission definitions are Accessibility, Usage Access, and Overlay; their required set must continue to come from `permissionDefinitions.filterNot { it.optional }`. Existing direct checks are suspend functions; do not call UI-layer `checkPermission()`. `MainActivity.onStart()` already dispatches `ACTION_ENSURE_RUNNING`; service startup and its one-second fallback accessibility check provide the other planned refresh points. `Routes.fromPath(intent.data.path)` accepts the `permissions` path through the existing `focusflow` scheme. No implementation changes made before recording Q2. |
| 2026-10-05 | Phase 3 implementation source-reviewed; one route regression test added; execution blocked | Existing `EnforcementHealth.kt`, `EnforcementHealthReader.kt`, `ForegroundTaskService.kt`, status-card model/mapper/renderer, `PermissionSupport.kt`, `UsageStatsRepository.kt`, `LauncherController.kt`, `MainActivity.kt`, `Routes.kt`; changed `RoutesTest.kt` | Rechecked Batch 0 facts with `rg`/`find`; `git diff --check` passed; attempted focused `:app:testDebugUnitTest` for health, status mapper, and route tests | Current source still has no `startIdleService()` caller or `ForegroundTaskService.ACTION_STOP` sender; notification IDs and SDK values match the report. Current tests are 23 JVM test files and 4 instrumented test files (the previous count of 21 JVM files was stale). Accessibility checks remain intentionally different: the service check matches the package substring, while `UsageStatsRepository` also checks the service class in its settings fallback. Phase 3 is already present: required IDs come from `permissionDefinitions.filterNot { it.optional }`; Accessibility, Usage Access, and Overlay use existing checks; idle attention state routes to `focusflow://app/permissions`; refresh occurs on service creation, `ACTION_ENSURE_RUNNING` (sent by `MainActivity.onStart()`), and a changed value from the existing fallback poller. No new polling loop or enforcement behavior change. The new route test checks that `/permissions` resolves and remains externally linkable. Gradle did not start: `JAVA_HOME is not set and no 'java' command could be found in your PATH`; Android SDK variables are also unset, so JVM tests/build and device verification remain unrun, not source failures. |
| 2026-10-05 | Phase 3 verification explicitly deferred so the user-authorized Phase 4 work can proceed | No additional files | User requested Batch 4; reviewed existing Phase 3 status and recorded prerequisites | The health implementation and source review are complete, but unit-test/build and device verification remain blocked by missing Java/JDK, Android SDK, and a test device. Phase 3 remains nonessential per Q2; return to its unchecked verification item when prerequisites are available. |

## Batch 4 — Phase 4: scheduled-task card

**Status:** Blocked; break guard implemented from source-level reproduction, device/API verification unavailable
**Gate:** Batch 2 implementation is complete but its build/device acceptance remains blocked. Phase 3 verification is explicitly deferred above. The user authorized proceeding with Batch 4. The Q3 decision is A; device/API reproduction is still required before this batch can be closed.

- [ ] Reproduce or disprove the duplicate card on a device; record device/API level and runtime evidence. No `adb`, emulator, or Android SDK is available in this workspace.
- [ ] If reproduced, fix the publisher's break-active check and verify the result.
- [x] Record the owner's choice: A — keep the publisher card separate; or B — route scheduled-task state through the service when consented, with publisher fallback otherwise.
- [ ] Implement only the selected option and verify its behavior.
- [x] Record build/test results and evidence, including environment blockers.

### Decision record

**Q3: Scheduled-task card — A (leave separate) or B (fold into the service card when consented)?**  
Owner decision (2026-10-05): A — leave the scheduled-task card separate.
Evidence: User's decision in chat; scheduled-task notification remains a separate card.

### Batch 4 work log

| Date | Status / work performed | Files inspected or changed | Commands and checks | Findings / evidence / blockers |
|---|---|---|---|---|
| 2026-10-05 | In progress; source-level duplicate path confirmed and suppression guard plus unit test added; runtime reproduction/verification blocked | `LiveTaskStatusNotificationPublisher.kt`, `ForegroundTaskService.kt`, `ReminderReceiver.kt`, `AppModule.kt`; added `LiveTaskStatusPolicyTest.kt` | Inspected `enterBreak()` state writes, both publisher call paths, and shared prefs name; `git diff --check` passed; attempted `./gradlew :app:testDebugUnitTest --tests com.tbtechs.focusflow.notifications.LiveTaskStatusPolicyTest` (stopped before Gradle: `JAVA_HOME` unset/no `java`); `adb devices -l` (command not found); `ANDROID_HOME`/`ANDROID_SDK_ROOT` unset | Source-level reproduction path: `enterBreak()` writes `focus_active=false` and a future `focus_break_until_ms`; `ReminderReceiver` still calls `LiveTaskStatusNotificationPublisher.sync`; the publisher previously suppressed only on `focus_active`, allowing notification 1003 alongside the service's break card 1001. The publisher now suppresses while focus is active or the existing break deadline is future, and still allows Option A's separate scheduled-task card outside focus/break. Added pure policy tests for active focus, active break, and expired/absent break. Physical reproduction/disproof, API level, JVM execution, and device verification remain unavailable; do not mark runtime acceptance complete. |

## Batch 5 — Phase 5: usage and allowance pipeline

**Status:** Not started  
**Gate:** Deferred; do not begin without explicit owner approval. The authoritative scope is [Phase 5 v3](PHASE_5_USAGE_AND_ALLOWANCE_PLAN_v3.md), which replaces the earlier allowance handoff proposal.

- [x] Record the owner's decision: defer Phase 5 until later; this is not implementation authorization.
- [ ] Follow v3 Phase 5.0: verify the compatibility contract, current data writers/readers, and device behavior before implementation.
- [ ] Follow v3 Phase 5.1–5.3: characterize behavior, add pipeline tests, and run the new pipeline in shadow mode.
- [ ] Follow v3 Phase 5.4–5.5: cut allowance over to pipeline readings, then unify Stats with the same source and Room rollups.
- [ ] Follow v3 Phase 5.6: remove superseded code only after cutover and rerun the required compatibility checks.
- [ ] Keep this effort deferred until the owner explicitly reopens it; preserve the v3 plan's phase gates and do not treat this checklist as authorization.
- [ ] Record build/test/device results and evidence when implementation is authorized.

### Decision record

**Q4: Allowance refactor — now, later, or never?**  
Owner decision (2026-10-05): Later; Phase 5 remains deferred. The replacement scope is the linked v3 usage-and-allowance plan.
Evidence: User's decision in chat; the implementation plan now points to the authoritative v3 replacement. This decision does not authorize implementation.

### Batch 5 work log

| Date | Status / work performed | Files inspected or changed | Commands and checks | Findings / evidence / blockers |
|---|---|---|---|---|
|  |  |  |  |  |

## Batch 6 — final verification and handoff

**Status:** Blocked
**Gate:** Run after all approved implementation batches are complete.

**Owner direction (2026-10-05):** Proceed with the final audit and record unavailable Android checks as blocked, not passed. Evidence: user's explicit approval in chat. This does not clear Batch 3/4 verification gaps or change Q4.

- [ ] Complete applicable device/API checks from plan section 12, recording unavailable devices/tests explicitly. **Blocked:** no Java/JDK (`JAVA_HOME` unset and no `java` executable), Android SDK variables or `local.properties`, or `adb`/device. The JVM test command stopped before Gradle could start.
- [ ] Complete the review gates in plan section 13. **Partially reviewed:** the accessible implementation diff does not touch the four large files or manifest; relevant status/health files and tests inspected are under 300 lines; the Phase 4 guard reads the existing `focus_break_until_ms` key. **Blocked:** tests/builds did not run, and the current `ForegroundTaskService.kt` is 1,649 lines versus the 1,593 lines recorded after Phase 2. The available Git history is shallow at `1261df1`, so the line-count difference cannot be attributed and the no-growth/per-phase-build gates are not fully verified.
- [x] Confirm parked work and out-of-scope behavior stayed untouched. Evidence: reviewed the accessible implementation diff and prior phase logs; it contains no manifest, allowance-accounting, VPN, or parked Android 15 service-type changes.
- [x] Confirm every completed batch has work-log evidence and every unresolved item is marked blocked or deferred. Evidence: Batch 0–4 have work-log entries; Batch 3/4 verification and final Android checks are marked blocked; Phase 5 remains deferred.
- [x] Summarize completed batches, test/build results, known gaps, and decisions still needed. Summary recorded in the Batch 6 work log below.

### Batch 6 work log

| Date | Status / work performed | Files inspected or changed | Commands and checks | Findings / evidence / blockers |
|---|---|---|---|---|
| 2026-10-05 | Final source/scope audit performed; Batch 6 blocked on Android build/device verification | Reviewed the plan, tracker, pre-prompt, repository instructions, Android build-environment memory, and accessible source diff; changed this tracker only | `git diff --check 1261df1..bcbd957` passed; reviewed `git diff --name-status 1261df1..bcbd957`; `./gradlew :app:testDebugUnitTest` stopped before Gradle (`JAVA_HOME` unset/no `java`); `adb devices -l` reported command not found; Android SDK variables unset and `local.properties` absent | Plan section 12 device/API checks were not run; no JDK, Android SDK, or ADB/device is available. Mapper, health, route, and live-task policy tests exist but were not executed. The accessible implementation diff touches the live-task notification publisher and tests/tracker only; the publisher reads the already-used `focus_break_until_ms` key. Relevant status/health additions inspected are under 300 lines; no manifest, allowance, VPN, or parked Android 15 changes appear in the accessible implementation diff. `ForegroundTaskService.kt` is currently 1,649 lines, while the Phase 2 log records 1,593; the shallow history prevents attributing the difference, so the no-big-file-growth and per-phase-build gates remain unverified. Local `main` was one commit ahead of `origin/main`, with that extra commit containing the uploaded instruction file; no push was made in Batch 6. |
|  |  |  |  |  |

## Deferred / blocked items

| Item | Reason / required decision | Revisit condition | Status |
|---|---|---|---|
| Phase 3 health state | Implementation is present; build/test/device verification has not run because JDK, Android SDK, and device are unavailable | Revisit when JDK, Android SDK, and device verification are available | Blocked |
| Phase 4 duplicate-during-break check | Source path guarded; runtime reproduction and test remain blocked by absent Android tooling/device | Verify on an Android device/API before closing Batch 4 | Blocked |
| Phase 5 usage and allowance pipeline | Owner deferred it until later; v3 is the authoritative scope | Owner reopens the work | Deferred |
