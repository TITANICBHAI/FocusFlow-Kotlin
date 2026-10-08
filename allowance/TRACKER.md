# FocusFlow Phase 5 — Work Tracker (v3)

Companion to `PHASE_5_USAGE_AND_ALLOWANCE_PLAN_v7.md`.  
No other document is needed. All plan references below are to sections of v7.

**v2 change:** Tests have been redistributed to the batch that introduces their production code.  
Batch 1 is now characterization only. Pipeline, read-model and rollup DAO tests live in Batch 3;  
allowance compatibility tests in Batch 2; durability state-machine tests in Batch 4; detector seam  
tests in Batch 5. Duplicate "all X tests pass" requirements have been removed from later batches.

**v3 change:** Updated the companion plan to v7. Batch 0 notes now combine the uploaded source audit with checks against the current checkout; device measurements and owner input remain open.

---

## Agent instructions

**Before starting a batch**
- Change its `Status` line from `NOT STARTED` to `IN PROGRESS` and fill in `Started`.
- Read its full section in v7 before writing any code.

**During a batch**
- Update the `Notes` block after each meaningful change, not at the end.
- Do not wait until the batch is finished to write anything down.

**Completing an item**
- Tick the checkbox (`- [x]`) only when the work is done and verified.
- Never tick an item that is unverified, partially complete, or merely attempted.

**Recording evidence**
- For every ticked item, add one entry to `Evidence` stating: which files changed, what verification was performed, and what the result was.
- One-liners are fine. The key word is "and the result was".

**Failures and blockers**
- If a check fails, keep the checkbox unchecked, add an entry to `Failures` with the observed failure, then add the fix and the successful rerun before ticking.
- Never delete a failure entry. Add the outcome below it.
- If a batch is blocked waiting on the owner, change `Status` to `BLOCKED`, describe the blocker in `Notes`, and stop.

**Decisions**
- Record every choice made (option picked, alternatives rejected) and every item deferred to later. Even a small design choice counts.

**Before marking a batch COMPLETE**
- Reconcile every checkbox with actual code and test output.
- Leave incomplete items unchecked and write why they are incomplete.
- Change `Status` to `COMPLETE` and fill in `Completed` only when all checkboxes are ticked.

---

## Batch 0 — Verify and measure

**Status:** `IN PROGRESS`  
**Started:** 2026-10-08  
**Completed:** <!-- YYYY-MM-DD -->

### Tasks

**SDK floor (Batch 0a)**
- [x] Read `minSdk`, `compileSdk`, `targetSdk` from Gradle and record them in `Notes`.
- [x] Confirm `minSdk` is at least 29; current value is 29, so no floor change is needed.
- [x] Verify the existing Lint `NewApi` gate and run matching local flavor lint tasks; record findings in `Notes`. Do not trigger GitHub Actions unless the owner explicitly requests it.

**Writer/reader maps**
- [x] Map every writer and reader of `daily_allowance_used` (prefs key): `AppBlockerAccessibilityService`, `ForegroundTaskService`, `LauncherActivity`, `SettingsRepository`, backup/export, and anything else found.
- [x] Map every writer and reader of `daily_app_usage` and `app_sessions` (Room): `BackgroundFetchWorker`, `AppUsageAndSessionTracker`, `DayRatingRepository`, `StatsViewModel`, detectors, and anything else found.
- [x] Map when `FindingDetectionRunner.runAll` is triggered (confirm `BackgroundFetchWorker.kt:138`).
- [x] Document exactly how `FindingRepository.submit` deduplicates (confirm `(detectionType, subjectPackage)` + `evidenceFingerprint` path).
- [x] Record how the existing tracker dates a midnight-crossing session (confirm `epochMsToLocalDate(sessionStartWall)`).
- [x] Record which code reads each of the four `ACTIVE_SESSION_*` keys and `daily_allowance_usage_stats_sync`, in both `AppBlockerAccessibilityService` and `ForegroundTaskService`.

