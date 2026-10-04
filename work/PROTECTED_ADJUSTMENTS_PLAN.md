# Protected Adjustments — Settings Screen Plan

## Goal

Include the verified Guarded Adjustments content in a full Settings **How to
Use** guide. The guide is a new file and full-screen Settings destination,
separate from the shorter onboarding `HowToUseScreen`. It combines the current
onboarding guide content with grouped, expandable questions about guarded
adjustments, PIN prompts, and active-block locks. It links to existing owner
flows; it does not replace or duplicate their controls or security checks.

The existing onboarding screen and flow remain unchanged. After the content is
migrated, remove the old Guarded Adjustments Settings entry, route, and standalone
screen.

## Audit baseline (before implementation)

At audit start, the live app had a Settings tab (`Routes.SETTINGS`), but no
Settings destination that collected guarded adjustments. `SettingsScreen`
contained independent sections and local dialogs. The navigation graph already
had separate destinations for Focus, Defense, permissions, Always-On, keyword
blocking, VPN lists, schedules, password protection, and related flows. The
implementation below adds the Settings-only guide route.

## Required user flow

1. Add a **How to Use** action in Settings' About section. Its description makes
   clear that this is a fuller guide to modes and protected changes.
2. Tapping the row opens a new full-screen guide, not a modal; the existing
   onboarding guide stays separate and unchanged.
3. The guide has a back action that returns to Settings.
4. Include the onboarding guide's practical mode and PIN sections, followed by
   grouped, expandable guarded-adjustment questions and answers. The page itself
   must not open a PIN dialog or act as another popup.
5. Group guard entries by type or owning area. Each answer states:
   - The adjustment/action that is protected.
   - The exact condition that triggers the Defense PIN, Focus PIN, or active
     block lock.
   - Any relevant safe-addition exception.
   - The existing screen or flow where the adjustment is managed, with a direct
     in-app navigation action when that destination can be reached safely.
6. Include a short note that the password protects changes that weaken a block;
   do not imply that every Settings value requires a PIN.
7. Keep every explanation aligned with the live guard-owner source. Correct
   inaccurate wording in this guide when the source proves a mismatch.

## Guarded popup and lock-state inventory

Source audit completed against the live Kotlin UI on 2026-10-04. The inventory
below records the verified guard families and owner screens; individual
conditions are detailed in the tracker. Onboarding PIN setup, ordinary
confirmations, and locks that only prevent adding an unsafe system app are not
guarded adjustments.

| Area | Existing popup/lock surface and source to inspect | Expected explanation |
|---|---|---|
| Focus session and task changes | PIN gates in `ui/focus/FocusScreen.kt`, `ui/active/ActiveScreen.kt`, `ui/home/HomeScreen.kt`, `ui/settings/SettingsScreen.kt`, and `ui/defense/DefenseScreen.kt` | Covers early stop, full-duration Focus, task deletion, and clearing all tasks while a PIN-protected Focus session is active. |
| Defense controls | Individual PIN prompts and active-block notices in `ui/defense/DefenseScreen.kt` | Covers the specific guarded disable actions; enabling these protections is allowed without a PIN. Other Defense settings do not inherit this rule automatically. |
| Always-On, VPN, and keywords | `ui/alwayson/AlwaysOnScreen.kt`, `ui/launcher/VpnBlockListScreen.kt`, `ui/keyword/KeywordBlockerScreen.kt`, and `ui/defense/BlockedWordsModal.kt` | Covers removals/clear actions, Defense PIN conditions, active-block locks, and unguarded additions. |
| Group schedules and daily allowances | `ui/defense/GreyoutScheduleModal.kt`, its `ui/defense/DefenseScreen.kt` owner callbacks, and `ui/settings/DailyAllowanceModal.kt` | New schedules and allowance additions are not PIN-gated; weakening edits/removals are guarded. During a block, existing allowance values are read-only and original entries cannot be removed. |
| Standalone block | `ui/defense/StandaloneBlockModal.kt`, `StandaloneBlockSetupScreen.kt`, and stop/clear actions in `ui/active/ActiveScreen.kt` | Covers locked existing app/expiry values, safe additions/extensions, save/clear Focus PIN prompts, saved-list Defense PIN, and stopping the active block. |
| Access and PIN administration | `ui/permissions/PermissionsScreen.kt`, `ui/launcher/LauncherSetupScreen.kt`, and `ui/profile/PasswordProtectionScreen.kt` | Permission settings are disabled during Focus or Standalone Block; launcher settings are disabled during Standalone Block; replacing/removing a PIN verifies the current PIN. |
| Backup restore | `ui/backup/ImportConfirmScreen.kt` and `ui/backup/BackupCoordinator.kt` | Defense PIN is checked only when the selected restore would weaken the protected configuration, including removing protection entries or disabling Focus Mirror. |

