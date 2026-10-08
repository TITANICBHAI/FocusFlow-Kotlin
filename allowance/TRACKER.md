# FocusFlow Phase 5 — Work Tracker

Companion to `PHASE_5_USAGE_AND_ALLOWANCE_PLAN_v6.md`.  
No other document is needed. All plan references below are to sections of v6.

---

## Agent instructions

**Before starting a batch**
- Change its `Status` line from `NOT STARTED` to `IN PROGRESS` and fill in `Started`.
- Read its full section in v6 before writing any code.

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

## Batch 5.0 — Verify and measure

**Status:** `NOT STARTED`  
**Started:** <!-- YYYY-MM-DD -->  
**Completed:** <!-- YYYY-MM-DD -->

### Tasks

**SDK floor (5.0a)**
- [ ] Read `minSdk`, `compileSdk`, `targetSdk` from Gradle and record them below.
- [ ] If `minSdk` < 29, raise it to 29 and verify the project still builds.
- [ ] Add the Lint `NewApi` CI gate and run it on the current code; record all findings below.

**Writer/reader maps**
- [ ] Map every writer and reader of `daily_allowance_used` (prefs key). Include `AppBlockerAccessibilityService`, `ForegroundTaskService`, `LauncherActivity`, `SettingsRepository`, backup/export, and anything else found. Record the map in `Notes`.
- [ ] Map every writer and reader of `daily_app_usage` and `app_sessions` (Room). Include `BackgroundFetchWorker`, `AppUsageAndSessionTracker`, `DayRatingRepository`, `StatsViewModel`, detectors, and anything else found.
- [ ] Map when `FindingDetectionRunner.runAll` is triggered (confirm it is `BackgroundFetchWorker.kt:138`).
- [ ] Document exactly how `FindingRepository.submit` deduplicates (confirm the `(detectionType, subjectPackage)` + `evidenceFingerprint` path).
- [ ] Record how the existing tracker dates a midnight-crossing session (confirm `epochMsToLocalDate(sessionStartWall)`).
- [ ] Record exactly which code reads each of the four `ACTIVE_SESSION_*` keys and `daily_allowance_usage_stats_sync`, in both `AppBlockerAccessibilityService` and `ForegroundTaskService`.

**Device matrix** — run on API 29, 31, 33, 34, and the latest available (section 3.3)  
For each case: record the device/API, what each source reported, and the diff.
- [ ] Screen off while an app is open — do `ACTIVITY_PAUSED` and `ACTIVITY_STOPPED` arrive?
- [ ] Leave an app via home button, recents, and a notification tap — does each generate the right event?
- [ ] Multi-activity app navigation — does switching activities inside one app close the session?
- [ ] Split-screen and picture-in-picture — which package gets credited?
- [ ] Keyboard open while typing in an allowance app — does it create a foreground change or new open?
- [ ] Notification shade pulled down in an allowance app — same question.
- [ ] Permission dialog and share sheet over an allowance app — decide the O2 bridge allow-list.
- [ ] Reboot mid-session, including first read before unlock (null handling) — does anything crash or reset to zero?
- [ ] A session that crosses midnight — is it one row attributed to the start date?
- [ ] DST day if possible — does the day boundary shift as expected?

**Measurements**
- [ ] Event latency: measure the time from a real app switch to its event appearing in `queryEvents`. Record the result and decide the tick interval for O3.
- [ ] Retention: record the earliest event timestamp available on each test device. This decides how often rollups must run.
- [ ] `queryEvents` cost: measure query time at the chosen tick on a low-end device. Record whether incremental reads are needed.
- [ ] Stats "Today" in the afternoon on the current code: capture a screenshot or log of the hourly distribution. Are bars sane or piled at midnight?
- [ ] Stats "Week" on the current code: confirm it fires 9 event scans and record the measured load time.

**Owner input**
- [ ] Report all diffs between sources to the owner. Owner sets the acceptable tolerance. Record the tolerance here before proceeding.

### Notes
<!-- SDK versions found: -->
<!-- Writer/reader maps: -->
<!-- Lint NewApi findings: -->
<!-- O2 bridge allow-list decision: -->
<!-- Tick interval chosen: -->
<!-- Retention window measured: -->
<!-- Owner-set tolerance: -->