**Device matrix** — run on API 29, 31, 33, 34, and the latest available (section 3.3)  
For each case: record the device/API, what each source reported, and the diff.
- [ ] Screen off while an app is open — do `ACTIVITY_PAUSED` and `ACTIVITY_STOPPED` arrive?
- [ ] Leave an app via home button, recents, and a notification tap.
- [ ] Multi-activity app navigation — does switching activities inside one app close the session?
- [ ] Split-screen and picture-in-picture — which package gets credited?
- [ ] Keyboard open while typing in an allowance app — does it create a foreground change or new open?
- [ ] Notification shade pulled down in an allowance app — same question.
- [ ] Permission dialog and share sheet over an allowance app — decide the O2 bridge allow-list.
- [ ] Reboot mid-session, including first read before unlock (null handling) — no crash, no reset to zero.
- [ ] A session that crosses midnight — one row, attributed to the start date.
- [ ] DST day if possible — day boundary is calendar arithmetic.

**Measurements**
- [ ] Event latency: time from a real app switch to its event in `queryEvents`. Record result and decide tick interval for O3.
- [ ] Retention: earliest event timestamp on each test device. Record.
- [ ] `queryEvents` cost at the chosen tick on a low-end device. Record whether incremental reads are needed.
- [ ] Stats "Today" in the afternoon on current code — screenshot or log of hourly distribution.
- [ ] Stats "Week" on current code — confirm 9 event scans fire and record load time.

**Owner input**
- [ ] Report all source diffs to the owner. Record the owner's tolerance for measurement disagreement before closing this batch.

### Notes
**Current checkout / SDK baseline (2026-10-08):** `app/build.gradle.kts` sets `minSdk = 29`, `compileSdk = 35`, and `targetSdk = 35`. No SDK floor change is needed. `FocusFlowDatabase.kt` is Room version 6 with schema export enabled. The source archive did not include Gradle files; that limitation does not apply to the current checkout.

**NewApi lint gate:** `app/build.gradle.kts` has `abortOnError = true`, `checkOnly += "NewApi"`, and `error += "NewApi"`. Both flavor workflows include their lint tasks. The persisted local reports for Production and Tbtechsdev say “No issues found.” GitHub Actions were not triggered.

**`daily_allowance_used` (`PREF_DAILY_ALLOWANCE_USED`) writers and readers:**
- Writers: `AppBlockerAccessibilityService` (lines 2548, 2784, 2855); `ForegroundTaskService` (547, 731, 754); `SettingsRepository` (1361, 1364, 1371, 1376 — restore/reset paths).
- Readers: `AppBlockerAccessibilityService` (2951); `ForegroundTaskService` (415, 444, 1504); `SettingsRepository` (1193 — backup export); `LauncherActivity` (1668, `loadAllowanceCardData`).
- `SettingsRepository` defines `KEY_DAILY_ALLOWANCE_USED = "daily_allowance_used"` as well as using the service constant; no third definition found.

**`daily_allowance_usage_stats_sync` (`PREF_USAGE_STATS_SYNC`) writers/readers:** `AppBlockerAccessibilityService` defines the key (115), reads it (2956), and writes it (2563, 2580, 2859). `ForegroundTaskService` reads/writes it (461, 551).

**`ACTIVE_SESSION_*` keys:**
- `PREF_ACTIVE_SESSION_PKG`: `AppBlockerAccessibilityService` reads at 2394, clears at 2443/2576 and writes at 2559; `ForegroundTaskService` reads at 451 and writes/clears at 713/767.
- `PREF_ACTIVE_SESSION_OPEN_AT_MS`: accessibility service writes/clears at 2444/2577 and 2560, reads at 2871; foreground service reads at 455 for `AllowanceExpiry` interval fallback.
- `PREF_ACTIVE_SESSION_LAST_CHECKPOINT_MS`: accessibility service uses it at 1605, 2395, 2445, 2561, 2651, 2857; foreground service reads it at 773.
- `PREF_ACTIVE_SESSION_END_MS`: accessibility service reads at 2408 on reconnect and clears/writes at 2446/2579 and 2562; `SettingsRepository` also reads it at 1200 for backup. Keep it in Batch 4.

