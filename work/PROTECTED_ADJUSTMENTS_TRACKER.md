# Protected Adjustments Work Tracker

## Reference documents

- [Plan](PROTECTED_ADJUSTMENTS_PLAN.md) — screen behavior, source audit, navigation, and completion criteria.
- [Agent context pre-prompt](AGENT_PRE_PROTECTED_ADJUSTMENTS.md) — read-only context gathering for the main agent; no implementation or subagents.
- [Text-size plan](TEXT_SIZE_PLAN.md) — adjacent Settings work and route/modal scale context; text size is not a guarded adjustment.

## Status key

- **Not started** — no implementation verified.
- **In progress** — source audit or implementation underway; leave unchecked until criteria pass.
- **Blocked** — record the exact decision or tool needed to proceed.
- **Verified** — source behavior and relevant checks support completion.

## Work items

| Work item | Status | Evidence / notes |
|---|---|---|
| Trace every live PIN- and active-block-guarded adjustment, popup, dialog, and in-screen lock state | Verified | Audited Focus, task deletion/clear-all, Defense toggles, lists, keywords, schedules, allowances, Standalone Block, access locks, PIN administration, and backup restore. Exact rules are recorded below. |
| Review guide wording against live guard behavior | Verified | The guide states that creating a new schedule is not PIN-gated; edits that weaken an existing schedule may be. |
| Add the Settings entry and internal screen route | Verified | One PROTECTION row opens the internal `guarded_adjustments` route; back uses the existing stack pop. Route is deliberately not externally linkable. |
| Build the sectioned question-and-answer guide | Verified | New full-screen accordion with five grouped sections, direct owner links, and no duplicated PIN flow or controls. |
| Verify every listed adjustment and navigation target | Verified | Q&A conditions and destinations were checked against owner screens; route assertions cover internal-only behavior. |
| Run available checks and record environment limitations | Blocked | `git diff --check` and LSP checks passed. Android compilation/unit tests cannot run: Java is absent from PATH, Android SDK variables are empty, and `local.properties` is absent. |

## Source-audit record

Audit completed 2026-10-04:

- Source files and guard branches inspected: `FocusScreen.kt`, `ActiveScreen.kt`,
  `HomeScreen.kt`, `SettingsScreen.kt`, `DefenseScreen.kt`,
  `AlwaysOnScreen.kt`, `VpnBlockListScreen.kt`, `KeywordBlockerScreen.kt`,
  `BlockedWordsModal.kt`, `GreyoutScheduleModal.kt`, `DailyAllowanceModal.kt`,
  `StandaloneBlockModal.kt`, `StandaloneBlockSetupScreen.kt`,
  `PermissionsScreen.kt`, `LauncherSetupScreen.kt`,
  `PasswordProtectionScreen.kt`, `ImportConfirmScreen.kt`, and
  `BackupCoordinator.kt`. Candidate-only paths were also searched:
  `OverlayAppearanceModal.kt`, `OnboardingScreen.kt`,
  `QuickBlockSheet.kt`, `ActiveHeaderButton.kt`, and `SettingsViewModel.kt`.
- Verified actions and exact guard conditions:
  - Ending active Focus early asks for the Focus Session PIN when configured;
    otherwise it still requires the existing stop confirmation. The
    full-duration rule uses the Focus Session PIN when disabling it.
  - Deleting a task asks for the Focus Session PIN only when one is configured.
    Clear All Tasks asks for it only when Focus is active and that PIN is set;
    cancelling keeps the active task and clears other tasks.
  - Turning off Always-On Enforcement, System Guard, Shorts/Reels blocking,
    Screen Dimmer, Vibration Harassment, Sound Alert, Network Blocking, and
    VPN Self-Healing uses the per-setting block/PIN guard. Enabling is allowed
    without a PIN; other Defense values are not implicitly PIN-gated.
  - Removing Always-On/VPN apps is blocked during active Focus or Standalone
    Block; otherwise the Defense PIN is requested when PIN protection is on.
    Adding apps remains available. Keyword additions are open; removal/clear
    requires the Defense PIN when enabled, and Standalone Block can lock
    removal.
  - New group schedules need no PIN. Weakening edits, removing apps, disabling
    a window or its VPN, shortening, and deleting may request the Defense PIN;
    an active Standalone Block prevents schedule deletion.
  - During an active block, original daily-allowance values are read-only and
    those entries cannot be removed. New apps can be added/configured and the
    active flow can clear only those new additions. After the block, removal
    requests the Defense PIN when configured.
  - During Standalone Block, existing apps and expiry are locked; adding apps
    or extending is available, with the Focus Session PIN required on save if
    configured. Stopping/clearing the active block uses that PIN when set.
    Clearing the saved Standalone Block app list separately uses the Defense
    PIN when PIN protection is enabled.
  - Permission settings are disabled during Focus or Standalone Block;
    launcher settings are disabled during Standalone Block. Replacing/removing
    a configured PIN verifies that current PIN.
  - Backup restore requests the Defense PIN only when the selected restore
    removes protection entries or disables Focus Mirror.
- Guard-related popup/dialog/locked states included: all actions above, including
  task deletion, task clear-all, password replacement/removal, and import-time
  weakening checks. The page links to their existing owner flows.
- Guide-copy correction: new schedules are not PIN-gated; the live modal gates
  weakening edits. The guide says new schedules can be added without a PIN.
- Shared destinations and navigation decisions: Settings → internal
  `GUARDED_ADJUSTMENTS` screen; back pops to Settings. Q&A links target existing
  Focus, Home, Settings, Defense, Active Blocks, Always-On, VPN list, Keyword
  Blocker, Standalone Block, Permissions, Launcher Setup, and PIN Protection
  flows. Backup/import links to Settings because restore requires a selected
  backup. The guide route is not externally linkable.
- Items intentionally excluded, with reason: onboarding PIN setup is initial
  configuration, not a guarded adjustment; ordinary confirmation dialogs do
  not protect block weakening; QuickBlock's system-app restriction prevents an
  unsafe addition; ActiveHeaderButton only summarizes status; the appearance
  modal has no PIN/active-block gate.
- Open product decisions: None.

## Verification and handoff record

| Date | Work | Checks and evidence | Remaining blockers |
|---|---|---|---|
| 2026-10-04 (initial scan) | Initial source search | Found guard hooks across Focus, Defense, Always-On, VPN, keywords, schedules, daily allowances, standalone blocking, and additional Settings/permission/backup candidates. No screen implementation or exhaustive branch verification at that stage. | Complete the source audit and implement the Settings guide. |
| 2026-10-04 | Implemented and audited Guarded Adjustments | Settings row and internal route added; five-section guide links to owner screens. Guide answers reflect the verified source. Route test asserts the guide is not externally linkable. `git diff --check` passed; LSP reported no diagnostics for changed Kotlin files. | Android compile and unit tests remain unavailable because Java/Android SDK are not configured in this workspace. |

Final handoff must name the files changed, the verified adjustment inventory,
all route/link targets, the checks actually run, and every unverified or
excluded item.