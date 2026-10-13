# FocusFlow Import/Export Upgrade (MD)

## Decision

**Do not replace the current import/export implementation with this archive.**
Treat it as a historical design reference and selectively adapt its strongest
ideas behind the existing `.focusflow` V1 contract.

The current implementation already owns the live format, bounded file parsing,
document-provider I/O, import review, merge/replace choice, restore gate, task
deduplication, and alarm reconciliation. The archive comes from a historical
commit and is not guaranteed to compile against this branch. Its purpose file
also says its test files and test runners were removed
([`FILE_PURPOSES_1791875130361.md`](attached_assets/FILE_PURPOSES_1791875130361.md),
lines 1–6 and 31–51). That makes wholesale adoption both a compatibility and a
verification risk.

## What the current app already does

- `BackupSerializer` owns `FocusFlowBackupV1`, enforces kind/version/settings/
  tasks, preflights input, and reports malformed task rows and unsupported
  settings ([`BackupSerializer.kt`](app/src/main/java/com/tbtechs/focusflow/data/backup/BackupSerializer.kt),
  lines 20–30 and 81–159).
- `BackupFileManager` handles URI streams and delegates bounded, strict UTF-8
  reading to the current preflight layer
  ([`BackupFileManager.kt`](app/src/main/java/com/tbtechs/focusflow/data/backup/BackupFileManager.kt),
  lines 10–37).
- `BackupViewModel` already owns asynchronous export/import states, holds a
  parsed import for review, and does not begin restoration until confirmation
  ([`BackupViewModel.kt`](app/src/main/java/com/tbtechs/focusflow/ui/backup/BackupViewModel.kt),
  lines 33–79 and 84–165).
- `ImportConfirmScreen` already shows backup contents and warnings, offers
  Merge versus Replace, prevents cancellation while restoring, and reports
  imported/skipped counts ([`ImportConfirmScreen.kt`](app/src/main/java/com/tbtechs/focusflow/ui/backup/ImportConfirmScreen.kt),
  lines 87–112, 151–185, 238–287, and 314–365).
- `BackupRestoreEngine` serializes writes through `RestoreGate`, checks for an
  active Focus session before task replacement, skips duplicate IDs, changes
  expired `scheduled` tasks to `skipped`, and reconciles alarms
  ([`BackupRestoreEngine.kt`](app/src/main/java/com/tbtechs/focusflow/data/backup/BackupRestoreEngine.kt),
  lines 65–75, 99–125, 143–189). This is a working baseline, but the sequence
  is multiple repository writes rather than one transaction; an exception or
  process death can leave a partially applied import.

## Recommended upgrade

### First: a truthful import plan and review

Keep the current V1 parser, exporter, URI I/O, and restore engine as the source
of truth. Add a **pure preview/plan layer** before applying an import. It should
report, from the exact current payload and local state:

1. Tasks that will be added, skipped as duplicate IDs, or normalized because
   their scheduled end time has passed.
2. Which portable settings will change, which will remain local, and any
   unsupported fields.
3. Whether the import weakens an existing protection, and whether user
   authorization is required.
4. Whether imported network-block entries will be active, inactive, or waiting
   for Android VPN consent.

The existing `ImportConfirmScreen` can display this plan; do not replace it with
the archive screen until the state contracts match. In particular, the current
engine skips every task with an existing ID without comparing its content
(`BackupRestoreEngine.kt`, lines 149–153). The current guide describes different
content under the same ID as a conflict, so that wording should be reconciled
with the actual behavior before the preview is shipped
([`FocusFlowFormatGuideData.kt`](app/src/main/java/com/tbtechs/focusflow/ui/support/FocusFlowFormatGuideData.kt),
lines 39–45).

### Second: safe protection and VPN decisions

Reuse the **policy separation** shown by the archive, but correct and test the
decisions before using them:

- Protection comparisons must compare the setting values that affect
  enforcement, not only item IDs. An allowance with the same package but a
  larger time budget, or a schedule with the same ID but weaker hours, can
  reduce protection without changing identity. If current protected state
  cannot be read or parsed, fail closed rather than treating it as empty.
- VPN permission must be checked again at the moment activation is requested.
  A permission state observed during preview can become stale before Apply.
- The result screen should derive “active” from the post-import setting and
  current permission state, not merely from the fact that a setting was present
  in the file.

### Later: crash-resumable restore

The archive's strongest architectural idea is a durable, phase-based restore
journal. Consider it only as a cohesive subsystem after the preview/policy
contract is settled. Each phase must be idempotent; phase boundaries, retries,
timeouts, cancellation, discard, and alarm reconciliation need crash-injection
tests. Align journal/pending-file size limits with the current backup parser
limit and keep persisted import data private to the app.

