# FocusFlow VPN wiring fix — batch tracker

**Implementation plan:** [VPN_WIRING_FIX_PLAN.md](VPN_WIRING_FIX_PLAN.md)
**Agent instructions:** [VPN_WIRING_AGENT_PRE_PROMPT.md](VPN_WIRING_AGENT_PRE_PROMPT.md)
**Overall status:** Batch 4 is complete for sequencing. Batch 5 implementation, unit suite, and debug APK build are complete; its real-device alarm/tunnel check remains blocked. Batch 6 T7 code and automated tests are complete; actual permission-revocation UI behavior remains unverified without a device. Batch 7's approved T9–T11 scope is complete; Q2 retains existing Wi-Fi/mobile behavior, and the optional T12 calculator was skipped. No Batch 8+ work is authorized.
**Last updated:** 2026-10-05

## Tracking rules

1. Update this tracker while work is happening. Do not reconstruct progress later from memory.
2. Leave checklist items unchecked until completed. Every checked item needs evidence in its batch work log.
3. Complete Batch 0 and report its read-only findings before changing code. The plan is a static audit of an uploaded source snapshot; verify its findings against the current repository.
4. Search by symbol and behavior rather than relying on the plan's snapshot line numbers.
5. Keep the plan's order and scope. Add P0 regression tests before changing behavior, but defer all test execution until final verification by owner instruction; if a repro test passes then, stop that task and report the mismatch.
6. Stop at unanswered owner-decision gates. A plan recommendation or default is not approval. Record the owner's exact decision and date before dependent implementation.
7. Record each batch's status, date, changed files, commands/checks, results, evidence, and blockers or decisions. Update the log before stopping or handing work off.
8. Do not mark work complete when required checks are unavailable. Record what could not run and why.
9. Route every VPN start/stop through `VpnPolicyCoordinator.requestSync` or `requestRecoverySync`. Keep its lock, generation counter, and debounce behavior intact.
10. Route writes through `restoreGate.write(...)` as the existing repositories do. Preserve preferences and backup data during migrations.
11. Do not touch the Launcher, Linux app, or backup file-format versions. Avoid UI, navigation, icon, and copy changes except where the plan explicitly requires them.
12. Do not push changes, start or poll GitHub Actions, or use the GitHub workflow unless the owner explicitly asks.
13. Keep project memory for durable constraints, not this work's progress log.

**Status values:** Not started · In progress · Blocked · Complete · Deferred

## Batch 0 — read-only baseline verification

**Status:** Complete
**Gate:** Finish and report this batch before any implementation.

- [x] Confirm current writers, readers, and call sites for every VPN preference listed in plan §1.
- [x] Re-verify the T1–T11 evidence against the current source; record any mismatches or findings already fixed.
- [x] Locate existing tests and identify the narrowest test seams for each approved task.
- [x] Check JDK 17, `JAVA_HOME`, Android SDK variables, `local.properties`, and available device/emulator tooling.
- [x] Review the relevant `SettingsRepository`, `VpnRepository`, coordinator, service, screen, schedule, backup, and permission code before proposing implementation.
- [x] Record the baseline findings and any owner decisions needed before coding.

### Batch 0 work log

| Date | Status / work performed | Files inspected or changed | Commands and checks | Findings / evidence / blockers |
|---|---|---|---|---|
| 2026-10-05 | Complete; read-only baseline verification. No application code changed. | `SettingsRepository.kt`, `VpnRepository.kt`, `VpnPolicyCoordinator.kt`, `NetworkBlockerVpnService.kt`, `VpnWatchdogReceiver.kt`, `BootReceiver.kt`, `ForegroundTaskService.kt`, `AppBlockerAccessibilityService.kt`, Defense/Always-On/VPN-list/permission-banner/schedule/import screens, settings models, backup adapter/coordinator/policy, existing related tests, Gradle config, `app/PERSISTENCE_CONTRACT.md`; this tracker updated | `rg` over `app/src` for every §1 preference and T1–T11 symbols/call sites; inspected affected code ranges and tests; counted 24 JVM test files and 4 instrumented test files; checked `java`, direct JDK 17 path/version, `JAVA_HOME`, Android SDK variables/paths, root and app `local.properties`, `adb`, emulator and SDK tools. No build/test/device command run. | Findings recorded below. Source evidence mostly matches the static plan; qualifications and documentation/owner gates are recorded below. Direct environment has JDK 17.0.15 in the Nix store but not on `PATH`/`JAVA_HOME`, and no Android SDK or device tools. |

**Preference writers, readers, and call-site baseline (plan §1):**

| Preference | Current writers / readers / call-site findings |
|---|---|
| `net_block_enabled`, `net_block_vpn` | Written by `SettingsRepository.setNetworkBlockEnabled` and `VpnRepository.setNetworkBlockSettings`; read by the coordinator, VPN service, watchdog/boot and health paths. Defense changes the setting through `SettingsViewModel`. |
| `net_block_explicit_packages` | Written by `VpnRepository.setNetworkBlockSettings`; `VpnRepository.startNetworkBlock` also seeds it if absent. The unused `SettingsRepository.setVpnSelectedPackages` writes both this key and `vpn_selected_packages`. Read by coordinator and repository, both with a fallback to `net_block_packages`. |
| `net_block_packages` | Written by `VpnPolicyCoordinator.requestSyncInternal` and by `NetworkBlockerVpnService` after tunnel establishment. Still read as a fallback by the coordinator and `VpnRepository.getNetworkBlockSettings`; the unused JSON settings getter also reads it. Thus it is currently both derived output and an input. |
| `always_on_vpn_packages` | Read/written by `SettingsRepository` and mapped by `TsSettingsAdapter` for backup import/export. Native VPN enforcement does not read it. The VPN list screens write the explicit key instead; the `AppSettings` setter path has no current screen caller that selects this list. |
| `net_block_focus_mirror` | Written by `SettingsRepository.setDefensePreferences` and portable restore; read by settings state and `VpnPolicyCoordinator`, which derives focus targets while focus is active. |
| `net_block_standalone_vpn_packages` | Read by coordinator and `VpnRepository`. `VpnRepository.setNetworkBlockSettings` can write it, and the unused `SettingsRepository.publishStandaloneSnapshot` writes it. The active standalone save path, `publishStandaloneAndAllowanceSnapshot`, does not persist a VPN package list. |
| `net_block_schedule_vpn_pkgs` | Read as a static target list by coordinator policy and persistence logic. `SettingsRepository.publishScheduleVpnSnapshot` is its only writer and has no external caller. Current schedule persistence instead stores schedule/window JSON. |
| `net_block_self_heal` | Written by `VpnRepository.setVpnSelfHealEnabled`, called by Always-On and VPN-list saves. Read by VPN service, watchdog, boot, foreground/accessibility health paths. |
| `vpn_self_heal_enabled` | Read/written by `SettingsRepository` for the Defense UI and excluded from backup application by `BackupSettingsPolicy`; no native enforcement reader was found. It is separate from `net_block_self_heal`. |
| `vpn_selected_packages` | Written only by the unused `SettingsRepository.setVpnSelectedPackages`; no reader or call site found. |
| `net_block_global` | Written through `VpnRepository.setNetworkBlockSettings`; read by repository, coordinator, VPN service, watchdog and accessibility enforcement. No active UI control for it was found. |
| `net_block_wifi`, `net_block_mobile`, `net_block_restore` | Written/read by `VpnRepository` settings/start/stop paths. No active UI controls were found. Wi-Fi defaults to `true`, mobile data to `false`, restore to `true`. |
| `vpn_failed_packages` | Written by the coordinator with invalid/uninstalled targets and by service `writeStatus` with service registration failures (default `[]` on other status writes); exposed as one `NetworkBlockStatus.failedPackages` list. |

