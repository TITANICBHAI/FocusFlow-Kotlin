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
| 03 | Text Size source-tab context for secondary routes and overlays (Prompt A) | Replit Agent | Implemented; verification blocked | 02 |
| 04 | Scoped `.sp` to `.scaledSp` conversion (Prompt B) | Unassigned | Blocked | 02, 03 |
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

**Agent:** Replit Agent
**Last updated:** 2026-10-04
**Status:** Complete
**Detailed record:** [Text Size tracker](TEXT_SIZE_TRACKER.md) and
[route/modal inventory](TEXT_SIZE_PLAN.md#12-screen-route-and-modal-coverage-audit)

- [x] Document the known route, caller, screen, overlay, and exception inventory.
  **Evidence:** `TEXT_SIZE_PLAN.md` §12.
- [x] Revalidate the inventory against the current checked-out source before
  implementation; record any stale names, routes, or assumptions.
  **Evidence:** `Routes.kt`, `FocusFlowNavGraph.kt`, `MainActivity.kt`,
  `ProtectedAdjustmentsScreen.kt`, and tab/modal call sites; §12 now records
  Home → `ACTIVE`, Settings-guide routes, shared-route callers, and top-level
  `QuickBlockSheet`.
- [x] Confirm route ownership and scale behavior for secondary destinations,
  shared destinations, caller-owned dialogs/sheets, direct entries, and all
  Home-origin flows.
  **Evidence:** §12 records root-tab ownership, caller-owned `ACTIVE` and
  `PERMISSIONS`, Home's `1f` pin, inline sheet inheritance, direct-entry
  General behavior, and the Stats top-level overlay exception.
- [x] Reconfirm the v1 exclusions and record any changed decisions.
  **Evidence:** Prompt B's directories/files and excluded paths exist in the
  checked-out tree; §§8–9 and §12 retain the exclusions. `LauncherSetupScreen`
  and `QuickBlockSheet` remain excluded even though shared navigation can reach
  them from additional source contexts.

## Batch 02 — Text Size core settings, theme, UI, and block overlay

**Agent:** Replit Agent
**Last updated:** 2026-10-04
**Status:** Implemented; verification blocked
**Prompt:** Prompt A in [TEXT_SIZE_PROMPTS.md](TEXT_SIZE_PROMPTS.md)  
**Depends on:** Batch 01

- [x] Add General and nullable per-tab scale settings, persistence, and
  read/write round-trip behavior, including resetting overrides to `null`.
  **Evidence:** `AppSettings.kt`, `SettingsRepository.kt`, and
  `TextScaleSettingsPersistenceTest.kt`; the instrumentation test was added but
  cannot run until a JDK/Android build environment is available.
- [x] Add the scale composition local, `.scaledSp`, and General-scaled
  typography without changing existing style properties other than size/line
  height. **Evidence:** `ui/theme/Theme.kt`; LSP reports no diagnostics. Gradle
  cannot start because this workspace has no Java executable.
- [x] Wire General and tab scales while keeping Home/Schedule at `1f` and
  leaving `MainScaffold` outside the tab-scale providers.
  **Evidence:** `MainActivity.kt` and the five root destinations in
  `FocusFlowNavGraph.kt`; source review confirms each provider is inside
  `MainScaffold` and Home is pinned to `1f`.
- [x] Add Settings scale controls, “Matches General” behavior, and reset-to-
  inherit actions.
  **Evidence:** `ui/settings/TextSizeSection.kt` and its insertion in
  `SettingsScreen.kt`; 80–150% one-percent sliders, inherited values, and
  “Use General” reset actions were source-reviewed. `SettingsViewModel
  `updateSettings()` persists changed `AppSettings` through the existing
  `setNotificationPreferences()` path.
- [x] Apply the persisted scale to the non-Compose block overlay.
  **Evidence:** `BlockOverlayActivity.kt` reads Defense scale with General
  fallback and multiplies all eight `TextView` sizes. Android build/test
  verification is blocked by the missing Java toolchain.

## Batch 03 — Text Size source-tab context for secondary routes and overlays

**Agent:** Replit Agent
**Last updated:** 2026-10-04
**Status:** Implemented; verification blocked
**Prompt:** Prompt A in [TEXT_SIZE_PROMPTS.md](TEXT_SIZE_PROMPTS.md), using the
route/caller rules in [TEXT_SIZE_PLAN.md §12](TEXT_SIZE_PLAN.md#12-screen-route-and-modal-coverage-audit)  
**Depends on:** Batch 02

- [x] Apply the owning tab's scale to tab-owned secondary destinations.
  **Evidence:** `RouteTextScaleContext.kt` maps Settings-, Stats-, and
  Defense-owned routes; `FocusFlowNavGraph.kt` applies those scales at each
  secondary destination. Direct entries without a source argument resolve to
  General.
- [x] Make shared destinations use the tab that opened them; use General for a
  direct entry with no source tab.
  **Evidence:** live callers in `FocusFlowNavGraph.kt` and `SideMenu.kt` pass
  Home/Focus/Stats/Settings/Defense context to `ACTIVE`, `PERMISSIONS`, and
  `HOW_TO_USE`. `RouteTextScaleContextTest.kt` covers each shared route for
  every root tab and the no-source General fallback; `RoutesTest.kt` confirms
  a deep-link query cannot supply the source-tab context.
- [x] Preserve caller scale for inline dialogs/sheets, explicitly handling
  top-level overlays outside the caller composition.
  **Evidence:** inline `AppPickerSheet` call sites in `ui/home/AllowedAppsDialog.kt`,
  `ui/launcher/AllowedAppsModal.kt`, and `ui/settings/DailyAllowanceModal.kt`
  remain under their caller provider. The global `SideMenu` and the Stats
  `QuickBlockSheet` are explicitly wrapped outside the active NavHost content.
- [x] Keep every Home-origin flow, including shared components, pinned to `1f`.
  **Evidence:** the Home route remains explicitly provided `1f`; shared routes
  retain Home as their source, and the Home picker inherits that provider.
  `RouteTextScaleContextTest.kt` checks Home scale on each shared route.
  `MainScaffold` remains outside the tab content providers.
- [ ] Run the route/context unit tests and Android build.
  **Blocked:** route behavior tests are added but unrun because `java`,
  `JAVA_HOME`, Android SDK variables, and `local.properties` are absent. Source
  checks (`git diff --check` and Kotlin LSP diagnostics) pass.

**Implementation and verification:** Added a source-tab argument to secondary
routes; owner routes replace the caller with their owner tab, while shared routes
retain the caller. Settings-origin backup imports retain Settings context through
the Activity Result callback; startup/direct pending imports use General. Updated
`TEXT_SIZE_PLAN.md` §12 to classify global-drawer How-to-Use as shared and record
the explicit top-level providers. `git diff --check` and Kotlin LSP diagnostics
passed for changed Kotlin files. Route behavior tests were added but not run:
this workspace has no `java`, `JAVA_HOME`, Android SDK variables, or
`local.properties`. No Android build or workflow was started. Prompt B remains
separate; Stats, `QuickBlockSheet`, `LauncherSetupScreen`, `ImportConfirmScreen`,
and `ui/common/` retain their documented raw-`.sp` exclusions.

## Batch 04 — Scoped `.sp` to `.scaledSp` conversion

**Agent:** Unassigned  
**Last updated:** 2026-10-04
**Status:** Blocked (conversion not started)
**Prompt:** Prompt B in [TEXT_SIZE_PROMPTS.md](TEXT_SIZE_PROMPTS.md)  
**Depends on:** Batch 02 and Batch 03

**Dependency gate:** Prompt B forbids starting the conversion unless Batches 02
and 03 are complete. Both currently remain **Implemented; verification blocked**;
Android build/tests are unrun because Java and Android SDK setup are unavailable.
No Prompt B source files were changed. Batch 04 remains unassigned and no
conversion counts or exception inventory are claimed.

- [ ] Convert only the exact Prompt B files and directories; do not expand scope.
  **Blocked:** prerequisite batches are not complete; no conversion started.
- [ ] Record replacement counts by directory and individually named file.
  **Pending:** no sites were converted, so occurrence/replacement counts have
  not been audited.
- [ ] Record every skipped non-composable `.sp` use or other exception; do not
  guess or silently leave unexplained gaps.
  **Pending:** no exception scan was started because the dependency gate is open.
- [x] Confirm excluded areas/files remain untouched.
  **Evidence:** this Batch 04 gate check changed no UI source files. Existing
  Batch 03 UI changes are separate; no conversion edits were made here.

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
| 2026-10-04 | 01 | Replit Agent | Completed source revalidation; updated `TEXT_SIZE_PLAN.md` §12 to include Home → ACTIVE and current shared/deep-link/modal caller edges. | None. |
| 2026-10-04 | 02 | Replit Agent | Implemented settings, persistence, theme scaling, root-tab providers, controls, and overlay scaling; added a persistence round-trip/reset instrumentation test. `git diff --check` and LSP diagnostics pass. | `bash ./gradlew :app:compileDebugKotlin :app:assembleDebugAndroidTest` stops before compilation: no `java` command / `JAVA_HOME`; Android tests remain unrun. |
| 2026-10-04 | 03 | Replit Agent | Implemented route-owner/source-tab providers, shared-route caller propagation, explicit drawer/QuickBlock overlay context, and source-level behavior tests. `git diff --check` and LSP diagnostics pass. | Android unit tests/build not run: no `java`, `JAVA_HOME`, Android SDK variables, or `local.properties`. |
| 2026-10-04 | 04 | Replit Agent (dependency check only) | Checked the Prompt B gate; did not claim the batch or edit conversion files. Batch 04 remains unassigned and blocked. | Batches 02 and 03 are still “Implemented; verification blocked”; Prompt B prohibits starting until both are complete. |