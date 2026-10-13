# FocusFlow Import/Export Upgrade Review

## Decision

**Keep the current native Kotlin import/export flow as the production baseline. Do not replace it with the attached source set.**

The attachments describe a broader restore system with useful ideas: a truthful restore plan, protection-change checks, VPN consent, and crash recovery through a durable journal. They are historical reference sources, not a verified drop-in upgrade for this branch. Adopt only the ideas that solve an agreed product need, and implement them through the current V1 format, repositories, write gate, and UI.

This document reviews the newly attached files under `attached_assets/`. It updates an earlier draft whose links referred to a different attachment set.

## Current implementation: the baseline to preserve

- **One V1 contract:** [`BackupSerializer.kt`](app/src/main/java/com/tbtechs/focusflow/data/backup/BackupSerializer.kt) owns `FocusFlowBackupV1`, export serialization, and import validation. It requires the kind, version 1, settings object, and tasks array; the active importer tolerates missing descriptive metadata such as `summary`, `presetSections`, `appVersion`, and `exportedAtHuman`, but the decoded envelope still requires `platform`. Its preflight limits the input to 8 MiB, depth 12, 2,000,000 JSON nodes, and 20,000 tasks.
- **Safe file handling:** [`BackupFileManager.kt`](app/src/main/java/com/tbtechs/focusflow/data/backup/BackupFileManager.kt) uses Android document-provider streams; bounded strict UTF-8 and JSON preflight are handled by the existing preflight layer.
- **Review before restore:** [`BackupViewModel.kt`](app/src/main/java/com/tbtechs/focusflow/ui/backup/BackupViewModel.kt) owns asynchronous state, retains a validated import for explicit confirmation, and preserves it after a retryable restore failure. [`ImportConfirmScreen.kt`](app/src/main/java/com/tbtechs/focusflow/ui/backup/ImportConfirmScreen.kt) displays the backup summary and warnings. Merge is the default; task replacement is separately selected and warned about.
- **Guarded restore:** [`BackupRestoreEngine.kt`](app/src/main/java/com/tbtechs/focusflow/data/backup/BackupRestoreEngine.kt) applies writes through the shared `RestoreGate`, blocks task replacement during an active Focus Session, skips duplicate task IDs, turns expired `scheduled` tasks into `skipped`, and reconciles alarms once after the batch.
- **Portable settings boundary:** [`PortableSettingsPolicy.kt`](app/src/main/java/com/tbtechs/focusflow/data/backup/PortableSettingsPolicy.kt) is an explicit export projection that omits device-local fields. [`BackupSettingsAdapter.kt`](app/src/main/java/com/tbtechs/focusflow/data/backup/BackupSettingsAdapter.kt) maps current portable settings without changing the historical one-time migration adapter.
- **External opening:** `ACTION_VIEW` file/content URIs are staged and routed into the same validation, review, and restore flow as an import selected from Settings.

This is a working implementation, not an all-or-nothing transaction. The restore gate prevents competing mutations, but a process death or repository failure between multiple setting/task writes can leave a partially applied restore. The archive's durable journal is relevant to that specific recovery gap; it is not a reason to replace the working parser, picker, format, or confirmation flow.

## The archive's overall design

The reference source set is organized around these ideas:

1. Parse a V1 backup into normalized task/settings data while preserving raw JSON for compatibility.
2. Stage the validated import privately so the UI can be recreated without rereading the original document.
3. Build a restore plan from the imported payload and a local task snapshot; show counts/conflicts before applying.
4. Ask separately which sections to import, require a Defense PIN for certain protection reductions, and handle VPN permission.
5. Persist a restore journal and apply task, settings, and reconciliation phases behind a closed gate; retry after interruption.
6. Show progress and a post-import protection summary, and provide a format guide and task-only AI prompt.

Those are sensible product and architecture themes. The specific implementations have contract mismatches, fail-open or stale-state risks, duplicated ownership, and no included tests. The source manifest identifies a historical commit and verifies file hashes; it does not establish compatibility or correctness on the current branch.

