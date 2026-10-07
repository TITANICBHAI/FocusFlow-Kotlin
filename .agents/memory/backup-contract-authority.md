---
name: Portable backup removal and legacy migration
description: Scope boundary for removed FocusFlow file backups and retained legacy database migration.
---

FocusFlow's user-facing `.focusflow` import/export feature has been removed. Keep the one-time legacy `app_settings` database migration so existing installations retain their settings; it is not a file-import path. Do not reintroduce backup screens, file dispatch, or restore coordination unless the user asks.

**Why:** The user asked to remove all FocusFlow backup import/export while preserving existing app behavior and data.

**How to apply:** Treat `.focusflow` files as unsupported by the app runtime. Preserve the existing native migration of legacy settings and do not delete archived/sample backup files unless asked.