**T1–T11 source verification:**

- **T1 — confirmed structurally during Batch 0; runtime repro recorded in Batch 1.** Both coordinator source-selection sites and `VpnRepository.getNetworkBlockSettings` fell back from explicit selections to `net_block_packages`; `startNetworkBlock` seeded the explicit key if absent. The focus-mirror stale-snapshot regression failed before the behavior changes; see the Batch 1 work log.
- **T2 — confirmed.** `SettingsRepository` reads/writes `vpn_self_heal_enabled`; enforcement reads `net_block_self_heal`. Both list screens call `setVpnSelfHealEnabled(hasPackages)`, so an empty list can write `false`. No migration or focused toggle/watchdog test exists.
- **T3 — confirmed with a grep qualification.** The repository still uses request code `2001` and `startActivityForResult`; callers are Defense, Greyout schedule, Always-On, VPN list, and permission-lost banner. The first two update enabled state immediately after launching consent; list/Always-On ask the user to save again, and the banner waits a fixed delay before checking. `ImportConfirmScreen` has no consent-result path. A repository-wide `startActivityForResult` grep will not become empty: `UsageStatsRepository` independently uses request code `1001` for admin permission, so the final check must be VPN-scoped.
- **T4 — current mismatch confirmed; owner/spec gates remain.** `readAppSettings`, export, and legacy migration use `always_on_vpn_packages`, while enforcement uses the explicit key; `BackupCoordinator.requiresDefensePin` compares the explicit list. Import has no VPN-consent activation flow or protection-category summary; its current success dialog reports task counts and warnings. Q3’s import-consent decision is recorded in the tracker, but Q4 remains pending. The format guide references `fixes/FOCUSFLOW_IMPLEMENTATION_PLAN_FINAL_v14.md`, which is absent from the accessible tree; `app/PERSISTENCE_CONTRACT.md` covers preference ownership but not the v14 backup wire contract. Do not infer undocumented backup behavior.
- **T5 — confirmed.** Both current standalone modal save callbacks discard the VPN selection; `StandaloneBlockAndAllowanceConfig` and the active save path omit it. The three named expiry-clearing paths in accessibility, foreground fallback and boot do not issue a sync at the clear itself. No boundary scheduler or standalone-VPN tests exist.
- **T6 — confirmed.** Schedule flags are stored in recurring/window JSON, but the coordinator reads the unwritten static key instead of evaluating active windows. `ScheduleDraft` does not preserve/set `vpnPackages`; schedule setters do not request VPN sync. The schedule toggle’s protection callback sets both Defense master switches. Window matching is private in `AppBlockerAccessibilityService`; no schedule VPN enforcement or boundary tests exist.
- **T7 — confirmed.** `MainActivity` supplies only explicit plus standalone packages; the banner returns early for an empty list and restarts via `startNetworkBlock`, which can seed the explicit list. Global, schedule and focus-mirror-only sources are not represented in its inputs.
- **T8-a — confirmed.** `VpnBlockListScreen` saves `enabled`/`vpn` from whether its explicit list is non-empty and calls self-heal with the same value. `AlwaysOnScreen` also sets the switches from explicit-or-standalone package presence and updates `AppSettings.networkBlockEnabled`; an empty explicit list can therefore turn the master off when no standalone VPN packages remain.
- **T8 — evidence confirmed; Q2 pending.** `startNetworkBlock` can disconnect Wi-Fi (default enabled); Android Q+ restore does not re-enable it. Mobile-data control uses reflection. The repository `stopNetworkBlock` has no call site; `ForegroundTaskService.stopNetworkBlock` is a separate private helper. No Wi-Fi/mobile UI controls were found.
- **T9 — confirmed.** The coordinator stores invalid targets in `vpn_failed_packages`; every service `writeStatus` overwrites the same key, normally with `[]`. Status exposes no separate invalid-target field.
- **T10 — call-site checks confirm candidates, subject to later dependent work.** No external call sites were found for `setVpnSelectedPackages`, `publishScheduleVpnSnapshot`, the `publishStandaloneSnapshot` overloads, `getNetworkBlockSettingsJson`, `getNetworkBlockStatusJson`, `isNetworkBlockActive`, or the public `VpnRepository.isAnotherVpnActive` wrapper. The private repository helper and `NetworkBlockerVpnService.isAnotherVpnActive` remain used. `startNetworkBlock` still has a banner caller. Service/coordinator headers contain stale descriptions. Do not remove anything before dependent work and a fresh zero-call-site search.
- **T11 — confirmed.** `SettingsRepository.setNetworkBlockEnabled` writes both master booleans without checking for an active block. The Defense UI gates the toggle and `VpnRepository.setNetworkBlockSettings` separately guards active-session disables; repository-level parity is absent.

**Tests and narrow seams:**

