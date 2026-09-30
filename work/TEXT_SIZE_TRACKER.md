# Text Size Work Tracker

## Reference documents

- [Architecture plan](TEXT_SIZE_PLAN.md) — feature architecture, boundaries, and implementation scope.
- [Agent prompts](TEXT_SIZE_PROMPTS.md) — separate core-wiring and mechanical-rollout instructions.
- [Agent pre-read](AGENT_PRE_TEXT_SIZE.md) — required reading, constraints, and handoff checks.

The two uploaded references were filed under their stable names with the numeric upload suffix removed. The copies in `work/` match the uploaded documents.

## Implementation status

| Work item | Source | Status | Notes |
|---|---|---|---|
| Core settings, persistence, theme, tab wiring, settings controls, and block overlay | Prompt A in `TEXT_SIZE_PROMPTS.md` | Not started | Current source has no text-scale implementation markers. |
| Scoped `.sp` to `.scaledSp` rollout | Prompt B in `TEXT_SIZE_PROMPTS.md` | Not started | Depends on the `scaledSp` extension from Prompt A. |

## Completion checks

- [ ] Complete Prompt A and verify General and per-tab preference persistence, reset-to-inherit, and Home remaining at 100%.
- [ ] Complete Prompt B only in its listed files and report replacements and skipped exceptions.
- [ ] Confirm Stats override limitations and all other documented scope boundaries.
- [ ] Run the available checks and record any Android build environment limits.