# Fix 3 — Backup import & VPN: final decision and spec

Supersedes the "Fix 3 — OWNER DECISION" section of `FocusFlow_VPN_Followup_Plan.md`.
Based on a full read of `focusflow.zip` (import flow: `ImportConfirmScreen`, `BackupCoordinator`, `VpnImportPolicy`,
`ImportProtectionPolicy`, `BackupSettingsPolicy`, `VpnRepository.activateImportedVpnBlock`).
Same ground rules as before: no UI redesign beyond what is listed, locate code by symbol, build + run tests after each change.

---

## 1. Decision

**Keep the current behaviour.** Importing settings that contain a VPN list restores the list and, when Network Blocking
is off, turns Network Blocking on after the Android VPN consent (or straight away if the permission is already granted).

**Do not add an opt-in checkbox.** Instead close the three information gaps below: a notice before the user taps
Import, and clearer result-card text after.

Why:
1. **Consent is already in the flow.** When the VPN permission is missing, `consentDecision` returns `REQUEST_CONSENT`,
   the Android VPN dialog is shown, and if the user declines the import still succeeds with the list stored but dormant.
2. **The rest of the import already works this way.** Always-On list (enabled whenever the list is non-empty), keywords,
   schedules and daily allowances all go live straight from the file (`featureEnabled = true` in
   `buildImportedProtectionCategories`). Making only VPN opt-in would be stricter than everything else in the same import.
3. **The real problem is missing information, not missing consent:** the user is told nothing before tapping Import, the
   Android dialog appears with no explanation while the button spinner runs, and when permission is already granted
   protection turns on with no prompt at all.

(The earlier recommendation of a default-off checkbox is withdrawn.)

---

## 2. How the flow works today (verified)

| Step | Where | What happens |
|---|---|---|
| 1 | `ImportReview` | Review screen; "Sections to import" checkboxes; no VPN wording |
| 2 | `onImport` | If `requiresDefensePin` (a VPN app would be *removed*) → PIN dialog first |
| 3 | `importBackup()` | `vpnImportConsentDecision(restoreSettings)` → `NOT_REQUIRED` / `REQUEST_CONSENT` / `PERMISSION_ALREADY_GRANTED` |
| 4a | `REQUEST_CONSENT` | Android VPN dialog; callback → `performImport(..., activate = granted)` |
| 4b | `PERMISSION_ALREADY_GRANTED` | `performImport(..., activate = true)` — **no prompt** |
| 5 | `importPending` | restore → `activateImportedVpnBlock()` (enables `net_block_enabled`, `net_block_vpn`, self-heal, then `requestRecoverySync`) → refresh settings → build protection cards |
| 6 | Result dialog | "Protection settings" cards; "VPN list" shows Active/Inactive with a reason |

`consentDecision` is `NOT_REQUIRED` when settings are not restored, the file has no VPN apps, or Network Blocking is
already on. Unknown local state counts as "already on" (so it never auto-activates) — **keep that safeguard**.

## 3. Gaps to close

- **G1** No notice before Import (and silent activation when the permission is already granted).
- **G2** The Android dialog appears with no app-side explanation.
- **G3** The inactive "VPN list" card text ("…the feature is off on this device.") gives no next step.

---

## 4. Changes

Reference implementation of the two pure helpers: `VpnImportPolicy.reference.kt` (it is the current
`VpnImportPolicy.kt` plus the additions — merge it, do not lose existing functions).

### 4a. Notice on the review screen (G1, G2)

1. `VpnImportPolicy` — add `VpnImportNotice(importedAppCount, permissionGranted)`, `notice(...)` and `noticeText(...)`
   (see reference). `notice` is **derived from `consentDecision`**, so the text and the real activation can never disagree.
2. `BackupCoordinator` — extract the data gathering in `vpnImportConsentDecision` (loaded pending backup, imported
   `alwaysOnVpnPackages` count, local `enabled && vpn`, `isVpnPermissionGranted()`) into one private function used by both
   `vpnImportConsentDecision` and a new:
   ```kotlin
   internal suspend fun vpnImportNotice(restoreSettings: Boolean): VpnImportNotice?
   ```
   Keep the `getOrElse { true }` fallback for unreadable local state.
