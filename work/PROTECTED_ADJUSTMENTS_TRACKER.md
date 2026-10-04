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
| Trace every live PIN- and active-block-guarded adjustment, popup, dialog, and in-screen lock state | In progress | Initial search found Focus/Defense PIN dialogs, Always-On/VPN/keyword/schedule/daily-allowance/standalone-block gates, plus guarded settings/data-flow candidates. Verify every branch; do not claim exhaustive coverage yet. |
| Reconcile the verified guard rules with How-to-Use copy | Not started | List any mismatch and correct the smallest justified text surface. |
| Add the Settings entry and internal screen route | Not started | Settings row → dedicated full-screen page → back to Settings; the page is not a popup. |
| Build the sectioned question-and-answer guide | Not started | Mirror How-to-Use's section/expandable-entry pattern; explain existing guard popups and locked states without recreating them. |
| Verify every listed adjustment and navigation target | Not started | Ensure safe additions and non-guarded settings are not mislabeled. |
| Run available checks and record environment limitations | Not started | Do not claim Android compilation or device behavior unless actually run. |

## Source-audit record

Update this section before implementation begins:

- Source files and guard branches inspected: Initial search only; see the in-progress audit row. Add exact source locations before implementation.
- Verified actions and exact guard conditions:
- Guard-related popup/dialog/locked states included:
- How-to-Use mismatches found:
- Shared destinations and navigation decisions:
- Items intentionally excluded, with reason:
- Open product decisions:

## Verification and handoff record

| Date | Work | Checks and evidence | Remaining blockers |
|---|---|---|---|
| — | Initial source search | Found guard hooks across Focus, Defense, Always-On, VPN, keywords, schedules, daily allowances, standalone blocking, and additional Settings/permission/backup candidates. No screen implementation or exhaustive branch verification. | Complete the source audit and reconcile the exact Q&A inventory with How-to-Use. |

Final handoff must name the files changed, the verified adjustment inventory,
all route/link targets, the checks actually run, and every unverified or
excluded item.