### Evidence
<!-- Item: [checkbox text] | Files: — | Verification: — | Result: — -->

### Failures and blockers
<!-- None yet -->

### Decisions
<!-- None yet -->

---

## Batch 5.1 — Characterization and pipeline tests

**Status:** `NOT STARTED`  
**Started:** <!-- YYYY-MM-DD -->  
**Completed:** <!-- YYYY-MM-DD -->

### Tasks

**Test infrastructure**
- [ ] Add a JVM test source set if one does not exist.

**Pin current behavior before any cutover**
- [ ] Tests that pin interval-window math as it exists today (start, expiry, remaining).
- [ ] Tests that pin day-rollover behavior as it exists today.
- [ ] Tests that pin remaining/exhausted math as it exists today.

**Pipeline tests — section 5 rules** (one test class, one test per rule)
- [ ] Missing `ACTIVITY_PAUSED` — session closed by the next `RESUMED`.
- [ ] `ACTIVITY_STOPPED` only — session closed by stop event.
- [ ] Screen off (`SCREEN_NON_INTERACTIVE`) — open session is closed.
- [ ] Keyguard shown — open session is closed.
- [ ] `DEVICE_SHUTDOWN` — all open sessions closed; no explicit `STOPPED` events needed.
- [ ] Startup — sessions begin from `DEVICE_STARTUP`.
- [ ] Midnight crossing — one session row, clipped daily time on each day, session count 1 on start day and 0 on the next.
- [ ] DST day (23 h and 25 h) — day boundary is calendar arithmetic, not 86 400 000 ms.
- [ ] Duplicate and out-of-order `ACTIVITY_RESUMED` — no duplicate open sessions.
- [ ] App already open at window start — clipped to window start, not counted from session start.
- [ ] Tail cap — open session capped at 4 h; measured session never capped.
- [ ] Null from `queryEvents` — last known value kept, not zero.
- [ ] `SecurityException` — same as null.

**Read-model precedence tests — 6.5 (all 14)**
- [ ] Test 1: shadow mode — every date including today from legacy; rollup tables untouched.
- [ ] Test 2: cutover — today in Group A is live; no rollup row exists for today after repeated loads.
- [ ] Test 3: cutover — detectors exclude today; rating eligibility includes today when live pipeline has a session.
- [ ] Test 4: `date < cutoverDate` with a complete shadow rollup and legacy rows — legacy wins in both groups.
- [ ] Test 5: `date >= cutoverDate` with legacy rows and no rollup — pipeline wins; legacy ignored.
- [ ] Test 6: yesterday not yet rolled up — on-demand result equals the rollup written later.
- [ ] Test 7: invariance — `live(D)` at 23:59 equals `rollup(D)` written after midnight for the same event log, apart from the carried-over session case.
- [ ] Test 8: PARTIAL day — Group A shows it with flag; Group B treats it as missing.
- [ ] Test 9: events unavailable for today — Group A shows unknown, not zero.
- [ ] Test 10: rollback — `cutoverDate` set to null returns legacy for all dates.
- [ ] Test 11: property test — for random combinations of modes, dates and row presence, no date is served from two sources.
- [ ] Test 12: seam predicate `start < cutoverDate <= end` including boundary dates.
- [ ] Test 13: midnight — one session row on D (30 min total), 10 min daily on D, 20 min on D+1, session count 1 on D and 0 on D+1.
- [ ] Test 14: day D rolled up while a session that started on D is still open — D stays PARTIAL, then COMPLETE after close, with no duplicate session row.

