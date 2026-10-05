# Always-on status notification: implementation plan

For the coding agent · FocusFlow Android · based on a read of the uploaded `app.zip` source · 5 Oct 2026

## 0. How to read this plan

- Phases are ordered. Each phase is its own commit or PR, and the app must build and behave correctly at the end of each one.
- **Decided** means the owner has said so. **Recommended** means it is a suggestion that needs the owner's OK (see section 11). **Parked** means do not touch it.
- Some facts below come from reading the code, not running it. Phase 0 re-verifies them before any change.

## 1. Goal

One persistent notification, owned by `ForegroundTaskService` (id 1001, channel `focusday_foreground`). It is shown whenever the user has consented to background operation, regardless of which blocking features are on. The service staying up keeps the process alive so the accessibility service, fallback poller, allowance sync and VPN health check keep running.

The notification does **not** list which modes (standalone, always-on, allowance, keyword) are active. Those features already check their own flags on every event, so the notification needs no state tracking of its own. The idle wording stays as it is today: title "FocusFlow", text "Monitoring active — tap to open", with the count-up timer (**Decided**).

## 2. What exists today

- `ForegroundTaskService` (about 80 KB) owns notification 1001. It builds the idle, active-task and break notifications inline, with the Done / +15m / +30m / Skip actions. Its own header says it "runs persistently at all times".
- `ForegroundServiceController.startIdleService()` has **zero callers**. `ACTION_STOP` on the service has **zero senders**. In practice the service starts only from a focus session, a task edit, or `BootReceiver` (and `BootReceiver` only starts idle mode when the consent flag `user_consented_background_service` is set).
- `LiveTaskStatusNotificationPublisher` posts a separate card (id 1003, channel `live-task-status`) for a scheduled task when no focus session is running. It is called from `ReminderReceiver.kt:97` and `AppModule.kt:254`, and cancelled by `ForegroundServiceController.startService`.
- The four task action buttons are built twice: in the service (request codes 2–5, no data URI) and in the publisher (codes 3001–3004, unique data URI per task and action).
- The publisher only checks `focus_active`. A break sets `focus_active` to false, so the publisher card may appear next to the service's break card (**not reproduced, verify in Phase 4**).
- Existing god files: `AppBlockerAccessibilityService.kt` (about 232 KB), `StandaloneBlockModal.kt` (about 85 KB), `ForegroundTaskService.kt` (about 80 KB), `LauncherActivity.kt` (about 80 KB).

## 3. Ground rules (every phase)

1. **No god files.** One responsibility per new file. Aim for under about 250 lines each; justify anything over 300. Separate pure logic (data in, data out) from Android framework calls so the pure part can be unit tested on the JVM.
2. **Do not grow the big files.** In `AppBlockerAccessibilityService.kt`, `ForegroundTaskService.kt`, `LauncherActivity.kt` and `StandaloneBlockModal.kt`, the net line count of every change must be zero or negative. Allowed edits there: delete code that moved out, add thin delegation calls, add a small command branch.
3. **No enforcement behavior changes** in Phases 1–4. Blocking, allowances, keyword blocking, VPN and focus logic are untouched.
4. **Reuse before writing:** `NotificationChannels`, the existing `focusday_foreground` channel creation, `SetupPersistenceManager` consent flag, `SettingsRepository` consent-key mapping, `StartupLogger`, `NotificationActionReceiver`, `VpnRecoveryNotifier` (pattern for a "needs attention" notification), `UsageStatsRepository.hasAccessibilityPermission()` / `hasPermission()`, existing PIN modals and `PinManager`, existing deep-link routes.
5. **Do not add:** new polling loops, new SharedPreferences keys, new permissions, manifest changes.
6. Out of scope: the VPN notification (owner does not care about it).

## 4. Phase 0: verify (read-only, report back before coding)

Confirm and report:

- `startIdleService` and `ACTION_STOP` really have no callers or senders.
- Every `notify` / `startForeground` site and its id (known: 1001, 1003, 9001 block alert, 8800 temptation/heads-up, task-alarm, day-rating, VPN).
- `minSdk`, `targetSdk` and `compileSdk` from the Gradle files (they were not in the upload).
- Whether `ForegroundTaskService.isAccessibilityServiceEnabled()` (substring match on `ENABLED_ACCESSIBILITY_SERVICES`) and `UsageStatsRepository.hasAccessibilityPermission()` agree.
- Which `PermissionDefinition` entries are `optional = false`.
- Whether any tests exist in the repo and what test setup is available.