**`daily_app_usage` / `app_sessions`:** writes flow through `AppUsageAndSessionTracker` via `AnalyticsProcessor` / `BackgroundFetchWorker`. Readers include `StatsViewModel`, `DayRatingRepository`, `FindingDetectors`, and `FindingDetectionRunner`; DAO and model references were also checked in the current repository.

**Detection and dedup:** `BackgroundFetchWorker.kt:138` invokes `FindingDetectionRunner.runAll()`. `FindingRepository.submit` looks up by `(detectionType, subjectPackage)`; the evidence fingerprint determines whether an active finding resurfaces, while intentional suppression and resolved-finding cooldowns remain in effect.

**Midnight-crossing behavior:** `AppUsageAndSessionTracker.closeCurrentSession` assigns `localDate = epochMsToLocalDate(sessionStartWall)`, stores the full duration, and `splitIntoHourlySegments` clips daily time. The new pipeline must preserve this behavior.

**O7:** `FindingDetectionRunner.dateRangeEnd()` still includes today; the `minusDays(1)` change is deferred to Batch 5.

**Lint/source inventory:** the uploaded source archive showed 23 pre-API-29 version checks across three files, a partial count only. The plan's broader inventory remains for Batch 7; the current lint reports contain no `NewApi` findings. `checkOpNoThrow` remains at `UsageStatsRepository.kt:67`, `ForegroundTaskService.kt:379`, and `AppBlockerAccessibilityService.kt:2477` (the plan says 2476; same call block).

**Baseline line counts:** `AppBlockerAccessibilityService.kt` 4,802 lines; `ForegroundTaskService.kt` 1,627 lines.

**Still open:** O2 bridge allow-list requires the device matrix. O3 remains 30 seconds by default pending latency measurement. Retention and `queryEvents` cost are unmeasured. Owner tolerance for measurement differences has not been reported.

### Evidence

### Failures and blockers

### Decisions

---

## Batch 1 — Test infrastructure and characterization (behavior-preserving)

**Status:** `NOT STARTED`  
**Started:** <!-- YYYY-MM-DD -->  
**Completed:** <!-- YYYY-MM-DD -->

**Scope:** Existing behavior only. No new production code. No tests for components that do  
not yet exist. Pipeline, read-model, rollup DAO, allowance state-machine, and detector seam  
tests each belong to the batch that introduces their production code (Batch 2–Batch 5).

### Tasks

**Test infrastructure**
- [ ] JVM test source set exists and Gradle can run it.

**Characterization — existing allowance math (pins behavior before Batch 2 changes it)**
- [ ] Interval-window math: start, expiry, remaining, boundary cases.
- [ ] Day-rollover: existing JSON resets correctly at midnight.
- [ ] Remaining/exhausted: correct result from the existing four read-side copies.
- [ ] Backward-compat snapshot: reading the existing `daily_allowance_used` JSON format produces the expected values. This test must continue to pass after Batch 2.

**Verification**
- [ ] All four characterization tests pass on the current code with no production changes.
- [ ] The test source set runs cleanly in CI.

### Notes

### Evidence

### Failures and blockers

### Decisions

---

## Batch 2 — `AllowanceLedger` (behavior-preserving)

**Status:** `NOT STARTED`  
**Started:** <!-- YYYY-MM-DD -->  
**Completed:** <!-- YYYY-MM-DD -->

### Tasks

**New class**
- [ ] `AllowanceLedger` is the sole owner of the `daily_allowance_used` JSON schema, the lock, day/window rollover, and the remaining/exhausted math.
- [ ] Stores five new fields from 7.1: `confirmedUsedMs`, `confirmedCount`, `confirmedAtMs`, `estimatedExtraMs`, `estimatedExtraOpens`.
- [ ] Old JSON (without new fields) reads as `confirmed = usedMs`, `extra = 0`. No data migration.
- [ ] In-memory cache with write-behind to prefs.
- [ ] All invariants from 7.1 enforced: `confirmed*` monotonic per day/window; `estimated*` non-negative; effective equals confirmed plus estimated.

