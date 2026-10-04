# Protected Adjustments — Settings Screen Plan

## Goal

Add a dedicated screen opened from a row in Settings where users can review the
adjustments and actions that FocusFlow protects. Use grouped sections with
expandable, question-style entries, following the interaction pattern of
**How to Use FocusFlow**. Explain what is protected, when a PIN or active-block
lock applies, and where the user can manage the setting.

This is its own full-screen destination, not a popup. It explains the existing
guard-related popups, dialogs, and locked states; those prompts still appear only
from their current owner flows. This is an informational/navigation guide, not a
second implementation of the underlying controls. Existing owning screens and
their PIN/active-state checks remain authoritative.

## Audit baseline (before implementation)

At audit start, the live app had a Settings tab (`Routes.SETTINGS`) and a
How-to-Use screen (`Routes.HOW_TO_USE`), but no Settings destination that
collected guarded adjustments. `SettingsScreen` contained independent sections
and local dialogs. The navigation graph already had separate destinations for
Focus, Defense, permissions, Always-On, keyword blocking, VPN lists, schedules,
password protection, and related flows. The implementation below adds the
Settings-only guide route.

`HowToUseScreen.kt` already describes these broad guard categories:

- Ending an active Focus session early and changing the full-duration Focus rule.
- Disabling protected Defense options, sometimes only while a block is active
  and otherwise with a Defense PIN.
- Removing apps from Always-On or VPN lists and removing protected keywords.
- Editing, shortening, or deleting group schedules.
- Changing other settings that reduce active protection.
- Actions that intentionally remain easier, such as adding more apps or
  keywords in the documented lists.

The source implements these checks in more than one UI path. The guide is useful
context, but it is not proof that its wording fully matches every live check.
The implementing agent must re-trace the behavior before finalizing the list.

## Required user flow

1. Add one clearly labeled Settings action row: **Guarded Adjustments**, with a
   concise description that says it explains protected changes and their rules.
2. Tapping the row opens a new full-screen destination, not a modal.
3. The new page has a back action that returns to Settings.
4. Present the content as grouped sections with expandable question-and-answer
   entries, following the existing How to Use section/card pattern. Use a
   question as each entry heading and a concise, practical answer beneath it.
   The page itself must not open a PIN dialog or act as another popup.
5. Group entries by guard type or owning area. Each answer states:
   - The adjustment/action that is protected.
   - The exact condition that triggers the Defense PIN, Focus PIN, or active
     block lock.
   - Any relevant safe-addition exception.
   - The existing screen or flow where the adjustment is managed, with a direct
     in-app navigation action when that destination can be reached safely.
6. Include a short note that the password protects changes that weaken a block;
   do not imply that every Settings value requires a PIN.
7. Keep the explanations aligned with How to Use. Correct stale or inaccurate
   guide text when the live source proves a mismatch. How to Use is the design
   and copy reference, not an additional entry point to this screen.

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
- Keep the new route out of `Routes.externalLinkableRoutes` unless a later
  product decision explicitly requires external deep links.
- Pass a Settings-scale context if text scaling has already been implemented;
  see §12 of `TEXT_SIZE_PLAN.md`. Do not duplicate any text-size controls here.
- If this screen is created after the Prompt B conversion has already run,
  record `ProtectedAdjustmentsScreen.kt` as a follow-up in-scope
  `ui/settings/` file; otherwise Prompt B's `ui/settings/ (all files)` rule
  already includes it. Do not claim its text respects the slider until checked.
- Provide direct links to existing owner screens where practical. If a guarded
  action lives in a modal inside another screen, navigate to its owner screen;
  let the existing UI open the modal through its established path.
- Do not duplicate persistent setting state, create a second PIN flow, add new
  PIN checks, remove existing checks, or create a path that mutates guarded
  state without its current guard.

## Likely files

- `app/src/main/java/com/tbtechs/focusflow/ui/settings/SettingsScreen.kt` —
  add the Settings action row and navigation callback.
- `app/src/main/java/com/tbtechs/focusflow/ui/settings/ProtectedAdjustmentsScreen.kt` —
  new explanatory/navigation screen.
- `app/src/main/java/com/tbtechs/focusflow/ui/navigation/Routes.kt` and
  `FocusFlowNavGraph.kt` — internal route and back/navigation wiring.
- `app/src/main/java/com/tbtechs/focusflow/ui/support/HowToUseScreen.kt` —
  compare its existing sections and guard explanations with the verified rules;
  update inaccurate copy if needed. Do not add a link from How to Use as another
  entry point unless the product decision changes.
- Guard-owner screens named in the inventory — inspect first; edit only if
  needed to expose a safe destination or correct a guide/source mismatch.

## Done when

- [x] Settings has one clearly named action that opens the new full-screen page.
- [x] Back navigation returns to Settings without losing the existing stack.
- [x] Every live PIN- or active-block-guarded adjustment and related user-visible
  popup/locked state is represented once, with its exact condition and owner
  screen; ordinary unrelated confirmation dialogs are excluded.
- [x] Safe additions and unguarded changes are not falsely described as locked.
- [x] Every destination link lands on the existing owner flow; no parallel
  controls or security bypasses were added.
- [x] How-to-Use content and the new page agree with current source behavior.
- [x] Add route/UI behavior tests or focused source-level checks, then run
  available project checks. Record unavailable Android tooling separately.

## Related work

- [Tracker](PROTECTED_ADJUSTMENTS_TRACKER.md)
- [Context-first agent pre-read](AGENT_PRE_PROTECTED_ADJUSTMENTS.md)
- [Text-size plan and route/modal audit](TEXT_SIZE_PLAN.md)