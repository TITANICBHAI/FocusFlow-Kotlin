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
| 04 | Scoped `.sp` to `.scaledSp` conversion (Prompt B) | Replit Agent | Implemented; verification blocked | 02, 03 |
| 05 | Text Size integration, verification, and final handoff | Replit Agent | Implemented; verification blocked | 02–04 |

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

**Agent:** Replit Agent
**Last updated:** 2026-10-04
**Status:** Implemented; verification blocked
**Prompt:** Prompt B in [TEXT_SIZE_PROMPTS.md](TEXT_SIZE_PROMPTS.md)  
**Depends on:** Batch 02 and Batch 03

**Dependency note:** Prompt B asks that Batches 02 and 03 be complete before
conversion. They remain implemented with Android verification blocked. The user
directed the source-only Batch 04 conversion to proceed without waiting for the
missing Java/Android SDK; this does not change or claim completion of Batches 02
and 03 verification. No Android build or tests will be claimed for this batch.

- [x] Convert only the exact Prompt B files and directories; do not expand scope.
  **Evidence:** 32 Kotlin files changed, all within the 38-file Prompt B allowlist;
  547 eligible literals converted and no eligible `.sp` targets remain there.
- [x] Record replacement counts by directory and individually named file.
  **Evidence:** exact counts are listed below; both groups sum to 547.
- [x] Record every skipped non-composable `.sp` use or other exception; do not
  guess or silently leave unexplained gaps.
  **Evidence:** none skipped; all 547 converted sites are inside `@Composable`
  functions, and no non-text `.sp` unit use was found.
- [x] Confirm excluded areas/files remain untouched.
  **Evidence:** no Kotlin diff outside the exact Prompt B allowlist, including
  `ui/home/`, `ui/common/`, `ui/theme/`, `ui/onboarding/`, excluded launcher
  files, and other profile/support files.

**Directory replacement counts**

| Prompt B directory | Replacements |
|---|---:|
| `ui/focus/` | 65 |
| `ui/settings/` | 85 |
| `ui/defense/` | 168 |
| `ui/active/` | 23 |
| `ui/permissions/` | 48 |
| `ui/alwayson/` | 29 |
| `ui/keyword/` | 19 |
| `ui/legal/` | 18 |
| **Directory subtotal** | **455** |

**Individually named file replacement counts**

| Prompt B file | Replacements |
|---|---:|
| `UserProfileScreen.kt` | 28 |
| `PasswordProtectionScreen.kt` | 10 |
| `ChangelogScreen.kt` | 9 |
| `HowToUseScreen.kt` | 9 |
| `VpnBlockListScreen.kt` | 14 |
| `AppPickerSheet.kt` | 22 |
| `AllowedAppsModal.kt` | 0 |
| **Named-file subtotal** | **92** |

**Total:** 547 replacements across 32 changed Kotlin files. Six allowlisted files
had no eligible values and were not edited: `DailyAllowanceDefenseDialog.kt`,
`ActiveBlockScreen.kt`, `ActiveHeaderButton.kt`, `PermissionSupport.kt`,
`DarkModeToggle.kt`, and `AllowedAppsModal.kt`.

**Checks and blocker:** `git diff --check`, LSP diagnostics, exact-transformation
and excluded-surface audits passed. Android compilation/tests were not run because
Java, `JAVA_HOME`, and Android SDK setup are unavailable; source completion is
recorded without claiming Android verification.

## Batch 05 — Text Size integration, verification, and handoff

**Agent:** Replit Agent
**Last updated:** 2026-10-04
**Status:** Implemented; verification blocked
**Depends on:** Batches 02–04

- [ ] Verify General/per-tab persistence and reset-to-inherit behavior.
  **Source evidence:** `AppSettings` defaults General to `1f` and all four tab
  overrides to `null`; `SettingsRepository` persists General and writes/removes
  each nullable override; `TextSizeSection` exposes “Use General” for custom
  overrides. **Test/result:** `TextScaleSettingsPersistenceTest.textScalesRoundTripAndNullOverridesRemoveTheirStoredValues`
  covers defaults, round trips, and key removal, but was not run because Gradle
  could not start without Java.