**Replace all callers**
- [ ] `isAllowanceAvailable` in `AppBlockerAccessibilityService` reads through the ledger.
- [ ] Unlock remaining calculation in `AppBlockerAccessibilityService` reads through the ledger.
- [ ] `isFallbackBlocked` in `ForegroundTaskService` reads through the ledger.
- [ ] `LauncherActivity.loadAllowanceCardData` reads through the ledger.
- [ ] `SettingsRepository` backup and reset access goes through the ledger.
- [ ] No direct pref read or write of `daily_allowance_used` remains outside the ledger.

**Cleanup**
- [ ] Contradictory comments about who owns allowance accounting removed and replaced with a single comment pointing to `AllowanceLedger`.

**Tests added in this batch**
- [ ] Allowance compatibility/math test: old JSON (no new fields) reads as `confirmed = usedMs`, `extra = 0`; `usedMs` stays the effective value for old readers.
- [ ] Existing interval/rollover/remaining characterization tests from Batch 1 still pass unchanged.

**Verification**
- [ ] `AppBlockerAccessibilityService.kt` line count lower than before this batch.
- [ ] `ForegroundTaskService.kt` line count lower than before this batch.
- [ ] Manual test: active allowance blocks at the same moment before and after the refactor.

### Notes

### Evidence

### Failures and blockers

### Decisions

---

## Batch 3 — Pipeline and rollup tables in shadow mode (behavior-preserving)

**Status:** `NOT STARTED`  
**Started:** <!-- YYYY-MM-DD -->  
**Completed:** <!-- YYYY-MM-DD -->

### Tasks

**`ForegroundSpanTracker`**
- [ ] Pure class with no Android imports: fed events in time order, emits `[start, end)` sessions.
- [ ] Implements all rules from section 5 of v6. Replicates existing tracker's midnight behavior: session on start date, full duration, time clipped via `splitIntoHourlySegments`.
- [ ] Null and `SecurityException` from the adapter produce "unknown", not zero.
- [ ] No SDK version checks inside the class.
- [ ] Thin adapter over `UsageStatsRepository` handles all platform API calls.

**Migration 6 → 7**
- [ ] Creates `usage_pipeline_state`, `usage_rollup_day`, `usage_rollup_app_day`, `usage_rollup_session` (section 6.2).
- [ ] `usage_pipeline_state` seeded with `cutover_date = null`.
- [ ] Migration test: build a v6 DB with rows in `daily_app_usage` and `app_sessions`, migrate, assert every old row is byte-for-byte unchanged.
- [ ] Exported schema JSON for version 7 committed.

**Rollup writer**
- [ ] Writes completed past days only — never today.
- [ ] One writer, one mutex, one transaction per date: delete then insert.
- [ ] COMPLETE day never downgraded.
- [ ] Triggers: app open, service start, day rollover, Stats open.

**Merged read model**
- [ ] `UsageHistoryRepository` (or equivalent) implemented.
- [ ] Shadow mode: all reads return legacy for every date; rollup tables never served.

**Pipeline tests (introduced here because `ForegroundSpanTracker` is introduced here)**
- [ ] Missing `ACTIVITY_PAUSED` — session closed by the next `RESUMED`.
- [ ] `ACTIVITY_STOPPED` only — session closed by stop event.
- [ ] Screen off (`SCREEN_NON_INTERACTIVE`) — open session closed.
- [ ] Keyguard shown — open session closed.
- [ ] `DEVICE_SHUTDOWN` — all open sessions closed; no explicit `STOPPED` events needed.
- [ ] Startup — sessions begin from `DEVICE_STARTUP`.
- [ ] Midnight crossing — one session row, clipped daily time on each day, session count 1 on start day and 0 on the next.
- [ ] DST day (23 h and 25 h) — boundary is calendar arithmetic, not 86 400 000 ms.
- [ ] Duplicate and out-of-order `ACTIVITY_RESUMED` — no duplicate open sessions.
- [ ] App already open at window start — clipped to window start only.
- [ ] Tail cap — open session capped at 4 h; measured session never capped.
- [ ] Null from `queryEvents` — last known value kept, not zero.
- [ ] `SecurityException` — same as null.

