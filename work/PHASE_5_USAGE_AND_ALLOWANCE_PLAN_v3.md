# Phase 5 (v3): one usage pipeline, one allowance ledger, one Stats source, working on Android 10 to latest

For the coding agent · FocusFlow Android · supported range **Android 10 (API 29) up to the latest (Android 17 / API 37 at time of writing)** · 6 Oct 2026

**This replaces v1 and v2.** Source implementation for Phases 0-4 and the health card is present, but Phase 3 build/test/device verification and Phase 4 runtime/device/API verification remain incomplete. Do not treat source implementation as full verification; see `BATCH_TRACKER.md`.

---

## 0. How to read this

- **Decided** = the owner chose it. **Revised** = I changed my own earlier recommendation after new evidence. **Open** = needs a quick owner answer; a default is given.
- **(code)** = verified by reading our source. **(docs)** = verified in official Android documentation. **(secondary)** = from non-official sources. **(verify)** = not verified; confirm in Phase 5.0.
- No code is given on purpose. Class names are suggestions.
- **Rooted phones and root-requiring approaches are out of scope.** Nothing here needs root or Shizuku. (Device-Watch has optional root/Shizuku code; ignore it.)
- **Licenses.** Nudge and Device-Watch are GPL. Read them to learn the rules; **do not copy their code**. Write our own implementation and tests from the rules in section 4.
- **No rewrite.** Reuse `UsageStatsRepository` as the OS adapter, keep the Room tables, the allowance UI and our exact-expiry timer.

## 1. What changed in v3

- **New section 3: the compatibility contract** (API 29 to 37), with the per-version rules that matter for this work, which versions to test, and a CI gate.
- **Three new platform facts (docs):**
  - `queryEvents` can return **null** when the device has not been unlocked since boot (Android 11+). Our code does not handle that. The pipeline adapter must treat null as "no data yet", never as zero usage.
  - The system keeps usage events **only for a few days**. That fixes the earlier inconsistency in D4.
  - A foreground service with several types must satisfy the rules of **all** of them. Our `dataSync|specialUse` inherits `dataSync` limits. This stays parked, but now has a real-world reference (section 9).
- **Already handled in our code (no work needed):** the Android 14 full-screen-intent permission check and settings link, exact-alarm capability checks, and Android 13 "restricted settings" recovery screens for accessibility.
- **Corrected assumption:** v2 said keyboard and notification-shade windows "are not activity events". That is reasoning, not a verified fact. It is now a Phase 5.0 test.

## 2. Decisions

| # | Decision | Status |
|---|---|---|
| D1 | Allowance counts usage **all day**, not only during blocks. | Decided |
| D2 | **One pure foreground-span pipeline over `UsageEvents`** measures time for allowance, Stats and findings. Accessibility detects/enforces and times the exact expiry; it does not produce the number. | Revised in v2 |
| D3 | Stats: the events pipeline for recent days, **Room rollups** (written by the same pipeline) for history. `queryUsageStats(INTERVAL_*)` buckets are removed from Stats. | Revised in v2 |
| D4 | The OS keeps events only for a few days (docs), so history lives in Room rollups written while the events still exist. The exact retention is measured in 5.0. | Adopted |

Open items (defaults apply if not answered):

- **O1. Interval-mode window start.** Default: starts at the first open of the app regardless of block state.
- **O2. What counts as an "open".** Default: every new foreground span of the package after another real app or the home screen. A short bridge only for a small allow-list of system dialog packages (permission controller, the system chooser): A → dialog → A within a few seconds is one open. No generic debounce (it creates a way around the limit).
- **O3. Replace the 60 s polling sync with a tick that runs only while an allowance app is in the foreground** (default 30 s, like Nudge), plus recompute on app open, service start and day rollover.

## 3. Compatibility contract (Android 10 to latest)

### 3.1 Rules

Current project configuration, verified in `app/build.gradle.kts`: `minSdk = 29`, `compileSdk = 35`, `targetSdk = 35`. API 36/37 checks below are runtime smoke tests; this phase does not implicitly change `compileSdk`. Android 15/target-35 requirements are already applicable and must not be described as a future pre-upgrade gate. Any work on those requirements stays separately scoped.

