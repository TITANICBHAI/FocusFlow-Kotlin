# Text Size Settings — Architecture Plan

Scope: adjustable text size in FocusFlow, via a continuous % slider — one **General** default plus optional per-tab overrides for **Focus, Stats, Settings, Defense**. **Home/Schedule is deliberately excluded and insulated** — Himanshu is tuning its sizes by hand, so this system must never touch it, even indirectly.

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
/** 1f = 100%, i.e. today's sizes. Applies everywhere except Home/Schedule. */
val generalTextScale: Float = 1f,
/** null = inherit generalTextScale. A non-null value REPLACES it for that tab (does not stack). */
val focusTextScale: Float? = null,
val statsTextScale: Float? = null,
val settingsTextScale: Float? = null,
val defenseTextScale: Float? = null,
```

## 2. Persistence — `data/repository/SettingsRepository.kt`

**Companion object** — add next to `KEY_DARK_MODE_ENABLED`:

```kotlin
private const val KEY_GENERAL_TEXT_SCALE = "general_text_scale"
private const val KEY_FOCUS_TEXT_SCALE = "focus_text_scale"
private const val KEY_STATS_TEXT_SCALE = "stats_text_scale"
private const val KEY_SETTINGS_TEXT_SCALE = "settings_text_scale"
private const val KEY_DEFENSE_TEXT_SCALE = "defense_text_scale"
```

**`setNotificationPreferences()`** — add one line next to `.putBoolean(KEY_DARK_MODE_ENABLED, ...)`:

```kotlin
.putFloat(KEY_GENERAL_TEXT_SCALE, settings.generalTextScale)
```

— then extend the function's existing nullable-field `.apply { }` block (the one currently handling `lastShownDebriefSessionId`) with the same remove-or-put shape for each of the four per-tab fields.

**`readAppSettings()`** — add:

```kotlin
generalTextScale = prefs.getFloat(KEY_GENERAL_TEXT_SCALE, 1f),
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

**`Routes.HOME` gets the opposite treatment** — pin it to `1f` explicitly, so Schedule is insulated even from **General**, and even if a shared `ui/common` component it uses is ever converted to `.scaledSp` later:

```kotlin
composable(Routes.HOME) {
    MainScaffold(currentRoute, ::navigate) {
        CompositionLocalProvider(LocalFocusFlowTextScale provides 1f) {
            ScreenBoundary(Routes.HOME) { HomeScreen(/* unchanged args */) }
        }
    }
}
```

These providers cover the tab roots only. A subsequent NavHost destination is
not a child of the provider that was active on the previous route. Carry and
apply the appropriate source-tab context to secondary routes and top-level
overlays as specified in §12 before calling the per-tab behavior complete.

## 7. New Settings UI — `ui/settings/TextSizeSettings.kt` (new file)

New file, matching the precedent set by `DarkModeToggle.kt` (one file per nontrivial control). Contents:

