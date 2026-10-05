# FocusFlow — VPN Wiring Fix Plan (for the coding agent)

Source: static audit of `app.zip` (303 files), 2026-10-05. Nothing was run on a device.
Line numbers are from that snapshot. **Locate code by symbol name if lines have drifted.**
Package root: `app/src/main/java/com/tbtechs/focusflow/` (called `<root>/` below).

---

## 0. Ground rules

1. **No UI redesign.** Do not touch layouts, navigation, icons or copy except where a task says so.
2. **Do not bypass the coordinator.** Every VPN start/stop goes through `VpnPolicyCoordinator.requestSync / requestRecoverySync`. Keep its lock, generation counter and debounce intact.
3. **Writes go through `restoreGate.write(...)`** exactly like the existing setters in `SettingsRepository` / `VpnRepository`.
4. **Keep existing pref key names** unless a task says to migrate one. Migrations must be one-time, idempotent and must never delete user data without copying it first.
5. **Write the failing test first** for P0 tasks (see §5). If the test passes on current code, stop and report — the audit finding was wrong.
6. Do not touch the Launcher, the Linux app, or backup file-format versions.
7. After each task: build, run unit tests, and run the grep checks in §6.

---

## 1. Ground truth: who writes / reads each VPN key

All keys live in SharedPreferences `focusday_prefs`.

| Key | Written by | Read by | Status |
|---|---|---|---|
| `net_block_enabled`, `net_block_vpn` | `SettingsRepository.setNetworkBlockEnabled`, `VpnRepository.setNetworkBlockSettings` | coordinator, service, watchdog, boot, health checks | **works** |
| `net_block_explicit_packages` | `VpnRepository.setNetworkBlockSettings` (`packages`), `VpnRepository.startNetworkBlock` (seed if absent), dead `setVpnSelectedPackages` | coordinator (`effectivePolicy`, `hasPersistentVpnConfiguration`), `VpnRepository.getNetworkBlockSettings` | **works, but see T1** |
| `net_block_packages` | coordinator `requestSyncInternal` (effective targets), service `startVpn` | **coordinator + `getNetworkBlockSettings` as a fallback input** | **hazard — T1** |
| `always_on_vpn_packages` | `setAlwaysOnVpnPackages` (no caller ever changes it), backup import | `readAppSettings` → backup export, `ImportProtectionPolicy` | **never enforced — T4** |
| `net_block_focus_mirror` | `setDefensePreferences` (Defense toggle), backup import | coordinator | **works** |
| `net_block_standalone_vpn_packages` | only `publishStandaloneSnapshot` (**zero callers**) | coordinator | **dead — T5** |
| `net_block_schedule_vpn_pkgs` | only `publishScheduleVpnSnapshot` (**zero callers**) | coordinator (always-on, not time-aware) | **dead + comment contradicts code — T6** |
| `net_block_self_heal` | `VpnRepository.setVpnSelfHealEnabled` (called only by the Always-On and VPN-list save buttons) | service `onRevoke/onDestroy`, watchdog, boot, 2 health checks | **works, but the Defense toggle doesn't write it — T2** |
| `vpn_self_heal_enabled` | `setDefensePreferences` (Defense toggle) | **nothing native** | **cosmetic — T2** |
| `vpn_selected_packages` | dead `setVpnSelectedPackages` | nothing | dead |
| `net_block_global` | `setNetworkBlockSettings` only; no UI sets it | coordinator, service, watchdog, a11y | unreachable from UI |
| `net_block_wifi` (default **true**), `net_block_mobile`, `net_block_restore` | `setNetworkBlockSettings` only; no UI sets them | `startNetworkBlock` / `stopNetworkBlock` | see T8 |
| `vpn_failed_packages` | coordinator **and** service `writeStatus` (which overwrites with `[]`) | `ActiveScreen`, `ActiveHeaderButton` | clobbered — T9 |

---

## 2. Priority 0 — correctness hazards (do first)

