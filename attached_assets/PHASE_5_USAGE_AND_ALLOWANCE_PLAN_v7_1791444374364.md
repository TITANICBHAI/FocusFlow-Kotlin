# Phase 5 (v7): one usage pipeline, one allowance ledger, one Stats source, Android 10 to latest

For the coding agent · FocusFlow Android · **minimum Android 10 (API 29), maximum = the latest Android (API 37 at time of writing)** · 8 Oct 2026

**This replaces v1 to v6.** Phases 0-4 and the health card are already implemented.

---

## 0. How to read this

- **Decided** = the owner chose it. **Revised** = I changed my own earlier recommendation. **Open** = needs a quick owner answer; a default is given so work is not blocked.
- **(code)** = verified by reading our source. **(docs)** = verified in official Android documentation. **(secondary)** = non-official source. **(verify)** = not verified; confirm in Phase Batch 0.
- No code is given on purpose. Class names are suggestions.
- **Rooted phones and root-requiring approaches are out of scope.** Nothing here needs root or Shizuku.
- **Licenses.** Nudge and Device-Watch are GPL. Learn the rules from them; **do not copy their code**. Write our own implementation and tests from section 5.
- **No rewrite.** Reuse `UsageStatsRepository` as the OS adapter, keep the existing Room tables, the allowance UI and our exact-expiry timer.

## 1. What changed in v7

v6 corrected eight code-reading discrepancies. This revision fixes one structural conflict introduced by the v2 tracker update: section 11 Batch 1 previously front-loaded all test suites, but the tracker redistributed tests to the batch that introduces their production code. The plan now matches the tracker. No production-code decisions were changed.

**Single change:** Batch 1 in section 11 is now characterization-only. Tests for the pipeline, read-model, rollup DAO, durability state machine, and detector seam are listed under the batch that introduces their respective production code (Batches 2–5).

The eight v6 corrections below are unchanged and still apply:

v5 introduced three structural fixes (source precedence, durable stale estimate, session identity). v6 corrected eight points found by re-reading the code against v5.

1. **D8 replicates existing tracker behavior, not a new rule (section 5).** `AppUsageAndSessionTracker.closeCurrentSession` already sets `localDate = epochMsToLocalDate(sessionStartWall)` and stores the full `durationMs` (code). The new pipeline must match this, not invent it. v5 presented D8 as a new decision.
2. **Three live-session keys must be kept in Batch 4, not two (7.1).** `ACTIVE_SESSION_END_MS` (`active_session_end_ms`) is read on accessibility reconnect to restore the interval expiry deadline (code, line 2407). Without it interval-mode restore silently fails. v5 said keep only `pkg` and `last_checkpoint_ms`.
3. **`ACTIVE_SESSION_OPEN_AT_MS` is read by `ForegroundTaskService` too (7.1, 11 Batch 4).** It feeds `sessionOpenAtMs` in the `AllowanceExpiry` for the interval fallback (code, line 454). v5 said "remove it" without flagging this cross-file dependency.
4. **`PREF_USAGE_STATS_SYNC` is in two files (section 11, Batch 4).** Both the accessibility service and `ForegroundTaskService` read and write `daily_allowance_usage_stats_sync` (code, lines 461 and 551 of the service). v5 only mentioned the accessibility side.
5. **`checkOpNoThrow` appears in three files, not two (section 11, Batch 7).** `UsageStatsRepository.kt:67`, `ForegroundTaskService.kt:379`, `AppBlockerAccessibilityService.kt:2476`. Step Batch 7 must fix all three.
6. **`BackgroundFetchWorker` runs `runAll()` (8.3, 11 Batch 5).** Detectors are called from `BackgroundFetchWorker.kt:138` (code), not from the service or tracker. The rollup write must complete before the worker calls `runAll()` in the same execution. The ordering is now explicit in Batch 5.
7. **O7 has a concrete fix (section 4, 8.3).** `dateRangeEnd()` in `FindingDetectionRunner` returns `LocalDate.now().format(ISO_DATE)` (code). The fix is one line: change it to `LocalDate.now().minusDays(1).format(ISO_DATE)`. This shifts the week assignment in `computeWeeklyAverages`, shortening week 4 by one day for EscalatingCapture and Substitution.
8. **`DayRatingRepository.getRatableDates` also reads today (8.1, 11 Batch 5).** It calls `dailyAppUsageDao.getForDateRange(cutoff, todayString)` where `todayString = LocalDate.now()` (code). After cutover it must use the merged read model for today.

Retained from v5: the fourth rollup table (`usage_pipeline_state`), the sharp `cutoverDate` seam, the durable stale-estimate model, the stabilization window, and the ground rules.

## 2. Supported range and the version-gate mismatch

### 2.1 Contract

- **Runs correctly on every Android release from 10 (API 29) up to the latest.** `minSdk = 29`. No `maxSdk` cap.
- **Running on the latest is not the same as targeting it.** `compileSdk` follows the newest stable SDK. `targetSdk` is raised stepwise, and each raise needs the behavior-change review in section 3.2. Raising it to 35 is gated on the parked foreground-service work (section 10) and on the notification-trampoline fix (section 9).
- **Rooted devices and any root requirement: out of scope.**

### 2.2 The mismatch (code)

| Item | Finding |
|---|---|
| Declared minimum | **Unknown.** No Gradle files or `uses-sdk` in the upload. |
| Version checks written for older minimums | **51 checks in 19 files** test thresholds at or below API 29 (LOLLIPOP, M, N, O, O_MR1, P, Q). With `minSdk = 29` every `>=` check is always true and every `<` check is dead code. |
| Biggest files affected | `AppBlockerAccessibilityService.kt` (10), `ForegroundTaskService.kt` (8), `UsageStatsRepository.kt` (5), `TaskAlarmActivity.kt` (4) |
| Other files | `NetworkBlockerVpnService`, `TaskAlarmReconcileWorker`, `BootReceiver`, `BlockOverlayActivity`, `LauncherActivity`, `AversiveActionsManager`, `VpnRecoveryNotifier`, `TemptationLogManager`, `PermissionSupport`, `ReminderChainScheduler`, `NotificationChannels`, `AlarmCapabilitySnapshot`, `ForegroundServiceController`, `LauncherController`, `VpnRepository` (1-2 each) |
| Checks that stay | Checks for R (30), S (31), TIRAMISU (33), 34 and VANILLA_ICE_CREAM (35) are live and must stay. |
| Reference projects | Nudge and Device-Watch target minSdk 26, so their code has pre-29 fallbacks we do **not** need. Do not port those. |

### 2.3 Resolution

