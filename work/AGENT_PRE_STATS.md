# Agent Pre-Read — Stats Fixes

## Mission

Implement the still-open Stats work described in:

- `work/STATS_FIX_PLAN.md`

This is an implementation plan, not a patch. Read the cited functions and
current call sites before editing; line numbers and assumptions may have
drifted.

## First step

Read Part 0 and verify that its five fixes still exist. Do not redo or silently
replace them. Then implement the open items from Part 1 onward, followed by
Part 2 and Part 3 only if the current code still matches the documented issue
and scope allows it.

## Recommended order

1. **1.5:** enforce the 60-day intentional suppression before fingerprint
   comparison.
2. **1.6:** make the one-per-week creation cooldown state-agnostic.
3. **1.4:** add calendar-time-based resolution after 21 days without positive
   evidence.
4. **1.1:** replace the structurally invalid three-month hourly aggregation
   with persisted daily hourly data, and only perform that read for the
   three-month window.
5. **1.2 and 1.3:** fix the task empty copy and remove the misleading
   Today-week heatmap.
6. **1.7:** apply the finding-card visual/content refresh.
7. **2.x and 3.x:** handle redundancy and low-priority chart cleanup after the
   correctness work, if still applicable.

## Critical decisions to preserve

- Finding resolution is **21 consecutive calendar days** without the detector
  firing for the specific `(detectionType, subjectPackage)`. Use a timestamp,
  not a background-job run counter, because Android jobs can be skipped.
- Every positive detection updates the timestamp, including resurfacing and
  first creation.
- Multi-candidate detectors must evaluate all qualifying candidates through one
  shared evaluation path. A non-winning app is not evidence of absence.
- Existing findings start with no historical timestamp, so a 21-day cold start
  after release is expected.
- An intentional finding remains suppressed until its stored 60-day deadline,
  regardless of fingerprint drift. Do not treat any fingerprint change as an
  automatic early resurface unless the current product decision explicitly
  changes.
- The weekly pacing limit is based on the most recent finding creation across
  all states, not only unread `detected` findings.
- Three-month hourly data must come from genuine persisted per-hour history.
  Do not redistribute an aggregate `queryUsageStats` bucket by its start hour.
- Do not render a day-of-week weekly heatmap for a one-day Today range.

## Safety boundaries

- Inspect Room entities, DAOs, migrations, repository lifecycle, detector
  runner behavior, and analytics call sites before changing schemas or
  resolution logic.
- Keep changes focused on the findings in the plan. Do not bundle unrelated
  Stats redesign or text-size work.
- Preserve existing state transitions and user actions while adding the missing
  time gates.
- If the current detector architecture cannot support a correct all-candidate
  absence check, document the gap instead of marking a winner-only result as
  absence.
- Check null/empty data paths after removing redundant cards; `AllTimeStats`
  must remain sensible if lifetime data is unavailable.

## Verification and handoff

Run the strongest available compile/test checks. Exercise or inspect:

- suppression before and after its deadline,
- cooldown with findings in `detected`, `seen`, and other states,
- timestamp updates and 21-day resolution,
- multi-candidate non-winner behavior,
- three-month hourly aggregation and non-three-month gating,
- Today/Week/All Time rendering branches.

Report files changed, schema/migration changes, each plan item completed or
deferred, tests/checks run, and any source mismatch or unresolved product
decision. Do not report an item as fixed merely because its UI is hidden.