**Read-model precedence tests (introduced here because the read model is introduced here; section 6.5)**
- [ ] Test 1: shadow mode — every date including today from legacy; rollup tables untouched.
- [ ] Test 2: cutover — today in Group A is live; no rollup row exists for today after repeated loads.
- [ ] Test 3: cutover — detectors exclude today; rating eligibility includes today when live pipeline has a session.
- [ ] Test 4: `date < cutoverDate` with a complete shadow rollup and legacy rows — legacy wins in both groups.
- [ ] Test 5: `date >= cutoverDate` with legacy rows and no rollup — pipeline wins; legacy ignored.
- [ ] Test 6: yesterday not yet rolled up — on-demand result equals the rollup written later.
- [ ] Test 7: invariance — `live(D)` at 23:59 equals `rollup(D)` written after midnight for the same event log.
- [ ] Test 8: PARTIAL day — Group A shows it with flag; Group B treats it as missing.
- [ ] Test 9: events unavailable for today — Group A shows unknown, not zero.
- [ ] Test 10: rollback — `cutoverDate` set to null returns legacy for all dates.
- [ ] Test 11: property test — no date served from two sources for any combination of mode, date and row presence.
- [ ] Test 12: seam predicate `start < cutoverDate <= end` including boundary dates.
- [ ] Test 13: midnight — one session row on D (30 min), 10 min daily on D, 20 min on D+1, session count 1 on D and 0 on D+1.
- [ ] Test 14: day D rolled up while a session that started on D is still open — D stays PARTIAL, then COMPLETE after close, no duplicate session row.

**Rollup DAO tests (introduced here because the DAO is introduced here)**
- [ ] Idempotency: run the writer twice with the same events, assert identical rows.
- [ ] Downgrade refused: a COMPLETE day is not overwritten.
- [ ] Today never written: after any number of writer calls, no rollup row exists for today's date.
- [ ] Delete-then-insert per date: no two rows for the same `(date, package_name)`.

**Shadow comparison**
- [ ] Debug-only comparison log running alongside the old logic for at least 7 days.
- [ ] Per-app daily differences recorded in `Notes` for O6 decision.
- [ ] No behavior change: existing allowance, Stats, and detector output identical to before.

### Notes
<!-- Shadow comparison results (fill after 7 days): -->

### Evidence

### Failures and blockers

### Decisions

---

## Batch 4 — Allowance cutover (behavior-changing: D1, D2, D9)

**Status:** `NOT STARTED`  
**Started:** <!-- YYYY-MM-DD -->  
**Completed:** <!-- YYYY-MM-DD -->

### Tasks

**Pipeline as measurement source**
- [ ] Time budget, count and interval readings come from the pipeline regardless of whether a block is active (D1). Enforcement stays gated on focus/standalone/always-on.
- [ ] While an allowance app is in the foreground: baseline from the pipeline, exact-expiry timer from effective value, tick re-read per O3.

**State machine and durable model (section 7)**
- [ ] FRESH / STALE / UNAVAILABLE state reducer implemented.
- [ ] Accumulate rules for completed segments (7.3): segment start is `max(session start, confirmedAtMs)`; split at midnight.
- [ ] Writes at segment end, every 15 s while a segment is open and state is not FRESH, on state change, and on teardown.
- [ ] Restart recovery: recover `min(now − lastCheckpoint, 2 × checkpoint interval)`, log the gap.
- [ ] Reconcile on successful read (7.4): confirmed never decreases; estimated resets; effective recomputed.
- [ ] First read on cutover day: `confirmedUsedMs = max(existing usedMs, V)`.