- **Batch 0a:** confirm the Gradle `minSdk`. If it is below 29, raise it to 29. The owner has already decided Android 10 is the floor.
- **Batch 7 (behavior-preserving PR):** collapse every check at or below API 29 to its always-true branch, using the inventory above. Read each one: several are compound conditions (for example `SDK_INT >= N && ...` in `VpnRecoveryNotifier`, `SDK_INT < M || permission check` in `PermissionSupport`, `theme != GLASSY || SDK_INT < O_MR1` in `LauncherActivity`). Keep the non-version part of each condition. This removes lines from the big files, which the ground rules require.
- Remove the `MOVE_TO_FOREGROUND/BACKGROUND` branches (deprecated in API 29, docs) in `UsageStatsRepository`, `ForegroundTaskService` and `AppBlockerAccessibilityService`.
- **New code must contain no checks at or below API 29.** The pipeline is pure and has no SDK checks at all.

## 3. Compatibility contract (Android 10 to latest)

### 3.1 Rules

1. **Event types** the pipeline needs exist natively on API 29: `ACTIVITY_RESUMED/PAUSED/STOPPED` and `DEVICE_STARTUP/SHUTDOWN` (API 29); `SCREEN_INTERACTIVE/NON_INTERACTIVE` and `KEYGUARD_SHOWN/HIDDEN` (API 28) (docs).
2. **The pipeline is pure.** API-level handling lives only in the thin adapter over `UsageStatsRepository`.
3. **The adapter must handle:**
   - `queryEvents` returning **null** before the first unlock after boot (Android 11+, docs). Treat as "unknown", never as zero usage. Our current code does not handle this.
   - `SecurityException` when usage access is revoked.
   - The system keeping events **only for a few days** (docs). Never assume older data exists.
4. **Do not use `UsageEventsQuery`** (API 35+, docs) without a version check. It is optional.
5. **Usage-access check:** use `unsafeCheckOpNoThrow` (API 29+). Our code uses the deprecated `checkOpNoThrow`.
6. **Day boundaries:** calendar arithmetic (DST days are 23 or 25 hours).
7. **Binder calls off the main thread.** Enforcement reads in-memory state.
8. **CI gate:** Android Lint `NewApi` must pass in CI. Nudge documents a crash on older versions that no JVM test and no single device caught; lint did.

### 3.2 Per-version table

| Android | Rule | Our status | Action |
|---|---|---|---|
| 10 (29) | Events above exist; `foregroundServiceType` attribute introduced (docs); no notification permission | Pre-29 checks and `MOVE_TO_*` branches still present (code) | Section 2 |
| 11 (30) | `queryEvents` returns null while locked (docs); package visibility | `QUERY_ALL_PACKAGES` declared (code); null not handled (code) | Rule 3. Store policy for `QUERY_ALL_PACKAGES` **(verify if publishing on Google Play)** |
| 12 (31) | FGS cannot start from the background (target 31+); **FGS notification delayed about 10 s unless it has action buttons or `FOREGROUND_SERVICE_IMMEDIATE`**; **notification trampolines banned** (docs) | `ensureRunning` is called only from the visible activity, in try/catch (code). **Idle card has no actions** (code). **`NotificationActionReceiver` calls `startActivity()`** (code) | Section 9 |
| 13 (33) | `POST_NOTIFICATIONS` runtime permission; **users can dismiss the FGS notification by default** (docs); restricted settings for sideloaded installs (secondary) | Permission declared and restricted-settings recovery screens exist (code) | Re-post the card from `ensureRunning()` |
| 14 (34) | FGS type and matching permission required when targeting 34; full-screen intent default only for calling/alarm apps (docs) | Types, permissions and `specialUse` subtype declared; full-screen-intent check and settings link exist (code) | None. Re-test on 14 |
| 15 (35) | `dataSync` times out after 6 h per 24 h; `BOOT_COMPLETED` cannot start `dataSync` (target 35+) | `dataSync\|specialUse`, no `onTimeout`, `BootReceiver` starts it (code) | **Parked. Gate before `targetSdk` 35** (section 10) |
| 16 (36) | Job quota applies to jobs running with an FGS (docs) | WorkManager workers exist (code) | Low risk |
| 17 (37) | Stricter memory limits for notification custom views (target 37+); optional accessibility text-change types (docs) | No custom notification views found (code) | Nothing now |

I found nothing in the Android 16 and 17 behavior-change pages I could retrieve that affects usage events, the foreground service or our accessibility service. That is a limit of what I read.

### 3.3 What to test on which version

Emulator images plus at least one physical phone. Minimum set, chosen where behavior changes:

| API | Why |
|---|---|
| 29 | Baseline: events, no notification permission, minSdk behavior |
| 31 | Background FGS start, 10 s card delay, trampoline, PendingIntent immutability |
| 33 | Notification permission, dismissible card, restricted settings (sideloaded install) |
| 34 | FGS types, full-screen intent permission |
| 35 | `dataSync` timeout and boot restriction (once you target 35) |
| 36 / 37 | Smoke test: service survives, card shows, allowance and Stats numbers sane |

On each: service starts at app open and after reboot; card appears and returns after dismissal (33+); allowance blocks at the right moment; Stats totals are plausible; a locked-boot read does not crash or reset usage.

## 4. Decisions

| # | Decision | Status |
|---|---|---|
| D1 | Allowance counts usage **all day**, not only during blocks. | Decided |
| D2 | **One pure foreground-span pipeline over `UsageEvents`** measures time for allowance, Stats and findings. Accessibility detects/enforces and times the exact expiry; it does not produce the number. | Revised in v2 |
| D3 | Stats reads the pipeline for recent days and **Room rollups** for history. `queryUsageStats(INTERVAL_*)` buckets are removed. | Revised in v2 |
| D4 | The OS keeps events only a few days (docs). History lives in Room rollups written while events still exist. | Adopted |
| D5 | Rollups go into **new additive tables**. Existing `daily_app_usage` and `app_sessions` are never rewritten. | Adopted |
| D6 | **One source per date, decided by a stored `cutoverDate`.** Before it: legacy. From it: pipeline. Sources are never summed. | **Revised in v5** (v4 chose per date by rollup status) |
| D7 | **Today is never stored.** It is always the live pipeline. | **New in v5** |
| D8 | **A session is never split.** One row per `(package, started_at)`, attributed to its start date. Daily time and hourly buckets are clipped across days. | **New in v5** |
| D9 | The allowance ledger keeps a **confirmed value, an accumulated estimate and a live-session marker**. | **New in v5** |

Open items (defaults apply if not answered):

