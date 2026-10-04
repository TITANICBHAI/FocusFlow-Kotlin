# Project Work Tracker

This is the project-wide entry point for planned work currently documented in
`work/`. It tracks batches, owners, progress, evidence, and blockers.

**Plans define what should be built. This file records what agents actually do.**
The feature-specific trackers remain the detailed audit and evidence records;
update this file as work progresses so batch status is visible in one place.
Add a batch here whenever a new workstream or agent job is planned.

## How agents update this tracker

1. Before starting a batch, replace `Unassigned` with your agent name/handle,
   set the status to **In progress**, and add the date.
2. As each checklist item is completed, tick it and add evidence on that line:
   source files/sections, test or build command and result, or another
   inspectable artifact. A checkbox without evidence is not complete.
3. If blocked, leave the blocked item unchecked and record the exact blocker,
   what was tried, and what would unblock it.
4. At handoff, update the batch status and evidence, then report changed files,
   checks run, skipped scope, and remaining blockers. Do not update another
   agent's batch unless you are explicitly taking it over.
5. Suggested agent names in prompts are suggestions only. Record the actual
   assigned agent here; do not infer who performed earlier work.
6. After a meaningful progress update or handoff, append a dated row to the
   update log at the end of this file. Keep it factual and link the evidence.

## Status key

- **Not started** — no work has begun.
- **In progress** — an agent is working or evidence is still being gathered.
- **Blocked** — progress needs a named tool, decision, or environment fix.
- **Complete** — the batch's required work and relevant verification are done.
- **Implemented; verification blocked** — source work is recorded, but required
  checks could not run. Keep the unchecked verification item visible.

## Batch overview

| Batch | Workstream | Agent | Status | Depends on |
|---|---|---|---|---|
| 00 | Guarded Adjustments Settings guide | Not recorded in existing handoff | Implemented; verification blocked | — |
| 01 | Text Size route/modal audit | Unassigned | In progress | — |
| 02 | Text Size core settings, theme, UI, and block overlay (Prompt A) | Unassigned | Not started | 01 |
| 03 | Text Size source-tab context for secondary routes and overlays (Prompt A) | Unassigned | Not started | 02 |
| 04 | Scoped `.sp` to `.scaledSp` conversion (Prompt B) | Unassigned | Not started | 02, 03 |
| 05 | Text Size integration, verification, and final handoff | Unassigned | Not started | 02–04 |

The current work folder contains the two workstreams listed above. Add later
workstreams here rather than treating either feature-specific tracker as the
project-wide list.

---

## Batch 00 — Guarded Adjustments Settings guide

**Agent:** Not recorded in the existing handoff  
**Last updated:** 2026-10-04  
**Status:** Implemented; Android verification blocked  
**Detailed record:** [Protected Adjustments tracker](PROTECTED_ADJUSTMENTS_TRACKER.md)