- Current inventory: 24 JVM test files and 4 instrumented test files. There are no VPN-coordinator, VPN consent, standalone VPN, or schedule-window tests.
- Existing nearby coverage: `TsSettingsAdapterTest`, `ImportProtectionPolicyTest`, `BackupSettingsPolicyTest`, `RestoreCoordinatorTest`, `RestoreGateTest`, and `EnforcementHealthTest`. Backup normalization/export and import PIN policy already have pure JVM seams; extend the adapter/policy tests for T4 mapping and protection rules.
- T1/T2/T7/T9/T11 need focused pure policy/state helpers or Android-backed tests; the coordinator and repositories currently depend directly on Android context/preferences/services. The configured local JVM dependencies include JUnit and coroutine-test, but not Robolectric or Mockito.
- T3 needs a result-aware consent state seam (and UI/instrumented coverage for cancellation). T4 consent and summary behavior belongs at the import flow and needs UI/instrumented coverage in addition to pure adapter tests.
- T5 boundary-time computation and T6 schedule matching are the narrowest pure test seams. T6’s existing schedule matching is private and tied to the accessibility service; extract fixed-calendar math before testing window edges. T8-a can be covered by a pure switch-state rule or Compose UI test. T10 is verified with scoped call-site greps.

**Environment and owner gates:**

- `java`/`javac` are absent from `PATH`, and `JAVA_HOME` is unset. A JDK 17.0.15 executable exists directly in the Nix store but is not configured for the shell.
- `ANDROID_HOME` and `ANDROID_SDK_ROOT` are unset; root and `app/local.properties` are absent; checked common SDK directories are missing. `adb`, `emulator`, `sdkmanager`, and `avdmanager` are unavailable. No device/emulator check is possible in the current direct environment.
- No build or test was run in this read-only batch. Android verification is blocked in the current shell by the missing SDK; use the project bootstrap-backed unit-test route when an implementation batch is authorized, and record its actual result.
- **Q1 pending** — schedule VPN target scope/window behavior. **Q2 pending** — Wi-Fi/mobile side effects. **Q4 pending** — single source of truth for the explicit VPN list. **Q3 decided (2026-10-05)** — consent-gated import activation and one informational category summary. The agent pre-prompt still says Q1–Q4 are pending; that conflicts with the plan/tracker’s dated Q3 decision. Follow the newer explicit decision in the tracker.
- At the Batch 0 handoff, T1 was the next implementation gate. The owner has marked Batch 1 complete for sequencing and authorized Batch 2; its final implementation tests remain deferred. Batch 2 is now in progress; no later batch has started.

## Batch 1 — P0 T1: explicit VPN targets and derived snapshots

**Status:** Complete for sequencing; Batch 1 unit tests passed, device checks remain for final verification
**Gate:** Batch 0 complete; T1 reproduced with a failing assertion before behavior changes.

- [x] Add and run the T1-repro test before changing implementation.
- [x] Add and run T1-migrate and T1-migrate-2 tests against the completed implementation.
- [x] Make `net_block_explicit_packages` the only explicit-list input and implement the one-time, idempotent migration without losing user data.
- [x] Remove any seeding of explicit packages from derived effective targets.
- [x] Preserve `net_block_packages` as diagnostic output only.
- [x] Run the relevant tests and record verification evidence.

### Batch 1 work log

| Date | Status / work performed | Files inspected or changed | Commands and checks | Findings / evidence / blockers |
|---|---|---|---|---|
| 2026-10-05 | In progress; added and ran the T1 regression before changing behavior. | `ExplicitVpnPolicy.kt`, `ExplicitVpnPolicyTest.kt`, current VPN coordinator/repository sources | Configured `Run Android unit tests` workflow (`bash scripts/test-unit.sh`); then `bash scripts/test-unit.sh --tests 'com.tbtechs.focusflow.enforcement.ExplicitVpnPolicyTest.migratesLegacySnapshotWhenExplicitKeyIsMissingAndGenerationIsZero' --tests 'com.tbtechs.focusflow.enforcement.ExplicitVpnPolicyTest.migratesToEmptyWhenExplicitKeyIsMissingAndGenerationIsPositive' --tests 'com.tbtechs.focusflow.enforcement.ExplicitVpnPolicyTest.migrationPreservesAnExistingExplicitList'` | Baseline full run compiled successfully and executed 113 tests; the new focus-mirror regression failed at its assertion as intended (1 failure, no compile/environment failure). The three helper-only migration/preservation checks passed before implementation; rerun the final test forms at final verification. |
| 2026-10-05 | Implemented the Batch 1 changes; post-implementation tests deferred at the owner's request. | `VpnPolicyCoordinator.kt`, `VpnRepository.kt`, `SettingsRepository.kt`, `ExplicitVpnPolicy.kt`, `ExplicitVpnPolicyTest.kt` | `git diff --check` and final Batch 1 diff review; source audit of snapshot and explicit-list reads/writes; confirmed the old seeding block is absent. | Diff/whitespace review passed. `net_block_packages` remains written by the coordinator and service and is read only as the generation-0 one-time migration source; the explicit-list fallback and start-path seeding are removed. Leave the final implementation test run pending until final verification, as requested. The earlier red regression and pre-implementation migration checks are documented above; no test has been run against the completed implementation. |
| 2026-10-05 | Owner marked Batch 1 complete for sequencing and authorized Batch 2; final tests remain deferred. | This tracker | Owner instruction in the Batch 2 request | Batch 2 may proceed without intermediate test execution; run the accumulated tests only at the designated final verification. |
| 2026-10-05 | Ran the completed Batch 1 policy tests in the requested full unit-test workflow. | `ExplicitVpnPolicyTest.kt`, this tracker | `Run Android unit tests` workflow (`bash scripts/test-unit.sh`); full unit suite: 127 passed, 0 failures/errors/skips. | The T1 regression, generation-zero migration, positive-generation migration, and existing-explicit-list precedence all pass against the completed implementation. Device checks remain unavailable. |

## Batch 2 — P0 T2: Defense self-healing toggle

**Status:** Complete for sequencing; implementation added and policy tests passed, device checks remain for final verification
**Gate:** Batch 1 complete.

- [x] Verify the current Defense toggle and all self-heal preference call sites.
- [x] Connect the Defense preference to the native self-heal key with a safe one-time migration.
- [x] Ensure the Defense toggle owns disabling; list screens may enable self-healing when needed but must not turn it off.
- [x] Preserve the backup import protection rule for this setting.
- [x] Add focused tests for migration, off/on transition effects, list-save behavior, and backup import protection.
- [x] Run the focused tests in the requested full unit-test workflow; record verification below.
- [x] Record results and checks that were not performed.

### Batch 2 work log