- **O1. Interval-mode window start.** Default: starts at the first open of the app regardless of block state.
- **O2. What counts as an "open".** Default: every new foreground session of the package after another real app or the home screen. A short bridge only for a small allow-list of system dialog packages (permission controller, the system chooser): A → dialog → A within a few seconds is one open. No generic debounce. A session that continues past midnight is **not** a new open on the new day.
- **O3. Replace the 60 s polling sync** with a tick that runs only while an allowance app is in the foreground (default 30 s, like Nudge) plus recompute on app open, service start and day rollover.
- **O4. Allowance when usage data is unavailable for a long time** (7.5). Default: **keep estimating, do not lock the user out**.
- **O5. Launch count shown on Stats and used by streak detection.** Default: session starts (same rule as O2). Today's Stats card uses a 30 s debounce, so displayed launch counts will change. Say so in release notes.
- **O6. Detector continuity across the seam** (8.4). Default: **strict**, one source per evaluated window. Relax later only if the shadow comparison shows the sources agree within a tolerance the owner sets.
- **O7. Detectors read completed days only (today excluded).** Default: yes. `dateRangeEnd()` in `FindingDetectionRunner` currently returns `LocalDate.now()` (code); the fix is one line: change it to `LocalDate.now().minusDays(1)`. This shifts week assignment in `computeWeeklyAverages`: week 4 shortens by one day for EscalatingCapture and Substitution. Thresholds stay the same.
- **O8. Legacy writer stabilization window after cutover.** Default: keep the old writer running for 14 days, with its rows never served, so rollback loses nothing.

## 5. The pipeline rules (from Nudge and Device-Watch, confirmed by the docs)

Implement as one pure, JVM-testable class (suggested name `ForegroundSpanTracker`), fed events in time order and emitting **sessions**: non-overlapping `[start, end)` intervals, one app at a time.

1. **One foreground package at a time.** A `RESUMED` for a different package closes the open session at that instant.
2. **Everything that ends the foreground ends the session:** `PAUSED`; `STOPPED` as a backstop; `SCREEN_NON_INTERACTIVE`; `KEYGUARD_SHOWN`; `DEVICE_SHUTDOWN` (docs: treat shutdown as all activities stopped; no explicit `STOPPED` events are generated).
3. **Match `STOPPED` to the activity.** Moving inside an app emits `A paused, B resumed, A stopped`; a package-wide stop would close B's new session. Nudge matches the exact activity class; Device-Watch keeps a set of open classes per package. Start with the simpler; choose in Batch 0.
4. **Cap the inferred tail.** An open session has only an observed start. Extended to "now" it is capped (Nudge: 4 h). Measured sessions are never capped.
5. **Filter sessions, never the stream.** Feed every event (including our own package and launchers); drop packages afterwards.
6. **Sessions are never split, time is clipped.** The existing `AppUsageAndSessionTracker` already does this (code): `closeCurrentSession` sets `localDate = epochMsToLocalDate(sessionStartWall)` and stores the full `durationMs`; `splitIntoHourlySegments` clips time across hour and day boundaries. The new pipeline must replicate the same rules.
   - A session keeps one identity `(package, start time)` attributed to the **local date of its start**.
   - Daily time and hourly buckets are derived by **clipping** sessions to local days and hours. Clipping never creates a new session.
   - One query pass over the window, with a lookback before the window start so a session already open at the start is clipped correctly (the 6 h lookback exceeds the 4 h tail cap).
   - Example: 23:50 to 00:20 is one 30-minute session on day D. Daily time is 10 minutes on D (hour 23) and 20 minutes on D+1 (hour 0). Session count for D is 1 and for D+1 is 0.
7. **One place interprets platform event types.** Weekly bars, hourly bars, launches, sessions and allowance all read the same pipeline. (Nudge fixed disagreeing numbers, a day showing "17 hours before lunchtime", by collapsing four copies of the pairing loop.)
8. **Opens/launches** = a session starts after the package was closed (O2). A session continuing past midnight is not a new open.
9. **Launchers and our own package** are excluded from totals and rankings at session level.
10. **Null or `SecurityException` means "unknown", not zero.** Keep the last known value.

## 6. Room rollups: no duplicates, history preserved, one source per date

### 6.1 What exists (code)

- `daily_app_usage`: primary key `(date, package_name)`. The live writer does read-modify-write accumulation (`addForegroundTime`, `incrementLaunchCount`); the generic `upsert` is **REPLACE**.
- `app_sessions`: auto-generated id, **no unique key**; inserts use IGNORE, which never fires because the id is generated. Inserting derived sessions would **duplicate** existing ones.
- Detector reads: session stats grouped by `local_date`, first session per `local_date` (`MIN(started_at)`), raw sessions filtered by `local_date`. A session therefore has exactly one date today.
- Retention: both tables are pruned with `deleteOlderThan` (90 days).
- Database version is **6**, `exportSchema = true`, with explicit migrations 0→1 through 5→6 and no destructive fallback found in the code. The database descends from an older hybrid app that left `user_version = 0`, handled by `prepareLegacyDatabase`.
- **(verify)** how the old tracker dates a session that crosses midnight. Document any difference; the seam rules (8.4) already keep the two definitions apart.

### 6.2 New tables (additive migration 6 → 7)

| Table | Key | Meaning |
|---|---|---|
| `usage_pipeline_state` | single row | `cutover_date` (null until cutover), `pipeline_version`, `shadow_started_on` |
| `usage_rollup_day` | `(date)` | Day status `PARTIAL` or `COMPLETE`, coverage start and end (ms), `pipeline_version`, `computed_at`, total clipped foreground ms (sanity check) |
| `usage_rollup_app_day` | `(date, package_name)` | Same shape as the legacy row so detectors can reuse it: app name, category, **clipped** foreground ms, **clipped** hourly ms (same 24-value format), launch count and session count (**sessions that started that day**), first start, last used |
| `usage_rollup_session` | `(package_name, started_at)` | **One row per session, never split**: end, full duration, `local_date` = date of `started_at` |

Consequences of D8:

- A midnight-crossing session has **one** row, with its full duration, on its start date. Session-count, average, maximum, variance and first-session-of-day read it once.
- Its time appears in two daily rows (clipped). Detectors that use daily totals (escalating capture, substitution, allowance suggestion) read daily time. Session-shape detectors (variable reward, infinite session, morning hijack) read session rows. No detector reads both for the same quantity, so nothing is counted twice.
- The existing tables are **not altered**. The migration only creates tables and indexes; `usage_pipeline_state` is seeded empty.
- Add the migration test with Room's migration test helper: build a version-6 database with rows in `daily_app_usage` and `app_sessions` (and the legacy `user_version = 0` path), migrate, and assert every old row is unchanged. Commit the exported schema JSON for version 7.
- **(verify)** whether backup/export includes the Room usage tables. If it does, include the new tables too. If it does not, document that.

### 6.3 Day lifecycle and idempotent writes