### T1. Stop the effective-targets snapshot from feeding back into the explicit list

**Evidence**
- `VpnPolicyCoordinator.effectivePolicy` (~L283) and `hasPersistentVpnConfiguration` (~L94) read `net_block_explicit_packages` **or fall back to `net_block_packages`**.
- `requestSyncInternal` (~L155) and `NetworkBlockerVpnService.startVpn` (~L454) write `net_block_packages` = the *effective* targets (explicit + standalone + schedule + **focus-mirror** targets).
- `VpnRepository.getNetworkBlockSettings` (~L104) uses the same fallback, so the VPN-list screens would show these as the user's own picks.

**Suspected failure (confirm with the repro test in §5):** a user who enabled Network Blocking + "Mirror Focus blocking to VPN" but never saved a VPN list has no explicit key. During a focus session the snapshot is filled with the focus targets. After the session ends, the fallback reads that snapshot as "explicit", so `hasPersistentVpnConfiguration` stays true and the VPN keeps blocking those apps indefinitely.

**Change**
1. `net_block_explicit_packages` is the **only** input. Remove the `?: prefs.getString("net_block_packages", …)` fallback in the coordinator (both sites) and in `VpnRepository.getNetworkBlockSettings`.
2. One-time migration (run before the first coordinator sync, e.g. lazily inside `requestSyncInternal`, guarded by a `net_block_explicit_migrated` boolean):
   - explicit key already exists → nothing to do;
   - missing and `net_block_policy_generation == 0L` → the snapshot can only be legacy user data: copy it to explicit;
   - missing and generation `> 0` → snapshot is derived: write explicit = `"[]"`.
3. `VpnRepository.startNetworkBlock`: delete the "seed explicit if absent" block (~L268). It copies whatever the caller passes (the banner passes `packages + standalonePackages`) into the permanent list.
4. `getNetworkBlockSettingsJson` (unused) should return the explicit list, or be deleted (T10).
5. Keep writing `net_block_packages`, but document it as **diagnostic output, never input**.

**Done when:** the T1 test in §5 passes; `grep '"net_block_packages"'` shows only write sites.

---

### T2. Make the Defense "VPN Self-Healing" toggle control the real self-heal

**Evidence:** `DefenseScreen` (~L468) → `update(settings.copy(vpnSelfHealEnabled))` → `setDefensePreferences` writes `vpn_self_heal_enabled`. All native code reads `net_block_self_heal`. Only `AlwaysOnScreen` (~L254) and `VpnBlockListScreen` (~L198) write that one, via `setVpnSelfHealEnabled(hasPackages)`.

**Change**
1. Point `SettingsRepository.KEY_VPN_SELF_HEAL_ENABLED` at the string `"net_block_self_heal"` so `readAppSettings` and `setDefensePreferences` use the native key.
2. Migration: if `vpn_self_heal_enabled` exists and `net_block_self_heal` does not, copy it once.
3. In `setDefensePreferences`, when the value changed: `false` → `VpnWatchdogReceiver.cancel(appContext)`; `true` → `VpnPolicyCoordinator.requestRecoverySync(appContext)` (same as `VpnRepository.setVpnSelfHealEnabled`).
4. In both list screens, change `setVpnSelfHealEnabled(hasPackages)` to **enable only**: call `setVpnSelfHealEnabled(true)` when `hasPackages`, never write `false`. Turning self-heal off is PIN-gated in Defense; an empty list must not bypass it.
5. Keep `vpnSelfHealEnabled` in `BackupSettingsPolicy.neverApplyImportKeys`.

**Done when:** Defense toggle off ⇒ `net_block_self_heal == false`, watchdog cancelled, both `checkAndHealVpn` functions return early. Toggle on with only focus-mirror configured ⇒ self-heal is active.

---

### T3. Handle the VPN consent result instead of assuming success

