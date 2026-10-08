# Phase 5 Usage and Allowance Work Tracker

## Current status

- **Overall:** Phase 5.0 in progress; source audit is complete, lint-gate work is authorized, and device measurements remain open.
- **Authorization:** The user authorized the full Batch 5.0 scope, including adding and running the NewApi CI gate. This does not authorize later implementation batches or GitHub Actions runs.
- **Plan authority:** [`PLAN.md`](PLAN.md) is v6 and replaces v1–v5.
- **Last updated:** 2026-10-08

Read [`PRE_PROMPT.md`](PRE_PROMPT.md), [`PLAN.md`](PLAN.md), and this tracker in that order before any future work.

## Tracking rules

- At the start of each batch, mark it in progress in its notes. Update the relevant notes after each meaningful change.
- Tick checkboxes only when the work is complete and verified. Leave partial, attempted, or unverified work unchecked.
- For every completed batch, record changed files, the exact verification performed, and its result.
- Record decisions, failed checks, fixes and successful reruns, blockers, and deferred work. Do not erase earlier failures or blockers.
- Preserve unrelated changes and user data. Follow the plan's no-touch list and implementation order.
- Replacement of tasks or any other data deletion is destructive: keep replacement disabled by default, require explicit user authorization, and retain the plan's confirmation and safety guards.
- Use the current Replit workflow or scripts under `scripts/` for local Gradle/Android verification. Do not push or start, poll, or monitor GitHub Actions unless explicitly requested. If checks were not run, say so.

## Batch 0 — Plan organization and setup

**Status:** Complete — documentation only; no app implementation performed.

- [x] Read the supplied Phase 5 plan before moving or renaming it.
- [x] Place and rename the v6 plan as `allowance/PLAN.md`.
- [x] Add this pre-prompt and a tickable tracker tied to the plan's implementation sequence.
- [x] Confirm implementation work has not been started or marked complete.

**Evidence / notes**

- Changed files: `allowance/PLAN.md` (moved and renamed from the supplied attachment), `allowance/PRE_PROMPT.md`, and `allowance/TRACKER.md`.
- Verification: compared the supplied plan's pre-move SHA-256 (`846be531958c83581d154606397852e502b2f2c62d288d5d514d936fe47e0ce6`) with `allowance/PLAN.md`; checked the pre-prompt's document links and required rules, all phase headings from 5.0 to 5.7, nonempty files, trailing whitespace, source-path removal, and working-tree status.
- Result: passed. The hashes match, the supplied path no longer exists, all three files are present, required phase links/sections are present, and no trailing whitespace was found.
- Scope: no Android source, tests, workflows, or GitHub Actions changed or run.

## Batch 5.0 — Verify and measure

**Status:** In progress — source review complete; NewApi lint-gate work underway; device/performance measurements remain open.

Plan phase: **5.0 — Verify and measure (read-only plus device testing)**

- [x] Confirm and report Gradle `minSdk`, `compileSdk`, and `targetSdk`; raise `minSdk` to 29 only if it is lower and implementation is authorized.
- [ ] Add/run the Android Lint `NewApi` CI gate as scoped by the plan; record findings.
- [x] Map all writers and readers of `daily_allowance_used`, `daily_app_usage`, and `app_sessions`, including the plan's named consumers.
- [x] Map `FindingDetectionRunner.runAll` invocation and `FindingRepository.submit` deduplication behavior.
- [x] Verify legacy cross-midnight session dating and all `ACTIVE_SESSION_*` key reads/writes.
- [ ] Run the plan's device matrix for supported Android versions and scenarios where devices/emulators are available.
- [ ] Measure event latency, event retention, query cost, and current Stats Today/Week behavior.
- [ ] Record measured differences, unknowns, and owner decisions/tolerances.

**Initial notes**

- The initial read-only source audit is complete. The user has now authorized the full Batch 5.0 scope, including the NewApi CI gate. Keep later implementation batches and GitHub Actions runs out of scope.
- Follow plan sections 2–3, 5.0, 7.1, and 11; record unavailable devices and unverified claims explicitly.

**Evidence / notes**

