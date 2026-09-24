# Behavioral Intelligence Implementation Tracker

Source briefs:

- `docs/IMPL_1A.md` — data layer
- `docs/IMPL_1B.md` — services layer
- `docs/IMPL_2.md` — Stats UI layer
- `docs/IMPL_3.md` — detection engine using existing task/session history
- `docs/IMPL_4.md` — detection engine using daily usage/session history

This tracker is the shared status record for IMPL_1A through IMPL_5. Update it after
each meaningful implementation, reconciliation, or verification step. The
attached briefs remain the source material; the Kotlin codebase is authoritative
when a brief's name, schema, or lifecycle differs from the current project.

## Working constraints

- Preserve the existing `focusday.db` database file.
- Upgrade Room from version 5 to version 6 with `MIGRATION_5_6`.
- Use the existing `focusday_prefs` preferences file.
- Keep `UsageStatsRepository.getUsageSummary` unchanged.
- Adapt queries to the actual schema (`tasks.start_time` and `tasks.end_time`).
- Do not run an Android build in Replit. Verify Kotlin/Android changes with GitHub Actions.
- Read the implementation brief for the phase being changed and inspect the real code
  before implementing or reviewing changes.

## Roadmap

| Phase | Document | Scope | Status |
|---|---|---|---|
| IMPL_1A | `docs/IMPL_1A.md` | Tracker data, entities, DAOs, migration, usage/session tracking | Complete |
| IMPL_1B | `docs/IMPL_1B.md` | Repositories, services, notifications, pruning | Complete |
| IMPL_2 | `docs/IMPL_2.md` | Stats UI, ratings, findings, cold start | Complete; review fixes applied |
| IMPL_3 | `docs/IMPL_3.md` | Detectors using existing task/focus-session data | Implemented; source complete; hosted build blocked |
| IMPL_4 | `docs/IMPL_4.md` | Manipulation-pattern detectors using daily history/session data | Implemented; source complete; hosted build blocked |
| IMPL_5 | — | Adaptive allowance | Planned |

## IMPL_1A — data layer

- [x] Add `DailyAppUsageEntity` and hourly-value parsing.
- [x] Add `AppSessionEntity`.
- [x] Add `DailyAppUsageDao` with serialized read-modify-write operations.
- [x] Add `AppSessionDao` session statistics and first-session queries.
- [x] Add the five remaining Phase 1 entities.
- [x] Add the five remaining Phase 1 DAOs.
- [x] Add `MIGRATION_5_6` for all seven behavioral tables.
- [x] Upgrade `FocusFlowDatabase` to version 6 and expose all seven DAOs.
- [x] Register `MIGRATION_5_6` in `AppModule`.
- [x] Add `AppUsageAndSessionTracker` with heartbeat, session closure, hourly segmentation, and 90-day-compatible storage.
- [x] Hook tracker creation, foreground transitions, screen-off/unlock, and service cleanup into `AppBlockerAccessibilityService`.
- [x] Add the actual task-date query using the existing ISO `start_time` column.

## IMPL_1B — services layer

- [x] Add `DayRatingRepository`.
- [x] Add `FindingRepository` with cooldown and acknowledgement transitions.
- [x] Add `BehaviouralHypothesisRepository`.
- [x] Add `ClarifyingQuestionRepository`.
- [x] Wire all four repositories in `AppModule`.
- [x] Add the `DAY_RATING` notification channel.
- [x] Add the day-rating scheduler and broadcast receiver.
- [x] Register the receiver in `AndroidManifest.xml`.
- [x] Ensure the reminder on app startup and boot/unlock/package-update recovery.
- [x] Add daily-gated 90-day pruning to `BackgroundFetchWorker`.
- [x] Add `LauncherActivity.ACTION_OPEN_DAY_RATING` as the stable IMPL_2 deep-link stub.

## IMPL_2 — Stats UI layer

- [x] Extend `StatsViewModel` with rating, finding, clarifying-question, and cold-start state.
- [x] Add `StatsViewModel` rating/finding/cold-start actions and repository wiring.
- [x] Add `DayRatingBar` with rating, retroactive date, context, suggested chips, and note controls.
- [x] Add `FindingCardView` with finding states and acknowledgement actions.
- [x] Add `FindingsSection` with baseline, clarifying question, active, watching, and clean states.
- [x] Add `ColdStartSheet` with three skippable seed questions.
- [x] Integrate Phase 1 state, cold-start sheet, day rating, and findings into `StatsInsightsExperience`.
- [x] Route `ACTION_OPEN_DAY_RATING` to the Stats experience and focus the rating control.
- [x] Move notification focus to the always-composed rating row so a cold Stats
  launch cannot request focus from a removed field.
