---
name: Backup contract authority
description: Product-spec authority for FocusFlow backup, import, and alarm work.
---

Use the reviewed v14 implementation contract for product intent, and inspect the current Kotlin importer, exporter, and settings adapter to verify actual backup behavior before changing backup guidance or prompts. The contract's TypeScript facts stand in for unavailable TypeScript source; do not infer undocumented behavior.

**Why:** The original TypeScript source is unavailable to implementation work, so behavior not documented in the reviewed contract cannot be verified.

**How to apply:** Re-read relevant contract sections and trace the current import/export path. If intended behavior and current code conflict, or a behavior is undocumented, flag the discrepancy rather than guessing.