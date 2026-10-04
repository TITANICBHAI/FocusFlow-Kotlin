# Text Size Work Tracker

## Reference documents

- [Architecture plan](TEXT_SIZE_PLAN.md) — feature architecture, boundaries, and implementation scope.
- [Agent prompts](TEXT_SIZE_PROMPTS.md) — separate core-wiring and mechanical-rollout instructions.
- [Agent pre-read](AGENT_PRE_TEXT_SIZE.md) — required reading, constraints, and handoff checks.
- [Protected Adjustments plan](PROTECTED_ADJUSTMENTS_PLAN.md) — separate Settings screen for finding and understanding guarded changes.
- [Protected Adjustments tracker](PROTECTED_ADJUSTMENTS_TRACKER.md) — source audit, route, UI, and verification checklist for that screen.
- [Protected Adjustments agent context pre-prompt](AGENT_PRE_PROTECTED_ADJUSTMENTS.md) — read-only context gathering for the main agent; no implementation or subagents.

The two uploaded references were filed under their stable names with the numeric upload suffix removed. The copies in `work/` match the uploaded documents.

## Implementation status

| Work item | Source | Status | Notes |
|---|---|---|---|
| Core settings, persistence, theme, tab wiring, settings controls, and block overlay | Prompt A in `TEXT_SIZE_PROMPTS.md` | Not started | Current source has no text-scale implementation markers. |
| Scoped `.sp` to `.scaledSp` rollout | Prompt B in `TEXT_SIZE_PROMPTS.md` | Not started | Depends on the `scaledSp` extension from Prompt A. |
| Route-origin scale context for secondary screens and shared dialogs/sheets | §12 in `TEXT_SIZE_PLAN.md`; Prompt A | Not started | Root-tab providers do not scope separate NavHost destinations. Carry caller context; shared routes follow their caller and Home stays at `1f`. |
| Screen/modal coverage audit and explicit v1 exceptions | §12 in `TEXT_SIZE_PLAN.md`; Prompt B | In progress | Root and secondary route inventory is documented. Complete the source-level modal/caller audit before claiming full coverage; Stats, shared components, and named exclusions remain out of the mechanical conversion scope. |
| Settings → Guarded Adjustments screen | Companion plan and tracker linked above | Verified | Separate full-screen, sectioned Q&A guide explains existing guarded popups/locked states and links to owner flows; it is not a popup or a duplicate control surface. Android build/test remain unavailable; see the companion tracker. |

## Completion checks

- [ ] Complete Prompt A and verify General and per-tab preference persistence, reset-to-inherit, and Home remaining at 100%.
- [ ] Complete Prompt B only in its listed files and report replacements and skipped exceptions.
- [ ] Verify per-tab scaling across the route/modal inventory in §12, including shared destinations, caller-owned sheets, and Home-origin UI.
- [ ] Confirm Stats override limitations and all other documented scope boundaries.
- [ ] Keep the Guarded Adjustments screen tracked and verified separately; text-size settings are not protected adjustments.
- [ ] Run the available checks and record any Android build environment limits.