## File-by-file assessment

| Attached reference | Essence | Decision and reason |
|---|---|---|
| [`AndroidRestorePhaseActions_1791875892208.kt`](attached_assets/AndroidRestorePhaseActions_1791875892208.kt) | Adapts restore phases to task/settings repositories and alarm reconciliation. | **Adapt the boundary only.** The separation of task writes, settings writes, and reconciliation is useful if crash recovery is approved. Its concrete repository methods belong to the historical branch. Map to current repositories and prove replay safety first. |
| [`BackupCoordinator_1791875892210.kt`](attached_assets/BackupCoordinator_1791875892210.kt) | Activity-facing bridge for picker operations, staging, preview, PIN, VPN, restore, and result summaries. | **Do not transplant.** It duplicates the current `BackupViewModel`/Settings flow and combines many policies in one coordinator. Reuse only small, independently tested policy decisions that the product adopts. |
| [`BackupEnvelopeV1_1791875892211.kt`](attached_assets/BackupEnvelopeV1_1791875892211.kt) | Raw-JSON V1 parser, task normalization, warnings, and unknown-field preservation. | **Compare cases; do not add a second parser.** It accepts an omitted version as V1, while the current importer requires an explicit version 1; it rejects duplicate IDs in the file, while the current restore engine skips repeated IDs; and its field/metadata requirements differ. Any compatibility change must be made in the existing serializer and covered by fixtures. It also validates a reminder's `taskId` as a string but does not verify that it matches the containing task. |
| [`BackupManager_1791875892212.kt`](attached_assets/BackupManager_1791875892212.kt) | Combines export intent/file writing, bounded reads, parser adaptation, and callback-based restore. | **Do not transplant.** Those responsibilities already have owners in the current app. This manager also retains a second, direct callback restore path alongside the journal coordinator. In that path, a settings-write failure is downgraded to a warning before task restore continues; task-list read failure falls back to a caller snapshot; and per-task deletion errors are swallowed during replacement. Those behaviors do not provide a reliable replacement contract. |
| [`BackupV1Exporter_1791875892213.kt`](attached_assets/BackupV1Exporter_1791875892213.kt) | Pure V1 JSON writer, timestamp canonicalization, preset sections, summary, and settings filtering. | **Use as a test/reference checklist, not as the exporter.** A pure writer and golden-fixture coverage are useful. Its settings filter is primarily a deny-list followed by copying the remaining input fields; an unlisted device-local key could therefore escape. The current explicit portable projection is the safer export boundary. |
| [`FILE_PURPOSES_1791875892214.md`](attached_assets/FILE_PURPOSES_1791875892214.md) | Describes the archive contents and removed tests/runners. | **Use as provenance, not validation.** It says the sources are from a historical commit and not guaranteed to compile here. It also records that 15 Kotlin tests and 2 test scripts were removed from the archive. The source listing is useful for navigation, but behavior must be verified against current code and new tests. |
| [`FocusFlowAgentGuide_1791875892215.kt`](attached_assets/FocusFlowAgentGuide_1791875892215.kt) | Self-contained prompt for creating task-only `.focusflow` files, including date/time-zone and device-state boundaries. | **Adapt the task-only scope and clarification rules.** The prompt is clearer than a generic backup request and explicitly keeps `settings` empty. Do not copy implementation claims without checking the current parser: it says expired `active` tasks are skipped, but the current engine only converts expired `scheduled` tasks; the active importer also requires fields that this prompt presents as optional/defaulted. |
| [`FocusFlowFileGuideScreen_1791875892216.kt`](attached_assets/FocusFlowFileGuideScreen_1791875892216.kt) | Guide screen with copy-prompt and web-guide actions. | **Keep the current app screen.** The copy action is useful, but this source is in the old `ui.settings` package and is coupled to this archive's guide data. Reuse only missing user-facing affordances after comparing with the integrated current support guide. |
| [`FocusFlowFormatGuideContent_1791875892218.kt`](attached_assets/FocusFlowFormatGuideContent_1791875892218.kt) | Compose layout for format sections, callouts, facts, and JSON examples. | **Use as a layout reference only.** Its hierarchy can inform future documentation/UI polish. Do not bring over copy or examples without reconciling them with the active serializer, task model, and restore behavior. |
| [`FocusFlowFormatGuideData_1791875892219.kt`](attached_assets/FocusFlowFormatGuideData_1791875892219.kt) | User-facing format facts, import semantics, settings list, and example backups. | **Do not copy as-is.** It says an omitted version is treated as V1, but current parsing requires version 1; its example envelope omits `platform`, which the current decoded envelope requires; and it calls a same-ID task with differing content a conflict, while current restore skips by ID without comparing content. The examples and policy descriptions must follow the live contract, not the archive's intended one. |
| [`ImportConfirmScreen_1791875892220.kt`](attached_assets/ImportConfirmScreen_1791875892220.kt) | Full review UI with task/settings selection, merge/replace, preview counts, PIN prompt, VPN consent, recovery, and result cards. | **Take selected interaction ideas, not the screen.** Section selection and truthful counts could help, but this 837-line composable owns launchers, coroutine work, policy branching, recovery state, and presentation. The current screen already owns confirmation safely. Extend it only after defining the new state contract. The VPN branch also exposes the stale-permission problem described below. |
| [`ImportProtectionPolicy_1791875892221.kt`](attached_assets/ImportProtectionPolicy_1791875892221.kt) | Compares snapshots of protected settings to decide whether to require a PIN. | **Do not use without redesign.** It compares sets of IDs/identities, not enforcement values. A schedule or allowance can become weaker under the same ID/package without triggering the removal check. Malformed local allowance JSON becomes an empty set, which can hide current protected state and fail open. Compare effective values and fail closed when local protected state cannot be read. |
| [`ImportProtectionSummaryPolicy_1791875892221.kt`](attached_assets/ImportProtectionSummaryPolicy_1791875892221.kt) | Pure mapping from imported protection facts to active/inactive result rows. | **Adapt if the result summary is wanted.** The pure fact-to-copy split is good. The caller must compute facts from post-restore settings and current permissions; counts or values merely present in the backup do not prove enforcement is active. |
| [`MANIFEST_1791875892222.tsv`](attached_assets/MANIFEST_1791875892222.tsv) | Maps the 18 archived Kotlin sources to repository paths and records byte sizes/SHA-256 values. | **Use to identify/prove the supplied snapshot only.** Matching hashes establish that the attached files match the manifest entries, not that they pass current build/tests or are safe to merge. |
| [`RestoreCoordinator_1791875892223.kt`](attached_assets/RestoreCoordinator_1791875892223.kt) | Stages pending imports, generates previews, admits restores, closes the gate, writes a journal, and launches recovery in application scope. | **Architecture reference; do not drop in.** Durable staging and admission separate from UI cancellation are useful for a journaled restore. This class coordinates parsing, local snapshots, PIN verification, conflict handling, journaling, and recovery; it must be redesigned around current interfaces and a narrower responsibility split. |
| [`RestorePlan_1791875892224.kt`](attached_assets/RestorePlan_1791875892224.kt) | Pure merge/replace plan, conflict projection, counts, task normalization, and journal models. | **Best source for a future preview, after adapting semantics.** The pure plan is useful, but it introduces a behavior change: merge rejects different content under an existing ID, whereas current restore skips every colliding ID. Its “identical” projection ignores reminder and timestamp differences. Decide whether that is the intended identity rule, then make preview and actual restore use the same plan. |
| [`RestoreProgressDisplay_1791875892225.kt`](attached_assets/RestoreProgressDisplay_1791875892225.kt) | Maps restore phases to user-facing progress copy. | **Only adopt with the journal phases.** The four-step copy is small and easy to reuse, but otherwise it describes internal states that the current flow does not have. |
| [`RestoreRecoveryEngine_1791875892225.kt`](attached_assets/RestoreRecoveryEngine_1791875892225.kt) | Replays journal phases with retry limits, timeouts, blocked recovery, retry/discard, and gate control. | **Take the recovery principle; rewrite against current ports and test it.** Keeping the gate closed after an interrupted write and requiring retry or explicit discard is the archive's strongest reliability idea. It replays a phase after failure, so each phase must be idempotent. A timeout does not make a partially completed repository call atomic. |
| [`RestoreStores_1791875892226.kt`](attached_assets/RestoreStores_1791875892226.kt) | Atomic private pending-import and journal files, versioned codecs, bounded reads, and quarantine. | **Adapt atomic-write discipline; do not copy limits blindly.** `AtomicFile`, private storage, explicit versions, and corruption states are useful. Its stored-document cap is 24 MiB, while the current imported-file cap is 8 MiB. Align limits and test interrupted writes, corruption, quarantine, retry, and cleanup before adoption. |
| [`VpnImportPolicy_1791875892227.kt`](attached_assets/VpnImportPolicy_1791875892227.kt) | Separates VPN notice, consent decision, activation request, and inactive-state copy. | **Take the separation; fix the stale-state bug before use.** `PERMISSION_ALREADY_GRANTED` returns `true` without checking the current `vpnPermissionGranted` argument. The confirmation screen passes `false` in that path, yet the policy still authorizes activation. Permission must be rechecked when activation is committed, and the outcome must reflect the resulting live state. |

