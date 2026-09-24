# Behavioral Intelligence Implementation Tracker

Source briefs:

- `docs/IMPL_1A.md` — data layer
- `docs/IMPL_1B.md` — services layer
- `docs/IMPL_2.md` — Stats UI layer
- `docs/IMPL_3.md` — detection engine using existing task/session history
- `docs/IMPL_4.md` — detection engine using daily usage/session history

This tracker is the shared status record for IMPL_1A, IMPL_1B, and IMPL_2. Update it after
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
| IMPL_3 | `docs/IMPL_3.md` | Detectors using existing task/focus-session data | Planned |
| IMPL_4 | `docs/IMPL_4.md` | Manipulation-pattern detectors using daily history/session data | Planned |
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

- [ ] Add the four pure detectors: post-failure cascade, session sweet spot,
  estimation drift, and day-of-week outlier.
- [ ] Add the detection runner and daily-gated background worker invocation.
- [ ] Surface generated findings through the existing repository/UI path.
- [ ] Verify detector thresholds and evidence fingerprints with source checks
  and GitHub Actions.

## IMPL_4 — manipulation-pattern detection

- [ ] Add the raw app-session query required by the manipulation detectors.
- [ ] Add variable reward loop, infinite-session design, morning hijack,
  escalating capture, and streak lock-in detectors.
- [ ] Keep notification conditioning deferred until the verified per-day,
  per-hour, per-package blocking-attempt source and DAO are identified.
- [ ] Have each detector return only its strongest qualifying match per run;
  the finding cooldown would otherwise silently discard multiple submissions
  from the same detection pass.
- [ ] Extend the runner and dependency wiring without changing Phase 3
  detector behavior.
- [ ] Verify that all detectors remain best-effort and do not block app startup.

### IMPL_4 review reconciliation

The attached review confirms that five manipulation detectors are grounded in
the planned `daily_app_usage` and `app_sessions` data:

- Variable Reward Loop
- Infinite Session Design
- Morning Hijack
- Escalating Capture
- Streak Lock-in

This is design validation, not a shipped-source claim: the current workspace
does not yet contain the IMPL_3/IMPL_4 detection engine files, so these items
remain planned. Notification Conditioning stays explicitly deferred until the
per-day, per-hour, per-package blocking-attempt source and DAO are verified.
When IMPL_3 is implemented, widen the shared detector helpers to `internal`
before IMPL_4 imports them from the second detector file.

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

## Follow-up scope

IMPL_3 owns the detection engine that generates the first `FindingEntity` rows
from existing task and focus-session data. IMPL_4 adds detectors that depend on
the new daily usage/session history. IMPL_5 owns adaptive allowance after those
signals are mature.