**Allowance durability tests (introduced here because the state machine is introduced here)**
- [ ] Test 1: accumulation across sessions during STALE — segments add up; completed segments survive to reconcile.
- [ ] Test 2: FRESH to STALE flip — a segment ending while FRESH after the last confirm is counted.
- [ ] Test 3: restart mid-segment — recovers at most `2 × checkpoint interval`; longer gap adds only the capped amount and logs it.
- [ ] Test 4: restart with no marker — nothing invented.
- [ ] Test 5: reconcile higher, lower and equal — `confirmed*` never decreases; `estimated*` resets; segments before T not re-added; segments after T kept.
- [ ] Test 6: midnight — segment split at boundary; new day starts from zero.
- [ ] Test 7: count mode — increments on new sessions only; midnight-spanning session is not a new open; reconcile resets the estimate.
- [ ] Test 8: interval — expiry by wall clock; time measured inside window.
- [ ] Test 9: cap — open segment never exceeds 4 h.
- [ ] Test 11: failure inputs — null, `SecurityException`, timeout, slow reads move state correctly and never reset usage to zero.

**Deletions — accessibility service**
- [ ] Accumulator and checkpoint code deleted.
- [ ] `reconcileCountAllowances` deleted.

**Deletions — foreground service**
- [ ] 60 s allowance sync loop deleted.

**Deletions — both files**
- [ ] `daily_allowance_usage_stats_sync` removed from `AppBlockerAccessibilityService`.
- [ ] `daily_allowance_usage_stats_sync` removed from `ForegroundTaskService` (lines 461, 551, and any others found).

**Key handling**
- [ ] `active_session_pkg` kept.
- [ ] `active_session_last_checkpoint_ms` kept.
- [ ] `active_session_end_ms` kept (interval-mode restore path, accessibility line 2407).
- [ ] `active_session_open_at_ms`: grep both files to confirm nothing reads it after `AllowanceExpiry` interval-fallback is replaced, then remove. If still needed, document why here.

**Apply open items**
- [ ] O1: interval-mode window starts at first open regardless of block state.
- [ ] O2: bridge allow-list from Batch 0 device matrix applied.

**Copy updates**
- [ ] `DailyAllowanceModal` text says allowance counts all usage today, not just during blocks.
- [ ] `DailyAllowanceDefenseDialog` text updated.
- [ ] Launcher card shows "about" marker when not FRESH.

**Verification**
- [ ] `AppBlockerAccessibilityService.kt` line count lower than before this batch.
- [ ] `ForegroundTaskService.kt` line count lower than before this batch.
- [ ] All durability tests above pass.
- [ ] Manual: a time-budget allowance blocks at the correct moment on a real device.
- [ ] Manual: kill the accessibility service mid-session; reopen; usage estimate within one checkpoint interval of true value.
- [ ] Manual: revoke usage access mid-session; no crash, last known value kept.
- [ ] Test on API 29 and 33.

### Notes
<!-- active_session_open_at_ms — kept or removed? Record here. -->

### Evidence

### Failures and blockers

### Decisions

---

## Batch 5 — Stats, rollups and detectors cutover (behavior-changing: D3, D5–D8)

**Status:** `NOT STARTED`  
**Started:** <!-- YYYY-MM-DD -->  
**Completed:** <!-- YYYY-MM-DD -->

### Tasks

**`DeviceUsageSource` (Group A)**
- [ ] One pipeline pass feeds `AnalyticsProcessor.buildPhoneUsageMetrics`: summary, hourly and per-day Week values.
- [ ] `INTERVAL_BEST` path removed from `UsageStatsRepository` and `AnalyticsProcessor`.
- [ ] 7 per-day `getUsageSummary` calls for Week removed.
- [ ] Observed Device Time card no longer disappears when only one of summary or hourly fails.