## What to take, in priority order

### 1. A truthful preview plan

This is the best first feature-sized upgrade. Build a pure preview from the validated envelope and the same local snapshot/rules the restore will use. Show:

- tasks to add, task IDs that collide, invalid rows skipped, and scheduled tasks normalized because they have ended;
- portable settings that will actually change, unsupported fields that will remain untouched, and device-local fields that are never imported;
- whether the choice applies to tasks only or also to explicitly selected settings;
- any enforcement protection that would be weakened, and any Android permission or external resource needed for activation.

Do not claim duplicate tasks are content-identical unless the current restore policy compares their content. Today, a task ID collision is skipped; the archive's planner instead compares a projection and can reject a conflict. That is a product behavior change, not merely better preview copy. Prefer generating the preview and apply operations from one shared plan so their counts cannot drift.

### 2. Protection and VPN decisions, only if approved as product behavior

The explicit policy boundaries are worth keeping. Reimplement protection comparison on effective values (including schedule times/enabled state and allowance budgets), and treat unreadable local protected state as unknown/fail-closed. Run the same checks again at apply time. For VPN activation, re-read permission immediately before activation and derive the success summary from post-import state and current permission.

The archive's PIN prompt, import-section checkboxes, and automatic activation change the product's confirmation behavior. Keep them out until the desired consent/PIN rules are specified and tested; do not silently add new destructive or automatic-enforcement behavior as part of a parser cleanup.

