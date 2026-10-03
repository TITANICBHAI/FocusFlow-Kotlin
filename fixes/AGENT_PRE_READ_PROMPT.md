# Agent Pre-Read Prompt — FocusFlow Backup / Import / Alarm Work

## Context

You are working in the existing FocusFlow Android app, written in Kotlin. Your task is
to implement a reviewed backup/import/alarm contract against the current source. The
app already has a legacy backup flow, but it does not yet implement the full durable
restore and alarm design.

This prompt is for an agent with no prior chat context. Start by reading these files in
full:

1. `fixes/IMPLEMENTATION_TRACKER.md`
2. `fixes/FOCUSFLOW_IMPLEMENTATION_PLAN_FINAL_v14.md`
3. `fixes/REVIEW_NOTES_FINAL_v14.txt`

The implementation contract is the single product authority. Its TypeScript facts
were documented because the original TypeScript source is not available to the coding
agent. Do not invent missing TypeScript behavior.

## What the review established

- The recovery concern in the uploaded review is already corrected in the v14 plan:
  post-mutation failures enter `RECOVERY_BLOCKED`, retain the journal, and keep the
  gate closed; corrupt journals are quarantined. The current Kotlin app has not
  implemented that recovery system yet. Implement the contract; do not weaken it.
- Task reminders are persisted with tasks, while duplicate identity intentionally
  excludes reminders. The v14 plan now explicitly states that reminder differences
  are ignored and the existing task/reminder array wins for a matching ID.
- The plan already has the v14 heading and supersession text. Do not make another
  version-label edit.

## Before editing

1. Read the entire plan and tracker, especially the data boundaries, security
   decisions, recovery contract, alarm policy, milestone order, and test plan.
2. Inspect the current implementation of every class or file you intend to change.
   At minimum, locate the live backup/import path, task persistence, task write sites,
   routes, manifest filters, alarms, notification scheduling, and legacy migration.
3. Treat the tracker baseline as a starting point, not permanent truth. Confirm each
   item against the current checkout.
4. Work only on the assigned milestone. If no milestone is named, begin with M0; do
   not attempt to implement M0–M6 in one unreviewed sweep.
5. If the source contradicts the contract or a required decision cannot be made from
   the contract, stop before making a product-level assumption. Record the exact
   mismatch/blocker in the tracker and report it.

## Non-negotiable behavior

- Never import live enforcement state from a backup, including older exports.
- Same-ID divergent task content in Merge must abort before any mutation. Do not
  silently skip, overwrite, or partially import the file.
- Replace means replace tasks only; preserve unrelated history and device-local
  security state.
- A post-mutation recovery failure must not reopen the restore gate. Keep recovery
  data; quarantine a corrupt journal. Discard is an explicit user choice followed by
  reconciliation.
- `Task.reminders` is opaque task data. Do not use it to build notification schedules.
- Task-end alarms must not silently fall back to inexact delivery. Reminder-chain
  alarms have their separately specified policy.
- Do not claim Android platform or device behavior is verified without checking the
  current documentation or running the specified device test.
- Do not broaden the assigned milestone into unrelated refactoring or UI changes.

## Working and tracking rules

- Keep changes small and consistent with the current architecture; reuse existing
  repositories and components when appropriate.
- Add or update tests for every behavior changed in your milestone. Follow the exact
  contract scenarios where tests are specified.
- Update `fixes/IMPLEMENTATION_TRACKER.md` as work progresses. Mark items complete
  only after code inspection and the relevant tests/checks support the claim. Record
  blockers instead of checking a box optimistically.
- Do not rewrite or renumber the implementation contract unless the user explicitly
  asks. If a correction is genuinely needed, first verify it against source, explain
  the evidence in the tracker, then make the smallest justified edit.
- Run available checks after the milestone. If Android build tooling or a physical
  device is unavailable, report that limitation separately; do not present it as a
  passing check.

## Handoff format

When finished, report:

1. Milestone and tracker items completed.
2. Files changed and the reason for each.
3. Tests/build/static checks run and their actual results.
4. Any source mismatch, unresolved decision, or item requiring a real-device test.
5. Any tracker items left unchecked and why.