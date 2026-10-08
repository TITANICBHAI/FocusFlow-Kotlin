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

- Initial `bash scripts/test-unit.sh` attempt timed out after five minutes while the bootstrap installed JDK 17, Android SDK platform/build tools, and Gradle; no Gradle test task result was produced. The bootstrap is now cached; rerun after the Batch 3 implementation is coherent.
- Second `bash scripts/test-unit.sh` attempt reached KSP but failed at `:app:kspTbtechsdevDebugKotlin` with `IllegalStateException: Empty schema file` while Room deserialized an exported schema. No unit test task ran; inspect the schema output and fix the cause before rerunning.
- A cached retry with `--max-workers=1` passed KSP and compiled the production source and JVM tests, but the five-minute tool timeout expired after `:app:testProductionDebugUnitTest` started; task completion is not yet confirmed.

### Decisions

---

## Batch 1 — Test infrastructure and characterization (behavior-preserving)

**Status:** `IN PROGRESS`
**Started:** 2026-10-08
**Completed:** <!-- YYYY-MM-DD -->

**Scope:** Existing behavior only. No new production code. No tests for components that do  
not yet exist. Pipeline, read-model, rollup DAO, allowance state-machine, and detector seam  
tests each belong to the batch that introduces their production code (Batch 2–Batch 5).

### Tasks

**Test infrastructure**
- [x] JVM test source set exists and Gradle can run it.

**Characterization — existing allowance math (pins behavior before Batch 2 changes it)**
- [x] Interval-window math: start, expiry, remaining, boundary cases.
- [x] Day-rollover: existing JSON resets correctly at midnight.
- [x] Remaining/exhausted: correct result from the existing four read-side copies.
- [x] Backward-compat snapshot: reading the existing `daily_allowance_used` JSON format produces the expected values. This test must continue to pass after Batch 2.

**Verification**
- [x] All four characterization tests pass on the current code with no production changes.
- [ ] The test source set runs cleanly in CI.

### Notes

**Batch start (2026-10-08):** Existing `app/src/test` JVM source set and JUnit dependency are present, and `scripts/test-unit.sh` runs both flavor unit-test tasks through the bootstrap-backed path. Allowance calculations remain private and embedded in Android service/activity classes. Decision: keep production code and dependencies unchanged; characterize the existing implementations with JVM cases plus checks against their current source expressions. Existing `.replit` modification and attached prompt file are unrelated and will be preserved.

**Implementation and verification (2026-10-08):** Added `app/src/test/java/com/tbtechs/focusflow/enforcement/AllowanceBehaviorCharacterizationTest.kt` with four JVM tests: interval expiry/remaining boundaries; stale-day reset behavior; remaining/exhausted behavior across the four existing readers; and a legacy JSON snapshot for count, time-budget, and interval modes. The tests check live source expressions because those readers are private and Android-bound. No production source or dependencies changed. `bash scripts/test-unit.sh` passed both `:app:testProductionDebugUnitTest` and `:app:testTbtechsdevDebugUnitTest`; each variant ran 162 tests with zero failures, and the new test class ran four tests with zero failures in each. The test source set is configured in both GitHub workflow files, but GitHub Actions were not run; the CI execution checkbox remains open.

### Evidence