- Changed files for Batch 5.0: `app/build.gradle.kts`, `.github/workflows/build-native-kotlin-apk.yml`, `.github/workflows/build-tbtechsdev-apk.yml`, and `allowance/TRACKER.md`. No app runtime source or tests changed.
- Exact checks and results:
  - Read `app/build.gradle.kts`: `minSdk = 29`, `compileSdk = 35`, `targetSdk = 35`; no SDK-floor change is needed.
  - Searched current Kotlin sources for `daily_allowance_used`, `daily_app_usage`, `app_sessions`, and the active-session/sync keys; inspected the service, DAO, repository, launcher, and tracker paths.
  - `AppUsageAndSessionTracker` writes launch counts at foreground transitions and clipped daily/hourly time through `DailyAppUsageDao`; closed sessions use their start date and full duration, but durations outside 1 s–4 h are not inserted. The device time path and stored session path therefore have a 4 h boundary to characterize.
  - `ForegroundTaskService` runs a 60 s UsageStats allowance sync and writes the shared allowance JSON; `AppBlockerAccessibilityService` also reads/writes it and owns the 15 s timed-session checkpoint path. `SettingsRepository` snapshots/parses and can reset it; `LauncherActivity` reads it for allowance cards.
  - Room is version 6. The existing usage tables are created by migration 5→6; the daily-usage DAO accumulates/replaces rows and both tables are pruned after 90 days in `BackgroundFetchWorker`.
  - `FindingDetectionRunner.runAll()` is called by the worker's once-daily finding pass. Its date end is today. `FindingRepository.submit()` looks up by detection type and subject; fingerprint changes can resurface an existing active/intentional finding subject to suppression/cooldown rules.
  - `DayRatingRepository.getRatableDates()` includes today from legacy usage rows. Stats health counts legacy usage dates. `AnalyticsProcessor` reads a summary and an `INTERVAL_BEST` hourly bucket; Week adds seven per-day summaries. The current Week path makes eight `queryEvents` scans plus one `queryUsageStats(INTERVAL_BEST)` query by source inspection, not runtime measurement. The phone-usage metrics builder returns null if the hourly read is unavailable.
  - `BackupSerializer` includes portable settings and tasks, not Room usage tables. Allowance configuration is portable; allowance usage counters are not part of that envelope.
  - Android Lint is configured with `abortOnError = false`; no `NewApi` lint gate was found in the inspected Gradle/CI/scripts. Two APK-build workflows exist; neither was run.
  - Changed lint configuration to run only `NewApi`, treat it as an error, and abort on findings; added the production and tbtechsdev lint tasks to their existing APK workflows.
  - Local lint verification attempted with `FOCUSFLOW_SKIP_APK_BUILD=1 bash scripts/build-apk-with-java.sh :app:lintProductionDebug :app:lintTbtechsdevDebug`. JDK/SDK bootstrap completed, but the command timed out during `:app:kspProductionDebugKotlin`, before either lint task reported a result. No lint report was produced and no Gradle process remained. The lint checklist stays unchecked.
- Decisions, blockers, and deferred checks:
  - No tests or device checks were run during the initial source-only review.
  - No `adb` or emulator executable is available in this workspace, and no physical device was provided. Device-matrix behavior, event latency, OS event-retention edge, query cost, and actual Stats output remain unmeasured.
  - GitHub Actions were not started, polled, or monitored.
  - Code inspection confirms the current Stats call pattern, but does not substitute for the requested device comparison against Digital Wellbeing or allowance enforcement. Owner tolerances remain unset.
  - In `UsageStatsRepository`, `queryEvents` is consumed without a visible null guard; the current summary path can fail rather than distinguish an unavailable event stream from a successful zero result. Device/locked-boot behavior remains unverified.
  - Current-source mismatch to account for in later planning: the existing session tracker rejects closed session rows over 4 h, while still accumulating daily foreground time; confirm this is the intended legacy baseline before defining pipeline parity.

## Batch 5.1 — Characterization and pipeline tests

**Status:** Not started.

Plan phase: **5.1 — Characterization and pipeline tests (behavior-preserving)**

- [ ] Establish the JVM test source set and characterize existing pure behavior before cutover.
- [ ] Add pipeline tests for every applicable rule in plan section 5, including event endings, midnight/DST, ordering, lookback, tail cap, and unknown input.
- [ ] Add all 14 read-model precedence tests in plan section 6.5.
- [ ] Add all 11 allowance durability tests in plan section 7.7.
- [ ] Add rollup DAO idempotency, complete-day protection, today-exclusion, and per-date replacement tests.
- [ ] Add detector seam tests from plan section 8.4.

**Initial notes**

- Keep pipeline logic pure and JVM-testable; test behavior before any cutover.

**Evidence / notes**