## File-by-file decision

| Attached file | Decision | Essence and decision |
|---|---|---|
| [`AndroidRestorePhaseActions_1791875130354.kt`](attached_assets/AndroidRestorePhaseActions_1791875130354.kt) | **Adapt the boundary, not the class** | Separates task writes, settings writes, and alarm reconciliation (lines 12–40). The repository methods are from the archived branch; map each operation to current repositories and prove repeated phase calls are safe before connecting a journal. |
| [`BackupCoordinator_1791875130356.kt`](attached_assets/BackupCoordinator_1791875130356.kt) | **Do not transplant** | Centralizes picker, preview, restore, VPN, PIN, and result work (lines 41–131 and 156–250), but duplicates the current `BackupViewModel`/screen flow and depends on archived `SettingsViewModel`, `RestoreCoordinator`, and repository contracts. Its broad responsibility also makes it difficult to test as one unit. Extract small policy functions instead. |
| [`BackupEnvelopeV1_1791875130357.kt`](attached_assets/BackupEnvelopeV1_1791875130357.kt) | **Keep as reference only** | Contains V1 models, parsing, normalization, and warnings (lines 19–69 and 82–160). Adding a second V1 parser beside `BackupSerializer` creates two authorities for required fields and normalization. Compare individual validation cases; do not switch parsers without fixture parity tests. |
| [`BackupManager_1791875130358.kt`](attached_assets/BackupManager_1791875130358.kt) | **Do not transplant** | Combines export document creation, URI I/O, parsing, and restore callbacks (lines 27–30 and 55–182). Those responsibilities already exist in `BackupFileManager`, `BackupSerializer`, `BackupViewModel`, and `BackupRestoreEngine`; a second manager would split ownership. |
| [`BackupV1Exporter_1791875130360.kt`](attached_assets/BackupV1Exporter_1791875130360.kt) | **Use for comparison, not replacement** | Its explicit export filtering/canonicalization is worth reviewing (lines 19–32 and 81–113), but the current serializer is already the V1 exporter. Maintain one schema implementation and add tests for any newly identified “never export” fields. |
| [`FocusFlowAgentGuide_1791875130362.kt`](attached_assets/FocusFlowAgentGuide_1791875130362.kt) | **Keep the current support copy** | The task-only prompt is useful, but this archive version says expired `scheduled` **or `active`** tasks become skipped (lines 54–59); the current engine only changes expired `scheduled` tasks. The current app copy has already been corrected. |
| [`FocusFlowFileGuideScreen_1791875130363.kt`](attached_assets/FocusFlowFileGuideScreen_1791875130363.kt) | **Keep the current support screen** | The screen's open-guide and copy-prompt actions are useful (around lines 60–130), but the archived package is `ui.settings`, while the current screen belongs in `ui.support`. The current implementation also uses the auto-mirrored back icon. Do not restore the older source over it. |
| [`FocusFlowFormatGuideContent_1791875130364.kt`](attached_assets/FocusFlowFormatGuideContent_1791875130364.kt) | **Keep the current integrated copy** | Its section/callout/code-block presentation is already represented in the app. Reuse wording only after checking every statement against the live serializer and restore engine. |
| [`FocusFlowFormatGuideData_1791875130365.kt`](attached_assets/FocusFlowFormatGuideData_1791875130365.kt) | **Do not copy as-is** | Several claims are stale: it says an omitted version is treated as V1 (lines 16–18), says missing `color`/`focusMode` default (lines 67–78), and its envelope examples omit `platform` (lines 145–190). The current parser requires version 1 (`BackupSerializer.kt`, lines 81–101); `BackupEnvelope.platform` is required (`BackupEnvelope.kt`, lines 8–22); and `Task.color`/`focusMode` have no defaults (`Task.kt`, lines 56–65). The current guide copy has these corrections. |
| [`ImportConfirmScreen_1791875130366.kt`](attached_assets/ImportConfirmScreen_1791875130366.kt) | **Take the review ideas, not the screen** | It contains detailed review, selection, progress, and result UI (lines 64–237 and 314–365), but is coupled to the archived `RestoreUiState` and coordinator. The current screen already covers the confirmation and task-mode choice. Use the new preview plan to extend it rather than replacing it. |
| [`ImportProtectionPolicy_1791875130367.kt`](attached_assets/ImportProtectionPolicy_1791875130367.kt) | **Do not use without redesign** | The extracted policy is testable (lines 16–27), but snapshots use sets of IDs/identities (lines 7–14 and 33–43), so changed values under the same ID can escape the removal check. Malformed local allowance JSON becomes an empty set (lines 98–100), which can make a PIN check fail open. Compare effective values and fail closed. |
| [`ImportProtectionSummaryPolicy_1791875130368.kt`](attached_assets/ImportProtectionSummaryPolicy_1791875130368.kt) | **Adapt** | The pure mapping from imported facts to active/inactive result categories is a good separation (lines 5–27). Keep it only if the facts come from the state after restore and actual permission checks. |
| [`RestoreCoordinator_1791875130370.kt`](attached_assets/RestoreCoordinator_1791875130370.kt) | **Architecture reference; do not drop in** | Admission, pending-import staging, preview, journal creation, and restore launch are grouped here (lines 41–131 and 188–346). This is valuable only together with the journal, recovery engine, stores, phase actions, and gate. It is not a small safe patch to the existing flow. |
| [`RestorePlan_1791875130371.kt`](attached_assets/RestorePlan_1791875130371.kt) | **Adapt the pure planner after schema mapping** | Merge/replace planning, conflict models, and counts are the best input to a truthful preview (lines 1–20 and 159–281). The task projection/settings plan must be checked against current `Task` and portable-setting rules; archive tests were removed, so do not trust it without new parity tests. |
| [`RestoreProgressDisplay_1791875130372.kt`](attached_assets/RestoreProgressDisplay_1791875130372.kt) | **Only if phased restore is adopted** | Keeps phase-to-copy mapping separate (lines 6–27), but hard-codes a four-step sequence. It has no value independent of an agreed restore state model. |
| [`RestoreRecoveryEngine_1791875130373.kt`](attached_assets/RestoreRecoveryEngine_1791875130373.kt) | **Take the recovery principle, rewrite against current ports** | Journal replay, bounded retries, blocked/retry/discard states, and cancellation handling are the key ideas (lines 30–49 and 78–181). The engine can repeat a phase after interruption, so every action must be demonstrably idempotent. A timeout cannot make a partially completed repository operation atomic. |
| [`RestoreStores_1791875130374.kt`](attached_assets/RestoreStores_1791875130374.kt) | **Take atomic-write discipline; adapt limits and format** | `AtomicFile`, bounded reads, quarantine, and explicit journal stores are useful (lines 80–101 and 105–227). The archive accepts stored documents up to 24 MiB (line 206), while the current backup input cap is smaller; align caps and test corruption/recovery before using the storage contract. |
| [`VpnImportPolicy_1791875130375.kt`](attached_assets/VpnImportPolicy_1791875130375.kt) | **Take the explicit decision model, fix stale-state behavior** | Separating notice, consent decision, activation, and inactive-result copy is useful (lines 14–63). But `PERMISSION_ALREADY_GRANTED` returns `true` without checking the current `vpnPermissionGranted` argument (lines 81–88), so a permission revoked after preview can still produce an activation request. Recheck permission at commit time and keep activation behind a single policy path. |