### 3. Crash-resumable restore, as a separate reliability phase

Use the journal idea only if recovery from process death during restore is a requirement. Before implementation, establish and test idempotency for task replacement/insertion, preference writes and side effects, alarm reconciliation, result persistence, journal phase advancement, retries, cancellation, startup recovery, and discard. Persist only private, bounded restore data. Ensure the write gate cannot reopen while a valid or quarantined restore remains unresolved.

Do not import the archive's 24 MiB store cap unchanged. The current file cap is 8 MiB; pending/import and journal limits must be bounded consistently and tested.

### 4. Keep the format and documentation single-sourced

Keep the current V1 serializer and explicit portable export policy authoritative. Add fixture tests for any compatibility relaxation, and update the in-app guide and task-only prompt against current behavior. Do not allow an old guide, second exporter, or second parser to redefine V1 semantics.

## Specific archive risks and mismatches

1. **Historical, unverified source.** `FILE_PURPOSES_1791875892214.md` identifies a historical commit and says the archive is not guaranteed to compile against this branch. It lists 15 removed Kotlin tests and 2 removed runner scripts. The attached production sources therefore arrive without their original verification evidence.
2. **Competing format authority.** The archive adds a second parser/exporter and manager despite existing current owners. The parser accepts omitted `version`; current production parsing requires the integer `1`. The archive also allows nullable/missing envelope metadata that may not decode under the current `BackupEnvelope`.
3. **Export boundary is weaker.** The archived exporter starts with the entire settings object and removes a list of known keys. The current exporter constructs a portable allowlisted projection. A future setting added to the model is safer when it is excluded by default.
4. **Restore isn't atomic merely because it is journaled.** The recovery runner may repeat an action after partial success. Repository operations and derived side effects need idempotency or their own transactional semantics; `AtomicFile` protects the journal write, not the database/settings phase.
5. **Protection policy can miss weakening.** Stable IDs/packages do not mean stable enforcement. Malformed current allowance data must not be interpreted as “nothing is protected.”
6. **VPN authorization can go stale.** The archived policy's already-granted branch bypasses the fresh permission argument, and its caller can turn that result into activation. Recheck permission at the point of activation.
7. **Preview and apply must agree.** The archive has useful preview counts and conflict reporting, but changes merge semantics relative to current production. A preview built from one rule set and an apply path using another would be misleading.
8. **Large UI/coordinator surface.** The archive centralizes picker, policy, recovery, PIN, VPN, and result state across a broad coordinator and an 837-line composable. Copying that structure increases coupling instead of upgrading the existing ViewModel and confirmation screen.
9. **Guide drift.** The attached format guide says missing version defaults to V1, claims different same-ID tasks conflict, and shows an envelope without `platform`. Those statements do not describe the current production parser/model/restore path.