- **Today is never stored.** No row for the current local date exists in any rollup table. Today is computed live (6.4).
- A past date D is written by one writer, one mutex, **one transaction per date**: delete D's rows in the three rollup tables, insert the recomputed rows. Running it twice with the same events gives the same rows.
- D's rows contain: clipped time and hourly buckets for every session overlapping D (including one that started on D-1), and session rows only for sessions that **started** on D. A session that started on D and continues past midnight appears under D when it closes; D's time slice up to midnight is exact either way.
- **COMPLETE** requires: D is over, the pipeline has no session that started on D still open (an open session older than the 4 h tail cap counts as closed), and the earliest available event is at or before the start of D (the Batch 0 retention measurement defines the probe).
- **PARTIAL** only in two cases: an unresolved carried-over session (resolved on the next run), or events covering only part of the day (retention edge). PARTIAL is never promoted by guesswork.
- A COMPLETE day is **never downgraded**. If its events have aged out, skip it. A `pipeline_version` bump recomputes a day only if its events still exist.
- Triggers: app open, service start, day rollover, and Stats open. Work per run is bounded by the few days the OS keeps.
- App name and category use the same helpers as the current tracker (`resolveAppName`, `resolveCategory`).

### 6.4 Which source wins (read-source precedence)

`cutoverDate` is stored once, at cutover, in `usage_pipeline_state`. It is the first local date served by the pipeline. The seam is exactly this date.

**Group A: Stats device-time card.** Its data is OS-derived today and **never reads the legacy tables**.

| Date | Source | Notes |
|---|---|---|
| Today | **Live pipeline** from local midnight, with lookback | Never read from rollup tables. Recomputed on each load. If events are unavailable, show "unknown", not zero |
| Past, rollup `COMPLETE` | Rollup | |
| Past, no `COMPLETE` rollup, events still available | On-demand pipeline (same rules), then written to rollups | Flag as partial if a carried-over session is unresolved or events cover part of the day |
| Past, nothing | Missing | Counted in the coverage notice |

Group A applies from Batch 5. Before that the card keeps its current OS-based code.

**Group B: history consumers** (detectors, rating-chip eligible dates, data-health day counts).

| Mode | Date | Source |
|---|---|---|
| Shadow (`cutoverDate` null) | any | **Legacy only** |
| Cutover | `date < cutoverDate` | **Legacy only.** Rollups that exist for these dates (from the shadow period) are never served |
| Cutover | `date >= cutoverDate`, past, `COMPLETE` | Rollup |
| Cutover | `date >= cutoverDate`, past, not `COMPLETE` | On-demand pipeline if events are available, else missing. Detectors treat PARTIAL as missing |
| Cutover | today | **Detectors: excluded (O7).** Rating-chip eligibility and data-health counts: counted if the live pipeline has any session today |

Rules that always hold:

- No date is ever served from two sources. Legacy is never served for `date >= cutoverDate`. Rollups are never served for `date < cutoverDate`.
- A window **crosses the seam** when `start < cutoverDate <= end`. Detectors use this predicate (8.4).
- PARTIAL data is shown only to Group A, with a flag.

### 6.5 Tests for source precedence

Pure read-model tests (no Android):

1. Shadow mode: Group B serves legacy for every date, including today, and never touches rollup tables; Group A keeps its current code.
2. Cutover: today in Group A is live; **no rollup row for today exists** after repeated loads (assert the table is empty for today).
3. Cutover: detector reads exclude today; rating-chip eligibility includes today when the live pipeline has a session.
4. `date < cutoverDate` with both a COMPLETE shadow rollup and legacy rows: **legacy wins** in both groups.
5. `date >= cutoverDate` with legacy rows present and no rollup: the pipeline wins; legacy is ignored.
6. Yesterday not yet rolled up: on-demand result equals the rollup written later.
7. **Invariance:** live(D) at 23:59 equals rollup(D) written after midnight for the same event log, apart from the carried-over-session case, which has its own test.
8. PARTIAL day: Group A shows it with the flag; Group B treats it as missing.
9. Events unavailable (null or `SecurityException`) for today: Group A shows unknown, never zero.
10. Rollback switch: `cutoverDate` set back to null returns legacy for all dates.
11. Property test: for random combinations of modes, dates and row presence, no date is served from two sources.
12. The seam predicate `start < cutoverDate <= end`, including boundary dates.
13. Midnight: a session 23:50 to 00:20 yields **one** session row on D (30 min), daily time 10 min on D and 20 min on D+1, session count 1 on D and 0 on D+1, and one session seen by the detector reads.
14. Day D rolled up while a session that started on D is still open: D stays PARTIAL, then becomes COMPLETE after the session closes, with no duplicate session row.

### 6.6 Legacy writer lifecycle and rollback

- **Shadow period (Batch 3):** the old accessibility-fed writer keeps writing the legacy tables unchanged; the new writer writes only the new tables, for completed days only. No cross-writes.
- **Cutover (Batch 5):** set `cutoverDate` to the cutover date. The pipeline computes the whole of that date from events, so the partly written legacy row for that date is ignored.
- **Stabilization (O8):** keep the old writer running for 14 days after cutover. Its rows for `date >= cutoverDate` are never served. After that window, stop it.
- **Rollback:** within the stabilization window, setting `cutoverDate` to null restores legacy for every date with no gap. After the window, rollback leaves a gap for the days since cutover (legacy has no rows for them). Say so before shipping.

## 7. Allowance when usage data is stale or unavailable

### 7.1 Durable data model

The existing allowance JSON (`daily_allowance_used`, one object per package) stays the single store, owned by `AllowanceLedger`. New fields are additive; old readers ignore them. **No new prefs keys.**

| Field | Applies to | Meaning |
|---|---|---|
| `date` (existing) | all | Local day the figures belong to |
| `usedMs` (existing) | time budget, interval | **Effective used time** = `confirmedUsedMs + estimatedExtraMs`. Keeps its current meaning so existing readers and UI still work |
| `count` (existing) | count | **Effective opens** = `confirmedCount + estimatedExtraOpens` |
| `windowStartMs` (existing) | interval | Window start. Interval modes use the same time fields, measured inside the window |
| `confirmedUsedMs` (new) | time budget, interval | Last value read successfully from the pipeline. **Never decreases** within the same date or window |
| `confirmedCount` (new) | count | Last opens value read from the pipeline. Never decreases within the date |
| `confirmedAtMs` (new) | all | Time of that last successful read |
| `estimatedExtraMs` (new) | time budget, interval | Live-measured time **after** `confirmedAtMs`. Reset to 0 at every successful read |
| `estimatedExtraOpens` (new) | count | New sessions seen **after** `confirmedAtMs`. Reset at every successful read |

Live-session marker: keep **three** of the four existing `ACTIVE_SESSION_*` keys instead of deleting them in Batch 4:
- **`active_session_pkg`** — which package is open.
- **`active_session_last_checkpoint_ms`** — last checkpoint time for the accumulator.
- **`active_session_end_ms`** — interval-mode session expiry deadline, read by the accessibility restore path on reconnect (code, line 2407). Dropping it silently disables interval recovery.