- Changed files:
- Exact checks and results:
- Failures, fixes, decisions, and deferred work:

## Batch 5.2 — AllowanceLedger

**Status:** Not started.

Plan phase: **5.2 — `AllowanceLedger` (behavior-preserving)**

- [ ] Make `AllowanceLedger` the sole owner of the existing `daily_allowance_used` JSON schema and its additive fields.
- [ ] Centralize locking, day/window rollover, remaining/exhausted calculations, and the live-session marker.
- [ ] Replace the plan-listed duplicated readers and route `SettingsRepository` access and launcher-card reads through the ledger.
- [ ] Preserve existing JSON compatibility: old objects map to confirmed values with zero estimated extras.
- [ ] Use the planned in-memory authoritative cache and write-behind behavior; correct contradictory comments.
- [ ] Verify the batch remains behavior-preserving.

**Initial notes**

- Do not add preference keys; new model fields remain inside the existing allowance JSON.

**Evidence / notes**

- Changed files:
- Exact checks and results:
- Failures, fixes, decisions, and deferred work:

## Batch 5.3 — Pipeline and rollup tables in shadow mode

**Status:** Not started.

Plan phase: **5.3 — Pipeline and rollup tables in shadow mode (behavior-preserving)**

- [ ] Implement the pure foreground-span tracker and thin OS adapter with null and `SecurityException` handling.
- [ ] Add only the specified additive Room migration and four rollup/state tables; preserve all existing usage tables and rows.
- [ ] Implement rollup writing for completed past days only; never store today.
- [ ] Implement the merged read model and keep Group B history reads on legacy data in shadow mode.
- [ ] Run the pipeline alongside existing logic and record debug-only per-app daily comparisons for at least seven days.
- [ ] Verify the migration against populated version-6 data and the legacy `user_version = 0` path; preserve/export the required schema.

**Initial notes**

- Follow the plan's one-writer, one-transaction-per-date, idempotency, completeness, and no-downgrade rules.
- No usage source cutover occurs in this batch.

**Evidence / notes**

- Changed files:
- Exact checks and results:
- Shadow comparison period and findings:
- Failures, fixes, decisions, and deferred work:

## Batch 5.4 — Allowance cutover

**Status:** Not started.

Plan phase: **5.4 — Allowance cutover (behavior-changing: D1, D2, D9)**

- [ ] Route allowance readings through the pipeline independently of block state; preserve enforcement gates and exact-expiry behavior.
- [ ] Implement FRESH, STALE, and UNAVAILABLE handling plus durable estimates, accumulation, restart recovery, and reconcile rules.
- [ ] Replace the 60-second sync with only the plan-approved foreground tick and recomputations.
- [ ] Remove the specified accessibility accumulator/checkpoint and count-reconciliation code only after verifying current callers.
- [ ] Remove the usage-stats sync key from both named services; preserve the three required active-session keys.
- [ ] Before removing `active_session_open_at_ms`, verify both named services no longer read it after interval fallback replacement.
- [ ] Preserve the cutover-day baseline with `max(existing usedMs, pipeline value)`.
- [ ] Apply O1/O2 only as specified or record explicit owner changes.
- [ ] Update allowance UI copy to say usage counts all day and show the planned approximate marker when not FRESH.
- [ ] Verify behavior-changing copy, release notes, tests, and service-file line-count direction.

**Initial notes**

- This batch changes enforcement inputs. Do not begin without explicit user authorization and completion of required preceding batches.
- Preserve existing confirmation and safety guards; never let unavailable usage reset the allowance to zero.

**Evidence / notes**

- Changed files:
- Exact checks and results:
- Data-safety review:
- Failures, fixes, decisions, and deferred work:

## Batch 5.5 — Stats, rollups, and detector cutover

**Status:** Not started.

Plan phase: **5.5 — Stats, rollups and detectors cutover (behavior-changing: D3, D5–D8)**

- [ ] Use one `DeviceUsageSource` pass for summary, hourly, and per-day Week values; remove the `INTERVAL_BEST` and seven-scan paths.
- [ ] Establish `cutoverDate` and implement Group B source precedence without ever combining sources for the same date.
- [ ] Exclude today from detector windows; use live-pipeline today data for rating eligibility and data-health counts as specified.
- [ ] Apply detector seam rules and the completed-days-only contract.
- [ ] Ensure `BackgroundFetchWorker` completes rollup writing before `runAll()` in the same worker execution.
- [ ] Keep the legacy writer for the 14-day stabilization window, then stop it only after the window.
- [ ] Extend the existing data-health notice for complete, partial, and missing coverage.
- [ ] Verify the dormant three-month path builds and uses the new `byHour` source.
- [ ] Delete `PhoneUsageSummary.kt` only if Batch 5.0 confirms it is unused.
- [ ] Verify behavior-changing copy/release notes, source-precedence tests, and service-file line-count direction.

