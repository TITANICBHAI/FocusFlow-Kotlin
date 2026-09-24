# Behavioral Intelligence Agent Pre-Prompt

You are implementing or reviewing the FocusFlow behavioral-intelligence work.
Before changing code, you MUST:

1. Read the complete phase specification in `docs/` (for example,
   `docs/IMPL_5.md`) and any upstream phase briefs it depends on.
2. Read `docs/behavioral-intelligence-implementation-tracker.md`.
3. Inspect the real Kotlin codebase and verify package names, Room schema
   version, migration chain, DAO names, preference keys, service lifecycle,
   worker behavior, notification channels, manifest entries, and activity
   locations. Do not blindly copy a brief's example when the codebase differs.
4. Update the tracker after every meaningful step:
   - mark completed items only after the corresponding code exists;
   - record adaptations when the real code differs from the brief;
   - record blockers and verification results immediately;
   - leave no completed item implied only by a plan.
5. Before finishing, re-read the applicable phase brief(s), inspect the final
   diff, and reconcile every explicit requirement against the tracker.

Non-negotiable project constraints:

- Preserve `focusday.db` and upgrade from Room 5 to Room 6 using
  `MIGRATION_5_6`.
- Preserve `focusday_prefs`.
- Do not modify `UsageStatsRepository.getUsageSummary`.
- Adapt date queries to the actual `tasks.start_time`/`tasks.end_time` schema.
- Do not build the Android project inside Replit. Use GitHub Actions for Android
  compilation, Room verification, and APK artifact validation.
- Never claim build verification until the GitHub Actions result is known and
  recorded in the tracker.

Implementation expectations:

- Prefer small, conventional Kotlin files and the repository's existing manual
  DI patterns.
- Keep live usage tracking real-time and independent of the short OS
  UsageEvents retention window.
- Keep screen-off/unlock and accessibility lifecycle hooks compatible with the
  existing allowance-tracking behavior.
- Keep allowance suggestions advisory unless a verified allowance-setting
  integration and an explicit enforcement design are in scope.
- Fail explicitly for configuration problems; do not add silent no-op fallbacks.