The fourth key **`active_session_open_at_ms`** is read by `ForegroundTaskService` at line 454 (code) where it feeds `sessionOpenAtMs` in `AllowanceExpiry` for the interval fallback. In Batch 4, when the fallback path is replaced by the pipeline, verify nothing else reads it in either file before removing.

The handoff key **`daily_allowance_usage_stats_sync`** is read and written by both `AppBlockerAccessibilityService` and `ForegroundTaskService` (code, lines 461 and 551 of the service). Remove all references in both files in Batch 4 when the polling sync is deleted.

Invariants (tests assert them): `confirmed*` is monotonic per date or window; `estimated*` is never negative; effective values always equal confirmed plus estimated; a JSON object without the new fields reads as `confirmed = usedMs` (or `count`), `extra = 0`.

### 7.2 States

| State | Meaning | Default thresholds |
|---|---|---|
| **FRESH** | Last successful read is recent | Within 2 ticks (about 60 s) while an allowance app is in the foreground |
| **STALE** | Reads are late or failing transiently (exception, timeout, null before the first unlock, slow query) | Older than the FRESH window, usage permission still granted |
| **UNAVAILABLE** | Usage access revoked, or no successful read since boot for longer than a grace period after unlock | Permission denied, or no read for 5 minutes after unlock (tune in Batch 0) |

### 7.3 Accumulation rules (what survives, and when it is written)

- **Accumulate always, use only when not FRESH.** A live segment is tracked in memory whenever an allowance app is in the foreground. Completed segments are added to `estimatedExtraMs` regardless of state, so there is no gap when FRESH turns into STALE. When FRESH, the effective value is simply the pipeline value (the pipeline already includes the open tail up to the read time).
- **Segment boundaries:** switch-away, screen off, keyguard, accessibility disconnect, shutdown, and **midnight** (split at the day boundary: the part before midnight finalizes on the old day, the remainder starts a new segment on the new day).
- **Start of a segment** is `max(session start, confirmedAtMs)`. Anything before `confirmedAtMs` is already in the pipeline value.
- **Count mode:** each new session start after `confirmedAtMs` increments `estimatedExtraOpens` (O2 rule). A session continuing past midnight is not a new open.
- **Persistence (write-behind, in-memory cache authoritative):** write at every segment end, every 15 s while a segment is open and the state is not FRESH, on every state change, and in the accessibility service's and foreground service's teardown. The worst-case loss is one checkpoint interval.
- **Restart survival:** on service or accessibility start, if the live marker names a package, add `min(now - lastCheckpoint, 2 × checkpoint interval)` to that package's `estimatedExtraMs`, then clear the marker. A longer gap means the process was dead; the extra time is not guessed. The pipeline fills it when readable. Log the gap.
- **Cap:** one open segment is capped at the same 4 h as the pipeline's tail cap.
- **Day rollover:** a new `date` resets `confirmed*` and `estimated*` to zero. The first successful read of the new day sets the baseline.

### 7.4 Reconcile when data returns

On a successful read at time T with value V (and N for count mode):

1. `confirmedUsedMs = max(confirmedUsedMs, V)`, `confirmedAtMs = T`, `estimatedExtraMs = 0`. For count mode: `confirmedCount = max(confirmedCount, N)`, `estimatedExtraOpens = 0`.
2. If the app is still open, the live segment restarts at T.
3. Effective values are recomputed (`usedMs` now equals the confirmed value).
4. Log the difference between the old effective value and the new one. If the estimate was **higher** than V, the app may unblock; if it was **lower**, the app may block sooner. Both are acceptable: estimates are only a bridge, and the monotonic rule means the number never goes below what was already confirmed.
5. Re-seed the exact-expiry timer from the new effective value.
6. First read on the cutover day: `confirmedUsedMs = max(existing usedMs, V)` so nobody's usage resets mid-day.

Segments that ended before T are not added again (the pipeline covers them if its latency allows; any shortfall is corrected on the next read).

### 7.5 Enforcement behavior

| Situation | Behavior |
|---|---|
| FRESH | Normal enforcement from the pipeline value |
| STALE | Keep enforcing using the effective value (confirmed plus estimate). Retry reads with backoff. Do not fail open and do not lock out |
| UNAVAILABLE | **Default (O4): same as STALE, keep estimating.** The health card already shows "Needs attention" when usage access is missing |
| UNAVAILABLE, alternatives | After a grace period treat the allowance as exhausted until data returns (fail closed), or as unlimited (fail open). Not recommended: fail closed punishes the user for an OS hiccup, fail open lets revoking the permission bypass the limit |
| Accessibility also down | No live signal and no pipeline: enforcement is already limited; the health card is the only signal |
| Boot, before first unlock | Treated as UNAVAILABLE-transient; allowance apps cannot be opened before unlock anyway. Keep the stored values and read once after unlock |
| Device clock or day change during staleness | Day key from the clock; baseline at the next successful read |

### 7.6 What the user sees and what we log

- Launcher allowance card: when not FRESH, show the value with an "about" marker (copy to confirm).
- Stats: the coverage notice (8.2) covers missing days; no per-minute staleness UI.
- Debug logs: state transitions, restart gaps, and the estimate-versus-confirmed difference at each reconcile. Package names only, as already used.

### 7.7 Tests (pure, no Android)

1. **Accumulation across sessions:** several app sessions during STALE add up in `estimatedExtraMs`; completed segments survive until the next reconcile.
2. **FRESH to STALE flip:** a segment that ended while FRESH, after the last confirm, is counted (no gap).
3. **Restart mid-segment:** the marker recovers at most `2 × checkpoint interval`; a longer gap adds only the capped amount and logs it.
4. **Restart with no marker:** nothing is invented.
5. **Reconcile higher, lower and equal:** `confirmed*` never decreases; `estimated*` resets to 0; segments ended before T are not re-added; segments after T are kept.
6. **Midnight:** a segment is split at the day boundary; the new day starts from zero.
7. **Count mode:** increments on new sessions only; a session spanning midnight is not a new open; reconcile resets the estimate.
8. **Interval:** expiry by wall clock; time measured inside the window.
9. **Cap:** an open segment never exceeds 4 h.
10. **Backward compatibility:** old JSON (no new fields) reads correctly; `usedMs` stays the effective value for old readers.
11. **Failure inputs:** null, `SecurityException`, timeout and slow reads move the state as in 7.2 and never reset usage to zero.

## 8. Stats surfaces and detectors

### 8.1 Surface inventory (code)