**Initial notes**

- Rollback before stabilization ends restores legacy reads; document the history gap risk after the window.
- Strict detector seam behavior may temporarily silence trends; communicate this before release.

**Evidence / notes**

- Changed files:
- Exact checks and results:
- Source-precedence and ordering checks:
- Failures, fixes, decisions, and deferred work:

## Batch 5.6 — Allowance suggestion and detector verification

**Status:** Not started.

Plan phase: **5.6 — Allowance suggestion and detector verification**

- [ ] Run all seven usage detectors against recorded data on both sides of the seam.
- [ ] Verify no findings use windows crossing the seam.
- [ ] Verify expected disappearance of inflated infinite-session findings and check for duplicate findings from changed fingerprints.
- [ ] Verify a midnight-crossing session is counted once and the suggested allowance uses enforcement units.

**Initial notes**

- Record data fixtures, detector outputs, and which results are expected changes versus regressions.

**Evidence / notes**

- Changed files:
- Exact checks and results:
- Detector data/results:
- Failures, fixes, decisions, and deferred work:

## Batch 5.7 — Version-gate cleanup and final cleanup

**Status:** Not started.

Plan phase: **5.7 — Version-gate cleanup (behavior-preserving)**

- [ ] Collapse every pre-API-29 version check from the plan inventory while preserving non-version condition parts.
- [ ] Remove the deprecated `MOVE_TO_FOREGROUND` / `MOVE_TO_BACKGROUND` branches in the three specified files.
- [ ] Replace all three `checkOpNoThrow` calls with `unsafeCheckOpNoThrow`.
- [ ] Remove dead code, shadow logging, and obsolete keys only after checking upgrade behavior and remaining callers.
- [ ] Re-run the plan's Android version matrix and report unavailable cases.
- [ ] Report line counts for every touched and new source file; confirm both large service files shrink in each touching step and no new file exceeds 300 lines.
- [ ] Verify Android Lint `NewApi` and all plan acceptance/review gates that can be run in the available environment.

**Initial notes**

- Keep API checks for Android R (30), S (31), Tiramisu (33), 34, and 35 as specified; only collapse checks at or below API 29.
- Android 15 / `targetSdk` 35 foreground-service work remains parked and is not part of this batch.

**Evidence / notes**

- Changed files:
- Exact checks and results:
- Per-file line counts:
- Version/device matrix results and unavailable cases:
- Failures, fixes, decisions, and deferred work:

## Final reconciliation and handoff

**Status:** Not started.

- [ ] Reconcile every plan review gate and acceptance item against current code and observed evidence.
- [ ] Confirm all completed work is ticked and all incomplete, unverified, or blocked work remains unchecked.
- [ ] Record changed files, exact local checks, results, failures/fixes, decisions, and deferred work.
- [ ] State whether APK/device checks or GitHub Actions were not run and why.
- [ ] Confirm no unrequested GitHub push or Actions run occurred.

**Evidence / notes**

- Reconciliation:
- Remaining blockers/deferred work:
- Final verification:

## Session notes

| Date | Scope | Evidence, decisions, blockers, or deferred work |
|---|---|---|
| 2026-10-08 | Read plan and set up allowance documentation | Read the supplied v6 plan before moving it. Added the pre-prompt and this phased tracker. No app-code implementation or tests were performed; implementation remains unstarted and requires explicit authorization. |
| 2026-10-08 | Phase 5.0 read-only source audit | User authorized source review only. Confirmed SDK values, mapped legacy allowance/usage readers and writers, detector invocation/deduplication, session-date behavior, active-session keys, Stats query shape, and backup contents. No app code, CI, tests, or GitHub Actions changed or run. Device measurements and owner tolerances remain open; see Batch 5.0 evidence. |
| 2026-10-08 | Phase 5.0 NewApi gate | User authorized full Batch 5.0. Added focused NewApi lint configuration and wired both flavor lint tasks into existing GitHub workflows. Local lint attempt timed out during Kotlin/KSP setup before lint results; no GitHub Actions were started. Device checks remain unavailable. |