- **JVM test source set:** `app/src/test/java/com/tbtechs/focusflow/enforcement/AllowanceBehaviorCharacterizationTest.kt`, `app/build.gradle.kts`, and `scripts/test-unit.sh`; ran `bash scripts/test-unit.sh`, both flavor Gradle test tasks passed.
- **Interval-window math:** `AllowanceBehaviorCharacterizationTest.kt`; boundary cases before, at, and after expiry plus remaining-time clamps passed in both flavors.
- **Day rollover:** `AllowanceBehaviorCharacterizationTest.kt`; prior-day JSON values resolve to zero and all four current readers retain date guards; test passed in both flavors.
- **Remaining/exhausted copies:** `AllowanceBehaviorCharacterizationTest.kt`; source assertions cover `isAllowanceAvailable`, the unlock calculation, `isFallbackBlocked`, and `LauncherActivity.loadAllowanceCardData`; threshold examples passed in both flavors.
- **Backward-compatible JSON snapshot:** `AllowanceBehaviorCharacterizationTest.kt`; old count, time-budget, and interval fields parsed to their expected values without Phase 2 fields; test passed in both flavors.
- **Current-code characterization verification:** `app/src/test/java/com/tbtechs/focusflow/enforcement/AllowanceBehaviorCharacterizationTest.kt`; `bash scripts/test-unit.sh` passed both flavors (162 tests each, zero failures; characterization class 4/4 each), with no production-code changes.

### Failures and blockers

- Initial `bash scripts/test-unit.sh` failed at test compilation: `Files.readString` was unresolved in the Android unit-test classpath. Replaced it with `File.readText(Charsets.UTF_8)`; the corrected run compiled and passed both flavor tasks.
- Second `bash scripts/test-unit.sh` compiled the tests but had one characterization assertion failure: the remaining-expression excerpt deliberately stopped before the separate exhausted check. Retained the check against the complete AccessibilityService source instead; the third run passed both complete flavor suites with zero failures.
- `git diff --check` initially reported trailing whitespace on the edited Batch 1 status lines. Removed that whitespace; the final `git diff --check` passed.

### Decisions

- Keep characterization-only scope: no production helper extraction, Android test framework, or dependency changes. The relevant allowance methods are private and embedded in Android-bound service/activity classes; test examples are paired with source checks against the current formula implementations.
- GitHub Actions were not triggered. The user explicitly directed that Phase 5 must not start GitHub Actions or build APKs unless they explicitly ask; local Android tests are allowed. Both CI workflows include their respective unit-test tasks, but the CI-execution checkbox remains unchecked.
---

## Batch 2 — `AllowanceLedger` (behavior-preserving)

**Status:** `BLOCKED`
**Started:** 2026-10-08
**Completed:** <!-- YYYY-MM-DD -->

### Tasks

**New class**
- [x] `AllowanceLedger` is the sole owner of the `daily_allowance_used` JSON schema, the lock, day/window rollover, and the remaining/exhausted math.
- [x] Stores five new fields from 7.1: `confirmedUsedMs`, `confirmedCount`, `confirmedAtMs`, `estimatedExtraMs`, `estimatedExtraOpens`.
- [x] Old JSON (without new fields) reads as `confirmed = usedMs`, `extra = 0`. No data migration.
- [x] In-memory cache with write-behind to prefs.
- [x] All invariants from 7.1 enforced: `confirmed*` monotonic per day/window; `estimated*` non-negative; effective equals confirmed plus estimated.

**Replace all callers**
- [x] `isAllowanceAvailable` in `AppBlockerAccessibilityService` reads through the ledger.
- [x] Unlock remaining calculation in `AppBlockerAccessibilityService` reads through the ledger.
- [x] `isFallbackBlocked` in `ForegroundTaskService` reads through the ledger.
- [x] `LauncherActivity.loadAllowanceCardData` reads through the ledger.
- [x] `SettingsRepository` backup and reset access goes through the ledger.
- [x] No direct pref read or write of `daily_allowance_used` remains outside the ledger.

**Cleanup**
- [x] Contradictory comments about who owns allowance accounting removed and replaced with a single comment pointing to `AllowanceLedger`.

**Tests added in this batch**
- [x] Allowance compatibility/math test: old JSON (no new fields) reads as `confirmed = usedMs`, `extra = 0`; `usedMs` stays the effective value for old readers.
- [x] Existing interval/rollover/remaining characterization tests from Batch 1 still pass unchanged.

**Verification**
- [x] `AppBlockerAccessibilityService.kt` line count lower than before this batch.
- [x] `ForegroundTaskService.kt` line count lower than before this batch.
- [ ] Manual test: active allowance blocks at the same moment before and after the refactor.