**Evidence:** `VpnRepository.requestVpnPermission` calls `startActivityForResult(intent, 2001)`; no `onActivityResult` exists. `DefenseScreen` (~L600) and `GreyoutScheduleModal` (~L981) set `networkBlockEnabled` / `vpnEnabled = true` immediately, even if the user cancels. `OnboardingScreen` (~L128) and `PermissionsScreen` (~L139) already do it correctly with `rememberLauncherForActivityResult`.

**Change**
1. Add `fun consentIntentOrNull(): Intent? = VpnService.prepare(context)` to `VpnRepository`.
2. Replace every `requestVpnPermission(...)` caller with `rememberLauncherForActivityResult(StartActivityForResult)` and apply the state change **only if `VpnService.prepare(context) == null` after the result**. Callers: `DefenseScreen` (~L604), `GreyoutScheduleModal` (~L988), `AlwaysOnScreen` (~L598), `VpnBlockListScreen` (~L559), `VpnPermissionLostBanner` (~L140). Read each caller and keep its other behaviour.
3. In `GreyoutScheduleModal`, `onNetworkProtectionRequired()` must run only after consent is confirmed.
4. Delete `requestVpnPermission` and the `2001` request code once no callers remain.

**Done when:** cancelling the system dialog leaves the toggle off and `net_block_enabled` unchanged.

---

## 3. Priority 1 — wire the options that currently do nothing

### T4. Make `alwaysOnVpnPackages` real (and fix backup of the VPN list)

**Evidence:** The VPN-list screens write `net_block_explicit_packages`; backup export reads `always_on_vpn_packages` (`TsSettingsAdapter.toWireSettings` ~L307 via `AppSettings.alwaysOnVpnPackages`), so a user's list exports as `[]`. Import writes `always_on_vpn_packages` (`TsSettingsAdapter` ~L262), which the coordinator never reads. `BackupCoordinator.requiresDefensePin` (~L174) already compares against the explicit list, so the PIN gate and the apply step disagree.

**Decision (default):** `net_block_explicit_packages` is the single source of truth.

**Change**
1. `SettingsRepository.readAppSettings`: `alwaysOnVpnPackages` ← parse `net_block_explicit_packages`.
2. `SettingsRepository.setAlwaysOnVpnPackages`: write `net_block_explicit_packages` and call `requestVpnSync()`. Stop writing `always_on_vpn_packages`.
3. `TsSettingsAdapter.normalizeForLegacyMigration` (~L262): change the target key for `ALWAYS_ON_VPN_PACKAGES` to `"net_block_explicit_packages"`. `syncFromStoreAfterRestore` already calls `VpnPolicyCoordinator.requestSync`.
4. One-time migration: if legacy `always_on_vpn_packages` is non-empty, merge it into explicit (union, sorted), then leave the old key untouched.
5. `BackupCoordinator.requiresDefensePin` can read `settingsViewModel.settings.value.alwaysOnVpnPackages` instead of calling `VpnRepository`.
6. Leave `net_block_enabled` alone on import (`vpnBlockEnabled` is deliberately device-local). Surface a notice if an import brings a non-empty list while Network Blocking is off (see §7, Q3).

**Done when:** export → import round-trip preserves the VPN list **and** the tunnel enforces it after restore.

---

### T5. Standalone Block per-app VPN selection must be saved and torn down

**Evidence:** Both `StandaloneBlockModal` callers discard the VPN argument: `FocusScreen` (~L458, `onSave = { packages, untilMs, allowances, _, rawPin -> … }`) and `StandaloneBlockSetupScreen` (~L299). `StandaloneBlockAndAllowanceConfig` has no VPN field and `publishStandaloneAndAllowanceSnapshot` (~L951) never writes `net_block_standalone_vpn_packages`. The modal's `vpnPackages` parameter is never supplied, so previous picks can't be shown.

**Extra finding (needed for T5 to be safe):** the three places that clear an expired standalone block (`AppBlockerAccessibilityService` ~L1001, `ForegroundTaskService` ~L857, `BootReceiver` ~L96) flip `standalone_block_active` to false **without** calling `requestSync`. Once standalone VPN packages are real, the tunnel would outlive the block.

