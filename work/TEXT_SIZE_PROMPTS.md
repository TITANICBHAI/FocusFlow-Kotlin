# Text Size Settings — Agent Prompts

Two independent jobs. Background/rationale for both is in `TEXT_SIZE_PLAN.md` —
paste that alongside if the receiving agent can use extra context, but each
prompt below is written to stand on its own. Batch-level progress and evidence
must be recorded in `PROJECT_WORK_TRACKER.md`.

---

## Prompt A — Core wiring (suggested: Terra)

```
Add a text-size setting to FocusFlow: one General % scale plus optional per-tab
overrides for Focus, Stats, Settings, and Defense. Home/Schedule must NOT be
affected — it's being tuned by hand separately.

TRACKING IS REQUIRED: Before editing, read work/PROJECT_WORK_TRACKER.md and
claim Batch 01 with your actual agent name/handle and date if you are completing
its open audit items. Record the audit evidence, then mark Batch 01 complete
before starting code work. Claim Batch 02 and set it to In progress before
starting core wiring. Complete Batch 02 before claiming and starting Batch 03.
As checklist items are completed, tick them and add inspectable evidence on the
same line. At handoff, update only the batches you worked on, record exact
checks/results and blockers, append a dated update-log row, and synchronize
work/TEXT_SIZE_TRACKER.md. Do not mark a later batch In progress before its
dependency is complete.

Before writing anything, open and read in full: data/model/AppSettings.kt,
data/repository/SettingsRepository.kt, ui/SettingsViewModel.kt,
ui/theme/Theme.kt, ui/navigation/FocusFlowNavGraph.kt, MainActivity.kt,
ui/settings/SettingsScreen.kt, ui/settings/DarkModeToggle.kt. If anything below
doesn't match what's actually in those files (a line has moved, a name is
different), follow the real file and flag the mismatch in your summary at the
end — don't silently guess.

Make these nine changes:

1. data/model/AppSettings.kt — add:
   val generalTextScale: Float = 1f,
   val focusTextScale: Float? = null,
   val statsTextScale: Float? = null,
   val settingsTextScale: Float? = null,
   val defenseTextScale: Float? = null,
   (null on a per-tab field means "inherit generalTextScale"; a non-null value
   REPLACES it for that tab, it does not stack with General.)

2. data/repository/SettingsRepository.kt:
   - In the companion object, add KEY_GENERAL_TEXT_SCALE = "general_text_scale"
     and KEY_FOCUS_TEXT_SCALE / KEY_STATS_TEXT_SCALE / KEY_SETTINGS_TEXT_SCALE /
     KEY_DEFENSE_TEXT_SCALE with matching snake_case string values, next to the
     existing KEY_DARK_MODE_ENABLED.
   - In setNotificationPreferences(settings: AppSettings): add
     .putFloat(KEY_GENERAL_TEXT_SCALE, settings.generalTextScale) next to the
     existing .putBoolean(KEY_DARK_MODE_ENABLED, ...) line. Then extend the
     function's existing nullable-field handling (it already does this for
     lastShownDebriefSessionId: Int? via .apply { if (x == null) remove(KEY)
     else putInt(KEY, x) }) with the same remove-or-put shape for each of the
     four per-tab Float? fields, using putFloat instead of putInt.
   - In readAppSettings(): add
     generalTextScale = prefs.getFloat(KEY_GENERAL_TEXT_SCALE, 1f),
     focusTextScale = if (prefs.contains(KEY_FOCUS_TEXT_SCALE)) prefs.getFloat(KEY_FOCUS_TEXT_SCALE, 1f) else null,
     (same shape for stats/settings/defense).

3. ui/SettingsViewModel.kt — no change needed. updateSettings() already has a
   catch-all (if (newSettings != current) settingsRepository.setNotificationPreferences(newSettings))
   that will persist these new fields automatically, the same way it already
   does for darkModeEnabled. Confirm this is still true of the real file; if
   it isn't, add the minimal equivalent rather than a bespoke setter per field.

4. ui/theme/Theme.kt:
   - Add: val LocalFocusFlowTextScale = staticCompositionLocalOf { 1f }
   - Add: val Number.scaledSp: TextUnit @Composable get() =
     (this.toFloat() * LocalFocusFlowTextScale.current).sp
   - Change FocusFlowTypography from a plain val into a @Composable function
     of the current scale (e.g. private fun focusFlowTypography(scale: Float):
     Typography), multiplying every style's fontSize AND lineHeight by scale
     (add a small TextStyle.scaled() helper to avoid repeating the multiply on
     every field). Keep every existing value (weights, letterSpacing, font
     family) unchanged — only fontSize and lineHeight scale.
   - Give FocusFlowTheme(...) a new parameter: generalTextScale: Float = 1f.
     Wrap its existing body in
     CompositionLocalProvider(LocalFocusFlowTextScale provides generalTextScale) { ... }
     and pass typography = focusFlowTypography(generalTextScale) into the
     MaterialTheme(...) call instead of the old FocusFlowTypography constant.

5. MainActivity.kt — at the existing FocusFlowTheme(darkTheme = settings.darkModeEnabled) { ... }
   call, add generalTextScale = settings.generalTextScale as a second argument.

6. ui/navigation/FocusFlowNavGraph.kt — settings is already collected here via
   settingsViewModel.settings.collectAsState(). Inside each of the FOUR
   composable() blocks for Routes.FOCUS, Routes.STATS, Routes.SETTINGS,
   Routes.DEFENSE: wrap the content that's currently inside MainScaffold { ... }
   (the ScreenBoundary { ... } call and everything inside it) in:
     CompositionLocalProvider(
         LocalFocusFlowTextScale provides (settings.<tab>TextScale ?: settings.generalTextScale),
     ) { /* the existing ScreenBoundary block, unchanged */ }
   using focusTextScale / statsTextScale / settingsTextScale / defenseTextScale
   respectively. Do NOT wrap MainScaffold itself — its bottom-nav-bar labels
   must stay at whatever size the CURRENTLY ACTIVE tab dictates only for that
   tab's own content, not flicker between tabs.
   For Routes.HOME specifically, do the opposite: wrap its ScreenBoundary
   content in CompositionLocalProvider(LocalFocusFlowTextScale provides 1f) { ... }
   — a hard pin to 100%, regardless of generalTextScale. This tab must be
   completely unaffected by this feature.

   The root providers above are not sufficient by themselves: every route in
   the outer NavHost is a separate destination, so a provider inside FOCUS (for
   example) does not automatically remain active when navigation opens
   ALWAYS_ON, PERMISSIONS, or another detail destination. Implement explicit
   source-tab scale context for secondary destinations before calling Prompt A
   complete:
   - A destination owned by one tab uses that tab's override.
   - A destination shared by multiple tabs (notably ACTIVE and PERMISSIONS)
     follows the tab that opened it.
   - A direct/deep-link entry without a source tab uses General.
   - Every Home-origin flow remains pinned to 1f, including shared sheets.
   - Inline dialogs/sheets inherit their current caller context; top-level
     overlays need an explicit context if they are outside that composition.
   Use the live route and caller inventory in §12 of TEXT_SIZE_PLAN.md. Do not
   infer scope from a Kotlin file's directory alone. Keep the exclusions there
   visible in the handoff; in particular, do not claim Stats or shared
   ui/common modal coverage when those files were not converted.

8. New file ui/settings/TextSizeSettings.kt:
   - SettingsSliderRow(title: String, description: String?, valuePercent: Int,
     onValueChange: (Int) -> Unit, onValueChangeFinished: () -> Unit): title
     and description laid out like SettingsToggleRow's Column (fontSize=15.sp
     SemiBold DarkTextPrimary for title, fontSize=13.sp DarkTextSecondary
     lineHeight=16.sp for description, both from ui.theme), then on the next
     line a Material3 Slider spanning the row width plus a "N%" text to its
     right. Range 80–150, treat as whole percent steps.
   - TextSizeSection(settings: AppSettings, onUpdate: (AppSettings) -> Unit):
     one SettingsSliderRow bound to generalTextScale, then one row each for
     focusTextScale / statsTextScale / settingsTextScale / defenseTextScale —
     each showing the General value (as "Matches General") until the user
     drags it, at which point it becomes an explicit override; include a way
     to reset a per-tab row back to null (inherit General again). Call
     onUpdate(settings.copy(generalTextScale = newValue)) (etc.) — do not add
     any new ViewModel/Repository call here, updateSettings() is enough per
     item 3.
   - In ui/settings/SettingsScreen.kt, add a new section right after the
     existing "APPEARANCE" item block (before "NOTIFICATIONS"):
     item {
         SettingsSectionHeader("TEXT SIZE")
         SettingsCard { TextSizeSection(settings = settings, onUpdate = settingsViewModel::updateSettings) }
     }

9. enforcement/BlockOverlayActivity.kt (this one is NOT Compose — plain
   Activity, builds TextViews programmatically, so it needs its own small fix
   rather than the CompositionLocal machinery above):
   - Add a class-level property: private var textScale: Float = 1f
   - In onCreate(), immediately after the existing line
     prefs = getSharedPreferences(AppBlockerAccessibilityService.PREFS_NAME, MODE_PRIVATE),
     add:
       textScale = if (prefs.contains("defense_text_scale")) {
           prefs.getFloat("defense_text_scale", 1f)
       } else {
           prefs.getFloat("general_text_scale", 1f)
       }
     (These string keys must exactly match KEY_DEFENSE_TEXT_SCALE /
     KEY_GENERAL_TEXT_SCALE from item 2 above — "defense_text_scale" and
     "general_text_scale".)
   - Multiply every hardcoded textSize by textScale in these eight TextView
     builders: buildLockEmoji (52f), buildBlockedLabel (15f),
     buildReasonHeading (11f), buildReasonLabel (13f), buildQuoteView (20f),
     the TextView inside buildCountdownView (12f), buildSubLabel (13f),
     buildXButton (20f). E.g. textSize = 52f becomes textSize = 52f * textScale.
     Confirm each line/value against the real file first — sizes or line
     numbers may have drifted.

Do not touch any of the individual .sp sizing calls inside any screen file
(ui/focus, ui/stats, ui/settings, ui/defense, or any of the secondary screens
those tabs open) beyond the core-wiring changes above — that's a
528-site rollout across a separately-specified file list and is a SEPARATE,
separately-executed job (Prompt B below). Don't convert any of them yourself.

When done, summarize: every file changed, anything in the real source that
didn't match this prompt's assumptions, and anything you deliberately left
out.
```