- [x] Verify Home remains at `1f`, the bottom navigation is not wrapped, and
  secondary/shared route context follows the caller.
  **Evidence:** source assertions passed for all five root `MainScaffold` calls,
  Home's `1f` provider, the sibling `MainScaffold` bottom bar (`11.sp`), and
  source-tab propagation in `RouteTextScaleContext`. The five
  `RouteTextScaleContextTest` cases and two `RoutesTest` cases are present but
  unexecuted; Android/JVM test execution remains blocked.
- [x] Verify the Prompt B scope and report Stats, shared-component, and other
  documented exclusions without claiming full coverage.
  **Evidence:** 38 scoped Kotlin files audited; 547 eligible sites are
  `.scaledSp`, zero eligible raw `.sp` sites remain, and no in-scope exception
  sites were skipped. Current out-of-scope literals and historical count
  mismatches are itemized below.
- [ ] Run available Android build/tests and other relevant checks.
  **Blocked:** exact command
  `bash ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:connectedDebugAndroidTest`
  exits 1 before Gradle starts: `JAVA_HOME is not set and no 'java' command
  could be found in your PATH`. Android SDK variables, standard SDK/JDK
  directories, `adb`, and `local.properties` are also absent.
- [x] Complete the handoff with changed files, mismatches, conversion counts,
  skipped exceptions, checks, and unresolved blockers.
  **Evidence:** this section records source/test evidence, current excluded
  surfaces, the count mismatch with `TEXT_SIZE_PLAN.md` §8, changed files, and
  the build blocker; detailed conversion counts are in the preceding Batch 04
  record and the [Text Size tracker](TEXT_SIZE_TRACKER.md).

**Prompt B current-tree exclusion scan** — counts are numeric
`fontSize`/`lineHeight`/`letterSpacing` `.sp` assignments, not a claim of total
visual-typography coverage:

| Out-of-scope surface | Raw eligible literals still present |
|---|---:|
| `ui/stats/` | 82 |
| `ui/common/` shared components | 41 |
| `ui/home/` | 65 |
| `ui/onboarding/` | 23 |
| `ui/theme/Theme.kt` | 33 |
| `ui/launcher/LauncherSetupScreen.kt` | 22 |
| `ui/launcher/QuickBlockSheet.kt` | 20 |
| Other `ui/support/` files | 19 |
| `ui/backup/ImportConfirmScreen.kt` | 0 |
| `ui/navigation/FocusFlowNavGraph.kt` bottom-bar label | 1 |
| `ui/splash/FocusFlowSplashScreen.kt` | 2 |
| **Total outside Prompt B scope** | **308** |

`ReportIssueModal.kt` accounts for all 19 raw sites in the four excluded support
files; the other three have zero. The three non-excluded named launcher files
were included in the 38-file Prompt B audit; the only other launcher files are
the two explicitly excluded above. No additional profile Kotlin file exists.
The broader scan found the navigation and splash literals outside the named
Prompt B allowlist; they were not changed. These counts are excluded scope, not
skipped Prompt B exception sites.

**Known count mismatches:** `TEXT_SIZE_PLAN.md` §8's earlier estimate is 528;
the current exact Prompt B audit is 547. The current Focus count is 65 versus
the plan's 63, and the Settings-owned total is 109 versus 92; Defense (280) and
shared (93) match. Exclusion estimates also differ slightly: current Stats is 82
versus 84 in the plan, and Home is 65 versus 66. The conversion counts recorded
in Batch 04 are the exact current-tree counts. Separately, §9 documents
Stats/common `MaterialTheme.typography` consumers that still follow General
rather than a per-tab override; Stats coverage is therefore intentionally
partial, not complete.

**Files:** Batch 05 changed no application source; this handoff changes
`work/PROJECT_WORK_TRACKER.md` and `work/TEXT_SIZE_TRACKER.md`. The 32 Kotlin
files in the current Prompt B source diff are:

- `ui/active/`: `ActiveScreen.kt`
- `ui/alwayson/`: `AlwaysOnScreen.kt`, `VpnConsentModal.kt`,
  `VpnPermissionLostBanner.kt`
