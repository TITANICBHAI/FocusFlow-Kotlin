# Protected Adjustments Work Tracker

## Reference documents

- [Plan](PROTECTED_ADJUSTMENTS_PLAN.md) — screen behavior, source audit, navigation, and completion criteria.
- [Agent pre-read](AGENT_PRE_PROTECTED_ADJUSTMENTS.md) — required context and implementation/handoff rules.
- [Text-size plan](TEXT_SIZE_PLAN.md) — adjacent Settings work and route/modal scale context; text size is not a guarded adjustment.

## Status key

- **Not started** — no implementation verified.
- **In progress** — source audit or implementation underway; leave unchecked until criteria pass.
- **Blocked** — record the exact decision or tool needed to proceed.
- **Verified** — source behavior and relevant checks support completion.

## Work items

| Work item | Status | Evidence / notes |
|---|---|---|
| Trace every live PIN- and active-block-guarded adjustment, including all owner screens and dialogs | Not started | Search and inspect current UI callers; use How-to-Use as context, not as the sole source of truth. |
| Reconcile the verified guard rules with How-to-Use copy | Not started | List any mismatch and correct the smallest justified text surface. |
| Add the Settings entry and internal screen route | Not started | Settings row → dedicated full-screen page → back to Settings. |
| Build the grouped, explanatory adjustment index with safe destination links | Not started | Do not duplicate controls or PIN verification logic. |
| Verify every listed adjustment and navigation target | Not started | Ensure safe additions and non-guarded settings are not mislabeled. |
| Run available checks and record environment limitations | Not started | Do not claim Android compilation or device behavior unless actually run. |

## Source-audit record

Update this section before implementation begins:

- Source files and guard branches inspected:
- Verified actions and exact guard conditions:
- How-to-Use mismatches found:
- Shared destinations and navigation decisions:
- Items intentionally excluded, with reason:
- Open product decisions:

## Verification and handoff record

| Date | Work | Checks and evidence | Remaining blockers |
|---|---|---|---|
| — | — | No implementation or verification recorded yet. | Complete the source audit first. |

Final handoff must name the files changed, the verified adjustment inventory,
all route/link targets, the checks actually run, and every unverified or
excluded item.