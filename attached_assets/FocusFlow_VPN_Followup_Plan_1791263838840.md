# FocusFlow — VPN follow-up fixes (round 2)

Review of `main.zip` + `test.zip` against `FocusFlow_VPN_Wiring_Fix_Plan.md`. The first plan was implemented
well; four things remain. Same ground rules as before: no UI redesign, every VPN start/stop goes through
`VpnPolicyCoordinator`, writes go through `restoreGate.write`, locate code by symbol (line numbers drift).

Provided files (drop into `app/src/test/java/com/tbtechs/focusflow/...` in the matching package folders):

| File | Package | Purpose |
|---|---|---|
| `NetworkBlockingGuardTest.kt` | `data.repository` | Fix 1 |
| `VpnSelfHealStaleWriteTest.kt` | `data.repository` | Fix 2 |
| `VpnPolicyBoundaryDstTest.kt` | `enforcement` | Fix 4 — **fails 4/4 on current code**, passes on the reference |
| `VpnPolicyBoundaryPolicy.reference.kt` | `enforcement` | Fix 4 reference implementation (replace the contents of `VpnPolicyBoundaryPolicy.kt` after merging) |

Add the tests first and watch them fail (or fail to compile), then implement.

---

## Fix 1 — Enabling Network Blocking during a block crashes the app (HIGH)

**Problem.** `SettingsRepository.setNetworkBlockEnabled` now throws `IllegalStateException` whenever Focus or a
Standalone block is active — for *enabling* too. The original rule in `VpnRepository.setNetworkBlockSettings`
only blocks **disabling**. `SettingsViewModel.updateSettings` calls it inside `viewModelScope.launch` with no
`try/catch`, so the exception is uncaught.

Reachable two ways: (a) Defense → turn Network Blocking **on** while Focus runs; (b) master switch off + a block
active + add a VPN schedule (`onNetworkProtectionRequired` → `update(networkBlockEnabled = true)`).

**Change**
1. Add to `ActiveBlockGuardPolicy` (one rule, used by both repositories):
   ```kotlin
   /** Only turning protection OFF is locked while a block runs; turning it ON is always allowed. */
   fun mayChangeNetworkBlocking(
       currentlyEnabled: Boolean,
       requestedEnabled: Boolean,
       blockActive: Boolean,
   ): Boolean = !(currentlyEnabled && !requestedEnabled && blockActive)
   ```
2. `SettingsRepository.setNetworkBlockEnabled(enabled)`: read `currentlyOn = KEY_NETWORK_BLOCK_ENABLED || KEY_NETWORK_BLOCK_VPN`
   and throw only if `!mayChangeNetworkBlocking(currentlyOn, enabled, isBlockingSessionActive())`.
3. `VpnRepository.setNetworkBlockSettings`: replace the inline `(currentEnabled && !requestedEnabled) || (currentVpn && !requestedVpn)`
   + session check with the same helper (applied to `enabled` and `vpn`). Keep the Defense-PIN check as is.
4. `SettingsViewModel.updateSettings`: wrap the `setNetworkBlockEnabled` call in `try/catch (IllegalStateException)`.
   On failure do **not** rethrow: report through the app's existing recoverable-error surface (e.g. `AppErrorEvents`,
   shown by `ErrorAlertBanner`) and call `refreshSettingsFromStore()` so the switch snaps back to the stored value.
5. (Optional) `DefenseScreen.blockActive` requires `standaloneBlockPackages.isNotEmpty()`; the repository does not.
   Prefer one definition (`ActiveBlockGuardPolicy`).

**Done when:** `NetworkBlockingGuardTest` passes, and manually: start Focus → Defense → enable Network Blocking → no crash,
switch turns on; with the master off and Focus running, saving a schedule with VPN on → no crash; disabling during a
block is still rejected with the existing notice.

---

## Fix 2 — A stale cached setting can silently turn self-heal off (MEDIUM)

**Problem.** The Defense toggle and the native flag now share `net_block_self_heal`. `VpnBlockListScreen` refreshes the
view model after saving; `AlwaysOnScreen` does not. After saving there, the store says `true` but
`SettingsViewModel._settings.vpnSelfHealEnabled` still says `false`. The next change to *any* defense-group field
(`updateSettings` → `setDefensePreferences`) writes `false` back, `toggleDecision` returns `CANCEL_WATCHDOG`, and self-heal
is off with no user action. Self-heal is the only field in that group written by another path, so only it needs this guard.

**Change**
1. Add to `VpnSelfHealPolicy`:
   ```kotlin
   /** A field the user did not change in this edit must keep the stored value, not the cached one. */
   fun valueToPersist(loadedValue: Boolean, requestedValue: Boolean, storedValue: Boolean): Boolean =
       if (requestedValue == loadedValue) storedValue else requestedValue
   ```
2. `SettingsRepository`: add `fun isVpnSelfHealEnabledNow(): Boolean = prefs.getBoolean(KEY_VPN_SELF_HEAL_ENABLED, false)`.
3. `SettingsViewModel.updateSettings`, before `setDefensePreferences(newSettings)`:
   ```kotlin
   val selfHeal = VpnSelfHealPolicy.valueToPersist(
       loadedValue = current.vpnSelfHealEnabled,
       requestedValue = newSettings.vpnSelfHealEnabled,
       storedValue = settingsRepository.isVpnSelfHealEnabledNow(),
   )
   settingsRepository.setDefensePreferences(newSettings.copy(vpnSelfHealEnabled = selfHeal))
   ```
   and use the same `selfHeal` value wherever `updateSettings` stores the new state into `_settings`.