- A `SettingsSliderRow` composable: title + description on one line, a Material3 `Slider` plus an "N%" readout on the line below. (`SettingsToggleRow`'s side-by-side layout is too narrow for a slider with a live readout.) Match the existing row styling exactly:
  - Title: `fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DarkTextPrimary`
  - Description: `fontSize = 13.sp, color = DarkTextSecondary, lineHeight = 16.sp`
  - Row padding: `horizontal = 12.dp, vertical = 8.dp`
- A `TextSizeSection(settings: AppSettings, onUpdate: (AppSettings) -> Unit)` composable wiring 5 of these together: General first, then Focus/Stats/Settings/Defense as override rows, each showing "Matches General" until moved, with a way to reset back to `null`.
- Suggested range: **80%–150%**, defaulting to **100%** (must default to exactly 1f / 100% so existing screens look identical until someone touches the slider).

**`ui/settings/SettingsScreen.kt`** — insert a new section right after `APPEARANCE` (before `NOTIFICATIONS`, currently starting at line 262):

```kotlin
item {
    SettingsSectionHeader("TEXT SIZE")
    SettingsCard {
        TextSizeSection(settings = settings, onUpdate = settingsViewModel::updateSettings)
    }
}
```

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

Revised total for this pass (Focus + Settings + Defense + shared; Stats and Home untouched): **528**, replacing the original ~706 (770 minus Stats' 84) estimate for these three tabs.

Replace every text-sizing `X(.Y).sp` literal — in `fontSize =`, `lineHeight =`, or `letterSpacing =` position — with `X(.Y).scaledSp`, in every file above. Full file list and worked examples are in `TEXT_SIZE_PROMPTS.md`.

**Explicitly out of scope for v1:**
- `ui/home/` (66) — Himanshu is tuning these by hand; must stay untouched.
- `ui/common/` (41, shared components) — converting these risks bleeding General's scale into Home through shared components. Leave alone for now; revisit as phase 2 if wanted (Home's explicit `1f` pin from §6 would hold regardless).
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
| Home/Schedule | `HomeScreen`; task detail/edit/quick-add and allowed-app dialogs; `AppPickerSheet` when opened from Home | Pin the entire Home-origin flow to `1f`, including shared sheets. |
| Focus | `FocusScreen`; its extension, session-debrief, standalone-block, and PIN dialogs; `Routes.ACTIVE`; `Routes.PERMISSIONS` | Focus-owned content uses the Focus override. Shared destinations use the caller's scale. |
| Stats | `StatsScreen`; `Routes.REPORTS` / `Routes.REPORT`; the top-level `QuickBlockSheet`; shared `Routes.ACTIVE` | Stats-specific `.sp` coverage is not in Prompt B. Keep this a visible v1 limitation; do not claim the Stats override works for raw literals or the quick-block sheet. |
| Settings | `SettingsScreen`; profile, permissions, changelog, privacy/terms, and backup/import destinations; overlay-appearance, allowed-apps, report-issue, PIN, import-choice, and destructive-confirmation dialogs | Settings-owned destinations use the Settings override. Inline dialogs inherit their caller unless their source file is an explicit exception. |
| Defense | `DefenseScreen`; Always-On, standalone-block setup, keyword blocker, VPN list, password protection, permissions, launcher setup, and How-to-Use destinations; daily-allowance, schedule, blocked-word, standalone-block, VPN-consent, and PIN dialogs | Defense-owned destinations use the Defense override. Shared destinations use the caller's scale. The existing launcher-setup exclusion remains explicit. |
| Enforcement overlay | `BlockOverlayActivity`, a plain Android Activity with programmatic `TextView`s | It cannot inherit a Compose local; use the direct preference-read path in §10. |

**Important navigation gap:** A `CompositionLocalProvider` wrapped around one
`composable()` body does not automatically scope a different NavHost destination.
The original §6 root wrappers alone therefore do not make a per-tab override
follow the user into secondary routes. Before marking this feature complete,
carry the source-tab context to secondary destinations and apply the corresponding
scale there. `ACTIVE` and `PERMISSIONS` are shared across tabs; they must use the
caller context rather than a hard-coded tab. Inline dialogs and sheets use their
caller's context. A direct/external entry with no source-tab context uses General;
Home-origin content remains pinned to `1f`.

**Known uncovered surfaces must stay visible in the handoff:**

- `ui/stats/` is outside Prompt B, and its `.sp` literals therefore do not receive
  per-tab scaling. The Stats slider is not full coverage in v1.
- `ui/common/` dialogs/components, `ui/launcher/QuickBlockSheet.kt`,
  `ui/launcher/LauncherSetupScreen.kt`, `ui/backup/ImportConfirmScreen.kt`, and
  other individually excluded files are not converted by Prompt B. Confirm their
  actual scale behavior and list them as exceptions; do not imply every modal or
  secondary screen is covered.
- `ui/home/` remains unconverted and pinned to `1f`, including all Home-origin
  editors and the shared `AppPickerSheet`.
- A shared screen or modal must not be silently assigned General merely because
  its file is outside a tab directory. Verify its route/callers and use the
  caller-scale rule above where it is in scope.

## 13. Separate Settings destination: Guarded Adjustments

The user also requested a dedicated Settings row that opens a separate,
full-screen **Guarded Adjustments** guide. Its content should use grouped,
expandable question-and-answer entries modeled on How to Use and explain the
existing guard-related popups and locked states; the page itself is not a popup.
This is separate from text-size scaling: text-size controls are not
PIN-protected and must not be moved into or described as guarded.

Track this companion feature in
[`PROTECTED_ADJUSTMENTS_PLAN.md`](PROTECTED_ADJUSTMENTS_PLAN.md),
[`PROTECTED_ADJUSTMENTS_TRACKER.md`](PROTECTED_ADJUSTMENTS_TRACKER.md), and
[`AGENT_PRE_PROTECTED_ADJUSTMENTS.md`](AGENT_PRE_PROTECTED_ADJUSTMENTS.md).
The text-size implementation must not add or weaken any security guards as part
of its work.
