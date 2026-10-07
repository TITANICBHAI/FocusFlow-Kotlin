# Allowance Phase 5 — Agent Pre-Prompt

Use this prompt before doing any work tracked in `allowance/`.

## Read first

Before inspecting implementation details or editing files, read these documents in full and in this order:

1. `allowance/PRE_PROMPT.md`
2. `allowance/PLAN.md`
3. `allowance/TRACKER.md`

Then read the current project instructions, inspect the relevant current source, and check the working tree before editing. Verify that paths, APIs, and behavior described in the plan still exist. Preserve unrelated user changes and user data.

## Required working rules

- Work alone. Do not create, invoke, or delegate to sub-agents.
- Follow the plan's implementation order and the tracker. Do not silently widen scope or change the plan's contract.
- Respect the plan's no-touch list unless the user explicitly changes the scope.
- Treat task replacement and any other deletion of user data as destructive. Keep replacement disabled by default, preserve explicit confirmation and required safety guards, and do not perform destructive actions without the user's authorization.
- Run Gradle tests, or Android tests on Replit by using the current workflow or scripts under `scripts/`. If verification is needed, use GitHub Actions only when the user explicitly requests APK-building verification. Otherwise state clearly that those checks were not run.
- Do not push to GitHub or start, poll, or monitor GitHub Actions unless the user explicitly asks.
- Do not expose, copy into files, or print secrets or credentials.
- The plan is the implementation contract, not authorization by itself. Do not begin app-code implementation until the user explicitly authorizes it. If the requested scope or authorization is unclear, stop before changing app code and ask.
- Keep Android 15 / `targetSdk` 35 foreground-service work and every item listed as parked in the plan out of scope unless the user explicitly changes the scope.

## Tracker discipline — required throughout the work

- Keep `allowance/TRACKER.md` current as work proceeds.
- At the start of a batch, mark it in progress in its notes. After each meaningful change, update the relevant notes rather than waiting until the whole project is finished.
- Tick each checkbox as soon as its work is complete and verified. Never mark unverified, partially completed, or merely attempted work as complete.
- For every completed batch, record evidence: changed files, the exact verification performed, and its result. Record decisions, failures, blockers, and deferred work as well.
- If a check fails, keep the affected completion item unchecked, record the observed failure, and document the fix and successful rerun before ticking it.
- Do not erase failed attempts or blockers from the record; add the outcome so another agent can follow the history.
- Before finishing, reconcile the tracker with the actual code and test evidence. Leave incomplete items unchecked and say why.

## Final response

Report what changed, which tracker batches are complete or still open, the evidence gathered, and any limitations or blockers. State clearly which checks were or were not run. Never claim a build, test, device check, or GitHub Actions run passed unless its result was observed.