**Allowance durability tests — 7.7 (all 11)**
- [ ] Test 1: accumulation across sessions during STALE — several sessions add up; completed segments survive to reconcile.
- [ ] Test 2: FRESH to STALE flip — a segment ending while FRESH after the last confirm is counted.
- [ ] Test 3: restart mid-segment — recovers at most `2 × checkpoint interval`; longer gap adds only the capped amount and logs it.
- [ ] Test 4: restart with no marker — nothing invented.
- [ ] Test 5: reconcile higher, lower and equal — `confirmed*` never decreases; `estimated*` resets; segments before T not re-added; segments after T kept.
- [ ] Test 6: midnight — segment split at boundary; new day starts from zero.
- [ ] Test 7: count mode — increments on new sessions only; midnight-spanning session is not a new open; reconcile resets the estimate.
- [ ] Test 8: interval — expiry by wall clock; time measured inside window.
- [ ] Test 9: cap — open segment never exceeds 4 h.
- [ ] Test 10: backward compatibility — old JSON (no new fields) reads as `confirmed = usedMs`, `extra = 0`; `usedMs` stays the effective value for old readers.
- [ ] Test 11: failure inputs — null, `SecurityException`, timeout and slow reads move state correctly and never reset usage to zero.

**DAO and rollup tests**
- [ ] Rollup idempotency: run the writer twice with the same events, assert identical rows.
- [ ] Downgrade refused: a COMPLETE day is not overwritten.
- [ ] Today never written: after any number of writer calls, assert no rollup row exists for today's date.
- [ ] Delete-then-insert per date: the transaction deletes before inserting so there are never two rows for the same `(date, package_name)`.

**Detector seam tests — 8.4**
- [ ] A window entirely before `cutoverDate` is evaluated against legacy and passes.
- [ ] A window entirely after `cutoverDate` with enough days passes.
- [ ] A window crossing the seam (`start < cutoverDate <= end`) is refused.
- [ ] Today is excluded from every detector window (O7).
- [ ] A midnight-crossing session is counted once per detector, on its start date.

### Notes

### Evidence

### Failures and blockers

### Decisions

---

## Batch 5.2 — `AllowanceLedger` (behavior-preserving)

**Status:** `NOT STARTED`  
**Started:** <!-- YYYY-MM-DD -->  
**Completed:** <!-- YYYY-MM-DD -->

### Tasks

**New class**
- [ ] `AllowanceLedger` is the sole owner of the `daily_allowance_used` JSON schema, the lock, day/window rollover, and the remaining/exhausted math.
- [ ] Ledger stores the five new fields from 7.1: `confirmedUsedMs`, `confirmedCount`, `confirmedAtMs`, `estimatedExtraMs`, `estimatedExtraOpens`.
- [ ] Old JSON (without new fields) is read as `confirmed = usedMs`, `extra = 0`. No data migration.
- [ ] In-memory cache with write-behind to prefs.
- [ ] All invariants from 7.1 are enforced: `confirmed*` monotonic per day or window; `estimated*` non-negative; effective equals confirmed plus estimated.

**Replace all callers**
- [ ] `isAllowanceAvailable` in `AppBlockerAccessibilityService` reads through the ledger.
- [ ] Unlock remaining calculation in `AppBlockerAccessibilityService` reads through the ledger.
- [ ] `isFallbackBlocked` in `ForegroundTaskService` reads through the ledger.
- [ ] `LauncherActivity.loadAllowanceCardData` reads through the ledger.
- [ ] `SettingsRepository` backup and reset access goes through the ledger.
- [ ] No direct pref read or write of `daily_allowance_used` remains outside the ledger.

**Cleanup**
- [ ] Contradictory comments in `AppBlockerAccessibilityService` and `ForegroundTaskService` about who owns the accounting are removed and replaced with a single comment pointing to `AllowanceLedger`.

**Verification**
- [ ] All 7.7 tests pass.
- [ ] Test 10 (backward compat) specifically passes against existing JSON from a device.
- [ ] `AppBlockerAccessibilityService.kt` line count is lower than before this batch.
- [ ] `ForegroundTaskService.kt` line count is lower than before this batch.
- [ ] No behavior change: a manual test of an active allowance before and after the refactor shows identical block timing.

### Notes

### Evidence

### Failures and blockers

### Decisions

---

## Batch 5.3 — Pipeline and rollup tables in shadow mode (behavior-preserving)

**Status:** `NOT STARTED`  
**Started:** <!-- YYYY-MM-DD -->  
**Completed:** <!-- YYYY-MM-DD -->

### Tasks