1. **minSdk 29.** All event types the pipeline needs exist natively: `ACTIVITY_RESUMED/PAUSED/STOPPED` and `DEVICE_STARTUP/SHUTDOWN` from API 29; `SCREEN_INTERACTIVE/NON_INTERACTIVE` and `KEYGUARD_SHOWN/HIDDEN` from API 28 (docs). `MOVE_TO_FOREGROUND/BACKGROUND` are deprecated since API 29 (docs). **Delete the `MOVE_TO_*` branches and the SDK-version checks around them.**
2. **The pipeline is pure and has no SDK checks.** It receives normalized events. All API-level handling lives in the thin adapter on top of `UsageStatsRepository`.
3. **Adapter must handle:**
   - `queryEvents` returning **null** before the first unlock after boot (Android 11+, docs). Keep the last known ledger value; retry after unlock.
   - `SecurityException` when usage access is revoked. Same: keep the last value, surface the health state.
   - Events being kept only for a few days (docs). Never assume older data exists.
4. **Do not use `UsageEventsQuery`** (API 35+, docs) unless wrapped in a version check. It is an optional optimisation (filtering event types), not needed.
5. **Usage-access check:** use `unsafeCheckOpNoThrow` (API 29+). Our code uses the deprecated `checkOpNoThrow`.
6. **Day boundaries** use calendar arithmetic (DST days are 23 or 25 hours), never `index * 86,400,000`.
7. **Binder calls off the main thread.** Enforcement reads in-memory state.
8. **CI gate:** run Android Lint `NewApi` in CI and treat its findings as failures. Nudge documents a crash on older versions that no JVM test and no single device could catch; lint caught it. Also run the pipeline unit tests on the JVM.

### 3.2 Per-version table (what matters for this work)

| Android | Rule | Our status | Action |
|---|---|---|---|
| 10 (29) | Events above exist; `foregroundServiceType` attribute introduced (docs); no notification permission needed | Uses `MOVE_TO_*` branches and the deprecated check | Rule 1 and 5 |
| 11 (30) | `queryEvents` returns null while locked (docs); package visibility | `QUERY_ALL_PACKAGES` declared (code); null not handled (code) | Rule 3. Store policy for `QUERY_ALL_PACKAGES` **(verify if publishing on Google Play)** |
| 12 (31) | FGS cannot start from the background (target 31+); **FGS notification delayed about 10 s unless it has action buttons or `FOREGROUND_SERVICE_IMMEDIATE`**; **notification trampolines banned** (docs) | `ensureRunning` is called only from the visible activity, in try/catch (code). **Idle card has no actions** (code). **`NotificationActionReceiver` calls `startActivity()`** (code) | Set the immediate behavior on the idle card. Trampoline fix: see section 8 (needs `targetSdk`) |
| 13 (33) | `POST_NOTIFICATIONS` runtime permission; **users can dismiss the FGS notification by default** (docs); restricted settings block accessibility etc. for sideloaded installs (secondary) | Permission declared (code); restricted-settings recovery screens exist (code) | Re-post the current card from `ensureRunning()` so a dismissed card returns |
| 14 (34) | FGS type + matching permission required when targeting 34 (docs); full-screen intent default only for calling/alarm apps (docs) | Types and permissions declared, `specialUse` subtype set (code); FSI check and settings link exist (code) | None. Re-test on 14 |
| 15 (35) | `dataSync` services time out after 6 h per 24 h; `BOOT_COMPLETED` cannot start `dataSync` (target 35+) | Current `targetSdk = 35`; service/boot path noted in source | Current target-35 compliance needs separate review; it is not a future pre-upgrade gate and is not Phase 5 scope. |
| 16 (36) | Job runtime quota applies to jobs running with a foreground service (docs) | WorkManager workers exist (code) | Low risk; watch for skipped jobs |
| 17 (37) | Stricter memory limits for notification custom views (target 37+); optional accessibility text-change types (docs) | No custom notification views found; the widget uses RemoteViews (code) | Nothing now |

I found nothing in the Android 16 and 17 behavior-change pages I could retrieve that affects usage events, the foreground service or our accessibility service. That is a limit of what I read, not a guarantee.

### 3.3 What to test on which version

Test on emulator images plus at least one physical phone. Minimum set, chosen where behavior changes:

| API | Why this version |
|---|---|
| 29 | Baseline: events, no notification permission, minSdk behavior |
| 31 | Background FGS start, 10 s card delay, trampoline, PendingIntent immutability |
| 33 | Notification permission, dismissible card, restricted settings (sideloaded install) |
| 34 | FGS types, full-screen intent permission |
| 35 | `dataSync` timeout and boot restriction (the app currently targets 35) |
| 36 / 37 | Smoke test: service survives, card shows, allowance and Stats numbers sane |

On each: foreground service starts at app open and after reboot; card appears (and returns after dismissal on 33+); allowance blocks at the right moment; Stats totals are plausible; locked-boot case does not crash or reset usage.

