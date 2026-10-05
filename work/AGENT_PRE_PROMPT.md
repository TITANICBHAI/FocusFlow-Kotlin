# Agent pre-prompt: Always-on status notification

You are continuing work on the FocusFlow Android always-on status notification plan. This file supplies the working context and conduct rules. Before acting, read:

1. `work/ALWAYS_ON_STATUS_NOTIFICATION_IMPLEMENTATION_PLAN.md` — implementation scope, constraints, phases, decisions, and acceptance criteria.
2. `work/BATCH_TRACKER.md` — current progress, owner decisions, evidence, blockers, and the mandatory work log.
3. `.agents/memory/MEMORY.md` and `.agents/memory/always-on-status-notification-workflow.md` — project memory entry for this work.
4. `replit.md` and only the linked memory topics relevant to the phase you are doing. In particular, check the Android build-environment and GitHub push/build-polling notes before those activities.

## How to conduct the work

- Treat the implementation plan as the scope and behavior contract. Treat the tracker as the sole source of progress state. Do not infer approval from a recommendation.
- Start with Batch 0. It is read-only and must be completed and reported before changing code. Its source observations came from an uploaded snapshot and must be rechecked against the current repository.
- Work in the plan's phase order. Complete only the current batch and batches the user has authorized. Stop at unanswered owner-decision gates and record the blocker in the tracker.
- Tick tracker items only when actually complete. As you work, record dates, files, commands/checks, results, concrete evidence, decisions, and blockers in the relevant batch log. Update the tracker before stopping or handing work off. Never claim a check ran if it did not.
- Keep changes within the plan's boundaries: no enforcement behavior changes in Phases 1–4; do not add polling loops, SharedPreferences keys, permissions, or manifest changes; do not expand the four named large files; do not touch parked or out-of-scope items.
- Preserve existing behavior and project conventions. Re-read the affected code before editing, make focused changes, and verify the whole batch before marking it complete.
- If the build cannot run because Java/JDK or Android SDK prerequisites are missing, record the exact environment limitation and do not misreport it as a source failure. Consult the linked Android build-environment memory.
- Do not push code, start/poll GitHub Actions, or use the configured GitHub workflow unless the user explicitly asks. A phase boundary in the plan does not grant push authorization.
- Do not silently choose among owner decisions Q1–Q4. Record the exact decision and date in the tracker before acting on it.
- Keep `.agents/memory/` for durable constraints and lessons, not a duplicate progress log. The tracker holds this effort's ongoing status.

## Context boundary

Use the plan, tracker, this prompt, repository instructions, relevant project memory, and source files needed for the active batch. Do not broaden the task into unrelated cleanup or assume unmentioned product behavior. If a requirement is ambiguous or a required decision is missing, stop that portion, record it, and ask the owner.

## Handoff format

Before ending a work session, update the tracker and report:

- batch(es) worked on and current status;
- completed checklist items with evidence;
- files changed;
- commands/tests/build checks and their actual results;
- decisions or blockers still needed;
- the next authorized batch, if clear.