## Guide/archive corrections to preserve

The attached guide sources are older than the current implementation. In addition
to the mismatches above:

- `FocusFlowFormatGuideData_1791875130365.kt` describes `description` as
  importing as an empty string (line 70), while the current model permits a
  nullable description (`Task.kt`, line 56).
- The current importer checks duplicate task IDs and skips them; it does not
  compare payload equality before calling one an identical duplicate
  (`BackupRestoreEngine.kt`, lines 149–153). Keep guide copy aligned with that
  observable behavior.
- The archive's guide package paths are `ui.settings`, per
  [`FILE_PURPOSES_1791875130361.md`](attached_assets/FILE_PURPOSES_1791875130361.md),
  lines 26–29. The current guide has already been integrated under `ui.support`.

The guide discrepancy is documentation drift, not evidence that the active
serializer should be replaced. Update the guide or implementation deliberately;
do not let an older prompt silently redefine the wire contract.

## Verification needed before any code adoption

The archive's `FILE_PURPOSES_1791875130361.md` lists removed parser, exporter,
restore, VPN-policy, UI, and prompt tests (lines 31–51). Therefore this review
does not treat the archive's behavior as tested. Before implementing the
recommended first phase, add tests for:

- Preview parity with the current parser and restore engine, including malformed
  task rows, duplicate IDs with different content, omitted settings, and expired
  scheduled tasks.
- Protection weakening when values change under stable IDs, invalid local
  settings, and missing imported fields.
- VPN permission changes between preview and Apply, denial/cancellation, and
  truthful active/inactive result text.
- If journaling is later chosen: process death after every phase write, retries,
  cancellation, discard, corrupted journals, and repeated reconciliation.

**Bottom line:** keep current format and restore behavior; take the planning,
policy-separation, and durable-recovery ideas selectively. Do not transplant the
historical parser/exporter/manager/UI stack or its guide data as a second source
of truth.