## Suggested implementation sequence

| Phase | Scope | Gate to proceed |
|---|---|---|
| A. Contract audit | Preserve current V1 writer/parser; add regression fixtures for required fields, tolerated metadata, bounds, invalid rows, and portable settings. Correct any user-facing format-guide drift. | Current parser/exporter remain the only format authorities; tests pass in both product flavors. |
| B. Preview plan | Add a pure planner over the current parsed envelope and local state. Make preview and apply share duplicate, normalization, settings, and count rules. Extend the existing confirmation UI rather than replacing it. | Explicit product decision on same-ID differing content; preview values match applied results in tests. |
| C. Protection/VPN (optional) | Add settings section selection, PIN gating, protection-value comparison, current-permission activation checks, and truthful post-import summary only if desired. | Fail-closed tests cover unreadable local state, stable-ID value changes, permission revoke/deny, and retry. |
| D. Durable recovery (optional) | Add private pending-import/journal stores, gated phase replay, startup recovery, retry/discard, and progress states. | Fault-injection tests prove each replay boundary and repeated action is safe; size limits match the V1 cap. |

Do not combine phases B–D into one transplant. B changes preview semantics; C adds new security/consent behavior; D adds a persistent recovery subsystem. Each needs a separately reviewable contract and test set.

## Verification and scope

- The attached `MANIFEST_1791875892222.tsv` records hashes for the 18 source files. `FILE_PURPOSES_1791875892214.md` is the archive's own inventory and caveat.
- The current app's existing local unit-test evidence is recorded in `work/focusflow-import-export-tracker.md`. It does not verify the attached archive.
- This review updates this Markdown proposal only. No attached source, current application source, import data, or backup fixture was modified, and no build/test or device verification was run for this documentation-only change.

**Bottom line:** preserve the current stable V1 flow. The most valuable near-term adaptation is a truthful plan-driven preview. Protection/PIN/VPN changes and durable crash recovery are separate optional upgrades; adopt them only with corrected policies and tests, not by copying the historical sources wholesale.