| Surface | Today | Change |
|---|---|---|
| **Primary Stats tab = `ArchivedStatsScreen`** (windows Today / Yesterday / Week / All Time) | **Observed Device Time** card (not shown for All Time): total minutes, "Focus / observed" ratio, top 5 apps with "minutes · launches", and for Week a per-day chart | Details below |
| Same card, data flow | Needs `getUsageSummary` (events) **and** `getHourlyUsageSummary` (`INTERVAL_BEST`): `buildPhoneUsageMetrics` returns nothing without the hourly part, so **the whole card disappears if the hourly read fails**. Week additionally runs **7 per-day `getUsageSummary` calls**, 9 event scans per Week load | One pass from `DeviceUsageSource` (Group A, 6.4); sessions clipped by day and hour inside the pipeline; the card no longer depends on a second API |
| Same card, caption | "Android app foreground time, FocusFlow home-screen time excluded" | Keep the definition; the pipeline excludes launchers and our package at session level. Document that launchers are also excluded |
| Same card, third metric | **Hard-coded `0` "Blocked attempts"** | Bug found while reading. Fix in a separate PR, not part of this phase |
| `UsageAccessCard` on the primary screen | Shows permission state | Keep; extend with the coverage notice (8.2) |
| **Extra view** (`StatsInsightsExperience`, opened from the primary screen) | Shares the same view model and snapshot: insight cards, day-rating bar, `FindingsSection`, `DataHealthNotice` | Reads through the source rules in 6.4 |
| **3-month window** | `CurrentStatsScreen` has no caller and the Extra view hides the tab, so it is **currently unreachable** (as far as I can find). It still has a permission gate, `hasUsableStats`, and `ThreeMonthRules` (night pattern, phone peak) that read `phoneUsage.byHour` | Keep compiling and add it to the test matrix so it works when re-exposed. No new work |
| `PhoneUsageSummary.kt` | No caller found | Delete only if Batch 0 confirms |
| Day-rating chips | `DayRatingRepository.getRatableDates` calls `dailyAppUsageDao.getForDateRange(cutoff, todayString)` including today (code); a day is rateable if usage or task data exists | Group B rules in 6.4; the today row comes from the live pipeline after cutover |
| Data-health counts | `dataHealthDayCount = max(usageDays, taskDays)`, from `countDistinctDates()` | Group B rules in 6.4 |
| Insight rules | `WeeklyRules`, `YesterdayRules`, `StatsParityCards` use **blocking** peak hours; only `ThreeMonthRules` uses phone usage | No change except the 3-month note above |

### 8.2 Primary-screen behavior after the change

- **Today** is the live pipeline. It is never read from rollups or from legacy tables. If events are unavailable the card says so; it does not show zero.
- **Yesterday and Week days** follow Group A in 6.4: rollup if complete, otherwise computed on demand, otherwise missing.
- Week chart: per-day totals from one pipeline run, clipped by local day, including the dynamic and fixed week modes. A midnight-crossing session contributes its clipped slices to the two days.
- The card shows even when only some hours or days are available. It marks partial coverage using the existing `DataHealthNotice` style (days complete, days partial, days missing). No new notice type.
- Launch counts follow O5. Totals will change versus today wherever the old code over-counted open sessions.
- No usage permission: same as today (the card is hidden and `UsageAccessCard` explains it).

### 8.3 Detector inventory (code)

`FindingDetectionRunner.runAll` is called from `BackgroundFetchWorker.kt:138` (code), not from the service or tracker. The rollup write must complete before the worker calls `runAll()` in the same execution; ordering is explicit in Batch 5.

`FindingDetectionRunner.runAll` runs 11 detectors. **Four are unaffected** (they read tasks or focus sessions): post-failure cascade, session sweet spot, estimation drift, day-of-week outlier. **Seven read the usage tables**, all as Group B consumers, **completed days only** (O7):

| Detector | Reads | Window and thresholds | What the change touches |
|---|---|---|---|
| Morning hijack | first session each day (`MIN(started_at)` per start date), categories | 14 days; at least 7 days; social/entertainment app is first on at least 65% of days | Same exclusions (launchers, our package, system UI) and the same start definition. A midnight-crossing session belongs to its start day, so it is never "the first session" of the next morning |
| Variable reward loop | per-day session stats, raw sessions per candidate package | 21 days; at least 10 days; at least 5 sessions/day; mean at most 4 min; variation at least 0.9 | **Very sensitive to the session definition** (merging brief interruptions, minimum length, screen-off splitting). Unsplit sessions keep real lengths, so variance and maximum are not distorted |
| Infinite session design | same, plus categories (skips utility) | 14+ days; mean over 5 min; standard deviation over 20 min; max over 3× mean | The tail cap and screen-off closing will remove artificially long sessions, so **some findings will disappear (expected)** |
| Escalating capture | daily rows (`foreground_ms`, clipped) | 28 days; at least 4 days in each of 4 weeks; strictly rising; week 1 over 5 min; growth at least 40% | Needs 28 days of single-definition data |
| Substitution | same weekly averages | down 30% or more, up 30% or more, combined shift under 15% | Same |
| Streak lock-in | rows with `launch_count > 0`, categories social/entertainment/communication | 14 days; at least 90% of days; at least 3 low-rated days | **Launch definition** (O5) and "row exists" semantics: legacy rows can exist with launches but zero time |
| Allowance suggestion | daily rows (clipped), ratings | 30 days; social/entertainment; at least 5 high-rated and 3 low-rated days; suggestion = high-day average × 1.1 rounded to 5 min | The suggested limit now means **all-day use** (D1), matching enforcement. Must not average days from two sources |

Session identity (D8) for detectors: every session read comes from one row on its start date with its full duration. Session-count and raw-session queries filter by that start date, so a session crossing midnight is counted once and never as two short sessions.

### 8.4 Source seam rules for detectors (O6)

At cutover the rollups only hold the few recent days the OS still has, so a detector window that spans the seam would average **two definitions** of the same quantity and could invent a trend.

- A window **crosses the seam** when `start < cutoverDate <= end` (6.4). Detectors compute this from the read model, not from guesses.
- **Default (strict):** a detector evaluates only windows that do not cross the seam. A window entirely after the seam needs enough pipeline days for its minimum; otherwise the detector returns "not enough consistent data yet". Trend detectors (escalating capture, substitution, streak lock-in, allowance suggestion) can stay silent for up to about four weeks after cutover. A window entirely before the seam is legacy and unchanged.
- **Optional relaxation (O6):** use the shadow period (Batch 3) to compare both sources on the same completed days. If per-app daily totals agree within a tolerance the owner sets, allow mixing for detectors whose inputs are totals (not session shapes).
- **Fingerprints:** findings are deduplicated by an evidence fingerprint that includes sample size and a quantized metric. After cutover the same pattern may produce a new fingerprint and be shown again. Check `FindingRepository.submit` behavior in Batch 0 and keep fingerprint inputs unchanged.
- Pure tests per detector: a window before the seam passes; a window after the seam with enough days passes; a window crossing the seam is refused; today is excluded; a midnight-crossing session is counted once.
- **(verify)** when `runAll` is triggered, so detector runs and rollup runs do not race. Run detectors after the rollup pass.

