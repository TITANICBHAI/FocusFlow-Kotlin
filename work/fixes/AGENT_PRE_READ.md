# Incoming Agent Prompt — FocusFlow Backup / Import

Copy this prompt when an agent joins this work after implementation has started:

---

You are taking over the FocusFlow backup/import and alarm-fronting implementation mid-work. Continue from the repository’s current state; do not restart the project or assume the plan’s original source observations are still true.

## Required reading

1. `work/fixes/FOCUSFLOW_BACKUP_IMPORT_TRACKER.md` — current progress, gates, batches, and evidence.
2. `work/fixes/FocusFlow_Backup_Import_Implementation_Plan_v12.md` — authoritative product contract, design, and tests.
3. The current code and relevant tests for the batch you will touch.

## First actions

1. Read the tracker and inspect the current working tree, recent changes, and relevant source before editing.
2. Verify each checked tracker item against the code and recorded verification. Treat unchecked items as open; if a checked item is no longer true, correct the tracker and report the regression.
3. Identify the first eligible unchecked item in batch order and continue that batch. Do not duplicate work already present.
4. Confirm Batch 0 reviewer approvals are explicitly recorded before production implementation. If any required decision is missing, do not implement production changes; report the exact missing gate and continue only with safe, non-implementation analysis/documentation.

## Non-negotiable instructions

- **Do not use subagents. Do not spawn, request, or delegate work to subagents.** Perform investigation, implementation, and verification directly in this session.
- Follow the frozen decisions in §§4 and 69B. Do not invent or silently change product semantics. If the contract and current source disagree, document the mismatch and stop before the affected irreversible or semantic choice.
- Preserve the batch order in the tracker. Keep changes within the active batch unless a dependency or blocker requires otherwise; explain any necessary scope change.
- Re-read the current code at cited locations; file paths, line numbers, and source-audit findings may have drifted.
- Use the tracker’s checkboxes as live status. Tick an item only after implementation **and verification**; add concise evidence in its row and add a progress-log entry after each completed item or coherent sub-batch.
- Do not mark a runtime-only behavior verified based solely on source inspection, nor claim Android alarm/full-screen behavior that was not observed on a capable device.
- Keep normal application writes coordinated with restore as required by the shared write gate. Do not introduce a post-mutation “abort and leave partial state” path.
- Do not expand scope into unrelated features or redesigns.

## Work and handoff expectations

- Implement the next eligible item, then run focused checks. At a coherent milestone, run the strongest available build/tests for the changed behavior.
- If blocked by missing reviewer input, unavailable device capabilities, or the Android build environment, leave affected boxes unchecked and record what is missing and what was actually verified.
- Before handing off, update the tracker, list files and behavior changed, report tests/build commands and results, identify unchecked or blocked items, and name the next eligible tracker item.
- Never claim a batch or the whole feature is complete while its required checks or sign-off remain open.

---