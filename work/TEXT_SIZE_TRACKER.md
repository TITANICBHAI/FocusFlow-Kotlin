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
| Scoped `.sp` to `.scaledSp` rollout | Prompt B in `TEXT_SIZE_PROMPTS.md` | Implemented; Android verification blocked | 547 eligible literals converted in 32 Prompt B files. User directed source-only work to proceed while Batches 02 and 03 Android verification remains blocked; this does not claim those checks passed. |
| Route-origin scale context for secondary screens and shared dialogs/sheets | §12 in `TEXT_SIZE_PLAN.md`; Prompt A | Implemented; Android verification blocked | Batch 03 maps tab-owned destinations to their owning tab, shared `ACTIVE`, `PERMISSIONS`, and `HOW_TO_USE` to the opening tab, and direct entries to General. Inline surfaces inherit; global SideMenu and Stats QuickBlockSheet receive explicit context. |
| Integration, verification, and final handoff | Batch 05 in `PROJECT_WORK_TRACKER.md` | Implemented; Android verification blocked | Static source and scope checks pass; persistence/route tests and Android build could not run because Java and Android SDK tooling are absent. See the Batch 05 record below. |
| Screen/modal coverage audit and explicit v1 exceptions | §12 in `TEXT_SIZE_PLAN.md`; Prompt B | Source audit complete; implementation pending | Batch 01 source audit is complete and documented in §12, including Home → ACTIVE, Guarded Adjustments routes, inline sheets, and the Stats top-level overlay. Stats, shared components, and named exclusions remain out of the mechanical conversion scope. |
| Settings → Guarded Adjustments screen | Companion plan and tracker linked above | Verified | Separate full-screen, sectioned Q&A guide explains existing guarded popups/locked states and links to owner flows; it is not a popup or a duplicate control surface. Android build/test remain unavailable; see the companion tracker. |

## Completion checks

- [ ] Complete Prompt A and verify General and per-tab preference persistence, reset-to-inherit, and Home remaining at 100%. The persistence test is present, but runtime verification remains blocked; see Batch 05.
- [x] Complete Prompt B only in its listed files and report replacements and skipped exceptions. 547 conversions, zero skipped exception sites; Android compilation remains unverified, and Batches 02 and 03 remain “Implemented; verification blocked.”
- [x] Verify the route/provider mapping across §12, including shared destinations, caller-owned sheets, and Home-origin UI. `RouteTextScaleContextTest` covers caller mapping; Android execution remains blocked.
- [x] Confirm Stats override limitations and all other documented scope boundaries. The current exact scan finds 82 Stats, 41 shared-common, 65 Home, 23 onboarding, and other out-of-scope raw targets; Stats/shared typography is not fully covered by per-tab overrides.
- [ ] Keep the Guarded Adjustments screen tracked and verified separately; text-size settings are not protected adjustments.
- [x] Run the available source checks and record Android build limits. Batch 05 source assertions and `git diff --check` passed; `bash ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:connectedDebugAndroidTest` exits before Gradle starts because Java is unavailable.

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

**Historical snapshot:** This records the initial gate check before the user
directed the source-only conversion to proceed. The implementation and handoff
below supersede its then-current Batch 04 status.

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

## Batch 04 implementation and verification record — 2026-10-04

- Proceeded with the source-only conversion at the user's direction despite the
  Android verification gate for Batches 02 and 03. Their verification remains
  blocked and is not claimed as complete.
- Converted exactly the Prompt B scope: 32 Kotlin files changed from 38
  allowlisted files; 547 eligible values changed from numeric `.sp` to
  `.scaledSp`. The six scoped files with zero candidates were left unchanged:
  `DailyAllowanceDefenseDialog.kt`, `ActiveBlockScreen.kt`,
  `ActiveHeaderButton.kt`, `PermissionSupport.kt`, `DarkModeToggle.kt`, and
  `AllowedAppsModal.kt`.
- Directory counts: `ui/focus/` 65; `ui/settings/` 85; `ui/defense/` 168;
  `ui/active/` 23; `ui/permissions/` 48; `ui/alwayson/` 29;
  `ui/keyword/` 19; `ui/legal/` 18. Directory subtotal: 455.
- Individually named files: `UserProfileScreen.kt` 28;
  `PasswordProtectionScreen.kt` 10; `ChangelogScreen.kt` 9;
  `HowToUseScreen.kt` 9; `VpnBlockListScreen.kt` 14;
  `AppPickerSheet.kt` 22; `AllowedAppsModal.kt` 0. Named-file subtotal: 92.