## 9. Small fixes found while rechecking (separate PRs, not part of Phase 5)

1. **Notification trampoline.** `NotificationActionReceiver` stores the action and then calls `startActivity()`. On Android 12+ apps targeting 31 or higher cannot start an activity from a receiver after a notification tap or action-button tap (docs). Done / +15m / +30m / Skip would not replay until the user opens the app. **Needs the owner's `targetSdk`.** Fix: apply the stored action inside the receiver through the same gate and repository, or make the buttons open the activity directly.
2. **Idle card delay.** Set the immediate foreground-service behavior on the idle card (no action buttons, so Android 12+ may show it about 10 s late).
3. **Dismissed card.** On Android 13+ users can dismiss the card by default (docs; I earlier said 14, that was wrong). Re-post the current card from `ensureRunning()`.
4. **Health card first draw.** `EnforcementHealth.UNKNOWN` counts as needs-attention, so the first card after each service start may flash "Needs attention". Treat unknown as neutral for the first draw.
5. **Stats card:** the hard-coded `0` "Blocked attempts" metric (section 8.1).

## 10. Parked (not part of this phase)

- Foreground-service type change, `onTimeout`, the Android 15 boot restriction, targetSdk 35 and anything above API 35. **Gate: do this before `targetSdk` 35.** A service with several types must satisfy the rules of **all** of them (docs), so `dataSync|specialUse` inherits `dataSync` limits. Reference: ActivityWatch's Android app moved its background service from `dataSync` to `specialUse`, starting it with the special-use type on Android 14+ and keeping a fallback for earlier versions (pull request 190 in `ActivityWatch/aw-android`; I read the description, not the code; license not checked, so read only).
- In-app off switch for the background service.
- Shared "what is enforced right now" state object.
- Anything about the VPN notification.

## 11. Steps (each its own PR; the app must build and behave at the end of each)

### Batch 0 — Verify and measure (read-only plus device testing)

- **Batch 0a SDK floor:** confirm Gradle `minSdk`, `compileSdk` and `targetSdk`; report them. Raise `minSdk` to 29 if lower.
- Add the Lint `NewApi` CI gate (3.1 rule 8) and run it on the current code; report findings.
- Map every writer and reader of `daily_allowance_used`, `daily_app_usage`, `app_sessions` (include `BackgroundFetchWorker`, `SettingsRepository`, `LauncherActivity`, backup/export, the detectors, `DayRatingRepository`).
- Map when `FindingDetectionRunner.runAll` runs and how `FindingRepository.submit` deduplicates.
- Report how the old tracker dates a session that crosses midnight, and how the existing `ACTIVE_SESSION_*` keys are used.
- **Device matrix** (3.3 versions). For each case compare: new pipeline output, current accessibility meter, current Stats card, and Digital Wellbeing:
  - Screen off while an app is open (do `PAUSED`/`STOPPED` arrive?).
  - Leaving an app via home, recents and a notification.
  - Multi-activity apps; split-screen; picture-in-picture (Android 10 and later may keep several visible activities resumed **(verify)**).
  - **Keyboard open while typing, and pulling the notification shade, in an allowance app: does either create a foreground change or an extra open?**
  - Permission dialog and share sheet over an allowance app (decides the O2 allow-list).
  - Reboot mid-session, including a locked-boot read (null handling); **a session that crosses midnight**; a DST day if possible.
- **Event latency**, **retention** (earliest event on each device), and **`queryEvents` cost** at the chosen tick on a low-end device.
- Stats Today in the afternoon (hourly distribution) and Week (9 event scans) on the current code.
- Report measured differences; the owner sets the tolerance.

### Batch 1 — Test infrastructure and characterization (behavior-preserving)

JVM test source set (add if missing). **Existing code only — no tests for components not yet introduced.** Pipeline, read-model, rollup DAO, durability, and detector seam tests each belong to the batch that introduces their production code (Batches 2–5).

Characterize the existing allowance math — this pins the current behavior before Batch 2 rewrites the owner:

- Interval-window math: start, expiry, remaining, boundary cases.
- Day-rollover: existing JSON resets correctly at midnight.
- Remaining/exhausted: correct result from all four existing read-side copies (`isAllowanceAvailable`, the unlock calculation, `isFallbackBlocked`, `LauncherActivity.loadAllowanceCardData`).
- Backward-compat snapshot: existing `daily_allowance_used` JSON format produces expected values. **This test must continue to pass after Batch 2.**

### Batch 2 — `AllowanceLedger` (behavior-preserving)

Sole owner of the `daily_allowance_used` JSON schema (including the new fields in 7.1), the lock, day/window rollover, the remaining/exhausted math and the live-session marker. Replace the four read-side copies (`isAllowanceAvailable`, the unlock calculation, `isFallbackBlocked`, `LauncherActivity.loadAllowanceCardData`), `SettingsRepository`'s access and the launcher card. Read the **existing JSON as is**; old objects read as `confirmed = usedMs`, `extra = 0`. In-memory cache with write-behind. Fix the contradictory comments.

Test added in this batch: old JSON (no new fields) reads as `confirmed = usedMs`, `extra = 0`; `usedMs` stays the effective value for old readers (7.7 test 10). The four characterization tests from Batch 1 must still pass without modification.

### Batch 3 — Pipeline and rollup tables in shadow mode (behavior-preserving)

- Build `ForegroundSpanTracker` and the thin adapter (null / `SecurityException` handling; no SDK checks inside the pipeline).
- Add migration 6 → 7 (four tables, `usage_pipeline_state` seeded empty) and the rollup writer (6.2, 6.3). It writes **completed past days only**, never today.
- Add the merged read model (6.4) and keep it in shadow mode: it serves legacy for every date.
- Run the pipeline alongside the old logic with a debug-only comparison log for at least 7 days. Record per-app daily differences (used for O6).

Tests added in this batch (all introduced here because their production code is introduced here):
- Pipeline tests for every rule in section 5: missing `PAUSED`, `STOPPED` only, screen-off, keyguard, shutdown/startup, **midnight crossing (one session, clipped time)**, DST day, duplicate and out-of-order `RESUMED`, app already open at window start, tail cap, null/unknown input.
- Read-model precedence tests (6.5), all 14.
- Rollup DAO idempotency tests: run twice, same rows; downgrade refused; today never written; delete-then-insert per date.

### Batch 4 — Allowance cutover (behavior-changing: D1, D2, D9)