### Notes

**Batch start (2026-10-08):** Baseline line counts: `AppBlockerAccessibilityService.kt` 4,802; `ForegroundTaskService.kt` 1,627. The `daily_allowance_used` JSON is read and mutated in the accessibility service, and read by the fallback service, launcher, and settings snapshot path. Preserve existing keys and behavior; no CI/APK run is authorized.

**Continuation audit (2026-10-08):** Working tree was clean. `AllowanceLedger.kt` and its persistence/math API already exist, but enforcement, launcher, and settings paths still read or write the JSON directly. Continuing Batch 2 by routing those paths through the ledger; no device verification or GitHub Actions run is authorized.

**Implementation update (2026-10-08):** Replaced direct allowance JSON access in `AppBlockerAccessibilityService`, `ForegroundTaskService`, `LauncherActivity`, and `SettingsRepository` with ledger operations. Added `AllowanceLedgerTest`; updated Batch 1 source assertions to verify delegation while retaining their behavior examples. Extracted the JSON codec, models, provider, and SharedPreferences adapter so every new ledger file stays below 300 lines. Kept confirmed timed usage monotonic for a same-day pre-midnight session.

**Final verification (2026-10-08):** `bash scripts/test-unit.sh` passed `:app:testProductionDebugUnitTest` and `:app:testTbtechsdevDebugUnitTest`. XML reports show 170 tests per flavor, 0 failures and 0 errors; `AllowanceBehaviorCharacterizationTest` passed 4/4 and `AllowanceLedgerTest` passed 8/8 in each. `git diff --check` passed. Final line counts: accessibility service 4,679 (baseline 4,802); foreground service 1,591 (baseline 1,627). New ledger production files are 21–299 lines; new ledger test is 239 lines. No APK build or GitHub Actions run.

**Final device availability check (2026-10-08):** `adb devices -l` returned no attached devices and the Android emulator executable is unavailable. The required active-allowance timing comparison cannot be run here; waiting for a connected device or emulator.

### Evidence

- **Ledger schema, lock, math, and callers:** `AllowanceLedger.kt`, `AllowanceLedgerJsonCodec.kt`, `AllowanceLedgerModels.kt`, `AllowanceLedgerProvider.kt`, `AllowanceLedgerStore.kt`, `AppBlockerAccessibilityService.kt`, `ForegroundTaskService.kt`, `LauncherActivity.kt`, and `SettingsRepository.kt`; `rg` confirmed the key is defined and read/written only in the ledger and its adapter. No `ALLOWANCE_USAGE_LOCK`, `loadUsedObject`, duplicate key, or duplicate parser remains.
- **Additive state and legacy compatibility:** `AllowanceLedgerJsonCodec.kt` and `AllowanceLedgerTest.kt`; Production and Tbtechsdev each passed the old-JSON, effective-field, clamping, and arithmetic tests; legacy JSON is not rewritten on read.
- **Cache/write-behind and invariants:** `AllowanceLedger.kt`, `AllowanceLedgerStore.kt`, and `AllowanceLedgerTest.kt`; both flavor suites passed cache, reset, monotonic-update, non-negative estimate, effective-sum, midnight recovery, and rollover cases.
- **Accessibility availability and unlock remaining:** `AppBlockerAccessibilityService.kt` and `AllowanceBehaviorCharacterizationTest.kt`; both flavor suites passed the interval boundary, daily rollover, remaining, exhausted, and ledger-delegation cases.
- **Fallback enforcement and UsageStats writes:** `ForegroundTaskService.kt` and `AllowanceBehaviorCharacterizationTest.kt`; both flavor suites passed; fallback checks and sync/expiry writes use the ledger.
- **Launcher allowance card:** `LauncherActivity.kt` and `AllowanceBehaviorCharacterizationTest.kt`; both flavor suites passed; count, daily time, and interval card reads use the ledger.
- **Settings backup and reset:** `SettingsRepository.kt`; code inspection confirms snapshot parsing and reset mutation are delegated to the ledger; the common ledger unit tests passed in both flavors.
- **No direct preference access outside the ledger:** app-source search found only the centralized key plus the launcher preference-change listener; the only matching `SharedPreferences.putString` is the ledger store adapter. Search result and `git diff --check` were clean.
- **Owner comments:** `AppBlockerAccessibilityService.kt` retains the single ownership note pointing to `AllowanceLedger`; obsolete AccessibilityService-only ownership comments were removed.
- **Characterization suite:** `AllowanceBehaviorCharacterizationTest.kt`; XML reports show 4/4 passed in each flavor. Behavior examples were retained; source-wiring assertions now check ledger delegation rather than the removed duplicate formulas.
- **Line-count reduction:** `AppBlockerAccessibilityService.kt` 4,802 → 4,679 and `ForegroundTaskService.kt` 1,627 → 1,591, measured with `wc -l`.
- **Batch unit tests:** `AllowanceLedgerTest.kt` and both flavor XML reports; 8/8 ledger tests and 4/4 characterization tests passed in Production and Tbtechsdev, within 170 total tests per flavor.