## 4. The pipeline rules (from reading Nudge and Device-Watch, confirmed by the docs)

Implement as one pure, JVM-testable class (suggested name `ForegroundSpanTracker`) fed events in time order, emitting non-overlapping `[start, end)` spans per package:

1. **One foreground package at a time.** A `RESUMED` for a different package closes the open span at that instant. Totals can never exceed the day.
2. **Everything that ends the foreground ends the span:** `PAUSED`; `STOPPED` as a backstop; `SCREEN_NON_INTERACTIVE`; `KEYGUARD_SHOWN`; `DEVICE_SHUTDOWN` (docs: treat shutdown as all activities stopped, no explicit `STOPPED` events are generated).
3. **Match `STOPPED` to the activity.** Moving inside an app emits `A paused, B resumed, A stopped`; a package-wide stop would close B's new span. Nudge matches the exact activity class; Device-Watch keeps a set of open classes per package. Start with the simpler (match the class that opened the span); choose in 5.0.
4. **Cap the inferred tail.** An open span has only an observed start. Extended to "now" it is capped (Nudge: 4 h). Measured spans (both ends seen) are never capped.
5. **Filter spans, never the stream.** Feed every event (including our own package and launchers) and drop packages afterwards.
6. **One query pass over the window**, spans split at day boundaries (rule 6 of 3.1).
7. **One place interprets platform event types.** Weekly bars, hourly bars, session counts, launches and allowance all read the same pipeline. Nudge fixed disagreeing numbers (a day showing "17 hours before lunchtime") by collapsing four copies of the pairing loop into one class.
8. **Opens/launches** = a package's span starts after being closed. Look back before the window start so an app already open at the start is counted from the start only (we already clip this way).
9. **Launchers** are excluded from totals and rankings at span level (Digital Wellbeing does the same).
10. **Failure mode:** null or `SecurityException` means "unknown", not zero. Keep the last known value.

Nudge also shows the enforcement pattern to keep: re-read the budget on a clock while the app is open (it measured a 150 s overshoot on a 1-minute budget before adding a tick). We keep our exact-expiry timer for precision, seeded from the pipeline's reading.

## 5. What exists today (verified in our code)