- [x] Save an in-progress rating/note when the rating editor leaves composition,
  including tab switches and back navigation.
- [x] Count recent task-history dates alongside daily usage dates for the
  baseline gate, so existing task history can unlock IMPL_3 findings.
- [x] Verify IMPL_2 with direct source consistency checks; Android build remains delegated to the existing GitHub Actions workflow per project constraints.

## Review reconciliation

- [x] Confirm `DayRatingReminderReceiver` is declared with the expected action.
- [x] Confirm `FOREGROUND_SERVICE_SPECIAL_USE` is declared.
- [x] Confirm the Kotlin serialization plugin and JSON dependency are present.
- [x] Fix the `FocusRequester` crash path identified in the review.
- [x] Fix note loss when leaving the rating editor without IME Done.
- [x] Fix the IMPL_3 cold-start gate so it uses the maximum of tracker days
  and task-history days from the last 90 days.

## IMPL_3 — detection engine using existing data

- [x] Add the four pure detectors: post-failure cascade, session sweet spot,
  estimation drift, and day-of-week outlier.
- [x] Add the detection runner and daily-gated background worker invocation.
- [x] Surface generated findings through the existing repository/UI path.
- [x] Verify detector thresholds and evidence fingerprints with focused JVM
  tests and source checks.
- [ ] Verify the Android build through GitHub Actions after unrelated existing
  Kotlin compilation errors are resolved. The latest hosted run reached the
  compiler but failed in `TaskRepository`, `SchedulerEngine`, `TaskViewModel`,
  `DayRatingBar`, and `StatsViewModel`; no IMPL_3 detector diagnostics appeared.

## IMPL_4 — manipulation-pattern detection

- [x] Add the raw app-session query required by the manipulation detectors.
- [x] Add variable reward loop, infinite-session design, morning hijack,
  escalating capture, and streak lock-in detectors.
- [x] Keep notification conditioning deferred until the verified per-day,
  per-hour, per-package blocking-attempt source and DAO are identified.
- [x] Have each detector return only its strongest qualifying match per run;
  the finding cooldown would otherwise silently discard multiple submissions
  from the same detection pass.
- [x] Extend the runner and dependency wiring without changing Phase 3
  detector behavior.
- [x] Verify that all detectors remain best-effort and do not block app startup.
- [x] Add focused detector tests for thresholds, positive matches, candidate
  selection, and Morning Hijack evidence.

### IMPL_4 review reconciliation

The attached review confirms that five manipulation detectors are grounded in
the planned `daily_app_usage` and `app_sessions` data:

- Variable Reward Loop
- Infinite Session Design
- Morning Hijack
- Escalating Capture
- Streak Lock-in

The five detectors are now implemented against the verified
`daily_app_usage` and `app_sessions` sources. Notification Conditioning stays
explicitly deferred until the per-day, per-hour, per-package blocking-attempt
source and DAO are verified. The shared IMPL_3 detector helpers are already
`internal` and are reused by IMPL_4.

## IMPL_5 — adaptive allowance

- [ ] Define the adaptive allowance inputs, safety limits, and user override.
- [ ] Implement allowance recommendations only after the detection/history
  pipeline has accumulated enough data.
- [ ] Add persistence, UI controls, and explicit opt-in before enforcement.

## Verification

- [x] The repository's existing GitHub Actions Android build passed before the
  current review fixes.
- [x] Room schema generation and migration validation passed in that run.
- [x] The debug APK artifact was uploaded in that run.
- [x] Existing GitHub Actions run `35705729749` passed:
  `https://github.com/TITANICBHAI/FocusFlow-Kotlin/actions/runs/35705729749`
- [ ] Run a new GitHub Actions build after the current review fixes.
- [ ] Run a new GitHub Actions build after the IMPL_4 source changes; the
  existing hosted build is currently blocked by unrelated Kotlin errors.

## Follow-up scope

IMPL_3 owns the detection engine that generates the first `FindingEntity` rows
from existing task and focus-session data. IMPL_4 adds detectors that depend on
the new daily usage/session history. IMPL_5 owns adaptive allowance after those
signals are mature.