| Date | Status / work performed | Files inspected or changed | Commands and checks | Findings / evidence / blockers |
|---|---|---|---|---|
| 2026-10-05 | Implementation and diff review complete; focused test execution deferred. | `SettingsRepository.kt`, `VpnRepository.kt`, `VpnSelfHealPolicy.kt`, `VpnBlockListScreen.kt`, `AlwaysOnScreen.kt`, `BackupSettingsPolicyTest.kt`, `VpnSelfHealPolicyTest.kt`, this tracker | Verified Defense and all native self-heal call sites; `rg` confirms the legacy snake-case key remains only in the migration policy; `git diff --check`; reviewed Batch 2 diff. No build or tests run. | Defense now reads/writes `net_block_self_heal`; when the setting changes, off cancels the watchdog and on requests recovery sync. Legacy value is copied only when the native key is absent, using a committed write under `restoreGate.write(...)`; existing native values win. Both list screens enable self-healing only when VPN packages are present, never disable it. `vpnSelfHealEnabled` remains excluded from backup import and has explicit policy coverage. Focused test source was added but not executed per owner instruction. No emulator/device checks were performed; Batch 0 recorded that `adb` and emulator tooling are unavailable in the direct environment. |
| 2026-10-05 | Ran the completed Batch 2 policy and backup-protection tests in the requested full unit-test workflow. | `VpnSelfHealPolicyTest.kt`, `BackupSettingsPolicyTest.kt`, this tracker | `Run Android unit tests` workflow (`bash scripts/test-unit.sh`); full unit suite: 127 passed, 0 failures/errors/skips. | All 5 self-heal policy tests and both backup import protection tests pass. Device checks remain unavailable. |

## Batch 3 — P0 T3: VPN consent result handling

**Status:** Complete for sequencing; ImportConfirmScreen now has a result-aware restore path. Android consent/device checks remain unavailable.
**Gate:** Batch 2 complete.

- [x] Re-verify the five existing VPN consent callers named in plan §2, T3; add the ImportConfirmScreen path.
- [x] Apply enabled-state changes only after system VPN consent is confirmed in the existing flows, including when the optional repository is absent.
- [x] Preserve the existing behavior of those flows; run the consent-cancellation and grant policy tests.
- [x] Remove the legacy VPN request-code flow after the existing callers were migrated.
- [x] Record results and unavailable device verification.
- [x] Add ImportConfirm consent-result handling: grant enables only after Android reports permission; denial/cancellation continues the restore with the list dormant.

### Batch 3 work log

| Date | Status / work performed | Files inspected or changed | Commands and checks | Findings / evidence / blockers |
|---|---|---|---|---|
| 2026-10-05 | Existing T3 consent flows verified and fail-open optional-repository paths fixed; Batch 3 remains blocked on the planned ImportConfirmScreen result-aware restore integration. | Changed: `DefenseScreen.kt`, `GreyoutScheduleModal.kt`. Reviewed: `VpnConsentModal.kt`, `VpnRepository.kt`, `AlwaysOnScreen.kt`, `VpnBlockListScreen.kt`, `VpnPermissionLostBanner.kt`, `ImportConfirmScreen.kt`, `VpnConsentPolicyTest.kt`, this tracker. | `rg` audit of consent helper and legacy APIs; `git diff --check`; `bash scripts/test-unit.sh --tests 'com.tbtechs.focusflow.ui.alwayson.VpnConsentPolicyTest'` — passed (2 tests; app and test sources compiled); `adb devices -l` — no devices; emulator binary unavailable. | Defense and Greyout now fail closed when their optional `VpnRepository` is absent: they check `VpnService.prepare` and only enable after confirmed permission. The shared result-aware helper is used by the five existing callers (Defense, Greyout, Always-On, VPN list, permission-lost banner); it checks actual permission after the Android activity result. Cancellation and grant policy tests passed. No VPN `requestVpnPermission` or request code `2001` references remain; the only remaining `startActivityForResult` is unrelated Usage Stats request `1001`. `ImportConfirmScreen` still has no VPN consent-result path. The plan places that in T3 for T4, but safe activation depends on the unresolved Q4 VPN-list source-of-truth decision; do not activate or migrate the imported list before Q4 is recorded. Device consent checks remain unavailable because no device or emulator is present. |
| 2026-10-05 | Added the missing test coverage for the optional-repository permission fallback and explicit-list precedence; ran all Batch 1–3 tests through the configured workflow. | `VpnConsentModal.kt`, `GreyoutScheduleModal.kt`, `VpnConsentPolicyTest.kt`, `ExplicitVpnPolicyTest.kt`, this tracker | `Run Android unit tests` workflow (`bash scripts/test-unit.sh`); full unit suite: 27 suites, 127 tests, 0 skipped, 0 failures/errors; `git diff --check`. | T3 now tests repository result precedence, missing-repository fallback, fallback failure denial, cancellation, and grant. T1 now asserts that an existing explicit list wins over a derived snapshot. Batch 2 migration/toggle/list-save and backup-protection tests also pass. The ImportConfirmScreen path remains gated by Q4; no device/emulator consent check was possible. |
| 2026-10-05 | Added the ImportConfirm consent-result path to the restore flow. | `BackupCoordinator.kt`, `ImportConfirmScreen.kt`, `VpnImportPolicy.kt`, `VpnRepository.kt`, `SettingsViewModel.kt`, `ImportProtectionSummaryPolicy.kt`, this tracker | Full unit suite recorded under Batch 4; `git diff --check`; VPN-consent and legacy-key greps. No Android device/emulator available. | Import requests system VPN consent only for a selected settings restore with a non-empty VPN list while Network Blocking (VPN) is off. A confirmed grant enables Network Blocking and VPN Self-Healing after restore; cancellation continues the restore with the list dormant. Existing permission skips the system dialog. Unit policy tests cover the decision branches; the actual Android ActivityResult flow and dialog remain unverified on-device. |

## Batch 4 — P1 T4 and T8-a: VPN list persistence and master-switch ownership

**Status:** Implementation and full unit suite complete for sequencing; Android consent/UI checks remain blocked. Contract label discrepancy is preserved (owner identifies intended v14; heading says v13).
**Gate:** Batch 3 complete. Use the owner-supplied contract and retain the v13/v14 label discrepancy in implementation notes; Q3 and Q4 are decided.