- Skipped exception sites: none. A lexical composable-context audit checked all
  547 converted sites; each is inside an `@Composable` function. No non-text
  `.sp` unit use was found.
- Exact-transformation audit confirmed each changed Kotlin file contains only
  the required `scaledSp` import and suffix substitutions. No Kotlin changes
  were found outside Prompt B, including the explicitly excluded UI surfaces.
  `git diff --check` passed and LSP reported no diagnostics.
- Android compilation and tests were not run because Java, `JAVA_HOME`, and
  Android SDK setup are unavailable. This is a verification blocker, not a
  failed build; no Android verification is claimed.

## Batch 05 integration, verification, and handoff — 2026-10-04

- **Persistence/reset source audit:** `AppSettings` defaults General to `1f`
  and each tab override to `null`; `SettingsRepository` reads/writes all five
  values and removes nullable override keys on reset; the Settings UI shows
  “Use General” for each custom tab scale and updates via
  `SettingsViewModel.updateSettings()`.
- **Persistence test/result:** `TextScaleSettingsPersistenceTest.textScalesRoundTripAndNullOverridesRemoveTheirStoredValues`
  asserts defaults, General/per-tab float round trips, and removal of all four
  nullable preference keys. **Not run**: Gradle could not start because Java is
  absent, so persistence remains runtime-unverified.
- **Home/navigation source audit:** all five root destinations call
  `MainScaffold` outside their inner scale provider; Home's inner content
  explicitly provides `1f`. The `MainScaffold` bottom bar is a sibling to its
  content lambda and retains its fixed `11.sp` label. `navigate()` passes
  `sourceTabForDestination()` into `routeWithSourceTab()`; the route context
  maps shared `ACTIVE`, `PERMISSIONS`, and `HOW_TO_USE` to the caller, owned
  routes to their tab, and direct entries to General.
- **Route test names/results:** the five tests in
  `RouteTextScaleContextTest` cover shared and owned route scale, direct-entry
  fallback, Home pinning, source propagation, and query preservation.
  `RoutesTest.internalImportAndStatefulRoutesAreRejectedFromExternalPaths` and
  `RoutesTest.knownPublicDeepLinksStillResolve` are also present. Static source
  assertions passed; none of these JUnit tests were executed because Java is
  unavailable.
- **Prompt B scope/exceptions:** exactly 38 allowlisted Kotlin files were
  audited: 547 eligible `.scaledSp` assignments and zero eligible raw `.sp`
  assignments remain. No in-scope non-composable or non-text exception sites
  were skipped. Directory and named-file counts, including six no-candidate
  files, are in the Batch 04 record above.
- **Out-of-scope inventory:** current raw numeric text-size assignment counts
  are Stats 82, `ui/common/` 41, Home 65, onboarding 23, Theme 33,
  `LauncherSetupScreen.kt` 22, `QuickBlockSheet.kt` 20, remaining support 19,
  and `ImportConfirmScreen.kt` 0. A wider UI scan additionally found one
  `MainScaffold` bottom-bar label and two splash-screen literals outside the
  Prompt B allowlist. Total raw eligible assignments outside scope: 308.
  These are exclusions, not skipped Prompt B sites; Stats and shared-component
  scaling are not fully covered.
- **Count mismatch:** `TEXT_SIZE_PLAN.md` §8 estimates 528 in-scope values
  (Focus 63, Settings-owned 92, Defense 280, shared 93); the exact current
  source audit is 547 (Focus 65, Settings-owned 109, Defense 280, shared 93).
  The exclusion estimates for Stats and Home are also 84 and 66 in the plan,
  versus 82 and 65 in the current tree. Batch 04's measured counts are the
  current-source record; no files were added to Prompt B scope.
- **Build/test command/result:** ran
  `bash ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:connectedDebugAndroidTest`.
  It exited 1 before Gradle startup with `JAVA_HOME is not set and no 'java'
  command could be found in your PATH`. `ANDROID_HOME`, `ANDROID_SDK_ROOT`,
  `adb`, `local.properties`, and standard JDK/SDK directories are absent. No
  Android compilation, JVM test, or instrumentation test is claimed as passed.
- **Changed files:** Batch 05 changed no application source; it updated
  `PROJECT_WORK_TRACKER.md` and this tracker. The 32 Prompt B Kotlin paths in
  the current source diff are listed in the Batch 05 section of the project
  tracker. `git diff --check` and the source assertions for defaults,
  persistence/removal, reset actions, Home `1f`, bottom-bar separation, and
  caller-context wiring passed.