**`ForegroundSpanTracker`**
- [ ] Pure class with no Android imports: fed events in time order, emits `[start, end)` sessions.
- [ ] Implements all rules from section 5 of v6.
- [ ] Null and `SecurityException` from the adapter produce "unknown" output, not zero.
- [ ] No SDK version checks inside the class.
- [ ] Thin adapter over `UsageStatsRepository` handles all platform API calls.
- [ ] All pipeline tests from batch 5.1 pass against this implementation.

**Migration 6 → 7**
- [ ] Creates `usage_pipeline_state`, `usage_rollup_day`, `usage_rollup_app_day`, `usage_rollup_session` (section 6.2).
- [ ] `usage_pipeline_state` is seeded with `cutover_date = null`.
- [ ] Existing `daily_app_usage` and `app_sessions` rows are byte-for-byte unchanged after migration.
- [ ] Migration test with Room's migration test helper passes (build v6 DB, migrate, assert old rows unchanged).
- [ ] Exported schema JSON for version 7 committed to the repo.

**Rollup writer**
- [ ] Writes completed past days only — never today.
- [ ] One writer, one mutex, one transaction per date: delete then insert.
- [ ] Idempotency test passes (write twice, same rows).
- [ ] COMPLETE day is never downgraded.
- [ ] Triggers: app open, service start, day rollover, Stats open.

**Merged read model**
- [ ] `UsageHistoryRepository` (or equivalent) implemented.
- [ ] Shadow mode active: all reads return legacy for every date.
- [ ] Read-model precedence tests 1 and 10 (shadow mode and rollback) pass.

**Shadow comparison**
- [ ] Debug-only comparison log running alongside the old logic.
- [ ] Runs for at least 7 days.
- [ ] Per-app daily differences recorded in `Notes` for O6 decision.
- [ ] No behavior change: existing allowance, Stats, and detector output is identical to before.

### Notes
<!-- Shadow comparison results (fill after 7 days): -->

### Evidence

### Failures and blockers

### Decisions

---

## Batch 5.4 — Allowance cutover (behavior-changing: D1, D2, D9)

**Status:** `NOT STARTED`  
**Started:** <!-- YYYY-MM-DD -->  
**Completed:** <!-- YYYY-MM-DD -->

### Tasks

**Pipeline as measurement source**
- [ ] Time budget, count and interval readings come from the pipeline regardless of whether a block is active (D1). Enforcement stays gated on focus/standalone/always-on.
- [ ] While an allowance app is in the foreground: baseline from the pipeline, exact-expiry timer from effective value, tick re-read per O3.

**State machine and durable model (section 7)**
- [ ] FRESH / STALE / UNAVAILABLE state reducer implemented and tested.
- [ ] Accumulate rules for completed segments (7.3) implemented.
- [ ] Restart recovery with checkpoint cap (7.3) implemented.
- [ ] Reconcile rules on successful read (7.4) implemented.
- [ ] Midnight segment split at day boundary (7.3) implemented.
- [ ] All 7.7 tests pass against the new implementation.

**Deletions — accessibility service**
- [ ] Accumulator and checkpoint code deleted.
- [ ] `reconcileCountAllowances` deleted.

**Deletions — foreground service**
- [ ] 60 s allowance sync loop deleted.

**Deletions — both files**
- [ ] `daily_allowance_usage_stats_sync` (`PREF_USAGE_STATS_SYNC`) references removed from `AppBlockerAccessibilityService`.
- [ ] `daily_allowance_usage_stats_sync` references removed from `ForegroundTaskService` (lines 461 and 551 and any others found).

**Key handling**
- [ ] `active_session_pkg` kept.
- [ ] `active_session_last_checkpoint_ms` kept.
- [ ] `active_session_end_ms` kept (interval-mode restore path, accessibility line 2407).
- [ ] `active_session_open_at_ms` — grep both files confirms nothing reads it after the `AllowanceExpiry` interval-fallback path is replaced, then remove. If still needed, document why and defer removal.

**Cutover safety**
- [ ] First read on the cutover day: `confirmedUsedMs = max(existing usedMs, V)` so no user's usage resets mid-day.
- [ ] Apply O1 (interval-mode window start at first open regardless of block state).
- [ ] Apply O2 (bridge allow-list from 5.0 device matrix).