### Failures and blockers

- First `bash scripts/test-unit.sh` attempt timed out while the bootstrap installed JDK 17, Android SDK packages, and Gradle; Gradle had just started and no test result was produced. Re-run after bootstrap is cached.
- Second `bash scripts/test-unit.sh` attempt also timed out before tests started; cached JDK/SDK setup completed and Gradle reached resource processing. Re-run asynchronously to capture the full test result.
- The asynchronous compile reached both flavors and found three invalid `return@Runnable` exits from inside the non-inline ledger-lock lambda in `ForegroundTaskService.scheduleAllowanceExpiry`. Reworked the locked block to return a Boolean and handle cancellation at the Runnable boundary; rerun is required.
- **Outcome:** The corrected async run passed both variants before the JSON-codec extraction; the final fresh run also passed both variants after the extraction and additional invariant tests (evidence above).
- **Still open / blocker:** Manual active-allowance timing comparison was not run because no Android device is attached and no emulator executable is available. Leave the manual checkbox open; no device result is claimed.

### Decisions

- Keep only `AllowanceLedger` as the caller-facing owner; the internal codec only translates its JSON schema, while the provider and SharedPreferences store contain no allowance decisions.
- Preserve the Batch 1 behavior examples and retarget source assertions from the removed duplicated formulas to the ledger delegation; the old source assertions could not pass after consolidating the implementation.
- No data migration: old JSON is interpreted lazily as confirmed usage with zero estimate and remains byte-for-byte untouched until an existing write/reset operation.

---

## Batch 3 — Pipeline and rollup tables in shadow mode (behavior-preserving)

**Status:** `IN PROGRESS`  
**Started:** 2026-10-08  
**Completed:** <!-- YYYY-MM-DD -->

### Tasks

**`ForegroundSpanTracker`**
- [x] Pure class with no Android imports: fed events in time order, emits `[start, end)` sessions.
- [x] Implements all rules from section 5 of v6. Replicates existing tracker's midnight behavior: session on start date, full duration, time clipped via `splitIntoHourlySegments`.
- [x] Null and `SecurityException` from the adapter produce "unknown", not zero.
- [x] No SDK version checks inside the class.
- [x] Thin adapter over `UsageStatsRepository` handles all platform API calls.

**Migration 6 → 7**
- [x] Creates `usage_pipeline_state`, `usage_rollup_day`, `usage_rollup_app_day`, `usage_rollup_session` (section 6.2).
- [x] `usage_pipeline_state` seeded with `cutover_date = null`.
- [ ] Migration test: build a v6 DB with rows in `daily_app_usage` and `app_sessions`, migrate, assert every old row is byte-for-byte unchanged.
- [x] Exported schema JSON for version 7 committed.

