---
name: Replit config side effects
description: Automatic toolchain provisioning and protected .replit edits in this workspace
---

Invoking a language runtime that is not already configured can automatically add its module to `.replit`. Direct edits to `.replit` are blocked by the workspace; restore or change it by writing the full TOML to a temporary file inside the workspace and calling `verifyAndReplaceDotReplit({ tempFilePath: absolutePath })`. Relative paths fail with `DOT_REPLIT_EDITING_ERROR`.

**Why:** A Python source-check command caused an unrelated module entry to appear, and the normal patch operation was rejected by the config guard.

**How to apply:** Prefer already-configured shell or Node tools for one-off checks. If a `.replit` change is needed, use the validated temporary-file replacement flow and verify the final diff.