**Copy updates**
- [ ] `DailyAllowanceModal` text updated to say allowance counts all usage today, not just during blocks.
- [ ] `DailyAllowanceDefenseDialog` text updated.
- [ ] Launcher card updated to show "about" marker when not FRESH.

**Verification**
- [ ] `AppBlockerAccessibilityService.kt` line count is lower than before this batch.
- [ ] `ForegroundTaskService.kt` line count is lower than before this batch.
- [ ] No file has grown.
- [ ] Manual test: a time-budget allowance on a real device blocks at the correct moment.
- [ ] Manual test: kill the accessibility service mid-session; reopen; confirm usage estimate is within one checkpoint interval of the true value.
- [ ] Manual test: revoke usage access mid-session; confirm the app does not crash and keeps the last known value.
- [ ] Test on API 29 and 33.

### Notes

### Evidence

### Failures and blockers

### Decisions
<!-- active_session_open_at_ms — kept or removed? Record result here. -->

---

## Batch 5.5 — Stats, rollups and detectors cutover (behavior-changing: D3, D5–D8)

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
- [ ] Detectors apply seam rules from 8.4 (cross-seam window refused, today excluded).
- [ ] All detector seam tests from 5.1 pass.

**BackgroundFetchWorker ordering**
- [ ] In the same worker execution, rollup pass completes before `runAll()` is called.
- [ ] A comment in `BackgroundFetchWorker` explicitly marks the required ordering.

**Legacy writer**
- [ ] Old `AppUsageAndSessionTracker` Room writes stopped (or scheduled to stop after stabilization window O8).
- [ ] Existing legacy rows still readable through the merged read model.

**Stats surfaces**
- [ ] `DataHealthNotice` extended to report coverage: days complete, days partial, days missing.
- [ ] Dormant 3-month path still builds and `ThreeMonthRules` still works with the new `byHour` source.
- [ ] `PhoneUsageSummary.kt` deleted only if 5.0 confirmed zero callers. If deleted, record that here.

**Verification**
- [ ] `AppBlockerAccessibilityService.kt` line count is lower than before this batch.
- [ ] Stats Today hourly distribution is sane (no midnight pile) on API 29 and 33.
- [ ] Stats Week shows per-day chart populated from a single pipeline pass.
- [ ] Detector output is unchanged for a window entirely before `cutoverDate`.
- [ ] A cross-seam window returns "not enough consistent data" for all seven usage detectors.
- [ ] `DayRatingRepository.getRatableDates` returns the same dates as before for a day with task data only.
- [ ] Test on API 29, 31 and 33.

### Notes
<!-- PhoneUsageSummary.kt deleted? Record here. -->
<!-- Stabilization window end date: -->

### Evidence

### Failures and blockers

### Decisions

---

## Batch 5.6 — Allowance suggestion and detector verification

**Status:** `NOT STARTED`  
**Started:** <!-- YYYY-MM-DD -->  
**Completed:** <!-- YYYY-MM-DD -->

### Tasks

**Detector correctness across the seam**
- [ ] Run all seven usage detectors against a dataset that spans the seam. Confirm no finding is produced from a mixed-source window.
- [ ] Confirm inflated "infinite session" findings from the old pipeline no longer appear.
- [ ] Confirm no findings reappear due to changed evidence fingerprints (check `FindingRepository.submit` dedup in the log).
- [ ] Confirm a midnight-crossing session is counted once in the morning-hijack, variable-reward, and infinite-session detectors.

**Allowance suggestion alignment**
- [ ] The suggested allowance limit (daily average × 1.1, rounded to 5 min) is computed from pipeline data.
- [ ] The suggested limit is in the same units as enforcement: all-day usage (D1), not blocks-only.
- [ ] On a dataset with known ratings and usage, the suggested value matches a manual calculation.

**Escalating capture and substitution week boundaries**
- [ ] Confirm week 4 in `computeWeeklyAverages` is D-7 to D-1 (not D-6 to today) after the O7 fix.
- [ ] A rising trend that existed only because today's partial row inflated week 4 is no longer detected.