**Change**
1. Add `vpnPackages: List<String> = emptyList()` to `StandaloneBlockAndAllowanceConfig`; thread it through `SettingsViewModel.setStandaloneBlockAndAllowance` into `publishStandaloneAndAllowanceSnapshot`.
2. In that function's single editor commit: active ⇒ write `KEY_STANDALONE_VPN_PACKAGES`; inactive ⇒ write `"[]"`. This must happen **before** the existing `requestVpnSync()`.
3. Update both `onSave` callbacks (and `saveStandalone` in `FocusScreen`) to pass the VPN list instead of `_`.
4. Feed the modal: pass `vpnPackages = networkSettings.standalonePackages` (from `VpnRepository.getNetworkBlockSettings()`), or add `standaloneVpnPackages` to `AppSettings`.
5. `setStandaloneBlock` / `setQuickBlockTemporary` (quick block): clear `KEY_STANDALONE_VPN_PACKAGES` when inactive.
6. **Boundary sync (shared with T6):** add `VpnPolicyBoundaryScheduler` + `VpnPolicyBoundaryReceiver` (register in `AndroidManifest.xml`).
   - Compute the next boundary = min(standalone `until` if active, next start/end of any VPN-enabled schedule window).
   - Set the alarm with `setExactAndAllowWhileIdle` when `canScheduleExactAlarms()` is true, otherwise `setAndAllowWhileIdle` (a few minutes of slop is acceptable). `SCHEDULE_EXACT_ALARM` is already declared; follow `ReminderChainScheduler`.
   - The receiver calls `VpnPolicyCoordinator.requestSync(context)`.
   - The coordinator reschedules the next boundary at the end of every `requestSyncInternal`.
   - Re-arm on boot (`BootReceiver`) and on `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`.
7. Also call `requestSync` at the three expiry-clear points above (cheap, and covers the case where the alarm is late).

**Done when:** standalone block with 2 VPN apps ⇒ tunnel up with exactly those apps; at expiry ⇒ tunnel stops without any user action.

---

### T6. Make schedule "Block Network (VPN)" real, and make the coordinator honest about schedules

**Evidence:** `GreyoutScheduleModal` saves `vpnEnabled` on the schedule (also copied into each `greyout_schedule` window), but nothing reads it for enforcement. `ScheduleDraft.toSchedule()` (~L538) never sets `vpnPackages`; `ScheduleDraft.from` drops any existing one. The only consumer of schedule VPN is the coordinator's read of `net_block_schedule_vpn_pkgs`, which nothing writes, and which (if ever written) would be treated as **always active** — contradicting the header comment ("Recurring schedules … remain separate product slices"). Enabling the toggle also flips the master Network Blocking switch and self-heal via `onNetworkProtectionRequired`, so Defense reports protection that does not exist.

**Change**
1. Extract the window-matching maths from `AppBlockerAccessibilityService.isInGreyoutWindow` (~L4741) into a pure, JVM-testable function (e.g. `GreyoutWindowMath.isActive(entry, calendar)`), keep identical semantics (days 1..7 = `Calendar.DAY_OF_WEEK`; overnight windows via the "day before" rule). Make the accessibility service call it.
2. Coordinator: replace the `net_block_schedule_vpn_pkgs` read with `scheduleVpnTargets(prefs, now)`. Parse `greyout_schedule`; collect `pkg` from every entry with `vpnEnabled == true` that is **currently** inside its window.
3. `hasPersistentVpnConfiguration` must count schedule targets **only while their window is active**. Update the `schedule_vpn` reason in `persistDesiredPolicy` and the class header comment so code and comment agree.
4. Semantics: when `vpnEnabled` is on, **all of `schedule.packages`** are the VPN targets (matches the UI text "Cut internet access for these apps during this window"). Set `vpnPackages = if (vpnEnabled) packages else emptyList()` in `ScheduleDraft.toSchedule()` for consistency.
5. Call `requestVpnSync()` from `setRecurringBlockSchedules` and `setUserGreyoutWindows` (restore already syncs).
6. Use the boundary scheduler from T5 so the tunnel starts/stops at window edges.
7. Delete `publishScheduleVpnSnapshot` and the `net_block_schedule_vpn_pkgs` key (or keep the key as diagnostic only).