- [x] Read the owner-supplied backup contract (its heading says v13; owner identifies it as intended v14); [x] verify current import/export wiring against the available adapter and persistence contract.
- [x] Record the owner's Q3 decision about restoring a non-empty list while Network Blocking is off (see the owner decision record and dated work-log entry).
- [x] Record the owner's Q4 decision about the single source of truth for the VPN list.
- [x] Implement the approved VPN-list persistence and one-time sorted-union migration without deleting the legacy preference.
- [x] During Import, request VPN consent for a non-empty restored list when Network Blocking is off; only after a confirmed grant, enable Network Blocking (VPN), VPN Self-Healing, and the matching persisted VPN settings.
- [x] If VPN consent is denied/canceled, continue the selected restore, preserve the list as dormant, and do not newly enable either switch.
- [x] Add one informational post-import summary with collapsible cards for imported supported protection categories and status derived from imported data, settings, and permissions; no extra confirmation prompt.
- [x] Ensure saving an empty list does not disable the master Network Blocking switch.
- [x] Add and run export/import, empty-list, consent-policy, and summary-classification unit tests.
- [x] Record results and unavailable checks; Android consent dialog and screen interaction checks remain blocked by the absence of a device/emulator.

### Batch 4 work log

| Date | Status / work performed | Files inspected or changed | Commands and checks | Findings / evidence / blockers |
|---|---|---|---|---|
| 2026-10-05 | Owner decision recorded; implementation not started. | `VPN_WIRING_FIX_PLAN.md`, this tracker, `VpnBlockListScreen`, `AlwaysOnScreen`, `VpnRepository`, `DefenseScreen` | Reviewed the user's answer and compared list-save/consent behavior with the current code. | Q3: request Android VPN consent during Import; on grant activate Network Blocking (VPN), VPN Self-Healing, and the imported list; on denial/cancel continue restore with the list dormant. Use one informational post-import summary with collapsible cards for imported protection categories. Read-only check found list-save/native and Defense UI self-heal state are not fully synchronized; T2/T8-a must verify and fix that. Q4 remains pending. |
| 2026-10-05 | Completed the read-only Batch 4 import/export audit; implementation remains blocked on Q4 and the absent v14 backup contract. | `app/PERSISTENCE_CONTRACT.md`, `TsSettingsAdapter.kt`, `SettingsRepository.kt`, `VpnRepository.kt`, `BackupCoordinator.kt`, `ImportConfirmScreen.kt`, `AlwaysOnScreen.kt`, `VpnBlockListScreen.kt`, this tracker | Searched for the referenced v14 contract; read the available persistence contract and adapter/coordinator/import/list-save paths. No feature code changed. | The available persistence contract documents preference ownership, not the v14 wire-format contract; the referenced v14 file is absent. Current list screens persist `net_block_explicit_packages`, while V1 backup import/export maps `alwaysOnVpnPackages` to/from `always_on_vpn_packages`; no bridge currently makes those values round-trip into native enforcement. Import applies portable settings and reconciles VPN policy but has no consent-result activation or category summary. Both list-save paths can write `enabled=false` and `vpn=false` when their VPN list is empty; Always-On also updates the UI master switch, while the VPN list screen does not. Q3 remains recorded in the owner decision table and dated log; Q4 is pending. |
| 2026-10-05 | Recorded the owner's Q4 source-of-truth decision; implementation remains blocked by the Batch 3 gate and absent v14 backup contract. | This tracker, `VPN_WIRING_FIX_PLAN.md` | Recorded the user's explicit Q4 choice. No code changed and no tests run. | Q4: `net_block_explicit_packages` is the canonical internal VPN-list source. Preserve the existing `alwaysOnVpnPackages` backup field through explicit adapter mapping; migration must not silently drop or overwrite values. |
| 2026-10-05 | Read and filed the owner-supplied backup contract; no implementation started, as requested. | `attached_assets/FocusFlow_Backup_Import_Implementation_Plan_v13_1791205856242.md`, `fixes/FocusFlow_Backup_Import_Implementation_Contract_v13.md`, this tracker | Reviewed the title and the settings/import/export sections; copied the source byte-for-byte and verified the copy with `cmp`. No code changed and no tests run. | The owner identifies the supplied document as the intended v14 contract, but its own title is “Implementation Contract v13” and says it supersedes v12; preserve and document that discrepancy rather than renaming or editing the source. It specifies V1 wire key `alwaysOnVpnPackages` as APPLY with the Kotlin target marked VERIFY, and lists `vpnBlockEnabled`, `vpnSelfHealEnabled`, and `standaloneVpnPackages` among device-local live keys that must not be imported. Q4 now resolves the internal target as `net_block_explicit_packages`; the external backup field remains `alwaysOnVpnPackages`. |
| 2026-10-05 | Implemented Batch 4 T4/T8-a and ran final JVM unit verification. | `BackupManager.kt`, `TsSettingsAdapter.kt`, `SettingsRepository.kt`, `VpnRepository.kt`, `SettingsViewModel.kt`, `BackupCoordinator.kt`, `ImportConfirmScreen.kt`, `AlwaysOnScreen.kt`, `VpnBlockListScreen.kt`; new VPN import/list/summary policies and tests; this tracker | `bash scripts/test-unit.sh` — final run passed: 137 tests, 0 failures, 0 errors, 0 skipped (30 JUnit XML files); `git diff --check`. Grep output: `always_on_vpn_packages` appears only at `SettingsRepository.kt:122,123` as migration keys; `requestVpnPermission` and VPN request code `2001` have no matches; the only remaining `startActivityForResult` is `UsageStatsRepository.kt:348` with request `1001`. The first compile attempt exposed a public/internal visibility mismatch; narrowed the method and reran successfully. `adb devices -l` previously showed no device; emulator unavailable. | Export/import keeps wire key `alwaysOnVpnPackages` while mapping to `net_block_explicit_packages`; one-time migration merges sorted, distinct lists and retains the old key. Device-local VPN switches are excluded from import. Import consent uses Android's result and only activates after permission is confirmed; denial/cancellation restores the list dormant. Post-import protection cards are expandable and status-aware. Both VPN list screens preserve master switches on empty-list saves. Unit coverage includes adapter round-trip/device-local toggle exclusion, empty-list switch policy, consent decisions, and summary classification. Android dialog/result and visual interaction checks remain unverified; no later batch or Q2 work was started. |

## Batch 5 — P1 T5 and T6: standalone and schedule VPN enforcement

**Status:** Implementation, unit tests, and APK build are complete; manual Android verification remains blocked. The owner has authorized proceeding to Batch 6.
**Gate:** Batch 4 complete. Q1 is decided. T5/T6 share the boundary scheduler and must be coordinated.

