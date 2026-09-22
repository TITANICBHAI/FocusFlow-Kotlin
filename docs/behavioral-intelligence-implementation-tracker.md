# Behavioral Intelligence Implementation Tracker

Source briefs:

- `attached_assets/IMPL_1A_1790066660444.md` — data layer
- `attached_assets/IMPL_1B_1790066660443.md` — services layer
- `attached_assets/IMPL_2_1790066660441.md` — Stats UI layer

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
- Read all three briefs and inspect the real code before implementing or reviewing changes.

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
- [x] Verify IMPL_2 with direct source consistency checks; Android build remains delegated to the existing GitHub Actions workflow per project constraints.

## Verification

- [x] Run the repository's GitHub Actions Android build.
- [x] Confirm Room schema generation and migration validation pass.
- [x] Confirm the debug APK artifact is uploaded.
- [x] GitHub Actions run `35705729749` passed:
  `https://github.com/TITANICBHAI/FocusFlow-Kotlin/actions/runs/35705729749`

## Follow-up scope

IMPL_3 owns the detection engine that generates the first `FindingEntity` rows
from the data now held in the database.