# Agent Pre-Prompt — FocusFlow Work Folder

Use this prompt before doing any work tracked in `work/`.

## Read first

Before inspecting implementation details or editing files, read these files in full and in this order:

1. `work/AGENT_PRE_PROMPT.md`
2. `work/focusflow-import-export-plan.md`
3. `work/focusflow-import-export-tracker.md`

Then read the current project instructions and the relevant source files. Check the current repository status and existing changes before editing. Do not assume that paths, APIs, or code described by the plan still exist; verify them in the current project.

## Current product context and authorization

The app's user-facing `.focusflow` import/export feature has been removed. The one-time legacy settings migration from the app database is separate and must remain intact.

The plan is retained as reference material. **Its presence in `work/` is not permission to implement or restore the feature.** Start implementation only after the user explicitly asks for that work. If that authorization is absent or the requested scope is unclear, stop before changing app code and ask the user.

## Required working rules

- **Work alone. Do not create, invoke, or delegate to sub-agents.**
- Follow the plan's implementation order and the tracker. Do not silently widen scope or change the plan's contract.
- Before editing, inspect relevant current code, project instructions, and the working tree. Preserve unrelated user changes and user data.
- Respect the plan's no-touch list unless the user explicitly changes the scope.
- Treat task replacement and any other deletion of user data as destructive. Keep replacement disabled by default, preserve explicit confirmation and required safety guards, and do not perform destructive actions without the user's authorization.
- Do not run Gradle, Android builds, or Android tests on Replit. If verification is needed, use GitHub Actions only when the user explicitly requests remote verification. Otherwise state clearly that those checks were not run.
- Do not push to GitHub or start, poll, or monitor GitHub Actions unless the user explicitly asks.
- Do not expose, copy into files, or print secrets or credentials.

## Tracker discipline — required throughout the work

- Keep `work/focusflow-import-export-tracker.md` current as work proceeds.
- At the start of a batch, mark it in progress in its notes. After each meaningful change, update the relevant notes rather than waiting until the whole project is finished.
- Tick each checkbox as soon as its work is complete **and verified**. Never mark unverified, partially completed, or merely attempted work as complete.
- For every completed batch, record evidence: changed files, the exact verification performed, its result, and any relevant GitHub Actions link. Record decisions, failures, blockers, and deferred work as well.
- If a check fails, keep the affected completion item unchecked, record the observed failure, and document the fix and successful rerun before ticking it.
- Do not erase failed attempts or blockers from the record; add the outcome so another agent can follow the history.
- Before finishing, reconcile the tracker with the actual code and test evidence. Leave incomplete items unchecked and say why.

## Final response

Report what changed, which tracker batches are complete or still open, the evidence gathered, any limitations or blockers, and whether remote CI was explicitly requested and completed. Never claim a build or test passed unless its result was observed.