**Rollup writer**
- [x] Writes completed past days only — never today.
- [x] One writer, one mutex, one transaction per date: delete then insert.
- [ ] COMPLETE day never downgraded.
- [x] Triggers: app open, service start, day rollover, Stats open.

**Merged read model**
- [x] `UsageHistoryRepository` (or equivalent) implemented.
- [x] Shadow mode: all reads return legacy for every date; rollup tables never served.

**Pipeline tests (introduced here because `ForegroundSpanTracker` is introduced here)**
- [x] Missing `ACTIVITY_PAUSED` — session closed by the next `RESUMED`.
- [x] `ACTIVITY_STOPPED` only — session closed by stop event.
- [x] Screen off (`SCREEN_NON_INTERACTIVE`) — open session closed.
- [x] Keyguard shown — open session closed.
- [x] `DEVICE_SHUTDOWN` — all open sessions closed; no explicit `STOPPED` events needed.
- [x] Startup — sessions begin from `DEVICE_STARTUP`.
- [x] Midnight crossing — one session row, clipped daily time on each day, session count 1 on start day and 0 on the next.
- [x] DST day (23 h and 25 h) — boundary is calendar arithmetic, not 86 400 000 ms.
- [x] Duplicate and out-of-order `ACTIVITY_RESUMED` — no duplicate open sessions.
- [x] App already open at window start — clipped to window start only.
- [x] Tail cap — open session capped at 4 h; measured session never capped.
- [ ] Null from `queryEvents` — last known value kept, not zero.
- [x] `SecurityException` — same as null.

**Read-model precedence tests (introduced here because the read model is introduced here; section 6.5)**
- [x] Test 1: shadow mode — every date including today from legacy; rollup tables untouched.
- [ ] Test 2: cutover — today in Group A is live; no rollup row exists for today after repeated loads.
- [x] Test 3: cutover — detectors exclude today; rating eligibility includes today when live pipeline has a session.
- [x] Test 4: `date < cutoverDate` with a complete shadow rollup and legacy rows — legacy wins in both groups.
- [x] Test 5: `date >= cutoverDate` with legacy rows and no rollup — pipeline wins; legacy ignored.
- [ ] Test 6: yesterday not yet rolled up — on-demand result equals the rollup written later.
- [x] Test 7: invariance — `live(D)` at 23:59 equals `rollup(D)` written after midnight for the same event log.
- [x] Test 8: PARTIAL day — Group A shows it with flag; Group B treats it as missing.
- [x] Test 9: events unavailable for today — Group A shows unknown, not zero.
- [x] Test 10: rollback — `cutoverDate` set to null returns legacy for all dates.
- [x] Test 11: property test — no date served from two sources for any combination of mode, date and row presence.
- [x] Test 12: seam predicate `start < cutoverDate <= end` including boundary dates.
- [x] Test 13: midnight — one session row on D (30 min), 10 min daily on D, 20 min on D+1, session count 1 on D and 0 on D+1.
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

**Batch start (2026-10-08):** User authorized Batch 3 while Batch 2 remains blocked only on its real-device comparison. The existing Batch 2 working-tree changes and supplied Batch 3 note are preserved. This batch must stay shadow-only: no existing read source is switched, no old usage rows are rewritten, and the 7-day comparison cannot be marked complete before observations exist. Batch 0's device measurements are still unavailable; choose and record the `STOPPED` matching strategy before implementing the tracker.

**Implementation resumed (2026-10-08):** Rechecked the working tree; only the two supplied prompt files are untracked. Room remains at v6 and its migration chain ends at 5→6. Batch 3 source wiring and tests will be added without switching any current read source. Device matrix, retention, latency, and query-cost measurements remain unavailable.

**Pipeline implementation update (2026-10-08):** Added the pure event/session models, foreground-span reducer, local-calendar aggregator, a UsageStats event adapter, and initial JVM coverage for stop matching, closures, lookback, midnight, DST, duplicate events, and tail capping. These changes have not yet been compiled or tested; all Batch 3 checkboxes remain open.