The page covers guard-related popups and locked states, not every ordinary
confirmation, date/time picker, permission explanation, or unrelated popup in
the app. Each answer states its own condition; PIN types are not interchangeable.

## Navigation and screen boundaries

- Add one internal route constant in `ui/navigation/Routes.kt` and one
  `composable` destination in `ui/navigation/FocusFlowNavGraph.kt`; the Settings
  row is the planned entry point.
- Keep the new Settings-guide route out of `Routes.externalLinkableRoutes`;
  remove the retired Guarded Adjustments route.
- Pass a Settings-scale context if text scaling has already been implemented;
  see §12 of `TEXT_SIZE_PLAN.md`. Do not duplicate any text-size controls here.
- The Settings guide is Settings-owned for text scaling. Keep the Text Size
  controls on their separate Settings destination; do not put them in the guide.
- Provide direct links to existing owner screens where practical. If a guarded
  action lives in a modal inside another screen, navigate to its owner screen;
  let the existing UI open the modal through its established path.
- Do not duplicate persistent setting state, create a second PIN flow, add new
  PIN checks, remove existing checks, or create a path that mutates guarded
  state without its current guard.

## Likely files

- `app/src/main/java/com/tbtechs/focusflow/ui/settings/SettingsScreen.kt` —
  add the About action; remove the former Guarded Adjustments section.
- `app/src/main/java/com/tbtechs/focusflow/ui/support/SettingsGuideScreen.kt` —
  new full guide combining onboarding and protected-adjustment content.
- `app/src/main/java/com/tbtechs/focusflow/ui/support/HowToUseScreen.kt` —
  keep the existing onboarding flow separate and unchanged.
- `app/src/main/java/com/tbtechs/focusflow/ui/navigation/Routes.kt` and
  `FocusFlowNavGraph.kt` — internal route and back/navigation wiring.
- Guard-owner screens named in the inventory — inspect first; edit only if
  needed to expose a safe destination or correct a guide/source mismatch.

## Done when

- [x] Settings' About section has a **How to Use** action that opens the full guide.
- [x] The guide includes onboarding content and all audited guard Q&As in a new
  file; the original onboarding guide remains a separate flow.
- [x] The old Guarded Adjustments Settings entry, route, and standalone screen
  are removed after migrating their content.
- [x] Back navigation returns to Settings without losing the existing stack.
- [x] Every live PIN- or active-block-guarded adjustment and related user-visible
  popup/locked state is represented once, with its exact condition and owner
  screen; ordinary unrelated confirmation dialogs are excluded.
- [x] Safe additions and unguarded changes are not falsely described as locked.
- [x] Every destination link lands on the existing owner flow; no parallel
  controls or security bypasses were added.
- [x] Guide answers reflect the verified source behavior.
- [x] Add route/UI behavior tests or focused source-level checks, then run
  available project checks. Record unavailable Android tooling separately.

## Related work

- [Project-wide work tracker](PROJECT_WORK_TRACKER.md)
- [Tracker](PROTECTED_ADJUSTMENTS_TRACKER.md)
- [Read-only agent context pre-prompt](AGENT_PRE_PROTECTED_ADJUSTMENTS.md)
- [Text-size plan and route/modal audit](TEXT_SIZE_PLAN.md)