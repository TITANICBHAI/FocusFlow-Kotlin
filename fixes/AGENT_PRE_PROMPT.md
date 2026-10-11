# FocusFlow Fixes Phase 5 — Agent Pre-Prompt

Use this prompt before doing work tracked by the v8 plan in this folder.

## Read first

Before inspecting implementation details or editing files, read these documents in full and in this order:

1. `fixes/AGENT_PRE_PROMPT.md`
2. `fixes/PLAN.md`
3. `fixes/TRACKER.md` — this is the main and authoritative tracker for the fixes plan.
4. `allowance/TRACKER.md` — read only as a reference for its tracker instructions/format.

The v8 plan supersedes `allowance/PLAN.md` (v7). `fixes/TRACKER.md` is the only progress record for this work. `allowance/TRACKER.md` belongs to a separate workstream: do not copy its statuses, evidence, decisions, or progress into the fixes tracker, and do not update it for fixes work. After reading these documents, read the current project instructions, inspect the relevant current source, and check the working tree. Verify paths, APIs, and behavior against the current repository. Preserve unrelated user changes and user data.

## Authorization and scope

- The plan is the implementation contract, not authorization by itself. Do not begin app-code implementation until the user explicitly authorizes it. If the requested scope or authorization is unclear, stop before changing app code and ask.
- Follow the plan's batch order and acceptance criteria. Do not silently widen scope or change the plan's contract. B and C may proceed in parallel only where the plan permits.
- Respect the plan's no-touch list: do not touch Gradle files, GitHub Actions, APK builds, the `.focusflow` backup format, or the Linux app unless the user explicitly changes the scope.
- Do not start a later batch before the earlier batch's “done when” condition holds, except for explicitly parallel batches.
- Device checks are owner-run and happen only at the end of Batch E. Never mark an owner-run check complete.
- Treat task replacement and any other deletion of user data as destructive. Keep replacement disabled by default, preserve explicit confirmation and required safety guards, and do not perform destructive actions without the user's authorization.

## Required working rules

- Work alone. Do not create, invoke, or delegate to sub-agents.
- Run Gradle or Android tests on Replit using the current workflow or scripts under `scripts/`. Test only the production flavor. Do not build APKs on Replit.
- Use GitHub Actions for APK-build verification only when the user explicitly asks. Do not push to GitHub or start, poll, or monitor GitHub Actions unless explicitly asked.
- Do not expose, copy into files, or print secrets or credentials.
- Use behavior tests with fake clocks and event sources. Do not add tests that read Kotlin source files as text. Logic under test must not call the system wall clock directly.

## Tracker discipline — required throughout implementation

- Keep `fixes/TRACKER.md` current; it is the main tracker for this effort. At the start of a batch, mark it in progress in its notes. After each meaningful change, update the relevant notes rather than waiting until the batch is finished.
- Give every item a status, `blocked by`, evidence, and owner-run state. Record decisions in the decision log; label applied defaults “default applied, awaiting owner.”
- Tick a checkbox only when its work is complete and the named evidence has been observed. Never mark unverified, partial, attempted, or owner-run work complete.
- For each completed item, record the changed files, the exact verification performed, and its result. Record decisions, failures, blockers, and deferred work.
- If a check fails, leave the item unchecked. Preserve the failure in the tracker, then record the fix and successful rerun before ticking it. Do not erase failed attempts or blockers.
- Reconcile the tracker with the current code and test evidence before finishing. Leave incomplete work unchecked and explain why. Mark a batch done only when its “done when” condition and required tests and gates pass.

## Final response

Report the batches completed and still open, evidence gathered, decisions, failures, blockers, deferred work, and checks not run. Never claim a build, test, device check, or GitHub Actions run passed unless its result was observed.