**Storage and trigger implementation update (2026-10-08):** Added the four additive Room entities, DAO, v6→v7 migration with a null cutover state seed, a serialized past-days rollup writer, debug-only per-app shadow comparisons, and event-driven app-open/service-start/Stats/calendar triggers. Existing usage readers and legacy rows are not routed or rewritten. Compilation, migration validation, and writer behavior remain unverified.

**Read model and test update (2026-10-08):** Added the shadow-only legacy repository boundary and source-precedence policy, including all 14 planned policy cases. Added instrumentation coverage for v6→v7, the version-0 migration path, rollup idempotency, completion preservation, today exclusion, replacement, and open-session completion; tests are not yet verified.

**Revalidation and test-fixture correction (2026-10-08):** Audited the current implementation and confirmed Room schema version 7, the 6→7 migration registration, and shadow-only read wiring. Corrected writer instrumentation fixtures that lacked an event before local midnight, expanded migration assertions to cover every legacy usage/session field, and added a focused `user_version = 0` pre-migration test. Corrected two existing JVM test assertions. Both flavor unit suites pass (195 tests each), and both AndroidTest Kotlin compile tasks pass; no instrumentation APK/device run is being attempted.

**Adapter unknown-result test seam (2026-10-08):** Extracted the null/`SecurityException` conversion into a pure boundary used by `UsageStatsRepository` and added JVM cases for null, revoked access, and an available read. Both flavor suites pass with 198 tests each, and both AndroidTest Kotlin compile tasks pass.

<!-- Shadow comparison results (fill after 7 days): -->

### Evidence

- **Pipeline and calendar behavior:** `ForegroundSpanTracker.kt`, `UsageCalendarAggregator.kt`, and `ForegroundSpanTrackerTest.kt`; `bash scripts/test-unit.sh` passed in Production and Tbtechsdev, with 11/11 tracker tests in each flavor covering transitions, closures, lookback, midnight, DST, ordering, and tail cap.
- **Read-model policy:** `UsageHistoryRepository.kt`, `UsageHistorySourcePolicy.kt`, and `UsageHistorySourcePolicyTest.kt`; both flavors passed 14/14 policy tests, including shadow source, cutover rules, PARTIAL/UNKNOWN handling, rollback, seam boundaries, and midnight aggregation.
- **Adapter unknown handling:** `UsageEventReadBoundary.kt`, `UsageStatsRepository.kt`, and `UsageEventReadBoundaryTest.kt`; both flavors passed 3/3 cases for null→unknown, `SecurityException`→unknown, and preserving an available read.
- **Full JVM suites:** `bash scripts/test-unit.sh`; Production and Tbtechsdev each passed 198 tests with zero failures and zero errors. The script sets `FOCUSFLOW_SKIP_APK_BUILD=1`; no APK was built.
- **Instrumentation-source compilation:** `FocusFlowDatabaseMigrationTest.kt` and `UsageRollupWriterInstrumentedTest.kt`; ran `bash scripts/test-unit.sh :app:compileProductionDebugAndroidTestKotlin :app:compileTbtechsdevDebugAndroidTestKotlin`; both AndroidTest Kotlin compile tasks and both JVM test tasks passed. This did not run instrumentation tests or build an APK.
- **Schema and migration wiring:** `FocusFlowDatabase.kt`, `AppModule.kt`, and `app/schemas/com.tbtechs.focusflow.data.local.FocusFlowDatabase/7.json`; source/schema inspection confirmed Room version 7, the registered 6→7 migration, the four additive tables, and the null cutover seed.
- **Shadow-only wiring and triggers:** `UsageHistoryRepository.kt`, `UsageRollupWriter.kt`, `UsageRollupCoordinator.kt`, `FocusFlowApp.kt`, `AppBlockerAccessibilityService.kt`, and `StatsViewModel.kt`; source inspection confirmed legacy-only history reads and app-open, service-start, calendar-change/day-rollover, and Stats-open triggers.
- `git diff --check` passed.

