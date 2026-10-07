---
name: Portable backup removal and legacy migration
description: Scope boundary for removed FocusFlow file backups and retained legacy database migration.
---

FocusFlow's user-facing `.focusflow` import/export feature has been removed. Keep the one-time legacy `app_settings` database migration so existing installations retain their settings; it is not a file-import path. Do not reintroduce backup screens, file dispatch, or restore coordination unless the user asks.

Keep any future backup portability filtering separate from the historical legacy migration policy. That migration adapter recognizes only part of the current settings model, and broadening it for backup support would change the one-time database migration contract.

After task replacement, run one alarm reconciliation even if the backup has no future scheduled tasks, so alarms for deleted local tasks are cleared. In merge mode, reconcile when the backup adds scheduled tasks; never reconcile once per inserted task.

**Why:** The user asked to remove all FocusFlow backup import/export while preserving existing app behavior and data.

**How to apply:** Treat `.focusflow` files as unsupported by the app runtime unless the user authorizes restoring them. Preserve the existing native migration of legacy settings and do not delete archived/sample backup files unless asked. If backup work is authorized, keep its field mapping and device-local exclusions from changing legacy migration behavior; serialize replace and alarm reconciliation through the shared restore gate.