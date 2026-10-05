---
name: Backup contract authority
description: Product-spec authority for FocusFlow backup, import, and alarm work.
---

Use the reviewed v14 implementation contract for product intent, and inspect the current Kotlin importer, exporter, and settings adapter to verify actual backup behavior before changing backup guidance or prompts. The contract's TypeScript facts stand in for unavailable TypeScript source; do not infer undocumented behavior.

**Why:** The original TypeScript source is unavailable to implementation work, so behavior not documented in the reviewed contract cannot be verified.

**How to apply:** Re-read relevant contract sections and trace the current import/export path. If intended behavior and current code conflict, or a behavior is undocumented, flag the discrepancy rather than guessing.

## FocusFlow identity in task-backup guidance

Describe FocusFlow as a broader focus and digital-wellbeing app, not merely a scheduler. Keep the copied `.focusflow` task prompt scoped to task records, and distinguish per-task options from app-wide settings and other features.

**Why:** The user explicitly asked that the prompt account for FocusFlow's other features and not define the app as a scheduler.

**How to apply:** Give brief, representative context about FocusFlow's focus, blocking, and wellbeing features, then state what a task-only backup can and cannot change. Verify behavior against the current importer and runtime.