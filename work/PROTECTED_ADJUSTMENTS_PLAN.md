# Protected Adjustments — Settings Screen Plan

## Goal

Add a dedicated screen reachable from Settings where users can review the
adjustments and actions that FocusFlow protects. Explain each guard in the same
practical style as **How to Use FocusFlow**, including what is protected, when a
PIN or active-block lock applies, and where the user can manage the setting.

This is an informational/navigation hub, not a second implementation of the
underlying controls. Existing owning screens and their PIN/active-state checks
remain authoritative.

## Current-source baseline

The live app has a Settings tab (`Routes.SETTINGS`) and a How-to-Use screen
(`Routes.HOW_TO_USE`), but no Settings destination that collects guarded
adjustments. `SettingsScreen` currently contains independent sections and local
dialogs. The navigation graph has separate destinations for Focus, Defense,
permissions, Always-On, keyword blocking, VPN lists, schedules, password
protection, and related flows.

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

1. Add a clearly labeled Settings action row: **Guarded Adjustments**, with a
   concise description that says it explains protected changes and their rules.
2. Tapping the row opens a new full-screen destination, not a modal.
3. The new page has a back action that returns to Settings.
4. Group the entries by guard type or owning area. Each entry states:
   - The adjustment/action that is protected.
   - The exact condition that triggers the Defense PIN, Focus PIN, or active
     block lock.
   - Any relevant safe-addition exception.
   - The existing screen or flow where the adjustment is managed, with a direct
     in-app navigation action when that destination can be reached safely.
5. Include a short note that the password protects changes that weaken a block;
   do not imply that every Settings value requires a PIN.
6. Keep the copy aligned with How to Use. Update its guide content or add a
   cross-link when source verification finds a missing or stale explanation.

## Guard inventory to verify before implementation

Use the live code to enumerate the complete set. At minimum, inspect:

| Area | Existing source entry points to inspect | Expected explanation |
|---|---|---|
| Focus session and duration | `ui/focus/FocusScreen.kt`, `ui/defense/DefenseScreen.kt` | Focus PIN conditions for ending early and for allowing a task to end Focus early. |
| Defense controls | `ui/defense/DefenseScreen.kt` | Which toggles are blocked during an active Focus/Standalone block and which disabling actions request the Defense PIN. Do not generalize one toggle's rule to all toggles. |
| Always-On and VPN app lists | `ui/alwayson/AlwaysOnScreen.kt`, `ui/launcher/VpnBlockListScreen.kt` | Exact removal/edit restrictions; additions that remain permitted. |
| Keyword list | `ui/keyword/KeywordBlockerScreen.kt`, `ui/defense/BlockedWordsModal.kt` | Removal/clear rules versus adding a keyword. |
| Group schedules | `ui/defense/DefenseScreen.kt`, `ui/defense/GreyoutScheduleModal.kt` | When opening, editing, shortening, deleting, or disabling a schedule asks for a PIN or is blocked. |
| Daily allowance and standalone block | `ui/settings/DailyAllowanceModal.kt`, `ui/defense/StandaloneBlockModal.kt`, `ui/defense/StandaloneBlockSetupScreen.kt` | Exact active-block and PIN rules; do not conflate creation/setup with weakening an active block. |
| Other protected flows | Search current `ui/` code for PIN verification and active-block checks, then inspect every hit | Add any genuinely guarded adjustment omitted from the list above; exclude unrelated destructive/data prompts unless the source ties them to the protection policy. |

The table is an investigation checklist, not a claim that every listed action
uses the same guard. The implementation must record the verified rule per
action and resolve any mismatch between the live source and How-to-Use copy.

## Navigation and screen boundaries

- Add one internal route constant in `ui/navigation/Routes.kt` and one
  `composable` destination in `ui/navigation/FocusFlowNavGraph.kt`.
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
  synchronize verified guide copy or add a discovery link if needed.
- Guard-owner screens named in the inventory — inspect first; edit only if
  needed to expose a safe destination or correct a guide/source mismatch.

## Done when

- [ ] Settings has one clearly named action that opens the new full-screen page.
- [ ] Back navigation returns to Settings without losing the existing stack.
- [ ] Every live PIN- or active-block-guarded adjustment is represented once,
  with its exact condition and owner screen.
- [ ] Safe additions and unguarded changes are not falsely described as locked.
- [ ] Every destination link lands on the existing owner flow; no parallel
  controls or security bypasses were added.
- [ ] How-to-Use content and the new page agree with current source behavior.
- [ ] Add route/UI behavior tests or focused source-level checks, then run
  available project checks. Record unavailable Android tooling separately.

## Related work

- [Tracker](PROTECTED_ADJUSTMENTS_TRACKER.md)
- [Context-first agent pre-read](AGENT_PRE_PROTECTED_ADJUSTMENTS.md)
- [Text-size plan and route/modal audit](TEXT_SIZE_PLAN.md)