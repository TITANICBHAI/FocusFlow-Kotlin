# Text Size Work Tracker

> Project-wide batch progress is tracked in
> [PROJECT_WORK_TRACKER.md](PROJECT_WORK_TRACKER.md). Use this file for the
> detailed Text Size status and evidence; keep both records synchronized.

## Reference documents

- [Project work tracker](PROJECT_WORK_TRACKER.md) — batch-level owners, progress, evidence, and blockers.
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
| Core settings, persistence, theme, tab wiring, settings controls, and block overlay | Prompt A in `TEXT_SIZE_PROMPTS.md` | Implemented; Android verification blocked | Batch 02 source changes are in place; a SharedPreferences round-trip/reset instrumentation test was added. Gradle cannot start because no Java executable or `JAVA_HOME` is configured. |
| Scoped `.sp` to `.scaledSp` rollout | Prompt B in `TEXT_SIZE_PROMPTS.md` | Blocked; conversion not started | Prompt B requires Batches 02 and 03 to be complete first; both remain implemented with Android verification blocked. No Prompt B source files were changed. |
| Route-origin scale context for secondary screens and shared dialogs/sheets | §12 in `TEXT_SIZE_PLAN.md`; Prompt A | Implemented; Android verification blocked | Batch 03 maps tab-owned destinations to their owning tab, shared `ACTIVE`, `PERMISSIONS`, and `HOW_TO_USE` to the opening tab, and direct entries to General. Inline surfaces inherit; global SideMenu and Stats QuickBlockSheet receive explicit context. |
| Screen/modal coverage audit and explicit v1 exceptions | §12 in `TEXT_SIZE_PLAN.md`; Prompt B | Source audit complete; implementation pending | Batch 01 source audit is complete and documented in §12, including Home → ACTIVE, Guarded Adjustments routes, inline sheets, and the Stats top-level overlay. Stats, shared components, and named exclusions remain out of the mechanical conversion scope. |
| Settings → Guarded Adjustments screen | Companion plan and tracker linked above | Verified | Separate full-screen, sectioned Q&A guide explains existing guarded popups/locked states and links to owner flows; it is not a popup or a duplicate control surface. Android build/test remain unavailable; see the companion tracker. |

## Completion checks

- [ ] Complete Prompt A and verify General and per-tab preference persistence, reset-to-inherit, and Home remaining at 100%. Batches 02–03 source changes are implemented, but Android persistence and route tests remain unrun.
- [ ] Complete Prompt B only in its listed files and report replacements and skipped exceptions. Blocked: Batches 02 and 03 are still “Implemented; verification blocked,” and Prompt B explicitly prohibits starting until both are complete.
- [x] Verify the route/provider mapping across §12, including shared destinations, caller-owned sheets, and Home-origin UI. `RouteTextScaleContextTest` covers caller mapping; Android execution remains blocked.
- [x] Confirm Stats override limitations and all other documented scope boundaries. Raw `.sp` in Stats, QuickBlockSheet, LauncherSetupScreen, ImportConfirmScreen, and common components remains outside Prompt B.
- [ ] Keep the Guarded Adjustments screen tracked and verified separately; text-size settings are not protected adjustments.
- [x] Run the available source checks and record Android build limits. `git diff --check` and LSP diagnostics passed; Android tests/build are blocked by the missing Java/SDK setup.

## Batch 02 implementation and verification record — 2026-10-04

- Updated `AppSettings`, `SettingsRepository`, the root `FocusFlowTheme`
  invocation, and `FocusFlowNavGraph` for General plus four nullable tab scales.
- Added Settings sliders with one-percent steps from 80–150, inherited
  “Matches General” values, and per-tab “Use General” reset actions.
- Applied Defense-scale/General-fallback sizing to all eight native overlay
  `TextView`s.
- Added `TextScaleSettingsPersistenceTest` for defaults, float round trips, and
  removal of nullable override keys. The test was not executed: the Gradle
  wrapper stops before source compilation because `java` and `JAVA_HOME` are
  unavailable; the Android SDK was also absent in the earlier environment
  inspection.
- `git diff --check` and LSP diagnostics for all changed Kotlin sources passed.
- Batch 03 route-origin wiring was completed separately; Prompt B’s `.sp` conversion remains not started.

## Batch 03 implementation and verification record — 2026-10-04

- Added validated `sourceTab` navigation arguments for secondary destinations.
  Tab-owned Settings, Stats, and Defense routes receive their owner scale when
  entered internally; direct entries without a source use General. Shared
  `ACTIVE`, `PERMISSIONS`, and `HOW_TO_USE` keep the opening tab's context.
- Verified the route ownership map against `FocusFlowNavGraph.kt` callers:
  `ACTIVE` opens from all five root tabs; `PERMISSIONS` opens from Focus,
  Settings, Defense, and the Guarded Adjustments guide; `HOW_TO_USE` opens from
  Defense and the global SideMenu. Root-tab calls and `SideMenu` route calls use
  the navigation helper; deep links and startup imports remain source-free.
- The Settings scale is retained when an import is initiated from Settings or
  the Settings-owned profile; a startup-pending import with no caller uses
  General. The route base is used for import and requested-route checks after
  destinations gained optional query arguments.
- Inline `AppPickerSheet` call sites in Home, Settings, and Defense stay inside
  their caller providers. The Stats `QuickBlockSheet` and global SideMenu are
  outside the active destination provider and now receive explicit context.
  Home stays at `1f` in its root content and Home-origin shared flows;
  `MainScaffold` remains outside all tab providers.
- Added `RouteTextScaleContextTest` for owner scales, General fallback,
  source propagation for every shared route, Home pinning, query preservation,
  and direct-entry behavior. Added a `RoutesTest` assertion that external query
  parameters cannot inject a source tab. These JUnit tests were not run.
- `git diff --check` and LSP diagnostics for changed Kotlin files passed. No
  Android build or workflow was started: `java`, `JAVA_HOME`, Android SDK
  variables, and `local.properties` are absent.
- No `.sp` values were converted. Stats, QuickBlockSheet, LauncherSetupScreen,
  ImportConfirmScreen, and `ui/common/` retain their Prompt B exclusions; the
  route context is present but raw `.sp` text in excluded files is not thereby
  scaled.

## Batch 04 dependency check — 2026-10-04

- Prompt B requires Batches 02 and 03 to be complete before any conversion
  begins. The master tracker still marks both as **Implemented; verification
  blocked**, with Android checks outstanding because Java and Android SDK setup
  are unavailable.
- Per Prompt B, Batch 04 was not claimed and no conversion or `.sp` exception
  scan was started. The worktree was checked: this gate check changed tracker
  documentation only; no Prompt B source file was edited.
- Replacement totals and the full skipped-site list are therefore **not
  assessed**; zero sites were converted, which is not a claim that no eligible
  literals exist. The Batch 04 checklist remains open until its dependency gate
  is cleared.