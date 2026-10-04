# Agent Pre-Read — Protected Adjustments Screen

## Mission

Implement the Settings → **Guarded Adjustments** screen described in
`work/PROTECTED_ADJUSTMENTS_PLAN.md`. This is a full-screen informational and
navigation hub for existing PIN- and active-block-protected changes. It must
preserve the current guard owners and must not create a parallel way to mutate
protected state.

Work directly in this session. Do not spawn or delegate to subagents.

## Read context before touching source

Read these documents in full first:

1. `work/PROTECTED_ADJUSTMENTS_PLAN.md`
2. `work/PROTECTED_ADJUSTMENTS_TRACKER.md`
3. `work/AGENT_PRE_PROTECTED_ADJUSTMENTS.md`
4. `work/TEXT_SIZE_PLAN.md` §12 — route and modal context relevant to the
   Settings page, especially if text scaling has been implemented meanwhile.
5. `app/src/main/java/com/tbtechs/focusflow/ui/support/HowToUseScreen.kt`
6. `app/src/main/java/com/tbtechs/focusflow/ui/settings/SettingsScreen.kt`
7. `app/src/main/java/com/tbtechs/focusflow/ui/navigation/Routes.kt`
8. `app/src/main/java/com/tbtechs/focusflow/ui/navigation/FocusFlowNavGraph.kt`

Then inspect the current guard implementation, not just its documentation.
At minimum trace Focus session/duration actions, Defense toggles, Always-On and
VPN app removal, keyword changes, group schedules, daily allowances, and
standalone blocking. Use the plan's source inventory as a starting point; search
for additional PIN checks and active-block restrictions before declaring the
inventory complete. Verify paths and behavior against the current checkout.

## Before implementation

1. Fill in the `Source-audit record` in
   `work/PROTECTED_ADJUSTMENTS_TRACKER.md`: list inspected sources, each action
   and its exact guard condition, guide mismatches, routes, exclusions, and open
   decisions.
2. If source behavior and How-to-Use wording differ, do not invent a policy.
   Record the evidence and update the smallest appropriate guide text to match
   the verified behavior.
3. Keep the destination internal. Do not add it to external/deep-link
   allowlists unless the plan is explicitly revised.
4. Build the page as a read-only guide/index with links to existing owner flows.
   Let those flows retain all existing PIN prompts and active-state checks.
5. Do not move font-size controls into this screen. Text size is not a protected
   adjustment.

## Non-negotiable constraints

- Do not weaken, bypass, duplicate, or broaden any security guard.
- Do not add persistent settings state or a second set of controls.
- Explain the exact conditions per action; do not claim all Defense settings
  require a PIN.
- Preserve actions that intentionally remain available, such as safe additions
  described by the verified source.
- Keep shared routes and in-screen modal entry paths valid; link to an owning
  screen when a modal cannot be opened safely as a direct destination.
- Keep the tracker current, and mark items Verified only after source review and
  relevant checks.

## Verification and handoff

Verify that Settings opens the new screen, its back path returns to Settings,
all entries match their live guard conditions, each link lands on the existing
owner flow, and no new state-mutation or guard-bypass path exists. Run available
checks; clearly separate source-level checks from Android build or device checks
that could not run.

Final handoff must include:

1. The source-audited list of guarded adjustments and exact guard conditions.
2. Files changed and navigation/link destinations.
3. How-to-Use mismatches or copy changes.
4. Checks run and their actual results.
5. Unverified, excluded, or blocked items, with reasons.