- Four meters of "time on an app today": the accessibility allowance meter, the service's 60 s UsageEvents sync (raise-only, handoff protocol), `AppUsageAndSessionTracker` (a11y-fed, Room), and `UsageStatsRepository` (OS, Stats).
- **Event handling gaps:**
  - `ACTIVITY_STOPPED`, `SCREEN_NON_INTERACTIVE`, `KEYGUARD_SHOWN` and `DEVICE_SHUTDOWN` are used **nowhere**.
  - `getUsageSummary`: a `package -> start` map (the pattern Nudge calls the bug), no closing when another app resumes, open spans credited to now with no cap, own-package events dropped before pairing, no null check on `queryEvents`.
  - `ForegroundTaskService.queryUsageEventsForegroundMs`: better (another package's resume closes the span) but no `STOPPED`, no screen-off/keyguard/shutdown, no tail cap.
  - Hourly Stats uses `queryUsageStats(INTERVAL_BEST)` and puts each bucket's total into the hour the bucket began. Nudge's code states pre-aggregated daily buckets are stale and midnight-misaligned on Android 12+ and replaced them with events.
  - Copies of the pairing loop: `getUsageSummary`, the service's allowance measure, `reconcileCountAllowances`, and foreground-detection loops in the accessibility service.
- **Enforcement-only recording:** the accessibility allowance meter returns early unless focus, standalone or always-on is active; the service's UsageEvents sync counts from midnight regardless. They measure different things today.
- **Four copies of the read-side "remaining" math:** `isAllowanceAvailable`, the unlock calculation (both in the accessibility service), `isFallbackBlocked` in the service, `LauncherActivity.loadAllowanceCardData`. `SettingsRepository` also touches the JSON (backup/reset).
- Sizes: `AppBlockerAccessibilityService.kt` 4,815 lines, `ForegroundTaskService.kt` 1,649, `LauncherActivity.kt` 1,952, `UsageStatsRepository.kt` 550, `AppUsageAndSessionTracker.kt` 228.

## 6. Ground rules

1. **No god files.** One responsibility per new file, aim under about 250 lines, justify over 300. Pure logic separated from Android calls and JVM-tested.
2. **The big files must shrink.** Net line count of `AppBlockerAccessibilityService.kt` and `ForegroundTaskService.kt` goes down in every step that touches them. Report the numbers.
3. **Label every step** behavior-preserving or behavior-changing.
4. **No new prefs keys** unless justified; no new polling loops.
5. Blocking logic, VPN, keyword blocker and the foreground-service type stay untouched. Target-35 Android 15 behavior is already applicable because the current target is 35; track any required compliance work separately and do not expand Phase 5 to include it (section 9).
6. Every behavior-changing step updates the user-facing copy.

## 7. Steps (each its own PR; the app must build and behave at the end of each)

### 5.0 Verify and measure (read-only plus device testing)

- Add the Lint `NewApi` CI gate (3.1 rule 8) and run it on the current code first; report findings.
- Map every writer and reader of `daily_allowance_used`, `daily_app_usage`, `app_sessions` (include `BackgroundFetchWorker`, `SettingsRepository`, `LauncherActivity`, backup/export, and the detectors). The current in-app V1 backup/export contains settings and task records, not Room usage history; document rollups as local-only for that export rather than expanding backup scope.
- **Device matrix** (3.3 versions). For each case compare: the new pipeline output, the current accessibility meter, the current Stats card, and Digital Wellbeing:
  - Screen off while an app is open (do `PAUSED`/`STOPPED` arrive?).
  - Leaving an app via home, recents and a notification.
  - Multi-activity apps; split-screen and picture-in-picture (Android 10 and later may keep several visible activities resumed **(verify)**).
  - **Keyboard open while typing, and pulling the notification shade, in an allowance app: does either create a foreground change or an extra open?**
  - Permission dialog and share sheet over an allowance app (decides the O2 allow-list).
  - Reboot mid-session, including a locked-boot read (null handling); midnight rollover; a DST day if possible.
- **Event latency:** time from a real app switch to its event appearing in `queryEvents` (decides the tick interval).
- **Retention:** earliest event timestamp available on each test device (decides how often rollups must run).
- **`queryEvents` cost** at the chosen tick on a low-end device. If heavy, add incremental reads with periodic full recompute.
- Stats "Today" in the afternoon and the 3-month view: what does the current code show, how many days come back?
- Report measured differences; the owner sets the acceptable tolerance.

### 5.1 Characterization and pipeline tests (behavior-preserving)

Add a JVM test source set if missing. Pin current pure behavior you will move or delete (interval-window math, day rollover, remaining/exhausted rules). Write tests for every rule in section 4 using hand-made event sequences: missing `PAUSED`, `STOPPED` only, screen-off, keyguard, shutdown/startup, midnight crossing, DST day, duplicate and out-of-order `RESUMED`, app already open at window start, tail cap, null/unknown input.

### 5.2 `AllowanceLedger` (behavior-preserving)

Sole owner of the `daily_allowance_used` JSON schema, the lock, day/window rollover and the remaining/exhausted math. Replace the four read-side copies, `SettingsRepository`'s access and the launcher card. Read the **existing JSON as is** (no data migration). In-memory cache with write-behind. Fix the contradictory comments.

### 5.3 The pipeline in shadow mode (behavior-preserving)

Build `ForegroundSpanTracker` plus the thin event adapter over `UsageStatsRepository` (one loop interpreting event types; null/`SecurityException` handling; no SDK checks inside the pipeline). Run it alongside the old logic with a debug-only comparison log (no UI, no prefs). Compare against the old meters and the 5.0 results for a few real days. Remove the log at cutover.

### 5.4 Allowance cutover (behavior-changing: D1, D2)

- Time budget, count and interval readings come from the pipeline, **independent of block state**. Enforcement stays gated on focus/standalone/always-on; only the *number* stops being gated.
- While an allowance app is in the foreground: read the baseline from the pipeline, schedule the exact-expiry timer from it, re-read on the tick (O3). Accessibility still detects the foreground and triggers the checks.
- Delete: the accessibility accumulator/checkpoint code, `ACTIVE_SESSION_*` and handoff bookkeeping, `reconcileCountAllowances`, and the service's 60 s allowance sync. Ledger "used" becomes a cache of the pipeline value. At cutover take the larger of the stored and pipeline values for today so nobody's usage resets mid-day.
- Apply O1 and O2.
- **Copy:** update `DailyAllowanceModal`, `DailyAllowanceDefenseDialog` and the launcher card to say it counts all usage today, not just during blocks.

### 5.5 Stats tab (behavior-changing: D3)

- `DeviceUsageSource` serves per-app minutes, hourly distribution and a coverage report from the pipeline, plugged into `AnalyticsProcessor.buildPhoneUsageMetrics` so the UI stays the same.
- **Hourly** from spans clipped to hour boundaries. Remove the `INTERVAL_BEST` path.
- **Rollups:** write `daily_app_usage` / `app_sessions` rows from the same pipeline on app open, service start and day rollover, idempotently, for every day whose events still exist. Align launcher/own-package/screen rules between the old accessibility-fed rows and the new rollups before switching. Keep the existing 90 days of history.
- Long ranges (3-month) read Room rollups; recent ranges use live events. Extend the existing `DataHealthNotice` to report coverage (days from rollups, days live, days missing). Check `PermissionGate` copy for the 3-month view.
- Verify the allowance-suggestion detector now sees the same numbers as enforcement. Delete `PhoneUsageSummary.kt` only if 5.0 confirms it is unused.

### 5.6 Cleanup

Remove dead code (`MOVE_TO_*` branches, retired `AppUsageAndSessionTracker` accumulation if fully replaced) only in files already in Phase 5 scope; leave unrelated VPN, keyword-blocking, notification and enforcement code untouched. Remove the shadow log and obsolete keys (with a note on upgrade behavior). Switch the usage-access check to `unsafeCheckOpNoThrow`. Re-run the 5.0 matrix on every version in 3.3. Report line counts for every touched and new file.

## 8. Small fixes found while rechecking (separate PRs, not part of Phase 5)

1. **Notification trampoline.** `NotificationActionReceiver` stores the action and then calls `startActivity()`. On Android 12+ apps targeting 31 or higher cannot start an activity from a receiver after a notification tap or action-button tap (docs), so Done / +15m / +30m / Skip may not replay until the user opens the app. The app currently targets 35, so treat this as a current separate compliance concern, not an unknown-target question or Phase 5 work. Any fix needs separate scope approval.
2. **Idle card delay.** Set the immediate foreground-service behavior on the idle card (it has no action buttons, so Android 12+ may show it about 10 s late).
3. **Dismissed card.** On Android 13+ users can dismiss the card by default (docs; I earlier said 14, that was wrong). Re-post the current card from `ensureRunning()`.
4. **Health card first draw.** `EnforcementHealth.UNKNOWN` counts as needs-attention, so the first card after each service start may flash "Needs attention". Treat unknown as neutral for the first draw.

## 9. Parked (not part of this phase)

- Foreground-service type change, `onTimeout`, Android 15 boot behavior, and Android 16/17 target changes are outside Phase 5. The app already targets 35, so any target-35 compliance concern is current and must not be described as a pre-upgrade gate. Track and authorize that work separately; do not change the service type or boot behavior in Phase 5. A service with several types must satisfy the rules of **all** of them (docs), so `dataSync|specialUse` inherits `dataSync` limits. Reference: ActivityWatch's Android app moved its background service from `dataSync` to `specialUse`, starting the service with the special-use type on Android 14+ and keeping an older fallback for earlier versions (pull request 190 in `ActivityWatch/aw-android`; I read the description, not the code; license not checked, so read only).
- In-app off switch for the background service.
- Shared "what is enforced right now" state object.
- Anything about the VPN notification.

## 10. What can go wrong

- **Split-screen / multi-resume:** "one foreground package" credits only the last resumed app. Document it; decide from the 5.0 results.
- **Event delivery delay:** keep the exact-expiry timer and the tick; do not rely on events alone for the instant a budget runs out.
- **Retention:** if neither the app nor the service runs for longer than the OS keeps events (a few days), history for those days is lost. The always-on service plus the rollup at day rollover is the mitigation.
- **Locked boot:** null from `queryEvents` must never reset usage to zero.
- **Time travel:** changing the device clock moves the day boundary. Check whether current behavior is intended. Out of scope.
- **Double counting in Room** during 5.5: run old and new rows together only in shadow mode.
- **Numbers users will see change:** Stats totals will move once inflated spans disappear. Say so in release notes.
- **Own-package and launcher filtering** at span level change totals slightly versus today; align and document the card's definition.

## 11. Review gates (definition of done)

- Every step builds and runs on its own and is labelled preserving or changing.
- The accessibility file and `ForegroundTaskService.kt` are smaller than before; no new file over 300 lines.
- One class interprets platform event types; one owner of the allowance JSON.
- The 60 s allowance polling loop and the handoff/heartbeat protocol are gone.
- Pipeline unit tests cover every rule in section 4; Lint `NewApi` is clean in CI.
- The 5.0 matrix is re-run on every version in 3.3, with measured deltas reported.
- Stats, allowance and the budget suggestion show the same numbers for the same app and day.