**Done when:** a weekday 09:00–18:00 schedule with VPN on blocks the app's network inside the window and not outside it, including an overnight window and a Sunday→Saturday wrap.

---

### T7. Make the permission-lost banner see every VPN source

**Evidence:** `MainActivity` (~L618) passes `policy.packages + policy.standalonePackages`; the banner (`VpnPermissionLostBanner` ~L80, ~L153) returns early when that list is empty. Focus-mirror-only, global and schedule setups never get a recovery prompt. The banner also calls `startNetworkBlock(vpnPackages)`, which (a) seeds the explicit list (T1) and (b) disconnects Wi-Fi by default (T8).

**Change**
1. Add `VpnRepository.getEffectiveTargets(): List<String>` (wraps `VpnPolicyCoordinator.effectivePackages`) and expose `global`.
2. Banner condition becomes `vpnBlockEnabled && (global || effectiveTargets.isNotEmpty() || mirrorActive)`.
3. Replace the `startNetworkBlock(...)` call with a new `VpnRepository.requestRecovery()` that only calls `VpnPolicyCoordinator.requestRecoverySync(context)`.

---

### T8-a. List screens must not turn the master switch off

**Evidence:** `AlwaysOnScreen.save` (~L246) and `VpnBlockListScreen.save` (~L188) write `enabled = hasPackages`, `vpn = hasPackages`, and `updateSettings(networkBlockEnabled = hasVpnPackages)`. Saving with an empty VPN list therefore turns off Network Blocking, which also silently disables focus-mirror, standalone and schedule VPN.

**Change:** list screens only turn the master **on** (when the list is non-empty). They never write `false`; Defense owns disabling (PIN-gated).

---

## 4. Priority 2 — hardening and cleanup

### T8. Wi-Fi / mobile-data side effects (**owner decision, see §7 Q2**)
`NetworkBlockSettings.wifi` defaults to **true**; `startNetworkBlock` calls `wm.disconnect()` on Android 10+, and nothing restores it (`VpnRepository.stopNetworkBlock` has no callers; `ForegroundTaskService.stopNetworkBlock` is a different function whose comment wrongly claims it restores connectivity). Mobile-data toggling uses reflection on a hidden API.
**Default plan:** delete `startNetworkBlock`, `stopNetworkBlock`, `tryDisable/RestoreWifi`, `tryDisable/RestoreMobileData` and the `wifi/mobile/restore` fields. If the owner wants them, default `wifi = false`, add UI, and make the restore path actually run.

### T9. Stop clobbering `vpn_failed_packages`
Coordinator stores "invalid/not installed" there; `NetworkBlockerVpnService.writeStatus` overwrites it with `[]` on every status change. Store invalid packages under `net_block_invalid_packages`, keep `vpn_failed_packages` for service registration failures only, and let `NetworkBlockStatus` expose both.

### T10. Dead code and stale docs
Remove only after the tasks above, and only if `grep` shows zero call sites:
`setVpnSelectedPackages` (+`vpn_selected_packages`), `publishStandaloneSnapshot` (both overloads, once T5 no longer needs it), `publishScheduleVpnSnapshot`, `getNetworkBlockSettingsJson`, `getNetworkBlockStatusJson`, `isNetworkBlockActive`, `VpnRepository.isAnotherVpnActive`.
Update the `NetworkBlockerVpnService` header (it still describes the JS bridge and says `AppBlockerAccessibilityService` calls `startNetworkBlock`) and the coordinator header.