## 5. Phase 1: always-on lifecycle (Decided)

1. **Add a side-effect-free "ensure running" command** to `ForegroundTaskService` (new action constant, one branch in `onStartCommand`). It only guarantees the service is up in the foreground and returns `START_STICKY`. **Do not reuse `startIdleService()` or `ACTION_SET_IDLE` for this.** Both end in `goIdle()`, which also stops aversive actions, calls `stopNetworkBlock()`, rebuilds the notification and pushes a widget update. Doing that on every app open is unnecessary and may interfere with a running block.
2. **`ForegroundServiceController.ensureRunning()`** wraps the start in try/catch (Android 12+ can reject foreground-service starts from the background) and logs failures through `StartupLogger`. It never throws.
3. **Call sites:** `MainActivity.onStart` (the app is visible, so starting is allowed) and the onboarding finish step right after the consent flag is written, so a fresh install shows the notification without a reboot. Both are gated on the consent flag and onboarding-complete. **Do not** start it from `FocusFlowApp.onCreate`: the process can be created in the background by receivers and alarms.
4. `BootReceiver` stays as is.
5. **Off switch.** Add a Settings control for the existing consent key (the key mapping already exists in `SettingsRepository`). Turning it off sends the existing `ACTION_STOP`. Warning: `ACTION_STOP` calls `clearFocusActive()`, which ends focus enforcement. It must not be reachable during an active focus session without the PIN (see decision Q1). Reuse the existing PIN verification pattern. Show a plain consequences message consistent with the wording on the Permissions screen.
6. Idle card content and count-up timer unchanged. Known quirk, accepted for now: the timer base is the service's last start time, so it restarts from zero after the service restarts.

**Acceptance:**

- Fresh install, finish onboarding: notification visible without reboot.
- Swipe the app from recents: service and notification still present (device dependent).
- Open the app repeatedly during a standalone block with aversive actions on: behavior unchanged.
- Focus session, extend, skip, break and end all look the same as before.
- Consent off: no notification, and none after reboot.
- Smoke test on API 29, 31+, 33, 34.

## 6. Phase 2: move notification building out of the service (pure refactor)

Do this **before** adding any new card state, so new states do not land in the 80 KB file. No visible change.

New package `notifications/status/`:

- `StatusCardModel`: immutable description of what the card shows (title, text, sub text, chronometer, progress, action set, priority).
- `StatusCardMapper`: pure functions that turn mode (idle, focus, break) plus task name, start, end, next name and clock into a `StatusCardModel`. It holds the end-time label and progress math that is currently inline.
- `StatusCardRenderer`: Android-side. Turns the model into a `Notification` via `NotificationCompat`.
- `TaskActionIntents`: the single place that builds the Done / +15m / +30m / Skip pending intents. Use the publisher's variant (unique data URI per task and action) because plain request codes with `FLAG_UPDATE_CURRENT` can collide. Keep `FLAG_IMMUTABLE`.

Then:

- The service's three build functions become one-line delegations. Output must be identical: same strings and emoji, same action order, same priority, ongoing and only-alert-once flags, same chronometer behavior.
- The publisher replaces its private pending-intent code with `TaskActionIntents` but keeps its own channel and behavior.
- JVM unit tests for the mapper: 12-hour label edge cases (12:00 AM, 12:30 PM), progress clamping, chronometer base math.

**Acceptance:** screenshots of idle, active and break match before and after. `ForegroundTaskService.kt` shrinks (target at least about 120 lines).

## 7. Phase 3: health state (Recommended, needs owner OK)

The idle card must not say "Monitoring active" when enforcement cannot work. Add one extra idle variant: "Needs attention — tap to fix".