### Notes

### Evidence

### Failures and blockers

### Decisions

---

## Batch 5.7 — Version-gate cleanup and final cleanup (behavior-preserving)

**Status:** `NOT STARTED`  
**Started:** <!-- YYYY-MM-DD -->  
**Completed:** <!-- YYYY-MM-DD -->

### Tasks

**SDK gate cleanup (section 2.3 inventory)**
- [ ] All 51 pre-API-29 version checks collapsed to their always-true branch. Record the file-by-file count below.
- [ ] `MOVE_TO_FOREGROUND` and `MOVE_TO_BACKGROUND` branches removed from `UsageStatsRepository`, `ForegroundTaskService`, and `AppBlockerAccessibilityService`.
- [ ] No remaining `VERSION_CODES` reference at or below `Q` in any `.kt` file.
- [ ] Lint `NewApi` CI gate passes with zero findings.

**Permission check**
- [ ] `checkOpNoThrow` replaced with `unsafeCheckOpNoThrow` in `UsageStatsRepository.kt:67`.
- [ ] `checkOpNoThrow` replaced in `ForegroundTaskService.kt:379`.
- [ ] `checkOpNoThrow` replaced in `AppBlockerAccessibilityService.kt:2476`.
- [ ] No remaining `checkOpNoThrow` calls in any `.kt` file.

**Other dead code**
- [ ] Shadow comparison log removed.
- [ ] Any retired `AppUsageAndSessionTracker` accumulation code removed if fully replaced.
- [ ] Obsolete prefs keys removed, each with a comment or migration note explaining the upgrade path.

**Final matrix re-run** (all versions in section 3.3: API 29, 31, 33, 34, and latest)
- [ ] Foreground service starts at app open and after reboot.
- [ ] Notification card appears and returns after dismissal on API 33+.
- [ ] Allowance blocks at the correct moment.
- [ ] Stats totals are plausible.
- [ ] Locked-boot read does not crash or reset usage to zero.

**Line count report** (record in `Evidence`)
- [ ] `AppBlockerAccessibilityService.kt`: before / after
- [ ] `ForegroundTaskService.kt`: before / after
- [ ] Every new file: line count
- [ ] No new file exceeds 300 lines

**All review gates met (section 14 of v6)**
- [ ] `minSdk` = 29 in Gradle; zero `VERSION_CODES` checks at or below `Q`.
- [ ] Lint `NewApi` clean in CI.
- [ ] Every batch step labelled preserving or changing.
- [ ] Accessibility file and `ForegroundTaskService.kt` smaller than before every batch.
- [ ] No new file over 300 lines.
- [ ] One class interprets platform event types.
- [ ] One owner of the allowance JSON.
- [ ] One merged read model for history.
- [ ] Migration test passes; old rows unchanged after upgrade.
- [ ] Rollup writes idempotent, never downgrade a complete day, never store today.
- [ ] All 14 source-precedence tests (6.5) pass including the property test.
- [ ] All 11 allowance durability tests (7.7) pass.
- [ ] Midnight-crossing session is one row, one open, clipped daily time — in pipeline, rollups and every detector.
- [ ] Primary Stats screen, Extra view and dormant 3-month path all build and show sane numbers.
- [ ] All seven usage detectors have seam tests.
- [ ] 5.0 matrix re-run on all versions in 3.3 with measured deltas reported.

### Notes

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
| 5.0 Verify and measure | NOT STARTED | — |
| 5.1 Characterization and pipeline tests | NOT STARTED | — |
| 5.2 `AllowanceLedger` | NOT STARTED | — |
| 5.3 Shadow pipeline and rollups | NOT STARTED | — |
| 5.4 Allowance cutover | NOT STARTED | — |
| 5.5 Stats, rollups and detectors cutover | NOT STARTED | — |
| 5.6 Detector verification | NOT STARTED | — |
| 5.7 Cleanup and final matrix | NOT STARTED | — |

**Incomplete items and reasons** (reconcile with code before closing)
<!-- List any unchecked items here with the reason they were left incomplete. -->

**Deferred work**
<!-- Items that were explicitly moved to a future phase. -->