3. `ImportConfirmScreen`:
   - `var vpnNotice by remember(pendingGeneration) { mutableStateOf<VpnImportNotice?>(null) }`
   - next to the existing `requiresDefensePin` effect:
     `LaunchedEffect(pendingGeneration, parsed, restoreSettings) { vpnNotice = if (parsed is BackupParseResult.Success) runCatching { backupCoordinator.vpnImportNotice(restoreSettings) }.getOrNull() else null }`
   - pass `vpnNotice` into `ImportReview`.
4. `ImportReview` — after the replace-warning block and before the Import button, when `vpnNotice != null`, add a card
   using the same pattern as the replace warning (`Card { Row(Modifier.padding(12.dp), spacedBy(8.dp)) { Icon + Text } }`):
   `Icons.Outlined.Info` in `MaterialTheme.colorScheme.primary`, text `VpnImportPolicy.noticeText(notice)` in
   `onSurfaceVariant`. It is informational, not an error, so do not use the error colour.
5. (Optional) In "This file contains", add `SummaryRow(Icons.Outlined.Settings, "VPN-blocked apps", count)` when the
   imported count is greater than 0.

The notice disappears on its own when "Portable settings and block lists" is unchecked, when the file has no VPN apps,
or when Network Blocking is already on.

### 4b. Result-card text (G3)

In `BackupCoordinator.buildImportedProtectionCategories`, for the `"vpn"` fact only, set
`inactiveDetails = VpnImportPolicy.inactiveDetails(count, enabled, vpnPermissionAvailable)`. Leave the generic
`inactiveDetails(...)` for the other categories and leave `activeDetails` unchanged.

Resulting text:
- Network Blocking off → "N apps were imported, but Network Blocking is off. Turn it on in Defense."
- Permission missing → "…but Android VPN permission isn't granted. Allow it from Defense."

("Turn it on in Defense" is a real path: the Defense toggle now goes through the verified consent requester.)

### 4c. Do NOT change

- `consentDecision`, `shouldActivateAfterConsent`, `shouldActivateImportedVpnBlock`, `activateImportedVpnBlock`.
- `ImportProtectionPolicy` (removing VPN apps still needs the Defense PIN; adding does not).
- `BackupSettingsPolicy.neverApplyImportKeys`.
- `importBackup()` / `performImport()` control flow, the consent launcher, the external `ACTION_VIEW` import path.
- No checkbox, no new switch, no new persisted state. In particular **never put the Defense PIN into `rememberSaveable`
  or any saved-instance state.**

### 4d. Known limitations (accept, do not fix here)

- If the process is killed while the Android VPN dialog is showing, the pending-import state (`remember`) is lost and the
  user taps Import again.
- A wrong Defense PIN re-runs the decision, so the Android dialog can appear a second time.

---

## 5. Tests

Add `VpnImportNoticeTest.kt` (provided; package `com.tbtechs.focusflow.ui.backup`, goes in
`src/test/java/com/tbtechs/focusflow/ui/backup/`). 8 tests, including a truth-table test that the notice is shown exactly
when `consentDecision != NOT_REQUIRED`. They pass against the reference together with the existing `VpnImportPolicyTest`.

## 6. Manual checks

| Scenario | Expected |
|---|---|
| Permission not granted, Network Blocking off, file has 3 VPN apps | Notice mentions the Android prompt → Import → Android dialog → Allow → card **Active** |
| Same, but tap **Don't allow** | Import still succeeds; card **Inactive** with "Turn it on in Defense." |
| Permission already granted, Network Blocking off | Notice **without** the Android sentence → Import → no dialog → card Active |
| Network Blocking already on | No notice, no dialog; restored list applies through the normal sync |
| Uncheck "Portable settings and block lists" | Notice disappears; VPN list not imported |
| File without `alwaysOnVpnPackages` | No notice |
| Import that would remove a VPN app | Defense PIN still required (unchanged) |

## 7. Definition of done

1. `VpnImportNoticeTest` and the existing import tests pass (`./gradlew testDebugUnitTest`).
2. The manual table above passes on a device.
3. No change to anything listed in 4c. If something outside the files named here needs touching, report it first.