---

## Prompt B — Mechanical `.sp` → `.scaledSp` rollout (suggested: Gemini)

```
Mechanical, repository-wide find-and-replace. No design judgment needed — if
something doesn't cleanly match the rule below, skip it and list it at the
end rather than guessing.

TRACKING IS REQUIRED: Before editing, read work/PROJECT_WORK_TRACKER.md and
confirm Batches 02 and 03 are complete. If either is still open, record the
dependency blocker and do not start the conversion. Otherwise, claim Batch 04
with your actual agent name/handle and date and set its status to In progress.
As checklist items are completed, tick them and add inspectable evidence on the
same line. At handoff, update the batch status, record exact counts, checks,
exceptions, and blockers, append a dated update-log row, and synchronize
work/TEXT_SIZE_TRACKER.md.

SCOPE — convert every file below. Nothing else, not even other files that
happen to sit in the same directory as one of these.

  ui/focus/                              (all files)
  ui/settings/                           (all files)
  ui/defense/                            (all files)
  ui/active/                             (all files)
  ui/permissions/                        (all files)
  ui/alwayson/                           (all files)
  ui/keyword/                            (all files)
  ui/legal/                              (all files)
  ui/profile/UserProfileScreen.kt
  ui/profile/PasswordProtectionScreen.kt
  ui/support/ChangelogScreen.kt
  ui/support/HowToUseScreen.kt
  ui/launcher/VpnBlockListScreen.kt
  ui/launcher/AppPickerSheet.kt
  ui/launcher/AllowedAppsModal.kt

Do NOT touch anything else — specifically:
  - ui/home/, ui/common/, ui/theme/, ui/onboarding/ (out of scope entirely)
  - ui/launcher/LauncherSetupScreen.kt and ui/launcher/QuickBlockSheet.kt
    (the only two files in ui/launcher/ NOT in the list above — everything
    else in that directory IS in scope, these two specifically are not)
  - anything in ui/support/ other than the two files named above
  - anything in ui/profile/ other than the two files named above

If you find a file in one of the "(all files)" directories that isn't in
this list because it didn't exist when this was written, convert it too —
the rule is "every file in these directories" for those, not a fixed list.
For ui/support/, ui/profile/, and ui/launcher/ the rule is the opposite: only
the exact files named above, since those directories mix files that belong
to this feature with files that don't.

RULE: In every .kt file in scope, find every text-sizing use of a numeric
literal followed by .sp — matching the pattern <integer or decimal>.sp — that
appears as the value of fontSize =, lineHeight =, or letterSpacing = (inside a
TextStyle, a Text() call, or similar). Replace it with the same number
followed by .scaledSp instead of .sp. Keep the number exactly as it is
(including decimals and negative signs) — only the unit suffix changes.

Examples:
  fontSize = 16.sp           ->  fontSize = 16.scaledSp
  fontSize = 13.5.sp         ->  fontSize = 13.5.scaledSp
  lineHeight = 20.sp         ->  lineHeight = 20.scaledSp
  letterSpacing = (-0.2).sp  ->  letterSpacing = (-0.2).scaledSp

`.scaledSp` is a new extension already added to ui/theme/Theme.kt by a
separate change (val Number.scaledSp: TextUnit @Composable get() = ...) — you
do not need to define it, just use it. No new import should be needed if the
file already imports from com.tbtechs.focusflow.ui.theme; if a file doesn't
currently import anything from that package, add
`import com.tbtechs.focusflow.ui.theme.scaledSp`.

EXCEPTIONS — do not convert, and instead list separately in your summary:
- Any .sp literal that is NOT inside a @Composable function (for example, a
  top-level val or a TextStyle built outside any @Composable) — .scaledSp
  requires @Composable scope, so converting these would fail to compile.
- Any .sp usage that is not sizing text at all, if you find one (there
  shouldn't be any — .sp in Compose is text-only — but flag instead of
  assuming if something looks different).
- Any file that fails to parse/compile after the change — revert that one
  file and list it rather than leaving broken code.

Do not change anything else in these files: no reformatting, no reordering,
no touching .dp values, no renaming.

When done, report: total files changed, total replacements made per directory
(ui/focus, ui/settings, ui/defense, ui/active, ui/permissions,
ui/alwayson, ui/keyword, ui/legal) and per individually-named file
(UserProfileScreen.kt, PasswordProtectionScreen.kt, ChangelogScreen.kt,
HowToUseScreen.kt, VpnBlockListScreen.kt, AppPickerSheet.kt,
AllowedAppsModal.kt), and the full list of anything in the exceptions above
that you skipped.
```
