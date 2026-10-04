# Text Size Settings — Architecture Plan

Scope: adjustable text size in FocusFlow, via a continuous % slider — one **General** default plus optional per-tab overrides for **Schedule/Home, Focus, Stats, Settings, and Defense**. A tab override replaces General; clearing it returns that tab to General.

**Current scope update (2026-10-04):** At the user's request, Schedule/Home now has the same nullable per-tab override as the other tabs. Its screens and Home-origin shared destinations use that scale; the bottom navigation remains outside the tab provider. This supersedes the earlier Home/Schedule exclusion and 100% pin documented below.

Everything below was verified directly against `main.zip` (the uploaded Kotlin/Compose source) — file paths, line numbers, and existing patterns are quoted from the real files, not assumed.

## 0. Baseline facts (verified)

- `Routes.SETTINGS` is already its own bottom-nav tab, with full scaffolding: `ui/settings/SettingsScreen.kt`, `ui/SettingsViewModel.kt`, `data/repository/SettingsRepository.kt`, `data/model/AppSettings.kt`.
- Typography is centralized in one `FocusFlowTypography` object (`ui/theme/Theme.kt`), but `FocusFlowTheme(...)` is invoked exactly **once**, at the app root (`MainActivity.kt:327`). There is no per-tab theming boundary today — this plan introduces the first one.
- Text sizes are overwhelmingly **hardcoded literals**, not routed through the shared typography: **836** raw `X.sp` literals across the app vs. only **22** files that reference `MaterialTheme.typography.*` at all. Per-directory raw-`.sp` counts: `ui/home` 66, `ui/focus` 63, `ui/stats` 84, `ui/settings` 68, `ui/defense` 168, `ui/common` 41 (shared components), `ui/theme` 30 (the Typography object itself), `ui/onboarding` 23.
- `SettingsViewModel.updateSettings(newSettings: AppSettings)` is a **diff-and-dispatch** function: it compares `newSettings` to the current value field by field and routes changed fields to the matching repository method. Anything not claimed by an earlier `if` block falls through to a final catch-all: `if (newSettings != current) settingsRepository.setNotificationPreferences(newSettings)`. `darkModeEnabled` is persisted through exactly this catch-all today, with no dedicated setter. **New scale fields need no new ViewModel method** — they ride the same catch-all automatically.
- `SettingsRepository.setNotificationPreferences()` already has a working precedent for a *nullable* field: `lastShownDebriefSessionId: Int?` is persisted via `.apply { if (x == null) remove(KEY) else putInt(KEY, x) }`. The four per-tab overrides (nullable Float, `null` = "inherit General") should follow this exact shape.
- No `Slider` composable exists anywhere in the codebase yet — this is the first one, so §7 specifies its look precisely rather than pointing at an existing example.
- **Correction after re-tracing every route and call site** (the first pass of this plan only checked each tab's own directory): "the screen a tab opens" is bigger than that tab's own folder, and a few files sit in a directory that doesn't match who actually renders them. §8 below replaces the original directory-only scope with the traced, file-precise one — §1–7 (the architecture itself) are unaffected.

## 1. Data model — `data/model/AppSettings.kt`

New section, following the file's existing `// ── Section ──` comment convention:

```kotlin
// ── Text size ─────────────────────────────────────────────────────────────
/** 1f = 100%, i.e. today's sizes. */
val generalTextScale: Float = 1f,
/** null = inherit generalTextScale. A non-null value REPLACES it for that tab (does not stack). */
val homeTextScale: Float? = null,
val focusTextScale: Float? = null,
val statsTextScale: Float? = null,
val settingsTextScale: Float? = null,
val defenseTextScale: Float? = null,
```

## 2. Persistence — `data/repository/SettingsRepository.kt`

**Companion object** — add next to `KEY_DARK_MODE_ENABLED`:

```kotlin
private const val KEY_GENERAL_TEXT_SCALE = "general_text_scale"
private const val KEY_HOME_TEXT_SCALE = "home_text_scale"
private const val KEY_FOCUS_TEXT_SCALE = "focus_text_scale"
private const val KEY_STATS_TEXT_SCALE = "stats_text_scale"
private const val KEY_SETTINGS_TEXT_SCALE = "settings_text_scale"
private const val KEY_DEFENSE_TEXT_SCALE = "defense_text_scale"
```

**`setNotificationPreferences()`** — add one line next to `.putBoolean(KEY_DARK_MODE_ENABLED, ...)`:

```kotlin
.putFloat(KEY_GENERAL_TEXT_SCALE, settings.generalTextScale)
```

— then extend the function's existing nullable-field `.apply { }` block (the one currently handling `lastShownDebriefSessionId`) with the same remove-or-put shape for each of the five per-tab fields.

**`readAppSettings()`** — add:

```kotlin
generalTextScale = prefs.getFloat(KEY_GENERAL_TEXT_SCALE, 1f),
homeTextScale = if (prefs.contains(KEY_HOME_TEXT_SCALE)) prefs.getFloat(KEY_HOME_TEXT_SCALE, 1f) else null,
focusTextScale = if (prefs.contains(KEY_FOCUS_TEXT_SCALE)) prefs.getFloat(KEY_FOCUS_TEXT_SCALE, 1f) else null,
statsTextScale = if (prefs.contains(KEY_STATS_TEXT_SCALE)) prefs.getFloat(KEY_STATS_TEXT_SCALE, 1f) else null,
settingsTextScale = if (prefs.contains(KEY_SETTINGS_TEXT_SCALE)) prefs.getFloat(KEY_SETTINGS_TEXT_SCALE, 1f) else null,
defenseTextScale = if (prefs.contains(KEY_DEFENSE_TEXT_SCALE)) prefs.getFloat(KEY_DEFENSE_TEXT_SCALE, 1f) else null,
```

## 3. `ui/SettingsViewModel.kt` — no changes

Confirmed by reading `updateSettings()` in full: the catch-all described in §0 handles this automatically. Do not add bespoke setter methods for these fields — that would just be dead code sitting next to the existing pattern.

## 4. Theming mechanism — `ui/theme/Theme.kt`

Add next to the existing `LocalFocusFlowDarkTheme` / `LocalFocusFlowDimensions`:

```kotlin
val LocalFocusFlowTextScale = staticCompositionLocalOf { 1f }

val Number.scaledSp: TextUnit
    @Composable get() = (this.toFloat() * LocalFocusFlowTextScale.current).sp
```

Turn the `FocusFlowTypography` top-level `val` into a function of the current scale, so the 22 files reading `MaterialTheme.typography.*` also respect **General** (they cannot respect a per-tab override — Typography is only set once, at the theme root; flagged as a known limit in §9):

```kotlin
@Composable
private fun focusFlowTypography(scale: Float): Typography {
    fun TextStyle.scaled() = copy(fontSize = fontSize * scale, lineHeight = lineHeight * scale)
    return Typography(
        displaySmall = /* existing displaySmall value */.scaled(),
        // ...repeat .scaled() for every existing style currently in FocusFlowTypography
    )
}
```

`FocusFlowTheme(...)` — add a parameter and wrap the existing body:

```kotlin
@Composable
fun FocusFlowTheme(
    darkTheme: Boolean = true,
    generalTextScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalFocusFlowTextScale provides generalTextScale) {
        // ...existing body unchanged, except:
        // typography = focusFlowTypography(generalTextScale) instead of typography = FocusFlowTypography
    }
}
```

## 5. Root wiring — `MainActivity.kt`

One-line change at the existing call site (line 327):

```kotlin
com.tbtechs.focusflow.ui.theme.FocusFlowTheme(
    darkTheme = settings.darkModeEnabled,
    generalTextScale = settings.generalTextScale,
) { /* unchanged */ }
```

## 6. Per-tab wiring — `ui/navigation/FocusFlowNavGraph.kt`

`val settings by settingsViewModel.settings.collectAsState()` is already collected here (line 126) — no new parameter needed. Wrap the **inner** content of each in-scope tab's `composable()` block (inside `MainScaffold { }`, around `ScreenBoundary`) — not `MainScaffold` itself, so the bottom nav bar's own label text never changes size when you switch tabs:

```kotlin
composable(Routes.FOCUS) {
    MainScaffold(currentRoute, ::navigate) {
        CompositionLocalProvider(
            LocalFocusFlowTextScale provides (settings.focusTextScale ?: settings.generalTextScale),
        ) {
            ScreenBoundary(Routes.FOCUS) { FocusScreen(/* unchanged args */) }
        }
    }
}
```

Identically for `Routes.STATS` / `settings.statsTextScale`, `Routes.SETTINGS` / `settings.settingsTextScale`, `Routes.DEFENSE` / `settings.defenseTextScale`.

`Routes.HOME` uses `homeTextScale ?: generalTextScale`, like the other tabs:

```kotlin
composable(Routes.HOME) {
    MainScaffold(currentRoute, ::navigate) {
        CompositionLocalProvider(
            LocalFocusFlowTextScale provides (settings.homeTextScale ?: settings.generalTextScale),
        ) {
            ScreenBoundary(Routes.HOME) { HomeScreen(/* unchanged args */) }
        }
    }
}
```

These providers cover the tab roots only. A subsequent NavHost destination is
not a child of the provider that was active on the previous route. Carry and
apply the appropriate source-tab context to secondary routes and top-level
overlays as specified in §12 before calling the per-tab behavior complete.

## 7. Dedicated Text Size screen

Keep the existing `TextSizeSection` slider behavior and preference persistence,
but show the controls on a dedicated `TextSizeSettingsScreen` instead of inline
in `SettingsScreen`. The Appearance card in Settings contains a **Text Size**
action row that opens this Settings-owned destination. The screen provides a
short explanation and reuses `TextSizeSection` for General plus the five
nullable Schedule/Focus/Stats/Settings/Defense overrides and their “Use General” resets.
The existing **80%–150%** range and **100%** default are unchanged.

Do not place the full controls back in the Settings list or combine them with
the help guide. Route the new screen as Settings-owned so its text uses the
Settings scale context.

## 8. Mechanical rollout — corrected, file-precise scope

Re-traced the routes in `FocusFlowNavGraph.kt` and call sites around the four tab directories. Two things came out of that, and both matter for getting the rollout right:

1. **A file's directory doesn't always match who renders it.** Kotlin doesn't care where a file sits. Confirmed by call-site: `ui/settings/DailyAllowanceModal.kt` (31 raw-`.sp` sites) is only ever opened from Defense; `ui/launcher/AllowedAppsModal.kt` (0) only from Settings. Several secondary screens live in their own directories, each reached from exactly one tab: `ui/alwayson/` (29), `ui/keyword/` (19), `ui/profile/PasswordProtectionScreen.kt` (10), `ui/support/HowToUseScreen.kt` (9), `ui/launcher/VpnBlockListScreen.kt` (14) — all Defense-only; `ui/profile/UserProfileScreen.kt` (28), `ui/legal/` (18), `ui/support/ChangelogScreen.kt` (9) — all Settings-only. These independent NavHost destinations need the owning tab's scale context carried to them; directory placement does not provide that context.
2. **Some components are genuinely reached from more than one tab.** `Routes.ACTIVE` (`ui/active/`, 23) is opened from Focus, Stats, Settings, and Defense; `Routes.PERMISSIONS` (`ui/permissions/`, 48) is opened from Focus, Settings, and Defense. `ui/launcher/AppPickerSheet.kt` (22) is called inline from Settings, Defense, and Home. The shared NavHost destinations must follow their caller; inline sheets inherit their current composition context. Use the route/caller rules in §12 rather than assigning every shared surface General by default.

**Corrected scope, by tab** (architecture in §1–7 is unaffected — only this list changes):

| Tab | Own directory | Plus (file : sites) | New total |
|---|---|---|---|
| Focus | `ui/focus/` (63) | none found | **63** |
| Settings | `ui/settings/` (68, minus `DailyAllowanceModal.kt`'s 31 → 37) | `UserProfileScreen.kt` 28, `ui/legal/` 18, `ChangelogScreen.kt` 9, `AllowedAppsModal.kt` 0 | **92** |
| Defense | `ui/defense/` (168) | `DailyAllowanceModal.kt` 31, `ui/alwayson/` 29, `ui/keyword/` 19, `PasswordProtectionScreen.kt` 10, `HowToUseScreen.kt` 9, `VpnBlockListScreen.kt` 14 | **280** |
| Shared — convert; apply caller context per §12 | — | `ui/active/` 23, `ui/permissions/` 48, `AppPickerSheet.kt` 22 | **93** |

Revised total for the original Prompt B pass (Focus + Settings + Defense + shared; Stats and Home were not in that mechanical rollout): **528**, replacing the original ~706 (770 minus Stats' 84) estimate for those three tabs. The later Schedule-scale follow-up is separate and does not change this count.

Replace every text-sizing `X(.Y).sp` literal — in `fontSize =`, `lineHeight =`, or `letterSpacing =` position — with `X(.Y).scaledSp`, in every file above. Full file list and worked examples are in `TEXT_SIZE_PROMPTS.md`.

**Explicitly out of scope for the original Prompt B rollout:**
- `ui/home/` (66) was excluded from that mechanical pass. Batch 07 later
  converted its Compose text literals to `.scaledSp` for the Schedule override;
  do not treat the old exclusion as current behavior.
- `ui/common/` (41, shared components) — excluded from the original rollout.
  Their remaining raw `.sp` values do not respond to tab-specific overrides;
  revisit shared-component scaling separately if needed.
- `ui/onboarding/` (23) — not part of any of the four in-scope tabs.
- `ui/launcher/LauncherSetupScreen.kt` (22) — judgment call: this is the home-launcher config UI, which is its own separate workstream (`LAUNCHER_TWO_THEME_PLAN.MD`). Leaving it out even though it's Defense-reachable, unless you'd rather it track Defense.
- `ui/launcher/QuickBlockSheet.kt` (20) — triggered from Stats' quick-block flow at the NavGraph's own top level, not nested under any of the four tabs in this pass. Deferring rather than guessing, since Stats itself is out of scope this round.
- Four remaining `ui/support/` files (`DiagnosticsModal.kt`, `ReportIssueModal.kt`, `TroubleshootModal.kt`, `DiagnosticSupport.kt`) — root-level / error-boundary-triggered (called from `MainActivity.kt`, `ErrorBoundary.kt`, `PermissionsScreen.kt`), not tied to one tab. Safe to convert (same auto-resolves-per-caller reasoning as the shared row above) but left out of v1 for scope discipline, not risk.

## 9. Known limits / open items

- The 15 `ui/stats` files (and 2 `ui/common` files) that read `MaterialTheme.typography.*` will follow **General** (via §4) but **not** a Stats-specific override, since Typography is theme-root-level, not per-tab. Making those also respect a per-tab override would need a small, separate `TextStyle.scaled()` helper applied at each such call site — a different, smaller mechanical change than §8. Flagging rather than assuming either way.
- Some `.sp` literals may sit in a **non-`@Composable`** context (a top-level `val`/`TextStyle`, the way `FocusFlowTypography` itself was before §4). `.scaledSp` only works inside `@Composable` scope, so a blind replace there won't compile. `TEXT_SIZE_PROMPTS.md` tells the executing agent to flag any such case instead of guessing at a fix.
- The 80%–150% slider range is a starting recommendation, not fixed — it's one constant to change later if it's wrong.
- `HowToUseScreen.kt` and `PrivacyPolicyScreen.kt` (bucketed above as Defense-only / Settings-only respectively) are *also* shown once during first-run onboarding, outside any tab. At that point scale is just the untouched default (1.0), so there's nothing to get wrong here — noted for completeness, not as a risk.

## 10. Block screen — `enforcement/BlockOverlayActivity.kt` (separate mechanism, not Compose)

Confirmed by reading the file: this is a plain `Activity()`, not Compose — it builds its UI with raw `TextView(this).apply { textSize = X }` calls, no `setContentView(R.layout...)`, no `@Composable` anywhere. Nothing built in §1–8 can reach it; `CompositionLocal` only propagates through a Compose composition, and this screen has none.

It already reads the *same* `SharedPreferences` file `SettingsRepository` uses — `prefs = getSharedPreferences(AppBlockerAccessibilityService.PREFS_NAME, MODE_PRIVATE)` at line 153 — so no new plumbing is needed, just a direct read right there.

Add a class-level property, set immediately after that existing line:

```kotlin
private var textScale: Float = 1f
// ...in onCreate(), right after the existing prefs = getSharedPreferences(...) line:
textScale = if (prefs.contains("defense_text_scale")) {
    prefs.getFloat("defense_text_scale", 1f)
} else {
    prefs.getFloat("general_text_scale", 1f)
}
```

(Using Defense's scale here is a judgment call, not a certainty — the block overlay is fired by the accessibility service whenever any rule trips, not only ones configured from the Defense tab, but Defense is where "what happens when a block triggers" conceptually lives in this app. Say so if General fits better. These string literals must stay byte-for-byte in sync with `KEY_DEFENSE_TEXT_SCALE` / `KEY_GENERAL_TEXT_SCALE` in `SettingsRepository.kt` §2 — duplicated here rather than shared because those constants are `private` to a different class.)

Then multiply every hardcoded size by `textScale`:

| Function | Line | Current | New |
|---|---|---|---|
| `buildLockEmoji()` | 418 | `textSize = 52f` | `textSize = 52f * textScale` |
| `buildBlockedLabel()` | 428 | `textSize = 15f` | `textSize = 15f * textScale` |
| `buildReasonHeading()` | 440 | `textSize = 11f` | `textSize = 11f * textScale` |
| `buildReasonLabel()` | 452 | `textSize = 13f` | `textSize = 13f * textScale` |
| `buildQuoteView()` | 470 | `textSize = 20f` | `textSize = 20f * textScale` |
| inside `buildCountdownView()` | 482 | `textSize = 12f` | `textSize = 12f * textScale` |
| `buildSubLabel()` | 518 | `textSize = 13f` | `textSize = 13f * textScale` |
| `buildXButton()` | 536 | `textSize = 20f` | `textSize = 20f * textScale` |

Line numbers are from the copy of `main.zip` this was written against — confirm against the real file before editing in case they've drifted.

## 11. Suggested agent split

Reads as two different-shaped jobs:
- **§1–7 (data model, persistence, theming, wiring, new UI — 6 files touched, all interconnected)**: lower volume but needs correctness across files that reference each other. Reads like Terra's lane (UI rewrite, has file access) rather than Gemini's (verbatim volume) — Claude would normally take integration work like this, but that's off the table this round.
- **§8 (the 528-site mechanical rollout across the file list in the table above)**: squarely Gemini's lane — high-volume, verbatim, no cross-file reasoning required.

This is a recommendation based on the stated role split, not a hard requirement — route however fits current pipeline availability.

## 12. Screen, route, and modal coverage audit

The current source was re-traced in `ui/navigation/Routes.kt`,
`ui/navigation/FocusFlowNavGraph.kt`, `ui/settings/SettingsScreen.kt`, and
`ui/support/HowToUseScreen.kt`. This section records user-visible surfaces that
the original root-tab wiring and file list do not fully describe.

| Entry point | Current screens / overlays to account for | Text-size rule |
|---|---|---|
| Home/Schedule | `HomeScreen`; task detail/edit/quick-add and allowed-app dialogs; `AppPickerSheet` when opened from Home; shared `Routes.ACTIVE` | Home-owned text uses the Schedule override or General; Home-origin shared destinations use the caller's scale. |
| Focus | `FocusScreen`; its extension, session-debrief, standalone-block, and PIN dialogs; shared `Routes.ACTIVE`; `Routes.PERMISSIONS` | Focus-owned content uses the Focus override. Shared destinations use the caller's scale. |
| Stats | `StatsScreen`; `Routes.REPORTS` / `Routes.REPORT`; the top-level `QuickBlockSheet`; shared `Routes.ACTIVE` | Stats-specific `.sp` coverage is not in Prompt B. Keep this a visible v1 limitation; do not claim the Stats override works for raw literals or the quick-block sheet. |
| Settings | `SettingsScreen`; dedicated `TextSizeSettingsScreen` and `SettingsGuideScreen`; profile, permissions, changelog, privacy/terms, and backup/import destinations; overlay-appearance, allowed-apps, report-issue, PIN, import-choice, and destructive-confirmation dialogs | Settings-owned destinations use the Settings override. Inline dialogs inherit their caller unless their source file is an explicit exception. |
| Defense | `DefenseScreen`; Always-On, standalone-block setup, keyword blocker, VPN list, password protection, permissions, and launcher setup destinations; daily-allowance, schedule, blocked-word, standalone-block, VPN-consent, and PIN dialogs | Defense-owned destinations use the Defense override. Shared destinations use the caller's scale. How-to-Use also opens from the global SideMenu and follows its caller; direct/onboarding entry without a source tab uses General. The existing launcher-setup exclusion remains explicit. |
| Enforcement overlay | `BlockOverlayActivity`, a plain Android Activity with programmatic `TextView`s | It cannot inherit a Compose local; use the direct preference-read path in §10. |

**Current-source revalidation (2026-10-04):** The route and caller inventory was
checked against `Routes.kt`, `FocusFlowNavGraph.kt`, `MainActivity.kt`,
`SettingsGuideScreen.kt`, and current screen/modal call sites. `Routes.ACTIVE`
is opened from all five root tabs, including Home/Schedule (`HomeScreen`'s
status indicator). Home-origin use uses the Schedule override or General;
Focus, Stats, Settings, and Defense origins use their respective source scale. The Settings guide
links to `Routes.PERMISSIONS` and Defense-owned secondary routes. The dedicated
Text Size and Settings guide destinations use Settings as their scale owner.

`Routes.PERMISSIONS` is opened from Focus, Settings, and Defense, as well as from
the Settings guide. `Routes.HOW_TO_USE` remains the separate onboarding/Defense
guide and is opened from Defense and the global SideMenu available on every tab;
it follows the opening tab except for direct/onboarding entry, which uses
General. The Settings guide is a separate Settings-owned route and includes the
onboarding guide content plus the guarded-adjustment Q&As. Its launcher action
opens
`Routes.HOME_LAUNCHER_SETUP`, so launcher setup is reachable from each of those
origins, not only directly from Defense. `ActiveScreen` can also navigate onward
to Focus, Defense, Always-On, Keyword Blocker, and the VPN list; `ALWAYS_ON`,
`KEYWORD_BLOCKER`, and `VPN_BLOCK_LIST` are additionally linked from the Settings
guide, while `ALWAYS_ON` is also opened by the Stats quick-block flow. Preserve
the Schedule scale across Home-origin shared flows; shared `ACTIVE`,
`PERMISSIONS`, and `HOW_TO_USE` use their caller's scale; tab-owned routes use
the owning tab's scale; direct external/deep-link entries without a source tab
use General.

`AppPickerSheet` is invoked inline through `ui/home/AllowedAppsDialog.kt` (Home),
`ui/launcher/AllowedAppsModal.kt` (Settings), and
`ui/settings/DailyAllowanceModal.kt` (Defense); those sheets inherit their
caller's composition. `QuickBlockSheet` is rendered after the NavHost in
`FocusFlowNavGraph.kt` and is opened from Stats. Batch 03 wraps this top-level
sheet in an explicit Stats provider; its current raw `.sp` literals still do not
respond until mechanical conversion is separately in scope.
External/deep-link entry is resolved by `MainActivity.routeFromIntent()` and
`Routes.fromPath()` without a source-tab marker. The five root destinations alone
render `MainScaffold`; its tab labels are outside the inner screen content.

**Source-tab context (Batch 03):** A `CompositionLocalProvider` wrapped around
one `composable()` body does not automatically scope a different NavHost
destination. Secondary route navigation now carries a validated root-tab
argument; tab-owned routes use their owner, and shared `ACTIVE`, `PERMISSIONS`,
and `HOW_TO_USE` routes use the caller. Direct/external entries without a
source-tab argument use General. Inline dialogs and sheets inherit their caller's
provider. The top-level Stats `QuickBlockSheet` and global `SideMenu`, both
outside the active destination provider, receive explicit caller context. Home
uses its override or General in root content and caller-owned shared flows.
`SideMenu` remains in the `ui/common/` mechanical-conversion exclusion.

**Known uncovered surfaces must stay visible in the handoff:**

- `ui/stats/` is outside Prompt B, and its `.sp` literals therefore do not receive
  per-tab scaling. The Stats slider is not full coverage in v1.
- `ui/common/` dialogs/components, `ui/launcher/QuickBlockSheet.kt`,
  `ui/launcher/LauncherSetupScreen.kt`, `ui/backup/ImportConfirmScreen.kt`, and
  other individually excluded files are not converted by Prompt B. Confirm their
  actual scale behavior and list them as exceptions; do not imply every modal or
  secondary screen is covered.
- `ui/home/` text literals use `.scaledSp` so Schedule's override applies to the
  screen and editors; the shared `AppPickerSheet` already inherits its caller.
- A shared screen or modal must not be silently assigned General merely because
  its file is outside a tab directory. Verify its route/callers and use the
  caller-scale rule above where it is in scope.

## 13. Full Settings guide and guarded-adjustment content

The Settings **How to Use** action opens a separate full-screen guide implemented
in `SettingsGuideScreen.kt`. It includes the practical mode and PIN guidance
from the onboarding `HowToUseScreen.kt` plus the verified guarded-adjustment
Q&As formerly shown on the standalone Guarded Adjustments screen. Keep the
onboarding screen and its onboarding flow intact; the Settings guide is a
separate file and route with more content. The old Guarded Adjustments Settings
row, route, and screen are retired after their content is migrated.

Text-size controls remain separate: the Appearance **Text Size** action opens
`TextSizeSettingsScreen.kt`, which reuses `TextSizeSection`. Text size is not
PIN-protected and must not be described as a guarded adjustment.

Track this Settings guide and its source audit in
[`PROTECTED_ADJUSTMENTS_PLAN.md`](PROTECTED_ADJUSTMENTS_PLAN.md),
[`PROTECTED_ADJUSTMENTS_TRACKER.md`](PROTECTED_ADJUSTMENTS_TRACKER.md), and
[`AGENT_PRE_PROTECTED_ADJUSTMENTS.md`](AGENT_PRE_PROTECTED_ADJUSTMENTS.md).
The text-size implementation must not add or weaken any security guards as part
of its work. Project batch progress for both workstreams is tracked in
[`PROJECT_WORK_TRACKER.md`](PROJECT_WORK_TRACKER.md).