- [x] Re-verify standalone-block VPN selection, persistence, expiry clearing, and all relevant callers.
- [x] Record the owner's Q1 decision on which schedule apps are VPN-blocked and when.
- [x] Add fixed-time tests for schedule windows, overnight windows, week boundaries, and standalone expiry; six focused tests pass.
- [x] Run the new tests during final verification; all 143 JVM unit tests pass with no failures, errors, or skips.
- [x] Implement shared boundary start/stop scheduling and verify source wiring for boot, clock/timezone, exact-alarm permission, and scheduled-boundary events.
- [ ] Verify Android alarm delivery triggers standalone expiry and schedule start/stop without user action; no device or emulator is available.
- [x] Record results and unavailable device checks.

### Batch 5 work log

| Date | Status / work performed | Files inspected or changed | Commands and checks | Findings / evidence / blockers |
|---|---|---|---|---|
| 2026-10-05 | Read-only re-verification complete; implementation and tests remain blocked by the Batch 4/Q4 gate. | `StandaloneBlockModal.kt`, `FocusScreen.kt`, `StandaloneBlockSetupScreen.kt`, `QuickBlockSheet.kt`, `ActiveScreen.kt`, `AppSettings.kt`, `SettingsViewModel.kt`, `SettingsRepository.kt`, `VpnPolicyCoordinator.kt`, `AppBlockerAccessibilityService.kt`, `ForegroundTaskService.kt`, `BootReceiver.kt`, `TaskAlarmReconcileWorker.kt`, this tracker | Searched all standalone/schedule writers and callers, expiry-state mutations, boundary alarm symbols, and related test sources; inspected the state transitions and persisted schedule keys. No tests run. | The standalone modal emits its VPN selection, but both UI callers discard it; `StandaloneBlockAndAllowanceConfig` and its main repository writer have no VPN list field. A separate `publishStandaloneSnapshot` can persist one but has no callers; Quick Block and Active Screen use the basic setter. Expiry is cleared in the accessibility service, fallback service, and boot receiver, without a shared VPN boundary scheduler; the exact-alarm reconciliation receiver only handles task alarms. Schedules persist `vpnEnabled`/`vpnPackages`, but the VPN coordinator reads the flat `net_block_schedule_vpn_pkgs` snapshot; its writer is uncalled, schedule writes do not request VPN sync, and there is no pure fixed-time window helper or schedule boundary test coverage. Q1 is decided: all apps in each VPN-enabled schedule's package list are VPN-blocked only during that schedule's active window. Batch 4 remains blocked on Q4. No implementation or tests were started, and no device checks were attempted. |
| 2026-10-05 | Current-source re-audit; Batch 5 implementation underway after Batch 4 and Q1 gates were resolved. | `StandaloneBlockModal.kt`, `FocusScreen.kt`, `StandaloneBlockSetupScreen.kt`, `QuickBlockSheet.kt`, `ActiveScreen.kt`, `AppSettings.kt`, `SettingsViewModel.kt`, `SettingsRepository.kt`, `VpnPolicyCoordinator.kt`, `VpnPolicyBoundaryPolicy.kt`, `VpnPolicyBoundaryScheduler.kt`, `VpnPolicyBoundaryReceiver.kt`, `AppBlockerAccessibilityService.kt`, `ForegroundTaskService.kt`, `BootReceiver.kt`, `AndroidManifest.xml`, this tracker | `rg` searches across `app/src/main` and test sources; inspected the matching repository, policy, alarm, receiver, manifest, and caller code. Checked `java`, `JAVA_HOME`, Android SDK variables, and `local.properties`; no test/build command run. | The current modal/config flow already carries VPN package selections through Focus and standalone setup, and the atomic standalone writer persists them before `requestVpnSync`. However, the plain standalone setter always clears that list, including while an active block is extended. Expiry is cleared in accessibility, foreground fallback, and boot paths; accessibility and fallback use `now > until` rather than the policy's `now < until` boundary, and none requests sync at the clear. Partial shared schedule math/policy/alarm classes exist, but `VpnPolicyBoundaryCoordinator` is referenced by the receiver and has no implementation; the receiver is not registered in the manifest; boot does not re-arm the policy alarm; the coordinator still consumes `net_block_schedule_vpn_pkgs` instead of active schedule windows and does not schedule the next boundary. Accessibility still uses its inline window matcher. Schedule repository setters already call `requestVpnSync`, so no extra setter change is currently indicated. Q1 remains: every app in a VPN-enabled schedule is blocked only while that schedule window is active. Test execution remains deferred until final verification. Current shell has no Java/JDK on PATH, no `JAVA_HOME`, no Android SDK variables, and no `local.properties`; device/emulator availability has not yet been rechecked. |

| 2026-10-05 | Batch 5 implementation, source checks, JVM tests, and APK build complete; final Android behavior check blocked. | `app/src/main/AndroidManifest.xml`, `app/src/main/java/com/tbtechs/focusflow/data/repository/SettingsRepository.kt`, `app/src/main/java/com/tbtechs/focusflow/enforcement/AppBlockerAccessibilityService.kt`, `ForegroundTaskService.kt`, `VpnPolicyBoundaryScheduler.kt`, `VpnPolicyCoordinator.kt`, `receivers/BootReceiver.kt`, `receivers/VpnPolicyBoundaryReceiver.kt`, `ui/SettingsViewModel.kt`, `ui/defense/GreyoutScheduleModal.kt`, `app/src/test/java/com/tbtechs/focusflow/enforcement/VpnPolicyBoundaryPolicyTest.kt`, this tracker | `bash scripts/test-unit.sh` (143 tests pass; six boundary tests); `bash scripts/build-apk-with-java.sh` (debug APK built); `git diff --check`; new-test whitespace check; manifest XML parse; scoped `rg` checks for obsolete schedule-snapshot references and boundary wiring; `adb devices -l`. | The first test run found a missing receiver import; after fixing it, the full unit suite passed with 0 failures, errors, or skips. The debug APK build also passed. Tests cover active schedule target selection, weekday/overnight/week-wrap boundaries, and standalone expiry policy. Source checks confirm the receiver is registered and syncs the coordinator for boundary/clock/permission events, boot re-arms the alarm, and policy/alarm calculations use one time snapshot. `adb devices -l` listed no devices and no emulator binary is installed, so Android alarm delivery and actual VPN start/stop at boundaries remain unverified. A Python module added to `.replit` by a one-off check was restored; no `.replit` change remains. |

## Batch 6 — P1 T7: permission-lost recovery banner

**Status:** Code and automated tests complete; actual on-device permission-revocation behavior remains unverified.
**Gate:** Batch 5 implementation, tests, and APK build are complete; its device-only check remains unavailable. The owner explicitly authorized proceeding with T7 while that check stays open.