- Readings come from the pipeline, **independent of block state**. Enforcement stays gated on focus/standalone/always-on; only the number stops being gated.
- Implement the state machine, the durable model and the accumulate/restart/reconcile rules (section 7). While an allowance app is in the foreground: baseline from the pipeline, exact-expiry timer seeded from the effective value, tick re-read (O3).
- Delete: the accessibility accumulator and checkpoint code, `reconcileCountAllowances`, the service's 60 s allowance sync, and `daily_allowance_usage_stats_sync` from **both** `AppBlockerAccessibilityService` and `ForegroundTaskService`. **Keep** `active_session_pkg`, `active_session_last_checkpoint_ms` and `active_session_end_ms` (7.1). Before removing `active_session_open_at_ms`, grep both files to confirm nothing reads it after the `AllowanceExpiry` interval-fallback path is replaced.
- First read on the cutover day: `confirmedUsedMs = max(existing usedMs, V)`.
- Apply O1 and O2.
- **Copy:** update `DailyAllowanceModal`, `DailyAllowanceDefenseDialog` and the launcher card to say it counts all usage today, not just during blocks, and show the "about" marker when not FRESH.

Tests added in this batch: all 10 allowance durability tests (7.7 tests 1–9 and 11; test 10 backward-compat was added in Batch 2).

### Batch 5 — Stats, rollups and detectors cutover (behavior-changing: D3, D5 to D8)

- `DeviceUsageSource` (one pass, Group A) feeds `AnalyticsProcessor`: summary, hourly and per-day Week values. Remove the `INTERVAL_BEST` path and the 7 per-day scans. Decouple the card from the hourly read (8.1).
- Set `cutoverDate` to the cutover date in `usage_pipeline_state`. Group B consumers switch to the rules in 6.4: detectors (`dateRangeEnd()` → `LocalDate.now().minusDays(1)`, O7), `DayRatingRepository.getRatableDates` (today row from live pipeline), and `dataHealthDayCount`. Detectors apply the seam rules (8.4).
- **`BackgroundFetchWorker` ordering:** within the same worker execution, complete the rollup pass first, then call `runAll()`. Add an explicit sequencing comment so a future refactor cannot reorder them.
- Keep the old Room writer running for the stabilization window (O8), then stop it.
- Extend `DataHealthNotice` for coverage. Check the dormant 3-month path still builds and its rules still work with the new `byHour` source.
- Delete `PhoneUsageSummary.kt` only if Batch 0 confirms it is unused.

Tests added in this batch: detector seam tests (8.4) — five tests per detector, covering: window before the seam passes; window after the seam with enough days passes; window crossing the seam (`start < cutoverDate <= end`) is refused; today is excluded; midnight-crossing session is counted once. Applies to all seven usage detectors.

### Batch 6 — Allowance suggestion and detector verification

Run the seven detectors against recorded data on both sides of the seam. Confirm: no findings from windows that cross the seam, expected disappearance of inflated "infinite session" findings, no duplicate findings from changed fingerprints, a midnight-crossing session counted once, and that the suggested allowance matches enforcement units.

### Batch 7 — Version-gate cleanup and final cleanup (behavior-preserving)

Collapse the 51 pre-API-29 checks (section 2.3). Remove dead code, the shadow log and obsolete keys (with a note on upgrade behavior). Switch `checkOpNoThrow` to `unsafeCheckOpNoThrow` in all three places: `UsageStatsRepository.kt:67`, `ForegroundTaskService.kt:379`, `AppBlockerAccessibilityService.kt:2476`. Re-run the Batch 0 matrix on every version in 3.3. Report line counts for every touched and new file.

## 12. Ground rules

1. **No god files.** One responsibility per new file, aim under about 250 lines, justify over 300. Pure logic separated from Android calls and JVM-tested.
2. **The big files must shrink.** Net line count of `AppBlockerAccessibilityService.kt` and `ForegroundTaskService.kt` goes down in every step that touches them. Report the numbers.
3. **Label every step** behavior-preserving or behavior-changing.
4. **No new prefs keys.** The five new fields live inside the existing allowance JSON (7.1). New Room tables only as in 6.2. Three existing keys are reused as the live-session marker and interval-restore path: `active_session_pkg`, `active_session_last_checkpoint_ms`, and `active_session_end_ms`. `active_session_open_at_ms` and `daily_allowance_usage_stats_sync` are removed in Batch 4 only after confirming no remaining callers in either `AppBlockerAccessibilityService` or `ForegroundTaskService`.
5. **No new polling loops.**
6. **One source per date, always.** No code path may add or average rows from two sources for the same date.
7. Blocking logic, VPN, keyword blocker and the foreground-service type stay untouched. Android 15 / targetSdk 35 work stays parked.
8. Every behavior-changing step updates the user-facing copy and the release notes.

## 13. What can go wrong

- **Duplicates in Room:** prevented by new tables, primary keys, one writer, one transaction per date, today never stored, and one source per date. Test it (6.5).
- **Silent history loss:** the migration must not touch existing tables; the migration test is mandatory.
- **Midnight double counting:** prevented by unsplit sessions and start-date attribution (D8). Test 13 and 14 in 6.5.
- **Lost estimate on restart:** bounded by the checkpoint interval and the 2× restart cap (7.3). The pipeline corrects it on the next read.
- **Estimate over- or under-count:** corrected at reconcile; the confirmed value never decreases (7.4).
- **Detector silence after cutover:** expected for up to about four weeks under the strict seam rule (O6). Tell the owner before release.
- **Findings reappearing** after fingerprint changes (8.4).
- **Rollback after the stabilization window** leaves a legacy gap (6.6).
- **Locked boot:** null from `queryEvents` must never reset usage to zero.
- **Split-screen / multi-resume:** "one foreground package" credits only the last resumed app. Document it; decide from Batch 0.
- **Event delivery delay:** keep the exact-expiry timer and the tick.
- **Retention:** if neither the app nor the service runs for longer than the OS keeps events, history for those days is lost. The always-on service plus the rollup at day rollover is the mitigation.
- **Time travel:** changing the device clock moves the day boundary. Check whether current behavior is intended. Out of scope.
- **Numbers users will see change:** Stats totals, launch counts and some findings change once inflated sessions disappear. Say so in release notes.

## 14. Review gates (definition of done)

- `minSdk` is 29 in Gradle; zero version checks at or below API 29 remain; Lint `NewApi` is clean in CI.
- Every step builds and runs on its own and is labelled preserving or changing.
- The accessibility file and `ForegroundTaskService.kt` are smaller than before; no new file over 300 lines.
- One class interprets platform event types; one owner of the allowance JSON; one read model for history.
- The migration test passes and old rows are unchanged after upgrade.
- Rollup writes are idempotent, never downgrade a complete day, and never store today.
- All 14 source-precedence tests (6.5) pass, including the property test and the seam predicate.
- All 11 allowance durability tests (7.7) pass; behavior in STALE and UNAVAILABLE matches 7.5.
- A midnight-crossing session is one session row, one open and clipped daily time, in the pipeline, the rollups and every detector read.
- The primary Stats screen, Extra view and the dormant 3-month path all build and show sane numbers; all seven usage detectors have seam tests.
- The Batch 0 matrix is re-run on every version in 3.3 with measured deltas reported.