**`cutoverDate` and Group B switch**
- [ ] `cutoverDate` written to `usage_pipeline_state`.
- [ ] `FindingDetectionRunner.dateRangeEnd()` changed to `LocalDate.now().minusDays(1)` (O7).
- [ ] `DayRatingRepository.getRatableDates` uses merged read model for today's row.
- [ ] `dataHealthDayCount` uses merged read model.
- [ ] Detectors apply seam rules from 8.4.

**BackgroundFetchWorker ordering**
- [ ] In the same worker execution, rollup pass completes before `runAll()` is called.
- [ ] A comment in `BackgroundFetchWorker` explicitly marks the required ordering.

**Legacy writer**
- [ ] Old `AppUsageAndSessionTracker` Room writes stopped (or scheduled to stop after stabilization window O8).
- [ ] Existing legacy rows still readable through the merged read model.

**Stats surfaces**
- [ ] `DataHealthNotice` extended to report coverage: days complete, days partial, days missing.
- [ ] Dormant 3-month path still builds and `ThreeMonthRules` works with the new `byHour` source.
- [ ] `PhoneUsageSummary.kt` deleted only if Batch 0 confirmed zero callers; record outcome here.

**Detector seam tests (introduced here because source switching is introduced here)**
- [ ] A window entirely before `cutoverDate` is evaluated against legacy and passes.
- [ ] A window entirely after `cutoverDate` with enough days passes.
- [ ] A window crossing the seam (`start < cutoverDate <= end`) is refused for all seven usage detectors.
- [ ] Today is excluded from every detector window (O7).
- [ ] A midnight-crossing session is counted once per detector, on its start date.

**Verification**
- [ ] `AppBlockerAccessibilityService.kt` line count lower than before this batch.
- [ ] All five detector seam tests above pass.
- [ ] Stats Today hourly distribution is sane (no midnight pile) on API 29 and 33.
- [ ] Stats Week populated from a single pipeline pass.
- [ ] Detector output unchanged for a window entirely before `cutoverDate`.
- [ ] `DayRatingRepository.getRatableDates` returns same dates as before for a day with task data only.
- [ ] Test on API 29, 31 and 33.

### Notes
<!-- PhoneUsageSummary.kt deleted? Record here. -->
<!-- Stabilization window end date: -->

### Evidence

### Failures and blockers

### Decisions

---

## Batch 6 — Allowance suggestion and detector verification

**Status:** `NOT STARTED`  
**Started:** <!-- YYYY-MM-DD -->  
**Completed:** <!-- YYYY-MM-DD -->

### Tasks

- [ ] Run all seven usage detectors against a dataset spanning the seam. Confirm no finding from a mixed-source window.
- [ ] Confirm inflated "infinite session" findings from the old pipeline no longer appear.
- [ ] Confirm no findings reappear due to changed evidence fingerprints (check `FindingRepository.submit` dedup log).
- [ ] Confirm a midnight-crossing session is counted once in morning-hijack, variable-reward and infinite-session detectors.
- [ ] Suggested allowance limit is computed from pipeline data in the same units as enforcement: all-day usage (D1).
- [ ] On a dataset with known ratings and usage, the suggested value matches a manual calculation.
- [ ] Confirm week 4 in `computeWeeklyAverages` is D-7 to D-1 after the O7 fix (not D-6 to today).
- [ ] A rising trend that existed only because today's partial row inflated week 4 is no longer detected.

### Notes

### Evidence

### Failures and blockers

### Decisions

---

## Batch 7 — Version-gate cleanup and final cleanup (behavior-preserving)

**Status:** `NOT STARTED`  
**Started:** <!-- YYYY-MM-DD -->  
**Completed:** <!-- YYYY-MM-DD -->

### Tasks

**SDK gate cleanup (section 2.3 inventory)**
- [ ] All 51 pre-API-29 version checks collapsed to their always-true branch. Record file-by-file count in `Notes`.
- [ ] `MOVE_TO_FOREGROUND` and `MOVE_TO_BACKGROUND` branches removed from `UsageStatsRepository`, `ForegroundTaskService`, and `AppBlockerAccessibilityService`.
- [ ] No remaining `VERSION_CODES` reference at or below `Q` in any `.kt` file.
- [ ] Lint `NewApi` CI gate passes with zero findings.