### Failures and blockers

- First configured `bash scripts/test-unit.sh` run compiled both production flavors but failed during `:app:testProductionDebugUnitTest` (195 tests, 2 failures): `ForegroundSpanTrackerTest.midnightSessionHasOneStartDateAndClippedDailyTime` included hour 0 in its “hours 1–23 are empty” assertion, and `UsageHistorySourcePolicyTest.shadowModeKeepsCurrentStatsAndLegacyHistory` called the CUTOVER policy while expecting SHADOW behavior. Fixed both test calls/assertions. No Batch 3 production failure was observed in this run.
- **Outcome:** The final corrected run passed all 198 JVM tests in both flavors (zero failures/errors), and both AndroidTest Kotlin compile tasks passed. The corrected failure details above are retained as history.
- **Device blocker:** `adb devices -l` listed no connected devices and the Android emulator executable is unavailable. Instrumentation tests and the device matrix were not run; no APK was built on Replit.
- **Shadow comparison blocker:** The required seven-day per-app comparison has not elapsed or produced observations. Keep the comparison and O6 tolerance items open.

### Decisions

- Match `ACTIVITY_STOPPED` only against the exact resumed activity class. A delayed stop from activity A must not close a newer session for activity B in the same package; if the stop event lacks a class name, do not close based on package alone. Device validation remains open.

---

## Batch 4 — Allowance cutover (behavior-changing: D1, D2, D9)

**Status:** `IN PROGRESS`  
**Started:** 2026-10-08  
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

**Batch start (2026-10-08):** The owner explicitly authorized Batch 4. Re-read the Phase 5 contract and tracker, checked the working tree, and inspected the current allowance ledger, accessibility accounting/recovery, foreground-service UsageStats sync/fallback, usage-event pipeline adapter, and allowance UI. The only pre-existing worktree change is the user-supplied Batch 4 prompt. Current source baselines: `AppBlockerAccessibilityService.kt` 4,680 lines; `ForegroundTaskService.kt` 1,591 lines. Batch 0's device matrix is unavailable; use the documented O2 default bridge set and leave device confirmation open. No Android device/emulator is known available from the preceding batch.

**Implementation checkpoint (2026-10-08):** Added the FRESH/STALE/UNAVAILABLE reducer, shared-session measurement with a narrow 3-second bridge for the documented Android permission-controller/intent-resolver packages, monotonic ledger reconciliation, estimates, interval initialization, and capped checkpoint recovery. Added initial pure tests. These are foundations only: the services are not wired to the coordinator yet, no Batch 4 item is verified, and all checkboxes remain open. Midnight clipping and service integration are still being completed.

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
| Batch 1 Test infra and characterization | IN PROGRESS | — |
| Batch 2 `AllowanceLedger` | BLOCKED | — |
| Batch 3 Shadow pipeline and rollups | IN PROGRESS | — |
| Batch 4 Allowance cutover | NOT STARTED | — |
| Batch 5 Stats, rollups and detectors cutover | NOT STARTED | — |
| Batch 6 Detector verification | NOT STARTED | — |
| Batch 7 Cleanup and final matrix | NOT STARTED | — |

**Incomplete items and reasons** (reconcile with code before closing)
- Batch 1 CI execution remains unchecked: the local Production and Tbtechsdev test tasks passed, and both CI workflow configs contain the matching test task, but GitHub Actions were not run because the user did not request APK-building verification.
- Batch 2 is blocked on its unchecked manual timing comparison: `adb devices -l` was empty and the emulator executable is unavailable.
- Batch 3 remains in progress: migration/writer instrumentation execution, read-model cases 2/6/14, DAO execution checks, preservation of the last known value on null event reads, the device matrix, and the seven-day shadow comparison are still open. The null/`SecurityException`-to-unknown behavior is verified by JVM tests.

**Deferred work**
<!-- Items explicitly moved to a future phase. -->