- `ui/defense/`: `BlockedAppOverlay.kt`, `BlockedWordsModal.kt`,
  `DefenseScreen.kt`, `GreyoutScheduleModal.kt`, `NuclearModeModal.kt`,
  `StandaloneBlockModal.kt`, `StandaloneBlockSetupScreen.kt`
- `ui/focus/`: `ExtendModal.kt`, `FocusScreen.kt`, `SessionDebriefModal.kt`
- `ui/keyword/`: `KeywordBlockerScreen.kt`
- `ui/launcher/`: `AppPickerSheet.kt`, `VpnBlockListScreen.kt`
- `ui/legal/`: `PrivacyPolicyScreen.kt`, `TermsOfServiceScreen.kt`
- `ui/permissions/`: `AccessibilityRestrictedRecovery.kt`, `PermissionCard.kt`,
  `PermissionsScreen.kt`, `RestrictedSettingsBanner.kt`
- `ui/profile/`: `PasswordProtectionScreen.kt`, `UserProfileScreen.kt`
- `ui/settings/`: `DailyAllowanceModal.kt`, `OverlayAppearanceModal.kt`,
  `ProtectedAdjustmentsScreen.kt`, `SettingsScreen.kt`, `TextSizeSection.kt`
- `ui/support/`: `ChangelogScreen.kt`, `HowToUseScreen.kt`

`git diff --check` and the Batch 05 static source assertions passed. The
`TextScaleSettingsPersistenceTest`, route JUnit tests, Android compilation, and
instrumentation execution remain unverified because no Java runtime or Android
SDK is available; no Android verification is claimed.

## Update log

| Date | Batch | Agent | Progress and evidence | Blockers |
|---|---|---|---|---|
| 2026-10-04 | 00 | Not recorded in the existing handoff | Baseline imported from the [Protected Adjustments tracker](PROTECTED_ADJUSTMENTS_TRACKER.md); implementation and source-level checks are documented there. | Android compilation/unit tests need a configured JDK and Android SDK. |
| 2026-10-04 | 01 | Replit Agent | Completed source revalidation; updated `TEXT_SIZE_PLAN.md` §12 to include Home → ACTIVE and current shared/deep-link/modal caller edges. | None. |
| 2026-10-04 | 02 | Replit Agent | Implemented settings, persistence, theme scaling, root-tab providers, controls, and overlay scaling; added a persistence round-trip/reset instrumentation test. `git diff --check` and LSP diagnostics pass. | `bash ./gradlew :app:compileDebugKotlin :app:assembleDebugAndroidTest` stops before compilation: no `java` command / `JAVA_HOME`; Android tests remain unrun. |
| 2026-10-04 | 03 | Replit Agent | Implemented route-owner/source-tab providers, shared-route caller propagation, explicit drawer/QuickBlock overlay context, and source-level behavior tests. `git diff --check` and LSP diagnostics pass. | Android unit tests/build not run: no `java`, `JAVA_HOME`, Android SDK variables, or `local.properties`. |
| 2026-10-04 | 04 | Replit Agent (dependency check only) | Checked the Prompt B gate; did not claim the batch or edit conversion files. Batch 04 remains unassigned and blocked. | Batches 02 and 03 are still “Implemented; verification blocked”; Prompt B prohibits starting until both are complete. |
| 2026-10-04 | 04 | Replit Agent | At the user's direction, converted 547 eligible Prompt B literals in 32 scoped Kotlin files; exact-scope, transformation, composable-context, and excluded-surface audits passed. `git diff --check` and LSP diagnostics are clean. | Android compilation/tests remain unrun because Java, `JAVA_HOME`, and Android SDK setup are unavailable; no Android verification is claimed. |
| 2026-10-04 | 05 | Replit Agent | Audited persistence/reset wiring, Home and bottom-bar scope, route-source context, exact Prompt B coverage, and current excluded literals; completed the handoff and recorded the 528-versus-547 plan mismatch. Static assertions and `git diff --check` pass. | Gradle build, JVM tests, and instrumentation tests remain unrun: the wrapper exits before startup because no `java`/`JAVA_HOME` is available; Android SDK and `adb` are absent. |