- [x] Re-verify all effective VPN target sources and current permission-loss banner conditions.
- [x] Ensure the banner covers each approved VPN source and recovery uses the coordinator without changing the explicit list.
- [x] Add/run focused tests for focus-mirror-only configuration and revoked VPN permission.
- [x] Record results and any unavailable device verification.

### Batch 6 work log

| Date | Status / work performed | Files inspected or changed | Commands and checks | Findings / evidence / blockers |
|---|---|---|---|---|
| 2026-10-05 | T7 source audit complete; banner/recovery changes and focused tests are in progress. | `MainActivity.kt`, `VpnPermissionLostBanner.kt`, `VpnRepository.kt`, `VpnPolicyCoordinator.kt`, `VpnPermissionRecoveryPolicy.kt`, focused policy tests, this tracker | Read plan T7 and current permission/banner/recovery flows; searched effective-target sources and existing tests. Test workflow has not yet run. | The banner previously gated on explicit plus standalone lists and retried with `startNetworkBlock`, which could seed the explicit list. The implementation now checks global, effective targets, and active focus-mirror policy; recovery calls only the coordinator. Focused tests cover mirror-only configuration with revoked permission and source gating. Batch 5 device verification remains open per owner direction. |
| 2026-10-05 | T7 implementation and automated verification complete; actual UI check remains blocked by device availability. | `MainActivity.kt`, `VpnPermissionLostBanner.kt`, `VpnRepository.kt`, `VpnPolicyCoordinator.kt`, `VpnPermissionRecoveryPolicy.kt`, `VpnPermissionRecoveryPolicyTest.kt`, this tracker | `Run Android unit tests` workflow (`bash scripts/test-unit.sh`): 146 tests, 0 failures/errors/skips; `adb devices -l`: no devices; `git diff --check`; reviewed recovery and effective-source code. | Banner gating now includes enabled VPN plus global mode, effective targets (explicit, standalone, active schedule, and focus-mirror packages), or active focus mirror. Permission restoration routes through `VpnPolicyCoordinator.requestRecoverySync`; the recovery path does not write `net_block_explicit_packages`. Policy tests cover focus-mirror-only with revoked permission, no-source suppression, and global/effective sources. Actual Android permission-revocation/banner behavior remains unverified. The workflow's unrelated Python module change to `.replit` was restored. Q2 remains pending; Batch 7 was not started. |

## Batch 7 — P2 T8–T12: hardening and cleanup

**Status:** Approved Batch 7 scope complete. T8 behavior is unchanged per the owner's Q2 decision; T12 was skipped.
**Gate:** The owner authorized Batch 7 while Batch 6's device-only check remains open. Q2 is resolved: retain existing Wi-Fi/mobile behavior without new opt-in controls. The owner chose not to include optional T12.

- [x] Record the owner's Q2 decision: retain current Wi-Fi/mobile-data behavior as-is, without adding opt-in controls; T8 behavior is unchanged.
- [x] Fix `vpn_failed_packages` clobbering while preserving a distinct, accurately exposed invalid-package state.
- [x] Verify dead-code candidates and dependent call sites; remove no code that still has callers.
- [x] Confirm the active-block guard is applied to the Defense master toggle.
- [x] Skip the optional pure policy calculator (T12), per the owner's choice.
- [x] Run relevant tests/build and record results.

### Batch 7 work log

| Date | Status / work performed | Files inspected or changed | Commands and checks | Findings / evidence / blockers |
|---|---|---|---|---|
| 2026-10-05 | Started independent T9/T11 work; T8 remains paused for Q2. | `VpnPolicyCoordinator.kt`, `NetworkBlockerVpnService.kt`, `VpnRepository.kt`, `SettingsRepository.kt`, `ActiveScreen.kt`, `ActiveHeaderButton.kt`, new active-block policy/tests, this tracker | Rechecked T8–T12 plan scope and current writers, readers, guards, and UI consumers; reviewed diff with `git diff --check`. No tests run; tests are deferred to Batch 7 final verification. | T9 separates coordinator-detected invalid packages into `net_block_invalid_packages` and exposes both lists; service registration failures remain in `vpn_failed_packages`. T11 now shares a pure expiry-aware Focus/Standalone active-block guard between repositories. Q2 is still pending, so T8 behavior has not been changed. |
| 2026-10-05 | Completed the authorized Batch 7 scope: T9 status persistence, T10 dead-code audit, and T11 guard verification. | Changed: `NetworkBlockerVpnService.kt`, new `VpnRegistrationFailurePolicy.kt` and `VpnRegistrationFailurePolicyTest.kt`; verified: `VpnPolicyCoordinator.kt`, `VpnRepository.kt`, `SettingsRepository.kt`, `ActiveBlockGuardPolicy.kt`/tests, `ActiveScreen.kt`, `ActiveHeaderButton.kt`; this tracker | First `bash scripts/test-unit.sh` attempt timed out during initial JDK/SDK provisioning after source compilation; retry passed the full suite: 152 tests, 0 failures/errors/skips. `bash scripts/build-apk-with-java.sh` — debug APK assembled successfully. `git diff --check`; scoped plan §6 and T10 candidate greps. | `writeStatus` now preserves prior service-registration failures when a status update does not report a new registration result; a new registration attempt and an intentional stop explicitly clear them. Coordinator-detected invalid/uninstalled targets stay in `net_block_invalid_packages`, and the existing status model/UI expose both lists separately. T10 candidates are already absent; current `isAnotherVpnActive` helpers have live callers and were retained; both service/coordinator headers accurately describe current behavior. T11's expiry-aware guard is already wired into `SettingsRepository.setNetworkBlockEnabled` and is covered by the passing policy tests. Q2 confirmed: retain existing Wi-Fi/mobile behavior, no opt-in controls. T12 skipped by owner choice. Earlier Batch 5/6 device-only checks remain unverified; no GitHub push or Actions polling. |
| 2026-10-05 | Ran the requested focused verification for T1–T8-a. | Existing explicit VPN, self-heal, consent, backup/import, boundary, permission-recovery, and list-save tests; this tracker | `bash scripts/test-unit.sh` with 12 exact test-class filters; result XML confirmed all 12 expected classes ran: 47 tests, 0 failures/errors/skips. `adb devices -l` showed no attached device; emulator binary unavailable. | T1 policy/migration, T2 self-heal transition/effect policy, T3 consent-cancel policy, T4 backup/import/summary policy, T5/T6 boundary policy, T7 recovery policy, and T8-a list-save policy tests pass. T2 effect dispatch is source-verified, not Android-device exercised. System consent dialogs, post-import UI, real alarm delivery, and permission-revocation/banner interaction remain unverified on-device. |