- [x] Audit PIN- and active-block-guarded actions and exact conditions.
  **Evidence:** [source-audit record](PROTECTED_ADJUSTMENTS_TRACKER.md#source-audit-record).
- [x] Add the Settings entry, internal destination, full-screen guide, and owner
  links. **Evidence:** detailed tracker, Work items rows 22–24; route assertions
  cover the internal-only route.
- [x] Run available source-level checks.
  **Evidence:** detailed tracker, Work items row 25 and handoff row 97:
  `git diff --check` and Kotlin LSP checks passed.
- [ ] Run Android compilation and unit tests.
  **Blocked:** Java is absent from `PATH`, Android SDK variables are empty, and
  `local.properties` is absent. **Evidence:** detailed tracker, Work items row
  25. The existing record checked these environment prerequisites; Android
  checks were not run. Unblock by configuring the JDK and Android SDK. Do not
  mark this item complete unless the checks actually run.

## Batch 01 — Text Size route and modal audit

**Agent:** Unassigned  
**Last updated:** Not recorded  
**Status:** In progress  
**Detailed record:** [Text Size tracker](TEXT_SIZE_TRACKER.md) and
[route/modal inventory](TEXT_SIZE_PLAN.md#12-screen-route-and-modal-coverage-audit)

- [x] Document the known route, caller, screen, overlay, and exception inventory.
  **Evidence:** `TEXT_SIZE_PLAN.md` §12.
- [ ] Revalidate the inventory against the current checked-out source before
  implementation; record any stale names, routes, or assumptions.
  **Evidence required:** updated inventory and a concise mismatch note here or
  in the detailed tracker.
- [ ] Confirm route ownership and scale behavior for secondary destinations,
  shared destinations, caller-owned dialogs/sheets, direct entries, and all
  Home-origin flows.
  **Evidence required:** source/call-site references and explicit outcomes for
  each case in the detailed tracker.
- [ ] Reconfirm the v1 exclusions and record any changed decisions.
  **Evidence:** `TEXT_SIZE_PLAN.md` §§8–9 and §12, reconciled with current source.

## Batch 02 — Text Size core settings, theme, UI, and block overlay

**Agent:** Unassigned  
**Last updated:** Not recorded  
**Status:** Not started  
**Prompt:** Prompt A in [TEXT_SIZE_PROMPTS.md](TEXT_SIZE_PROMPTS.md)  
**Depends on:** Batch 01

- [ ] Add General and nullable per-tab scale settings, persistence, and
  read/write round-trip behavior, including resetting overrides to `null`.
  **Evidence required:** changed-file list and test/result for persistence.
- [ ] Add the scale composition local, `.scaledSp`, and General-scaled
  typography without changing existing style properties other than size/line
  height. **Evidence required:** source references and compile/test result.
- [ ] Wire General and tab scales while keeping Home/Schedule at `1f` and
  leaving `MainScaffold` outside the tab-scale providers.
  **Evidence required:** source references and route/UI verification.
- [ ] Add Settings scale controls, “Matches General” behavior, and reset-to-
  inherit actions. **Evidence required:** source references and behavior check.
- [ ] Apply the persisted scale to the non-Compose block overlay.
  **Evidence required:** source references and build/test result.

## Batch 03 — Text Size source-tab context for secondary routes and overlays

**Agent:** Unassigned  
**Last updated:** Not recorded  
**Status:** Not started  
**Prompt:** Prompt A in [TEXT_SIZE_PROMPTS.md](TEXT_SIZE_PROMPTS.md), using the
route/caller rules in [TEXT_SIZE_PLAN.md §12](TEXT_SIZE_PLAN.md#12-screen-route-and-modal-coverage-audit)  
**Depends on:** Batch 02

- [ ] Apply the owning tab's scale to tab-owned secondary destinations.
  **Evidence required:** route-to-provider mapping verified against live
  navigation code.
- [ ] Make shared destinations use the tab that opened them; use General for a
  direct entry with no source tab.
  **Evidence required:** source references and checks for each shared route.
- [ ] Preserve caller scale for inline dialogs/sheets, explicitly handling
  top-level overlays outside the caller composition.
  **Evidence required:** source/call-site mapping and behavior checks.
- [ ] Keep every Home-origin flow, including shared components, pinned to `1f`.
  **Evidence required:** route/caller verification; no Home scale leakage.

## Batch 04 — Scoped `.sp` to `.scaledSp` conversion

**Agent:** Unassigned  
**Last updated:** Not recorded  
**Status:** Not started  
**Prompt:** Prompt B in [TEXT_SIZE_PROMPTS.md](TEXT_SIZE_PROMPTS.md)  
**Depends on:** Batch 02 and Batch 03

- [ ] Convert only the exact Prompt B files and directories; do not expand scope.
  **Evidence required:** changed-file list checked against the prompt's allowlist.
- [ ] Record replacement counts by directory and individually named file.
  **Evidence required:** counts in the handoff and this batch record.
- [ ] Record every skipped non-composable `.sp` use or other exception; do not
  guess or silently leave unexplained gaps.
  **Evidence required:** complete exception list and reason for each item.
- [ ] Confirm excluded areas/files remain untouched.
  **Evidence required:** diff/scope audit.

## Batch 05 — Text Size integration, verification, and handoff

**Agent:** Unassigned  
**Last updated:** Not recorded  
**Status:** Not started  
**Depends on:** Batches 02–04

- [ ] Verify General/per-tab persistence and reset-to-inherit behavior.
  **Evidence required:** test names and results.
- [ ] Verify Home remains at `1f`, the bottom navigation is not wrapped, and
  secondary/shared route context follows the caller.
  **Evidence required:** source checks or tests with outcomes.
- [ ] Verify the Prompt B scope and report Stats, shared-component, and other
  documented exclusions without claiming full coverage.
  **Evidence required:** scope audit and explicit exception list.
- [ ] Run available Android build/tests and other relevant checks.
  **Evidence required:** exact commands and outcomes; if blocked, keep unchecked
  and record the environment limitation.
- [ ] Complete the handoff with changed files, mismatches, conversion counts,
  skipped exceptions, checks, and unresolved blockers.
  **Evidence required:** handoff note and links to the detailed tracker.

## Update log

| Date | Batch | Agent | Progress and evidence | Blockers |
|---|---|---|---|---|
| 2026-10-04 | 00 | Not recorded in the existing handoff | Baseline imported from the [Protected Adjustments tracker](PROTECTED_ADJUSTMENTS_TRACKER.md); implementation and source-level checks are documented there. | Android compilation/unit tests need a configured JDK and Android SDK. |