**Permission check**
- [ ] `checkOpNoThrow` → `unsafeCheckOpNoThrow` in `UsageStatsRepository.kt:67`.
- [ ] `checkOpNoThrow` → `unsafeCheckOpNoThrow` in `ForegroundTaskService.kt:379`.
- [ ] `checkOpNoThrow` → `unsafeCheckOpNoThrow` in `AppBlockerAccessibilityService.kt:2476`.
- [ ] No remaining `checkOpNoThrow` calls in any `.kt` file.

**Other dead code**
- [ ] Shadow comparison log removed.
- [ ] Retired `AppUsageAndSessionTracker` accumulation code removed if fully replaced.
- [ ] Obsolete prefs keys removed, each with a comment or migration note on the upgrade path.

**Final matrix re-run** — API 29, 31, 33, 34, latest (section 3.3)
- [ ] Foreground service starts at app open and after reboot.
- [ ] Notification card appears and returns after dismissal on API 33+.
- [ ] Allowance blocks at the correct moment.
- [ ] Stats totals are plausible.
- [ ] Locked-boot read does not crash or reset usage to zero.

**Line count report** (record in `Evidence`)
- [ ] `AppBlockerAccessibilityService.kt`: before → after.
- [ ] `ForegroundTaskService.kt`: before → after.
- [ ] Every new file: name and line count.
- [ ] No new file exceeds 300 lines.

**All review gates met (section 14 of v6)**
- [ ] `minSdk` = 29; zero `VERSION_CODES` checks at or below `Q`.
- [ ] Lint `NewApi` clean in CI.
- [ ] Every batch step labelled preserving or changing.
- [ ] Accessibility file and `ForegroundTaskService.kt` smaller than before every batch.
- [ ] No new file over 300 lines.
- [ ] One class interprets platform event types.
- [ ] One owner of the allowance JSON.
- [ ] One merged read model for history.
- [ ] Migration test passes; old rows unchanged after upgrade.
- [ ] Rollup writes idempotent, never downgrade a complete day, never store today.
- [ ] All 14 read-model precedence tests (6.5) pass.
- [ ] All allowance durability tests pass (10 state-machine + 1 backward-compat).
- [ ] Midnight-crossing session is one row, one open, clipped daily time — in pipeline, rollups and every detector.
- [ ] Primary Stats screen, Extra view and dormant 3-month path all build and show sane numbers.
- [ ] All seven usage detectors have seam tests.
- [ ] Batch 0 matrix re-run on all versions in 3.3 with measured deltas reported.

### Notes
<!-- Pre-API-29 gate counts by file: -->

### Evidence
<!-- AppBlockerAccessibilityService.kt: XXXX → XXXX lines -->
<!-- ForegroundTaskService.kt: XXXX → XXXX lines -->
<!-- New files: -->

### Failures and blockers

### Decisions

---

## Summary (fill in at project end)

| Batch | Status | Completed |
|---|---|---|
| Batch 0 Verify and measure | IN PROGRESS | — |
| Batch 1 Test infra and characterization | NOT STARTED | — |
| Batch 2 `AllowanceLedger` | NOT STARTED | — |
| Batch 3 Shadow pipeline and rollups | NOT STARTED | — |
| Batch 4 Allowance cutover | NOT STARTED | — |
| Batch 5 Stats, rollups and detectors cutover | NOT STARTED | — |
| Batch 6 Detector verification | NOT STARTED | — |
| Batch 7 Cleanup and final matrix | NOT STARTED | — |

**Incomplete items and reasons** (reconcile with code before closing)
<!-- List any unchecked items here with the reason they were left incomplete. -->

**Deferred work**
<!-- Items explicitly moved to a future phase. -->