### T11. Guard parity for the Defense master toggle
`SettingsRepository.setNetworkBlockEnabled` has no "block is active" guard, whereas `VpnRepository.setNetworkBlockSettings` throws while Focus or Standalone is active. Only the Defense screen gates the toggle. Add the active-block guard to `setNetworkBlockEnabled` (PIN verification can stay in the UI).

### T12. (Optional) Make the policy testable
Extract the pure part of `effectivePolicy` into `VpnPolicyCalculator.compute(snapshot: PrefsSnapshot, nowMs, installed: Set<String>, launcher: List<String>)` so T1/T5/T6 can be covered with plain JVM tests.

---

## 5. Tests to add

| ID | Scenario | Expected |
|---|---|---|
| T1-repro | Fresh prefs; `net_block_enabled=true`, `net_block_focus_mirror=true`, `focus_active=true`, `allowed_packages=[A]`, launcher apps `{A,B,C}`; run sync; then `focus_active=false`; run sync | targets = `[B,C]` during focus, `[]` afterwards; `hasPersistentVpnConfiguration == false`; stop command dispatched |
| T1-migrate | explicit missing + generation 0 + snapshot `[X]` | explicit becomes `[X]` once |
| T1-migrate-2 | explicit missing + generation > 0 + snapshot `[B,C]` | explicit becomes `[]` |
| T2 | toggle Defense self-heal off/on | `net_block_self_heal` follows; watchdog cancel/arm called |
| T3 | consent cancelled | `net_block_enabled` unchanged |
| T4 | export → import round-trip with explicit `[A,B]` | explicit `[A,B]` after import; coordinator targets include A,B |
| T5 | standalone with VPN `[A]`, then expiry | targets `[A]` while active; `[]` at expiry; stop dispatched without UI |
| T6 | schedule Mon–Fri 09:00–18:00 VPN on; also overnight 22:00–06:00 | targets present only inside windows (use fixed `Calendar`); Sunday after-midnight case correct |
| T7 | focus-mirror-only config + permission revoked | banner visible |
| T8-a | save empty VPN list in either screen | master switch unchanged |

---

## 6. Verification greps (all must hold at the end)

```
grep -rn '"net_block_packages"'            # only the two write sites
grep -rn 'always_on_vpn_packages'          # only migration code
grep -rn 'vpn_self_heal_enabled'           # only migration code
grep -rn 'net_block_schedule_vpn_pkgs'     # none, or diagnostic only
grep -rn 'startActivityForResult'          # none
grep -rn 'publishScheduleVpnSnapshot\|setVpnSelectedPackages'  # none
```

Manual device checklist (needs a real device or emulator with another VPN app installed):
1. Cancel the VPN consent dialog from Defense and from a schedule → toggles stay off.
2. Standalone block with a VPN app → app loses network; at expiry network returns with no interaction.
3. Defense self-heal off → revoke VPN from system settings → it is **not** restarted; on → restarted within ~3 s.
4. Focus with mirror on, end focus → tunnel stops (this is the T1 regression check).
5. Restore a backup that has a VPN list → apps blocked after restore (if Network Blocking is on).

---

## 7. Questions for the owner (the plan assumes the default; change before handing off if you disagree)

- **Q1 — schedule VPN scope.** Default: all apps in the schedule are VPN-blocked while its window is active.
- **Q2 — Wi-Fi/mobile knobs.** Default: delete them (T8). Alternative: expose with `wifi=false` default.
- **Q3 — restore with Network Blocking off.** Default: keep the list stored but dormant and show a notice; do **not** auto-enable (that would trigger VPN consent flows).
- **Q4 — single source of truth for the VPN list.** Default: `net_block_explicit_packages` (T4).

## 8. Suggested order and commit boundaries

1. T1 → T2 → T3 (three small commits, each with its tests)
2. T4, then T8-a
3. T5 + T6 together (they share the boundary scheduler and `GreyoutWindowMath`)
4. T7
5. T8, T9, T10, T11, T12

If any "Evidence" item doesn't match the code, **stop and report the mismatch** rather than adapting the fix.