4. `AlwaysOnScreen` save: after `vpnRepository.setVpnSelfHealEnabled(true)` call `settingsViewModel.refreshSettingsFromStore()`
   (as `VpnBlockListScreen` does) and build the following `updateSettings(...)` from `settingsViewModel.settings.value`,
   not the `settings` captured before the save.

**Done when:** `VpnSelfHealStaleWriteTest` passes, and manually: save a VPN list in Always-On → open Defense → toggle
"Mirror Focus blocking to VPN" → "VPN Self-Healing" is still on and `net_block_self_heal` is still `true`.

---

## Fix 3 — Import switches protection on (OWNER DECISION — do not implement until answered)

**Current behaviour (implemented by the agent).** Importing a backup whose settings contain a VPN list, while Network
Blocking is off, asks for Android VPN consent (or skips the prompt if already granted) and then enables Network Blocking,
VPN and self-heal (`VpnRepository.activateImportedVpnBlock`). The first plan's default was the opposite: keep the list
stored but dormant and show a notice.

**Owner decision: `____`**
- **A — keep it.** No behaviour change. Add one line to the import-confirm screen whenever the decision is not
  `NOT_REQUIRED`: *"Importing will turn on Network Blocking for N apps."* (the already-granted path currently activates
  with no prompt at all).
- **B — dormant.** Remove `activateImportedVpnBlock`, the activation branch of `VpnImportPolicy`, and the VPN consent
  launcher in `ImportConfirmScreen`. After import show the in-app notice *"VPN list imported — Network Blocking is off."*
  Update `VpnImportPolicyTest` to match.

If no answer is given, **do nothing for Fix 3.**

---

## Fix 4 — Schedule boundary alarms can be up to 60 min late on DST days (LOW)

**Problem.** Found by comparing `nextBoundaryAfter` with a minute-by-minute scan of `isActive` (America/New_York,
Australia/Sydney, Australia/Lord_Howe): when a window edge falls in the skipped or repeated hour, the alarm is late.
Two causes:
1. `GreyoutWindowMath.nextBoundaryAfter` uses `Calendar`, which resolves a repeated local time (01:30 on fall-back day)
   to the **second** occurrence, and a skipped time (02:30 on spring-forward day) to 03:30 although the wall clock
   already satisfies the window at 03:00.
2. Android sends no broadcast for an automatic DST switch, so nothing re-syncs at the switch.

(An hourly cap on the alarm only bounds the lateness; it does not remove it. Use the change below.)

**Change.** Replace `VpnPolicyBoundaryPolicy.kt` with `VpnPolicyBoundaryPolicy.reference.kt`:
- `nextBoundaryAfter` computes edges with `java.time` (`ZonedDateTime.ofLocal(..., zone, null)` → first occurrence of a
  repeated time).
- `nextBoundaryMs` also includes `nextZoneTransitionMs(timeZone, nowMs)` (only when at least one VPN-enabled schedule
  window exists) so the policy is recomputed exactly at the DST switch.
- `isActive` and its public signatures are unchanged.

Check `minSdk`/desugaring: `TimeZone.toZoneId()` and `java.time` need API 26 or core-library desugaring. If the project
already uses `java.time` in main sources, nothing to do.

**Done when:** `VpnPolicyBoundaryDstTest` (4 tests) and the existing `VpnPolicyBoundaryPolicyTest` (6 tests) pass.

---

## Optional, low priority (separate commits)

- **Banner freshness.** `MainActivity` no longer polls settings every 1.5 s, so `VpnPermissionLostBanner` re-checks only on
  first load and `ON_RESUME`. Re-run `check()` when `settings.networkBlockEnabled` or the policy generation changes.
- **Focus expiry in the fallback poller.** In `ForegroundTaskService.fallbackPollRunnable` the focus branch clears
  `focus_active` at expiry without `VpnPolicyCoordinator.requestSync`; the standalone branch already syncs.
- **Migration failure.** In `VpnPolicyCoordinator.deferSyncUntilExplicitMigration` the `catch` rethrows inside a coroutine
  with no handler (crash if `commit()` fails). Log, clear `migrationInFlight`, and let the next sync retry.
- **Dead code.** `VpnRepository.startNetworkBlock/stopNetworkBlock/tryDisable*/tryRestore*` and the `wifi/mobile/restore`
  fields have no callers; delete them. Rename `ForegroundTaskService.stopNetworkBlock` (it only requests a sync).
- **Third copy of window maths.** `ForegroundTaskService` (~L1476) still has its own copy; switch it to
  `GreyoutWindowMath.isActive`. Note: `isActive` now rejects `endMinuteOfDay == 1440` (24:00); the Kotlin UI clamps to
  0..23, so only imported/hand-edited JSON can hit it. If legacy data may contain 24:00, allow `0..1440` for the end.

---

## Definition of done

1. All tests in `test.zip` plus the three new files pass (`./gradlew testDebugUnitTest`).
2. Manual script: Fix 1 and Fix 2 checks above; one Focus session with mirror on ends and the tunnel stops.
3. Do not touch anything outside the files named above without reporting it.