## Owner decision record

Record each decision, date, and evidence before dependent work. Pending questions remain implementation gates.

| Plan question | Decision needed | Status | Owner answer / date / evidence |
|---|---|---|---|
| Q1 | Schedule VPN scope and active-window behavior | Decided (2026-10-05) | All apps in a VPN-enabled schedule's package list are VPN-blocked only while its configured window is active; selected the plan default in response to the owner question. |
| Q2 | Wi-Fi/mobile-data side effects | Decided (2026-10-05) | Keep existing behavior as-is; do not add opt-in controls. No T8 code changes. |
| Q3 | Behavior when import includes a VPN list but Network Blocking is off | Decided (2026-10-05) | Request VPN consent during Import; on grant turn on Network Blocking and VPN Self-Healing and activate the imported list. On denial/cancel, continue restore with the list dormant. Use one informational expandable summary for imported protection categories; do not ask for another in-app confirmation. |
| Q4 | Single source of truth for the explicit VPN list | Decided (2026-10-05) | `net_block_explicit_packages` is the canonical internal VPN-list source; preserve the existing `alwaysOnVpnPackages` backup field through explicit mapping. |
| T12 | Optional pure policy calculator | Decided (2026-10-05) | Skip the optional refactor; no clear need was identified for this batch. |

## Required test scenarios

- [x] T1-repro: focus-mirror targets stop blocking after focus ends when there is no explicit VPN list (policy test passed).
- [x] T1-migrate: missing explicit key plus generation 0 migrates legacy snapshot once (policy test passed).
- [x] T1-migrate-2: missing explicit key plus positive generation does not treat derived targets as user picks (policy test passed).
- [x] T2: self-heal toggle policy tests cover native-value decisions and watchdog-cancel/recovery-sync effects; repository dispatch was source-verified, not device-exercised.
- [x] T3: consent-cancel policy test confirms protection is not enabled; actual Android system-dialog interaction remains unverified.
- [x] T4: unit-tested backup round-trip preserves the approved VPN list source of truth.
- [x] T4-consent-granted: import activation policy branch is unit-tested; actual Android consent and switch activation remain unverified on-device.
- [x] T4-consent-cancelled: import policy keeps the list dormant and avoids enabling switches; actual Android cancellation remains unverified on-device.
- [ ] T4-vpn-permission-pregranted: on-device check that existing permission skips the prompt and activates the imported list and both switches; policy branch is unit-tested.
- [x] T4-summary policy: category/status classification is unit-tested.
- [ ] T4-summary device: verify collapsible cards and truthful displayed status on Android.
- [x] T5 policy: standalone expiry and next-boundary policy tests pass.
- [ ] T5 device: verify Android alarm delivery stops targets at expiry without interaction.
- [x] T6 policy: active-window, overnight, and week-wrap policy tests pass.
- [ ] T6 device: verify Android schedule transitions apply only within approved windows.
- [x] T7 policy: configured VPN-source recovery/banner eligibility tests pass.
- [ ] T7 device: verify permission revocation and banner/recovery behavior on Android.
- [x] T8-a: empty-list master-switch policy is unit-tested and used by both VPN list save screens; device interaction remains unavailable.
- [x] T9: service-registration failures survive unrelated status updates and remain separate from invalid/uninstalled targets; focused policy tests pass.
- [x] T11: the repository master-toggle setter uses the active/expiry guard; active, expired, and inactive states are covered by unit tests.

## Final verification

- [x] Build and run unit tests for the completed scope; Batch 5 passed 143 tests and Batch 6 passed 146 tests, with 0 skipped/failures/errors.
- [x] Run applicable verification greps from plan §6; obsolete schedule snapshot/class references are absent, and boundary receiver/scheduler wiring is present.
- [ ] Complete feasible manual device checks from plan §6; blocked because no Android device/emulator is available.
- [x] Review the final diff for data loss, unauthorized scope, and changes outside the plan; the unrelated `.replit` Python-module side effect was restored.
- [x] Confirm Batch 5/6 device checks remain blocked, record Q2 as decided, complete the authorized Batch 7 scope, and do not start Batch 8+ without authorization.
- [x] Record final handoff summary with scope, test results, blockers, and the authorized stopping point.

## Final work log

| Date | Status / work performed | Files inspected or changed | Commands and checks | Findings / evidence / blockers |
|---|---|---|---|---|
| 2026-10-05 | Handoff documents prepared; no implementation started | `fixes/VPN_WIRING_FIX_PLAN.md`, `fixes/VPN_WIRING_FIX_TRACKER.md`, `fixes/VPN_WIRING_AGENT_PRE_PROMPT.md` | Read the full attached plan and the existing `work/BATCH_TRACKER.md` and `work/AGENT_PRE_PROMPT.md`; no code/build/test changes made | Plan moved under `fixes/`; implementation status remains Not started. Batch 0 and all owner decisions remain open. |
| 2026-10-05 | Final handoff for the authorized Batch 3–4 scope. | VPN backup mapping/migration, consent-result import flow, list-save switches, protection summary, focused policy tests, and this tracker. | `bash scripts/test-unit.sh`: 137/137 passed; `git diff --check` passed; focused VPN-key and consent API greps passed. No GitHub push or Actions polling. | Batch 3–4 implementation is complete for sequencing. Android system consent, post-import screen interaction, and device behavior remain unverified because no device/emulator is available. Do not start Batch 5 or later or resolve Q2 without new authorization. The backup contract's v13 heading/intended-v14 discrepancy remains documented. |
| 2026-10-05 | Handoff for the approved Batch 7 scope. | `NetworkBlockerVpnService.kt`, `VpnRegistrationFailurePolicy.kt`, `VpnRegistrationFailurePolicyTest.kt`, and this tracker; T10/T11 source verification | `bash scripts/test-unit.sh`: 152 passed, 0 failures/errors/skips; `bash scripts/build-apk-with-java.sh`: debug APK built; `git diff --check`; scoped VPN-key, consent API, status-separation, and dead-code greps. | T9–T11 complete; T8 unchanged per Q2; T12 skipped per owner choice. The first test attempt timed out during initial JDK/SDK provisioning; the cached-tool retry passed. Batch 5/6 device-only checks remain unverified. No Batch 8+ work authorized. No GitHub push or Actions polling. |