- New `EnforcementHealth` (pure data) and `EnforcementHealthReader` (framework). Reuse `UsageStatsRepository.hasAccessibilityPermission()` and `hasPermission()`. Do not call `checkPermission()` from `PermissionSupport.kt`: it is a UI-layer suspend function that reaches into `AppModule`.
- Derive which permissions count as required from the existing `PermissionDefinition.optional` flags. Do not hard-code a list.
- Tap opens the existing Permissions route through the existing deep-link mechanism.
- No new loops. Refresh on service start, on activity start, and when the fallback poller's existing accessibility check changes value (it already runs once a second). Staleness of up to about a minute elsewhere is acceptable.

## 8. Phase 4: scheduled-task card (needs owner decision)

0. Reproduce the possible duplicate card during a break. If real, fix it in the publisher by also skipping while a break is active. This is a small fix in a 7 KB file.
1. **Option A (default): leave the publisher card as is.** During a scheduled task with no focus session the user sees the small idle card plus the live-task card.
2. **Option B:** fold the scheduled-task state into the service card. Add a tiny coordinator at the two publisher call sites that routes by the consent flag: consented sends the service a scheduled-task update (one new action), not consented uses the publisher as fallback.

Suggested path: ship Phases 1–3, look at it on a device, and only do B if the two cards bother the owner.

## 9. Phase 5: allowance, single owner of the number (Recommended, separate effort)

Current design is primary plus fallback, not two competing counters. `AppBlockerAccessibilityService` times the live session and checkpoints every 15 s. `ForegroundTaskService` re-reads UsageEvents every 60 s and only raises the stored total. It skips an app while the accessibility heartbeat is under 2 minutes old, and otherwise writes a handoff timestamp. The two files carry contradictory comments about who owns the accounting.

Goal: one component owns reads and writes of `daily_allowance_used`, the lock and the handoff rules, instead of two services touching the prefs directly. **Do not move to UsageStats-only**: it lags and cannot block in real time.

- Extract by responsibility, verbatim first, then switch callers: `AllowanceLedger` (storage, lock, JSON schema), `AllowanceSessionTracker` (live timing and checkpoints), `UsageEventsReconciler` (the event queries and raise-only merge), `AllowanceHandoff` (heartbeat and TTL rules).
- Write characterization tests first for the handoff rules (fresh heartbeat skip, raise-only merge, interval-window mismatch skip, handoff timestamp).
- Do not change the numbers (15 s, 60 s, 2 min) in this phase. Fix the contradictory comments.
- No notification code here. If the card ever shows allowance, it reads the ledger only and never counts on its own.
- If extracting from the accessibility file proves too risky, stop after the ledger.

## 10. Parked (do not do now)

- Swapping `dataSync|specialUse` for `specialUse` only, handling `Service.onTimeout`, and the Android 15 `BOOT_COMPLETED` start restriction. These affect the app when `targetSdk` reaches 35. **Gate:** do this pass before raising `targetSdk` to 35. Nothing in this plan depends on it, and the manifest is not touched.
- Anything above API 35.
- OEM kill handling beyond the existing battery-optimization prompt, `BootReceiver` and the VPN watchdog.
- A shared "what is enforced right now" state object. About a dozen places re-derive standalone expiry. It is real technical debt, but the always-on design does not need it, so leave it for a later cleanup.
- Splitting the big files fully.
- Mode-specific text for standalone, always-on, allowance or keyword in the notification.

## 11. Decisions needed from the owner

1. May the background service be switched off while a focus session, standalone block or always-on block is active? Suggested: only with the PIN, or not at all while enforcing.
2. Do you want the health state (Phase 3)?
3. Scheduled-task card: leave separate (A) or fold in (B)?
4. Allowance refactor (Phase 5): now, later, or never?

## 12. Verification checklist

- Devices: API 29, one of 31/32, 33, 34, and 35 if available.
- Focus session: start, +15m, +30m, skip, done, break, break end, task-end alarm.
- Standalone block with dimmer, vibration and sound each on, then open FocusFlow.
- Notification permission denied on 13+: service still runs, no crash.
- Swipe the card away on 14+: service keeps running.
- Consent off then on, and a reboot with consent off.
- Widget and the VPN notification unchanged.

## 13. Review gates (definition of done)

- No new file over 300 lines; none of the four big files grew.
- No new SharedPreferences keys, polling loops, permissions or manifest changes.
- Pure mapping logic has unit tests.
- No enforcement behavior change in Phases 1–4.
- Each phase builds and runs on its own.
