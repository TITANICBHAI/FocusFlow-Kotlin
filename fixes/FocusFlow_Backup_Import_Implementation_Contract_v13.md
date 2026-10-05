# FocusFlow `.focusflow` Backup / Import / Alarm — Implementation Contract v13

**Supersedes v12.** Self-contained: the implementation agent does **not** have the TypeScript source, so every TS fact needed is written here (Appendix B lists them). Where this document and v12 disagree, this document wins.

**Evidence tags used throughout**

| Tag | Meaning |
|---|---|
| `[TS]` | Verified by reading the TS hybrid source (`backupService.ts`, `types.ts`, `database.ts`, `notificationService.ts`, `AppContext.tsx`, `import-confirm.tsx`, `_layout.tsx`, `changelog.tsx`) |
| `[KT]` | Verified by reading the Kotlin archive |
| `[DOC]` | Verified against Android documentation fetched during this review (FSI limits page; Activity security / background-activity-launch page) |
| `[KNOWLEDGE]` | Android platform behavior from general knowledge, **not** re-verified. Implementer must confirm against current docs before relying on it |
| `[DEVICE]` | Cannot be settled from source; needs a device test (Section 9.6) |
| `PRODUCT DECISION REQUIRED` | Source does not establish intent. A **default** is given so work can proceed; the owner must confirm. Each default sits behind a named constant so it is a one-line change |

---

## 0. Verdict and what changed from v12

**v12 verdict: NEEDS TARGETED CORRECTIONS.** Its skeleton (gate → session → idempotent replay → reconcile) was sound. It was not implementable by an agent without TS access because it contained no wire schemas, and several of its semantic rulings contradict the TS source.

| # | v12 position | Evidence | v13 ruling |
|---|---|---|---|
| 1 | Abort the whole import when a same-ID task differs from the local one | `[TS]` restore skips matching IDs. The import screen says existing IDs are kept; the changelog says "Add tasks skips matching IDs" | **Existing wins, skip, report** identical vs divergent counts. Never abort (PD-1) |
| 2 | Statuses: scheduled / completed / skipped | `[TS]` `TaskStatus` = `scheduled \| active \| completed \| skipped \| overdue`. `overdue` is persisted by a startup sweep | Five statuses. Unknown status ⇒ that task invalid, not the file |
| 3 | Exact-alarm-only, no downgrade | No source basis; `[KT]` `AlarmRepository` already has a three-tier ladder | Keep ladder; surface capability (PD-2) |
| 4 | Per-`Reminder` identity, ID remap, per-reminder registry | `[TS]` `Task.reminders` is always `[]` (`createTask`) and `defaultReminderOffsets` is never read; reminders are derived from task start/end | **Delete reminder identity machinery.** Preserve `reminders` opaquely. Task IDs are never remapped in V1 |
| 5 | Persist `PendingImport` to disk; reject a second file | `[TS]` pending import is in-memory only; staging a new file replaces the old one. No mutation happens before confirm | In-memory only; **latest file wins** while previewing. Reject only while a restore is running |
| 6 | Reject unknown/duplicate JSON keys via `org.json` | `[KT]` `org.json` is last-wins on duplicates | Streaming parser with explicit duplicate detection (Section 4) |
| 7 | `launcherDockPackages` / `launcherClockStyle` = `READ_IGNORE_LEGACY` | `[TS]` both are real settings synced to native prefs by `updateSettings`; `[KT]` `LauncherActivity` reads `launcher_dock_packages` from the shared prefs file; `SettingsRepository` persists both keys | **Portable, apply** |
| 8 | Matrix treats the 21 omitted keys as "currently exported" by Kotlin | `[KT]` `BackupManager` strips the same 21 keys **at export** (`PORTABLE_OMITTED_KEYS`, referenced only there), so v12's "currently exported" is wrong. **But** `BackupCoordinator.mergePortableBackup` applies `vpnBlockEnabled`, `systemGuardEnabled`, `pomodoroEnabled`, `alwaysOnEnforcementEnabled` (and other live keys) from any imported file, with **no import-side filter** | **Security P0** on the import side: an untrusted file can switch off protections. Never apply any of the 21 live-state keys |
| 9 | Clear external wallpaper paths on import | Clearing destroys a valid local value | **Ignore** (keep local), report count |
| 10 | Reminder/alarm FSI "capability tuple" only | `[KT]` no `canUseFullScreenIntent()` anywhere; global PendingIntent request code 7 and notification ID 9101 | Per-task identity, capability probe, diagnostics (Section 9) |

**Architecture preserved from v12 (justified):** global writer gate, validated immutable plan, persisted journal with idempotent replay, derived-state reconcile after commit, no AlarmManager side effects before commit. These are kept but **fully specified below** because the agent cannot refer back to v12.

---

## 1. Product decisions

Each default is implemented now. The owner confirms or changes the constant.

| ID | Question | Default implemented | Why default / alternatives |
|---|---|---|---|
| **PD-1** | Merge, same task ID, different content | Existing local task wins; skipped; counted as `skippedIdentical` / `skippedDivergent`; divergent titles listed in preview | Established by TS UI copy and changelog. Alternatives: backup wins (can clobber newer edits); per-task choice (out of scope) |
| **PD-2** | Exact-alarm permission unavailable | Ladder `setAlarmClock` → `setExactAndAllowWhileIdle` → `setAndAllowWhileIdle`; permission state shown in diagnostics; reconcile upgrades on grant | Exists in `[KT] AlarmRepository`. Do **not** assert which tier needs which permission without checking current docs `[KNOWLEDGE]` |
| **PD-3** | Imported task with status `active` | Treated exactly like `scheduled`: future end ⇒ `scheduled`; past end ⇒ `skipped`, `updatedAt = planNow`. Never imported as live | `active` is runtime state of the *source* device. TS has no code that writes it (not provable either way) |
| **PD-4** | Port TS 365-day task-history prune? | Not ported. Import history verbatim | `[TS]` prunes completed/skipped older than 365 days. `[KT]` no equivalent found. If ported later, import must apply the same cutoff |
| **PD-5** | Task-end alarm missed because device was off | No late alarm. Past tasks are never alarmed. Status handled by the startup overdue sweep (Section 8.5) | TS behavior; a "you missed X" notification is a new feature |
| **PD-6** | Notification permission denied | Still schedule the task-end alarm; diagnostics show "cannot alert" | **Deviation:** `[TS]` returns before scheduling anything (including the native alarm) when permission is denied. Looks like accidental coupling, not intent |
| **PD-7** | Intent filter breadth | Keep `application/octet-stream` filter and `*/*` fallback; **remove `BROWSABLE`** from both `content` filters; reject non-backup content fast with a clear message | `[KT]` the current `*/*` content filter offers FocusFlow as a handler for every content file type. Option: drop `*/*` and rely on octet-stream plus the in-app picker `[DEVICE]` |
| **PD-8** | Imports that *weaken* protection while a Defense PIN is active | If `pinProtectionEnabled` is on locally and a PIN is set, require PIN at confirm when the import would remove any entry from `alwaysOnPackages`, `alwaysOnVpnPackages`, `blockedWords`, `recurringBlockSchedules`, `greyoutSchedule`, `dailyAllowanceEntries`, or turn `focusMirrorVpnEnabled` from true to false | `[TS]` has no such gate (not verified either way). An external file should not silently downgrade enforcement |
| **PD-9** | External file opened before onboarding / privacy acceptance | Stage in memory; show "Backup ready to import" only after onboarding completes | `[TS]` onboarding handling of external files not verified |
| **PD-10** | FSI unavailable UX | One-time prompt per app version plus a persistent diagnostics row | Not a data-integrity matter |
| **PD-11** | Are Kotlin-only preferences (notification toggles, `bedTime`, thresholds, `taskRemindersEnabled`, `autoFocusEnabled`) portable between devices? | Not portable: not exported, ignored on import. Keeps V1 byte-compatible with TS | If the owner wants Kotlin-to-Kotlin portability, add them under one namespaced object `settings.kotlin` (an unknown key that TS spread-merges harmlessly). That is a V1-compatible extension, not a schema redesign |

---

## 2. Authoritative TS facts (the agent has no TS source)

### 2.1 V1 envelope `[TS backupService.ts]`

```jsonc
{
  "kind": "FocusFlowBackupV1",          // REQUIRED exact string
  "version": 1,                          // TS writes 1; TS import never checks it
  "exportedAt": "2026-06-15T14:30:00.000Z",   // toISOString(), always .sssZ
  "exportedAtHuman": "15/06/2026, 14:30:00",  // toLocaleString() — locale text, INFORMATIONAL
  "appVersion": "1.1.2",                 // optional string
  "platform": { "os": "android" },
  "settings": { ... },                   // REQUIRED object
  "tasks": [ ... ],                      // REQUIRED array
  "presetSections": [ ... ],             // INFORMATIONAL inventory; TS import never reads it
  "summary": { "taskCount": 0, "blockedWordCount": 0, "greyoutWindowCount": 0, "dailyAllowanceCount": 0 } // INFORMATIONAL
}
```

TS import accepts a file iff: JSON parses, top level is an object, `kind === "FocusFlowBackupV1"`, `settings` is an object, `tasks` is an array. Nothing else is checked. File extension is not enforced (`.focusflow` or `.json`, "content may still be valid").

**Kotlin rules:** require the same four conditions. If `version` is present and is not integer `1`, reject (non-mutating). Missing `version` ⇒ treat as 1. `summary`, `presetSections`, `platform`, `appVersion`, `exportedAtHuman` are never trusted and never drive behavior. Unknown fields at every level are ignored (and never logged by value).

### 2.2 Older V1 exports carry live toggles

TS's current exporter strips 21 keys (Section 2.4), but **older V1 files** (for example `appVersion` 1.0.6) include `focusModeEnabled`, `pomodoroEnabled` and similar. TS's restore spreads the whole `settings` object over local settings, so old files overwrote local toggles. That is a TS defect and is **not** retained. Treat an old-export fixture containing these keys as mandatory test data.

### 2.3 Task object `[TS types.ts]`

| Field | Type | Notes |
|---|---|---|
| `id` | string | nanoid-style, about 21 chars from letters, digits, `_`, `-`. Primary key |
| `title` | string | required |
| `description` | string? | optional |
| `startTime`, `endTime` | string | ISO-8601 UTC instants (`toISOString`, always `.sssZ`) |
| `durationMinutes` | integer | |
| `status` | enum | `scheduled` `active` `completed` `skipped` `overdue` |
| `priority` | enum | `low` `medium` `high` `critical` |
| `tags` | string[] | |
| `reminders` | Reminder[] | always `[]` in practice. Preserve opaquely, never schedule from it |
| `color` | string | hex such as `#22c55e` |
| `focusMode` | boolean | |
| `focusAllowedPackages` | string[]? | **Three-way:** absent/null = use the global allow list; `[]` = all apps allowed; `[...]` = that list. Must round-trip exactly |
| `createdAt`, `updatedAt` | string | ISO-8601 UTC |

`Reminder` (preserve only): `{ id, taskId, offsetMinutes:number, type:"pre-start"|"at-start"|"post-start", notifId?:string }`.

TS restore behavior for tasks:

1. Rows are inserted with `INSERT OR IGNORE` keyed by `id`.
2. A task with a missing or falsy `id` is skipped and counted.
3. An id already present locally is skipped and counted (**existing wins**).
4. A `scheduled` task whose `endTime` is in the past is stored as `skipped` with `updatedAt = now`.
5. All imported rows are inserted with alarms suppressed (`skipAlarms:true`). After the loop, every imported task that is still `scheduled` is passed once to the scheduler.
6. Duplicate IDs *inside* the file are not detected by the TS loop. The DB silently ignores the repeat, yet TS still counts it as imported. This is a TS counting bug, not retained.
7. `replaceTasks` refuses to run while a focus session is active (in-memory flag **or** DB row), then deletes every existing task one by one through the normal delete path, then inserts.

### 2.4 Settings: the 21 keys TS never exports (device-local live state)

```
standaloneBlockPackages standaloneBlockUntil standaloneVpnPackages autoCopiedAlwaysOnPackages
focusModeEnabled pomodoroEnabled notificationsEnabled weeklyReportEnabled launcherEnabled
alwaysOnEnforcementEnabled aversionDimmerEnabled aversionVibrateEnabled aversionSoundEnabled
systemGuardEnabled blockInstallActionsEnabled blockYoutubeShortsEnabled blockInstagramReelsEnabled
vpnBlockEnabled autoCopyToAlwaysOn vpnSelfHealEnabled pinProtectionEnabled
```

TS exports everything else in `AppSettings`, with `focusMirrorVpnEnabled` forced to `settings.focusMirrorVpnEnabled ?? false` (explicitly "portable"). On import, TS does `{...localSettings, ...backup.settings, focusMirrorVpnEnabled: backup.focusMirrorVpnEnabled ?? local ?? false}` and then calls its normal `updateSettings` (which syncs native prefs). **Collections are overwritten, never unioned.**

### 2.5 Nested objects `[TS types.ts]`

```
GreyoutWindow           { pkg, pkgs?[], startHour, startMin, endHour, endMin, days[], scheduleId?, scheduleName?, vpnEnabled? }
RecurringBlockSchedule  { id, name, packages[], days[], startHour, startMin, endHour, endMin, enabled, vpnEnabled?, vpnPackages?[] }
DailyAllowanceEntry     { packageName, mode: "count"|"time_budget"|"interval", countPerDay?, budgetMinutes?, intervalMinutes?, intervalHours? }
AllowedAppPreset        { id, name, packages[] }     // packages [] means "all apps allowed"
BlockPreset             { id, name, packages[] }
UserProfile (all optional) { name, occupation, dailyGoalHours, wakeUpTime "HH:MM", sleepTime "HH:MM",
                             focusGoals[], chronotype, focusSessionLength, breakStyle,
                             distractionTriggers[], motivationStyle[], weeklyReviewDay }
   chronotype: morning|midday|afternoon|evening|night|flexible
   breakStyle: short_frequent|balanced|long_infrequent|no_break
   weeklyReviewDay: sun|mon|tue|wed|thu|fri|sat
```

**Day numbering (critical):** `days[]` in both `GreyoutWindow` and `RecurringBlockSchedule` is `Calendar.DAY_OF_WEEK`: **1=Sunday … 7=Saturday**. Hour/minute fields are device-local wall-clock; overnight windows (end earlier than start) cross midnight and must be preserved, not normalized. `weekStartDay` is a different convention: integer 0=Sunday … 6=Saturday. Adapters must be tested on Sunday (1) and Saturday (7) specifically.

### 2.6 Greyout vs recurring schedules `[TS AppContext._recurringSchedulesToGreyoutWindows]`

`recurringBlockSchedules` is the **authoritative, user-authored** list. `settings.greyoutSchedule` holds (a) user-created windows with **no** `scheduleId`, plus (b) a possibly stale copy of windows derived from recurring schedules (with `scheduleId`). The native layer is fed `[...windowsWithoutScheduleId, ...derived]`, where derived = for each schedule with `enabled && packages.length>0`, one window **per package**:

```
{ pkg, startHour, startMin, endHour, endMin, days: sched.days,
  scheduleId: sched.id, scheduleName: sched.name, vpnEnabled: sched.vpnEnabled ?? false }
```

Rule: on import, take only the no-`scheduleId` windows from backup `greyoutSchedule`; discard windows that carry a `scheduleId`; then regenerate derived windows from the imported `recurringBlockSchedules`. On export, write both fields: `recurringBlockSchedules` verbatim and `greyoutSchedule` = user windows + derived windows. `[KT]` today `BackupCoordinator` writes recurring schedules under the `greyoutSchedule` key with field names `startMinute/endMinute/daysOfWeek`. That is wrong on the wire.

### 2.7 Notification schedule `[TS notificationService.ts]`, identical in `[KT] NotificationRepository`

Per task with status not in `{completed, skipped, overdue}`, identifiers:
`<id>-pre-600000`, `<id>-pre-300000`, `<id>-pre-60000` (10, 5, 1 min before start), `<id>-pre0` (at start), `<id>-mid900000`, `<id>-mid1800000` (15 and 30 min after start), `<id>-almost` (end − 60 s), `<id>-end`.
Rules: skip any slot whose trigger is less than 1 s from now; mid check-ins only if the fire time is before the end **and** at least 10 minutes remain; global budget 450 scheduled notifications, earliest first; a separate native task-end alarm is scheduled only when `endTime` is in the future, keyed by task ID.
`[KT]` `NotificationScheduler` is an interface with **no implementation** and `NotificationRepository` is never constructed in `AppModule`. Reminder notifications therefore do not exist in the Kotlin app today (Section 8).

### 2.8 Other TS behavior that affects the plan

- Startup sweep: unfinished tasks whose end has passed are persisted as `overdue`.
- Import UI: file is read and parsed first, a preview with counts is shown, nothing is mutated until the user confirms; a "Replace tasks" toggle exists; "Existing task IDs are kept" is shown for merge.
- The pending import is in-memory (`pendingBackupImport.ts`); staging replaces any earlier staged import.
- Incoming-file handler keeps a `handledUris` set that is **never cleared**: re-opening the same URI after Cancel was silently ignored. Not retained.
- `PendingPresets` and the doc comment "imports land as TEMPORARY presets" are **dead**: no TS code reads or writes `pendingPresets`. Observed behavior is direct apply after a confirm screen.
- `weekStartDay` has default 0 and no TS UI writes it.
- `focusSessionLength` writes through to `defaultDuration`/`pomodoroDuration` when the profile is *saved in the UI*. Import must **not** re-apply that write-through.
- TS blocks `replaceTasks` during an active focus session. Native alarm source is **not** in the TS archive.

---

## 3. Data-boundary classification

| Data | Class | In V1 file | Import behavior |
|---|---|---|---|
| Task rows (all five statuses) | persistent user data | yes | Merge / Replace rules (Section 5) |
| `Task.reminders` | persistent, inert | yes | Preserve opaquely |
| Analytics and history tables (daily completions, ratings, findings, insights, report notes, temptation log) | persistent, **not** portable in V1 | no | **Never touched**, including by Replace |
| Portable settings (Section 3.1) | persistent user data | yes | Overwrite per key |
| 21 live-state keys | device-local runtime | stripped on export | **Never applied**, even if present |
| Focus session row, `focus_active`, standalone block state | runtime/session | no | Never imported. Active session blocks Replace |
| PIN hash / Defense password | device-local secret | no | Never |
| `onboardingComplete`, `privacyAccepted` | device-local consent | TS exports them | **Never applied** (would skip onboarding and consent on a new device) |
| `launcherWallpaperUri`, `overlayWallpaper` | external resource (local file path) | TS exports | **Ignore**, keep local, report `externalResourcesIgnored` |
| Package-name references | portable identifiers | yes | Keep even if not installed; never fail import for missing apps; validate syntax only |
| AlarmManager alarms, PendingIntents, posted notifications, channels, alarm registry, reminder ledger | **derived OS state** | no | Never exported/imported; rebuilt by the reconciler |
| `PendingImport` | transient, memory only | n/a | Never written to disk |
| `RestoreJournal` | transient, `noBackupFilesDir` | n/a | Deleted on completion (Section 6) |

### 3.1 Settings matrix (wire key → disposition)

**APPLY (overwrite local value; absent key = leave local unchanged):**

| Wire key | Kotlin target `[KT]` unless marked VERIFY | Note |
|---|---|---|
| `defaultDuration` | `defaultDurationMinutes` | Kotlin export currently uses the Kotlin name on the wire — wrong |
| `pomodoroDuration`, `pomodoroBreak` | `pomodoroWorkMinutes`, `pomodoroBreakMinutes` | same |
| `allowedInFocus` | `allowedFocusPackages` | |
| `alwaysOnPackages` | `alwaysBlockPackages` | |
| `alwaysOnVpnPackages` | VERIFY (VPN repository package set) | |
| `focusMirrorVpnEnabled` | VERIFY | Imported value wins when present, else keep local. **Not** `||` (current Kotlin bug) |
| `blockedWords` | `PREF_BLOCKED_WORDS` via `setBlockedWords` | |
| `dailyAllowanceEntries` | `dailyAllowanceConfigJson` | |
| `allowedAppPresets` | existing allowed-presets store | |
| `blockPresets` | **VERIFY; create a dedicated store if absent** (prior audit: Kotlin was writing them into the allowed bucket) | |
| `recurringBlockSchedules` | `recurring_block_schedules` prefs key | Adapter maps `days`↔Kotlin internal, `startMin`↔`startMinute` |
| `greyoutSchedule` | `GreyoutRepository` key `greyout_schedule` | Section 2.6 rule |
| `userProfile` | `user_profile` JSON string | Replace TS-known fields (absent ⇒ cleared). Preserve Kotlin-only fields (`weekUsageReportStartDay`) |
| `launcherTheme`, `focusToolPackages`, `launcherHiddenPackages`, `launcherDockPackages`, `launcherClockStyle`, `launcherBlockUninstall`, `launcherLockDuringStandalone` | `SettingsRepository` keys `launcher_theme`, `focus_tool_packages`, `launcher_hidden_packages`, `launcher_dock_packages`, `launcher_clock_style`, `launcher_block_uninstall`, launcher-lock key | Dock/clock were wrongly marked ignorable in v12 |
| `keepFocusActiveUntilTaskEnd`, `autoRescheduleEnabled`, `overlayQuotes`, `darkMode`, `protectionMode` | VERIFY for each; if Kotlin has no store, accept and ignore | |

**ACCEPT AND IGNORE (never persisted):** the 21 live keys; `onboardingComplete`; `privacyAccepted`; `launcherWallpaperUri`; `overlayWallpaper`; `defaultReminderOffsets` (dead in TS); `weekStartDay` (TS never changes it from 0, and importing it would force Sunday over Kotlin's own `userProfile.weekUsageReportStartDay`, default Monday); `launcherPinnedPackages` (declared, never synced); `beginnerMode`, `tipsCardDismissed`, `tipsCardFirstShownAt`, `lastShownStreakMilestone`, `pendingAchievementCelebration`, `pendingPresets`.

**Collections:** overwrite wholesale in both modes. The Merge/Replace switch governs **tasks only**.

**Invalid setting value** (wrong JSON type, out of range): ignore that key, add a warning, continue.

**Kotlin-only preferences** (`morningDigestEnabled`, `achievementNotificationsEnabled`, `patternInsightNotificationsEnabled`, `rescheduleNotificationsEnabled`, `blockSuggestionEnabled`, `weekAheadEnabled`, `temptationSpike*`, `bedTime`, `productiveWindowNudgeEnabled`, `taskRemindersEnabled`, `autoFocusEnabled`) have no TS wire name. `[KT]` today they are written into the exported file (`BackupManager` does not strip them) **and** applied on import. **PD-11** decides their fate; until the owner confirms, the default is: not exported, and **ignored** on import (including from older Kotlin-made files).

---

## 4. Wire parsing, validation and limits

### 4.1 File-level failures (reject whole file, zero mutation)

Not JSON; invalid or malformed UTF-8; top level not an object; `kind` mismatch; `settings` not an object; `tasks` not an array; `version` present and not 1; **duplicate object key at any depth**; any limit below exceeded; **duplicate task `id` within `tasks`** (the TS exporter cannot produce this, so it signals tampering or a bad merge; rejecting is non-lossy).

### 4.2 Parser requirements (implementation choice is the agent's)

- Stream or tokenize; never build an unbounded tree before checking limits.
- Strict UTF-8 (`CharsetDecoder` with REPORT). Strip a leading BOM. Reject lone surrogate escapes.
- Detect duplicate keys per object. `org.json` is last-wins; kotlinx `parseToJsonElement` is believed last-wins `[KNOWLEDGE]`. Do not use either alone for this check. A streaming reader with per-object key tracking meets the requirement.
- Count bytes while reading from the `InputStream`; do **not** trust `OpenableColumns.SIZE`.

### 4.3 Limits (constants, defaults)

`MAX_FILE_BYTES` 8 MiB · `MAX_DEPTH` 12 · `MAX_TASKS` 20,000 · `MAX_STRING` 20,000 chars (title 1,000; tag 100; package name 255; id 128) · `MAX_PACKAGES_PER_LIST` 5,000 · `MAX_BLOCKED_WORDS` 5,000 (each ≤ 200) · `MAX_ALLOWANCE_ENTRIES` 1,000 · `MAX_RECURRING` 500 · `MAX_GREYOUT_WINDOWS` 5,000 · `MAX_PRESETS` 500 · `MAX_TAGS_PER_TASK` 100 · `MAX_REMINDERS_PER_TASK` 100 · `MAX_JSON_NODES` 2,000,000.

### 4.4 Per-task validation (failure ⇒ that task is skipped and counted under `invalid` with a reason code; import continues)

- `id`: string, 1–128 chars, no control characters.
- `title`: string, non-empty after trim.
- `description`: string, null or absent ⇒ `""`.
- `startTime`, `endTime`, `createdAt`, `updatedAt`: parse as ISO-8601 with `Z` or offset; reject unparsable (TS silently passes `NaN`); require `endTime ≥ startTime`.
- `durationMinutes`: integral number in `[0, 100000]`.
- `status` ∈ 5 values; `priority` ∈ 4 values; unknown ⇒ invalid.
- `tags`: array of strings within limits.
- `reminders`: array; elements that fail to decode are dropped with a warning (not the task).
- `color`: string ≤ 32; missing ⇒ `#6366f1`.
- `focusMode`: boolean, missing ⇒ false.
- `focusAllowedPackages`: absent/null ⇒ **null**; array ⇒ list (keep `[]` distinct from null).

**Canonical timestamps:** `[KT]` Kotlin writes `Instant.toString()` (no `.000` when millis are zero, variable fraction digits) while TS and the migrated DB use `toISOString()` (always `.sssZ`). The Kotlin DAO compares ISO strings lexicographically, so mixed formats mis-order equal-second values. **Normalize all four timestamps at import to `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'` (UTC), and introduce one shared formatter used by all Kotlin task writes** (TaskViewModel currently uses `Instant.now().toString()`).

**Package names:** syntax check `^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$`; an invalid entry is dropped from its list with a warning, not the whole file.

**Schedules:** `startHour` 0–23, `startMin` 0–59, `days` elements integers 1–7 (distinct), `packages` non-empty for an enabled schedule. A schedule or window failing this is dropped with a warning.

---

## 5. Import semantics

`[KT]` facts used below: `TaskDao.insertTask` is `OnConflictStrategy.IGNORE`; `TaskDao` has `DELETE FROM tasks` and `DELETE FROM tasks WHERE id != :preserved`; the only Room foreign key in the schema is `finding_acknowledgements → findings` (CASCADE). **No entity references `tasks` by foreign key**, so deleting task rows cannot cascade into history tables. Re-check this if a migration adds one.

### 5.1 Preview (read-only, in memory)

Produces an `ImportPreview` from the validated backup and the *current* local task IDs:
`tasksInFile`, `newTasks`, `skippedIdentical`, `skippedDivergent` (with up to 20 titles), `invalid` (by reason code), `pastScheduledToSkipped`, `settingsSectionsPresent`, `externalResourcesIgnored`, `weakeningDetected` (PD-8), `warnings`.
**Identical** = equal on `title, description, startTime, endTime, durationMinutes, status, priority, tags, color, focusMode, focusAllowedPackages` after timestamp normalization; ignore `createdAt`, `updatedAt`, `reminders`. Compare *before* the past-scheduled downgrade. Preview counts are advisory; the plan (5.2) is recomputed at confirm time and is authoritative.

### 5.2 Plan generation (pure function, unit-testable without Android)

Inputs: validated backup, `localTaskIds`, `activeSession: Boolean`, `mode`, `planNowMillis`. Output: `RestorePlan` (this is exactly what the journal stores).

For every valid backup task whose `id` is **not** in the retained local set:

| Backup status | Rule |
|---|---|
| `scheduled` or `active` (PD-3) | `endTime > planNow` ⇒ store `scheduled`. Otherwise store `skipped` with `updatedAt = planNow` (canonical format) |
| `completed`, `skipped`, `overdue` | store verbatim |

### 5.3 Merge (default)

- Retained local set = all local task IDs. Tasks with those IDs are skipped (PD-1), counted identical vs divergent.
- Inserts happen in **one Room transaction**, `IGNORE` conflict. Settings are applied per Section 3.1. Nothing else is deleted.
- Re-importing the same file repeatedly is a no-op for tasks (all identical) and re-applies the same settings.

### 5.4 Replace tasks

- Label it "Replace tasks" in the UI. It never means "replace everything".
- **Precondition:** no active focus session (runtime flag **or** active row in `focus_sessions`). If violated: refuse, zero mutation, explain.
- **Deleted:** every row in `tasks` (`DELETE FROM tasks`) followed by inserts, in **one transaction**. The journal records the pre-delete ID set.
- **Remains untouched:** analytics/history tables, focus session history rows (their `task_id` may dangle; analytics readers must tolerate a missing task), PIN, device-local settings, enforcement state.
- Derived state for deleted/new tasks is fixed by the reconcile phase (Section 8), not by per-task calls.
- Settings are applied identically to Merge.

### 5.5 Result object

`RestoreResult { tasksInserted, skippedIdentical, skippedDivergent, invalidByReason, downgradedToSkipped, settingsApplied[], settingsIgnoredCount, externalResourcesIgnored, warningCodes[], alarmsArmed, alarmsCancelled, capabilityWarnings[] }`. Counts and key names only; never task titles or backup content in persisted/logged results.

---

## 6. Restore engine (gate → journal → idempotent phases)

Room and SharedPreferences cannot commit atomically together. The design therefore uses an **absolute, idempotent plan** replayed from a durable journal. Every step sets state to a fixed value, so repeating it is harmless.

### 6.1 `RestoreGate`

Process-wide singleton created first in `AppModule`. States: `OPEN`, `RECOVERING`, `RESTORING`.

- `suspend fun <T> write(owner: String, block: suspend () -> T): T` — normal writers. Suspends while the state is not `OPEN`; counts in-flight writers.
- `suspend fun tryBeginRestore(): Boolean` — waits for in-flight writers to drain, then `OPEN → RESTORING` atomically; returns false if not `OPEN`.
- `fun reopen()` — only the coordinator or recovery calls this.
- **Must pass through `write()`:** every `TaskRepository` mutator; `TaskViewModel` actions; `NotificationActionReceiver` complete/extend/skip; every setter for a key in the Section 3.1 APPLY list plus the greyout, recurring and VPN-package setters; the overdue sweep; the reconciler.
- Receivers use `goAsync()` and `withTimeoutOrNull(8_000) { gate.write { … } }`. On timeout the action is dropped and logged (its notification stays visible). The boot/permission/time receivers do **not** do the work inline: they enqueue unique WorkManager work (`reconcile`) which waits on the gate with a long timeout (`BackgroundFetchWorker` shows WorkManager is already a dependency).
- Restore itself bypasses `write()` by calling internal appliers (6.3), never the public setters.

### 6.2 `RestoreJournal`

One file: `<noBackupFilesDir>/restore/journal.json`, written with `AtomicFile` (write, fsync, rename). Never in a backed-up directory. Contents:

```
{ journalVersion: 1, sessionId, mode, planNowMillis, phase, attempts,
  deleteAllExisting: bool, tasksToInsert: [normalized Task…],
  settingsPlan: { key → normalized value }, warningCodes: […], counts: {…} }
```

Phases, in order: `PLANNED → TASKS_APPLIED → SETTINGS_APPLIED → RECONCILED`. The phase is advanced (journal rewritten atomically) **after** the corresponding step commits. The journal holds backup content at rest only for the duration of a restore; it is deleted on completion and by startup cleanup of any journal in a terminal phase.

### 6.3 Phase actions (all idempotent)

1. **TASKS** — one Room transaction. Replace: `DELETE FROM tasks` + inserts. Merge: `INSERT OR IGNORE`. Replaying a Merge after partial success still ends in the same state (rows inserted earlier are ignored and are identical to the plan).
2. **SETTINGS** — group the plan by SharedPreferences file; for each file write all keys in **one** `editor.commit()` and check the boolean result (retry 3×, else fail the attempt). `apply()` is forbidden here: it returns before disk write, so a crash could lose data the journal already marked done. Writes go through a `SettingsApplier`, not the public setters.
3. **RECONCILING** — run the side-effect syncs the public setters normally trigger (regenerate derived greyout windows per 2.6; notify enforcement services to re-read prefs; any VPN/native sync). The agent must list each public setter's side effects in the PR and show which `syncFromStore()` call replaces it. Then run the full reconcile (Section 8) with the **real** current time.
4. Delete the journal, `reopen()` the gate, persist a small `last_restore_result` for one-time display.

### 6.4 Admission (`RestoreCoordinator.begin`)

1. `gate.tryBeginRestore()`; false ⇒ `Busy` (UI: "A restore is already running").
2. Replace ⇒ active-session check (5.4). Violation ⇒ `Refused`, gate reopened, no mutation.
3. PD-8 PIN confirmation if required.
4. Build the plan (5.2); write the journal (`PLANNED`). If the journal write fails ⇒ abort with no mutation.
5. Run phases in an **application-scoped** coroutine on `Dispatchers.IO` (not a ViewModel/Activity scope, so backgrounding or rotation cannot cancel it).
6. A second import (in-app or external) while not `OPEN` is rejected with `Busy`; its URI is discarded.

### 6.5 Startup recovery

- `Application.onCreate` runs on the main thread: it only checks `File(journalPath).exists()` and sets the gate to `RECOVERING` or `OPEN`. **No parsing, no Room, no prefs work there.**
- If `RECOVERING`, launch `RestoreRecovery.run()` on an application-scope IO coroutine. A process started by a receiver (no Activity) takes the same path.
- `run()` reads the journal and executes the remaining phases from `phase`. On success: delete journal, `reopen()`, set the "completed after interruption" result.
- On a phase failure: increment `attempts` in the journal. After 3 attempts, `reopen()` the gate anyway and surface a blocking-but-dismissable "Restore could not be completed" screen with **Retry** and **Discard** (Discard deletes the journal, runs reconcile, leaves data as is). State at that point: tasks are all-old or all-new (single transaction); settings may be partially applied, which is shown honestly.
- Corrupt/unreadable journal or unknown `journalVersion`: delete it, `reopen()`, run reconcile, show "A previous restore may have been partial".
- Normal writers started during `RECOVERING` simply suspend on the gate (UI) or are enqueued/dropped per 6.1 (receivers).

### 6.6 Crash-point table (this is the test spec)

| Crash point | State on restart | Expected outcome |
|---|---|---|
| Before journal exists | no journal | Normal startup, nothing changed |
| After `PLANNED`, before tasks | journal `PLANNED` | Recovery replays all phases |
| Mid tasks transaction | journal `PLANNED`, SQLite rolled back | Replay tasks |
| After tasks commit, before phase write | journal `PLANNED` | Replay tasks (no-op or same result), advance |
| `TASKS_APPLIED`, some prefs files committed | journal `TASKS_APPLIED` | Replay all settings (absolute sets) |
| `SETTINGS_APPLIED`, before/inside reconcile | journal `SETTINGS_APPLIED` | Re-run reconcile |
| `RECONCILED`, before journal delete | journal `RECONCILED` | Delete journal only |
| Torn journal rewrite | old or new version (rename is atomic) | Either is valid; replay |
| Second import during any restore state | any | `Busy`, no state change |
| Writer starts during `RECOVERING` | any | Suspended/dropped, no interleaving |

---

## 7. External `.focusflow` files (open from Files / Drive / etc.)

### 7.1 Manifest (`MainActivity`, which is `singleTop` `[KT]`)

- Remove `BROWSABLE` from both `content` filters (content URIs do not come from browsers).
- Keep `application/octet-stream` and the `*/*` fallback per PD-7. **Do not add a `file` scheme filter.**
- Keep the `focusflow` scheme filter only if the product uses it, but enforce the route whitelist in 7.2.

### 7.2 Routing fix (P0, independent of the rest)

`[KT]` `routeFromIntent` calls `Routes.fromPath(intent.data.path)`. For a `content://` URI that path resolves to NOT_FOUND today, and a hostile `focusflow://import_confirm` link resolves to the internal import route.
`Routes.fromPath` must use an explicit **whitelist of deep-linkable routes**. `IMPORT_CONFIRM` and any route that depends on in-memory state are **never** reachable from an external intent. The confirm screen is reachable only from `IncomingBackupDispatcher` state.

### 7.3 Dispatch (cold and warm)

```
onCreate(savedInstanceState): if (savedInstanceState == null) handleIntent(intent)
onNewIntent(intent):           setIntent(intent); handleIntent(intent)

handleIntent(i):
  if i.action == ACTION_VIEW && i.data?.scheme == "content" -> dispatcher.onContentUri(i.data)
  elif i.action == ACTION_VIEW && i.data?.scheme == "file"  -> show "Unsupported file location. Use Import in Settings."
  else -> existing whitelisted deep-link routing
```

The `savedInstanceState == null` guard is what prevents re-handling after rotation or process-death recreation. There is **no** global handled-URI set (the TS set was never cleared, so re-opening a cancelled file was ignored). A second delivery of the **same** URI while a read is already in flight is ignored.

### 7.4 `IncomingBackupDispatcher` (application scope)

State: `Idle | Reading | Ready(PendingImport) | Failed(reason) | Busy`.

- `onContentUri(uri)`: if the gate is not `OPEN` ⇒ `Busy` and discard. Otherwise cancel any in-flight read (**latest file wins**), then read **eagerly** on IO (the URI grant is tied to the receiving task, so never defer or persist it):
  `SecurityException ⇒ PermissionDenied`, `FileNotFoundException ⇒ NotFound`, `IOException ⇒ ReadFailed`, null stream ⇒ `ProviderNull`.
- Stream with a hard byte counter (4.3). Parse and validate (Section 4). Build the preview (5.1). Emit `Ready(PendingImport)`.
- `PendingImport` holds the **validated normalized model** plus a display label (display name from `OpenableColumns.DISPLAY_NAME`, truncated to 80 chars, display only). **The URI is not retained.** Nothing is written to disk.
- **Navigation:** `Ready` navigates to the confirm screen only when the app is ready: DB open, onboarding complete, privacy accepted, PIN gate (if any) unlocked. Otherwise stay `Ready` and show a "Backup ready to import" entry once ready (PD-9). Preview content is sensitive and must not render behind a locked PIN gate.
- **Cancel:** `Idle`, no side effects. Re-opening the same file afterwards works.
- **Replacement while on the confirm screen:** re-render with the new preview and one line "Replaced by the newly opened file".
- **Process death:** `PendingImport` is lost by design (no mutation had occurred); the user re-opens the file. No cleanup needed because nothing was persisted.
- The in-app picker (SAF `OpenDocument`) calls the same `onContentUri`.
- **Logging:** never log the URI, file name, or any content. Log reason code, byte count, counts.

---

## 8. Scheduler and derived Android state

**Principle:** AlarmManager registrations, PendingIntents, posted notifications and channels are *derived* from `(DB, clock, capabilities)`. They are never exported, imported or trusted. A reconciler recreates them.
`PendingIntent` existence (a `FLAG_NO_CREATE` lookup returning non-null) is **not** proof that an AlarmManager registration exists. A token can outlive a fired or cancelled alarm. Reconcile therefore always (re)schedules every desired alarm; scheduling is idempotent.

### 8.1 Task-end alarms (per task)

- **Identity.** `Intent(ctx, TaskEndAlarmReceiver)` with action `com.tbtechs.focusflow.TASK_END_ALARM`, `data = Uri.parse("focusflow-internal://task-end/" + Uri.encode(taskId))`, extras `taskId`, `triggerAtMs`. Request code 0; flags `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`. The data URI makes `Intent.filterEquals` unique per task (no request-code hash collisions). Setting an alarm with an equal PendingIntent replaces the earlier one `[KNOWLEDGE]`.
- **Ladder (PD-2).** On API 31+ check `canScheduleExactAlarms()`; try `setAlarmClock`, then `setExactAndAllowWhileIdle`, then `setAndAllowWhileIdle`, catching `SecurityException` at each tier. Verify the permission matrix against current docs `[KNOWLEDGE]`.
- **Past trigger ⇒ no-op.** `[KT]` `AlarmRepository.scheduleAlarm` posts the alarm **immediately** when the trigger time has passed. That path must be removed: a restore or reconcile must never fire a stale "time's up".
- **Registry.** `focusflow_alarm_state` prefs file (not the enforcement prefs file), key `alarm_registry` (set of task IDs). Always `commit()`. **Add before scheduling; remove after cancelling.** The registry is therefore a *superset* of possibly-registered tasks, which is the safe direction after a crash.
- **Horizon.** Arm at most the earliest 100 future task-end alarms (AlarmManager caps an app at about 500 alarms `[KNOWLEDGE]`). The receiver triggers a reconcile after firing, extending the window.
- **Fire-time validation** (receiver, `goAsync()`, 8 s budget, Room read): task exists **and** status ∈ {`scheduled`, `active`} **and** `now ≥ endMs − 5000`. Failure ⇒ no alarm (this suppresses stale alarms from edits and legacy PendingIntents). Read timeout or exception ⇒ **fail open** (post the alarm, mark `unvalidated` in diagnostics): a spurious alarm is better than a missed one.
- **Dedupe ledger.** Prefs `alarm_posted` map `taskId → endMs`, pruned after 48 h. Skip if the same `(taskId, endMs)` was already posted.
- **Existing call sites** (`TaskViewModel` schedule/cancel on create, edit, extend, complete, skip, delete) stay, but all route through the registry-aware `AlarmRepository`.

### 8.2 Reminder notifications (pre / mid / almost)

They are derived from task start/end only (Section 2.7), **not** from `Task.reminders`. `[KT]` there is no scheduler implementation, so this is new work required by the "reminders restored correctly" objective.

- `ReminderPlanner` (pure): tasks + `now` ⇒ ordered `Slot(id, taskId, kind, triggerMs, text)`. **Reuse** the slot generation already in `NotificationRepository`, which matches the TS rules. Parity cap: 450 slots.
- `ReminderChainScheduler`: arms **one** AlarmManager alarm (fixed identity `focusflow-internal://reminder-chain`) for the earliest slot with `triggerMs > now`. This avoids the per-app alarm cap and removes any orphan problem (a single PendingIntent identity).
- `ReminderReceiver`: on fire, recompute the plan from the DB with the current time, post every slot with `triggerMs ≤ now + 1000` that is not in the ledger, record them, re-arm the chain.
- Notification identity `(tag = slotId, id = 1)`. Cancel by the same pair.
- Ledger: prefs map `slotId → triggerMs`, pruned after 48 h. Slot IDs derive from task IDs, which are never remapped, so they are stable across restore.
- Gated by the device-local, Kotlin-only `taskRemindersEnabled` preference (not on the wire).
- If the owner defers reminder notifications, restore still calls `reconcile()`, which then skips this part.

### 8.3 `reconcile(reason)`

```
serialize with a Mutex; wait for gate OPEN (or run inside RECONCILING)
now = clock.now()
sweepOverdue(now)                                   // 8.5, persists 'overdue'
desired = tasks with status in {scheduled, active} and end > now, earliest 100
registry = load()
for id in registry − desired: cancelTaskEnd(id)
registry.add(desired); commit
for t in desired: scheduleTaskEnd(t)               // idempotent replace
registry.set(desired); commit
ReminderChainScheduler.rearm(ReminderPlanner.plan(now))
cancel posted reminder notifications whose task no longer exists or is resolved
record capability snapshot (9.2)
```

### 8.4 Triggers

| Trigger | Mechanism |
|---|---|
| App start / `Activity.onStart` | `reconcile("start")` (cheap) |
| Device boot / unlock / app update | **Extend the existing `BootReceiver`** `[KT]` (already `directBootAware`, already filters `BOOT_COMPLETED`, `QUICKBOOT_POWERON`, `USER_UNLOCKED`, `MY_PACKAGE_REPLACED`, but does not re-arm task alarms today). It must enqueue unique WorkManager `reconcile` work and must **not** touch Room or credential-encrypted prefs until the user is unlocked (`UserManager.isUserUnlocked`) |
| Exact-alarm permission change (`AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`) | same (new manifest receiver; none exists today `[KT]`) |
| `ACTION_TIME_CHANGED` | same (task times are absolute instants, so **time-zone changes need no work**) |
| Notification permission regained | `reconcile` on next `onResume` |
| Task create/edit/delete/complete/skip/extend | existing call sites ⇒ reconcile for that task |
| Restore | RECONCILING phase |
| After a task-end alarm fires | `reconcile("fired")` extends the horizon |

### 8.5 Overdue sweep (parity requirement)

TS persists `overdue` for unfinished tasks whose end has passed, at app load. Run the same sweep **before** reconcile so overdue tasks are never alarmed. `[KT]` I did not find a writer of `"overdue"` in the data or ViewModel layer; run `grep -rn '"overdue"' java/` and implement the sweep if it is absent. Note the intentional difference: **import** stores past `scheduled` rows as `skipped` (closed history), while the **sweep** marks locally unresolved tasks `overdue`.

### 8.6 Restore interplay

The scheduler never runs mid-restore (the gate blocks it). The RECONCILING phase uses the real current time, not `planNowMillis`. Alarms for replaced/deleted tasks are removed by the registry diff, not by per-task calls.

---

## 9. Alarm presentation (the reported "notification first, UI only after tap" symptom)

### 9.1 Source-proven runtime path `[KT]`

`AlarmManager` → `TaskEndAlarmReceiver.onReceive` (synchronous, no `goAsync`) → static `ForegroundTaskService.postTaskEndAlarmNotification` → ensure channel `task_alarm` (`IMPORTANCE_HIGH`) → one **global** full-screen `PendingIntent` (request code 7) → post to the **fixed** notification ID 9101 (`PRIORITY_MAX`, `CATEGORY_ALARM`, ongoing, auto-cancel, `setFullScreenIntent(pi, true)`) → then a "belt-and-braces" `app.startActivity(activityIntent)` inside `try { } catch (_: Exception) {}`.

Manifest facts `[KT]`: declares `SYSTEM_ALERT_WINDOW`, `SCHEDULE_EXACT_ALARM` (not `USE_EXACT_ALARM`), `USE_FULL_SCREEN_INTENT`, `POST_NOTIFICATIONS`. `TaskAlarmActivity` is `singleInstance`, `noHistory`, `showWhenLocked`, `turnScreenOn`, `excludeFromRecents`, `taskAffinity=""`. `TaskEndAlarmReceiver` is non-exported with no filter.

What does **not** exist: any `canUseFullScreenIntent()` / `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` use, any diagnostics, any per-task identity. Both the notification and the activity are global, so two simultaneous task ends overwrite each other. `TaskAlarmActivity.onNewIntent` updates `taskId` but the visible text was built once in `onCreate`, so a second alarm can show task A's name while the buttons act on task B.

### 9.2 Failure points, evidence class, and the discriminating test

| Stage | What can fail | Class | How to tell |
|---|---|---|---|
| Trigger | Tier downgrade ⇒ late alarm | source + `[DEVICE]` | `dumpsys alarm` shows type and time. The user says the notification *does* appear on time, so the trigger works |
| Receiver | Process cold-start; no `goAsync` | source | log timestamps (9.5) |
| Notification | `POST_NOTIFICATIONS` denied | `[DEVICE]` | not the symptom (the notification shows) |
| **Channel** | Channel config is **sticky after first creation** and there are **two creation sites** with different attributes (`"Task End Alarm"` vs `"Task Alarm"`; the second also sets vibration, lights, `setBypassDnd(true)`). Whichever ran first on that install wins. The user can also override importance/pop-up per channel | source-proven (two defs) + `[DEVICE]` | `adb shell dumpsys notification --noredact` channel block |
| **FSI capability** | On API 34+ `USE_FULL_SCREEN_INTENT` can be off. `[DOC]` it is granted by default at install, but Play revokes it for apps whose core function is not calling/alarm, and the user can revoke it. No code checks it | Android-platform-constrained | `NotificationManager.canUseFullScreenIntent()`; `adb shell cmd appops get com.tbtechs.focusflow USE_FULL_SCREEN_INTENT` `[KNOWLEDGE]` op name |
| **FSI ≠ guaranteed launch** | `[KNOWLEDGE]` the platform documents that while the user is actively using the device the system may show a heads-up instead of launching the full-screen activity. A heads-up that needs a tap is then *expected platform behavior* | Android-platform-constrained | Repeat the test with screen **off**, **locked**, and **unlocked and in active use** |
| Direct `startActivity` fallback | `[DOC]` background activity launches are blocked unless an exemption applies (for example `SYSTEM_ALERT_WINDOW` granted, a visible window, or a system-sent PendingIntent such as a notification tap). A blocked launch **does not throw**, and the code swallows exceptions anyway | Android-platform-constrained | `adb logcat | grep -i "background activity launch"`; `Settings.canDrawOverlays()` |
| PendingIntent | Global request code 7 with `UPDATE_CURRENT`; on target SDK 35+ `[DOC]` PendingIntent creators must opt in to background-activity-start privileges explicitly | source + `[DEVICE]` | check `targetSdk` (Gradle files are **not** in the archive) |
| Activity create / resume | `onCreate` extras; no logging of resume | source | add 9.5 timestamps |

**Why "after tapping" is plausible without any Kotlin bug:** a tap is a user-initiated, system-sent PendingIntent, which is always permitted to start an activity. The same code path with FSI downgraded or suppressed would present exactly this symptom.

**Comparison with the TS hybrid:** the TS archive contains **no native source**, so TS runtime behavior is **not provable** from it. The Kotlin alarm classes look like the relocated native code, which suggests parity of logic. If the TS build fronted correctly on the *same device and package*, permission/channel state carried over, so a regression points at code, manifest or `targetSdk` differences, not at permission state. If the builds were installed fresh or under different application IDs, state differs and the checklist below applies. **Do not assume either.**

### 9.3 Required changes

1. **Capability probe** `AlarmCapabilities` (snapshot recorded at schedule time and at fire time): `postNotificationsGranted`; channel importance and `isBlocked`; `canUseFullScreenIntent()` (API 34+); `Settings.canDrawOverlays()`; `canScheduleExactAlarms()` (API 31+); battery-optimization exemption; `targetSdk`; device interactive/keyguard state at fire time.
2. **Settings affordances:** a diagnostics row showing the snapshot; when FSI is off on API 34+, a one-time prompt (PD-10) that opens `Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` with the package URI; a path to the overlay and exact-alarm settings screens.
3. **Per-task identity.** Activity PendingIntent: `data = focusflow-internal://task-alarm/<encodedTaskId>`, request code 0, `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`. Notification `(tag = "task-end:" + taskId, id = 9101)`.
4. **Target SDK 35+:** create the activity PendingIntent with `ActivityOptions.makeBasic().setPendingIntentCreatorBackgroundActivityStartMode(MODE_BACKGROUND_ACTIVITY_START_ALLOWED)` (verify the exact API against current docs `[DOC]`/`[DEVICE]`).
5. **Keep the direct `startActivity` fallback only when `canDrawOverlays()` is true;** never rely on it. Log the result instead of swallowing exceptions.
6. **Never make the activity the only path.** The notification's Done / +15 / +30 / Skip actions must keep working without it. When the device is in active use, the heads-up notification is the primary surface; do not claim more.
7. **`TaskAlarmActivity`:** maintain a queue ordered by `endTime`; rebind **all** UI (title and actions) on every `onNewIntent`; actions operate on the displayed task ID only; dismissing one alarm cancels only its own notification tag.
8. **One channel definition.** Create `NotificationChannels.ensureTaskAlarmChannel()` and delete the duplicate. Keep ID `task_alarm`. **Never delete or mutate an existing channel**: it would discard user customization carried over from the TS build. Compare the two existing definitions before choosing the attribute set; the second one (sound, vibration, lights, bypass-DND, public visibility) is the more complete.

### 9.4 Product-owner constraint

Do **not** promise "the alarm always comes to the front". The achievable guarantee is: a high-priority alarm notification is posted on time; the full-screen activity launches when the platform and permissions allow; otherwise the heads-up and its actions are fully functional, and the diagnostics screen says why.

### 9.5 Diagnostics (no PII)

Ring buffer of the last 20 fires in the alarm-state prefs: `scheduledAt`, `firedAt` (lateness), `validated|unvalidated`, notification post result, `activityOnCreateAt`, `activityOnResumeAt`, capability snapshot. A fire with `onResume` present **before** any tap proves fronting worked; absent proves it did not. Never log task titles.

### 9.6 Device test matrix (the symptom cannot be closed without it)

Run each row on the *same* device with both builds where possible: (screen **on, in use**) × (screen on, idle) × (screen off) × (locked) × FSI {granted, revoked} × overlay {granted, denied} × exact alarm {granted, denied} × notification permission {granted, denied} × channel importance {HIGH, lowered by user}. Record the 9.5 buffer plus `dumpsys notification`, `dumpsys alarm | grep com.tbtechs.focusflow`, and the logcat "background activity launch" lines. Also test two task ends within 5 seconds of each other.

---

## 10. Export

- Write the exact V1 envelope (Section 2.1) with `kind:"FocusFlowBackupV1"`, `version:1`, `exportedAt` (UTC, `.sssZ`), `exportedAtHuman` (locale text, informational), `appVersion` (`BuildConfig.VERSION_NAME`), `platform:{os:"android"}`, `settings`, `tasks`, `presetSections` (informational inventory; keep for parity), `summary` (counts).
- **Tasks:** all rows, all five statuses, TS field names; `focusAllowedPackages` omitted or `null` ⇒ null (Section 2.3). Timestamps in canonical `.sssZ`.
- **Settings:** emit APPLY keys under their **TS wire names** (Section 3.1). Never emit the 21 live keys. Emit `focusMirrorVpnEnabled`. Emit `recurringBlockSchedules` with TS field names and day numbering, and `greyoutSchedule` = user windows + derived windows (2.6). Emit `weekStartDay` derived from `userProfile.weekUsageReportStartDay` (sun=0 … sat=6; default Monday ⇒ 1) so a TS import preserves the week start. Emit `defaultReminderOffsets: [-10,-5,0]` (dead in TS but present in its schema).
- **Never emit** `launcherWallpaperUri`, `overlayWallpaper`, `onboardingComplete`, `privacyAccepted`, or Kotlin-only keys. **Omit** the keys rather than writing empty values: TS merges by spread, so an omitted key preserves the TS user's local value.
- File: UTF-8, no BOM, 2-space indentation, MIME `application/octet-stream` via SAF `CreateDocument`, name `focusflow-YYYY-MM-DDTHH-mm-ss.focusflow`.
- **Round-trip definition:** `export → import(replace) → export` is equal after canonicalizing JSON (ignore `exportedAt`, `exportedAtHuman`, key order, `presetSections` order, derived `summary`).

---

## 11. Security and robustness

- Treat the file as hostile: all limits in 4.3; strict UTF-8; duplicate-key rejection; no reflection-style polymorphic decoding; no HTML rendering of any field.
- **Never apply live-state keys** (Section 2.4). `[KT]` today `mergePortableBackup` reads `systemGuardEnabled`, `vpnBlockEnabled`, `alwaysOnEnforcementEnabled` and `pomodoroEnabled` from the file, so a crafted file can switch protections off. I did not verify whether the in-app import screen is PIN-gated. This is the highest-severity finding in this review.
- PD-8: PIN confirmation for protection-weakening imports.
- Logging: no backup content, no URIs, no display names, no task titles. Counts and reason codes only.
- Sensitive storage: the only at-rest copy is the journal (6.2): `noBackupFilesDir`, deleted on completion, startup-cleaned in terminal phases. `PendingImport` is memory only.
- Size/time: all parsing and plan generation on `Dispatchers.IO`, cancellable; peak memory bounded by limits.
- External-resource and package references are data, never fetched or launched during import.

---

## 12. Tests

All pure logic (parser, validator, plan generation, slot planning, ladder selection) must run as plain JVM tests with an injected clock. Journal and phase tests use a fake file system and fake store.

### 12.1 Compatibility
1. TS V1 export fixtures (current exporter; **old 1.0.6-style export carrying the live toggles**) import into Kotlin; live keys are ignored; assert **no** local toggle changed.
2. Kotlin export parses with the TS rules in 2.1 (envelope fields, nested field names, day numbering 1–7, `weekStartDay`, no live keys, no wallpaper keys).
3. `export → import(replace) → export` canonical equality.
4. Unknown fields at every level ignored; unknown `kind` / `version` ≠ 1 ⇒ zero mutation (assert Room and every prefs file unchanged).

### 12.2 Data
5. Duplicate task `id` inside the file ⇒ whole file rejected, zero mutation.
6. Same-ID identical collision ⇒ skipped, counted identical. Same-ID divergent ⇒ skipped, counted divergent, **import does not abort**, settings still apply.
7. `focusAllowedPackages`: absent, `[]`, `["a"]` each round-trip exactly (`null`/`[]`/list).
8. Status rules: past `scheduled` and past `active` ⇒ `skipped`; future ⇒ `scheduled`; `completed`/`skipped`/`overdue` verbatim; unknown status ⇒ task invalid, rest imported.
9. Timestamp normalization: `Z`, `+05:30`, no-millis, 6-digit fraction all normalize to `.sssZ`; unparsable ⇒ invalid; `end < start` ⇒ invalid.
10. Collections overwrite (not union): backup list shorter than local ⇒ local shrinks.
11. Schedules: weekday adapters for Sunday=1 and Saturday=7; overnight window preserved; derived greyout windows regenerated; backup windows with `scheduleId` discarded.
12. `userProfile`: TS fields replaced (absent ⇒ cleared), Kotlin-only `weekUsageReportStartDay` preserved; `weekStartDay` ignored.
13. External resources: wallpaper keys ignored and the local value kept; `onboardingComplete`/`privacyAccepted` never applied.
14. Package references: uninstalled packages kept; syntactically invalid entries dropped with a warning.
15. Limits: each limit in 4.3 at limit and limit+1; malformed UTF-8; lone surrogate; depth 13.
16. Replace: all task rows deleted and replaced in one transaction; history tables untouched; refused with an active session (runtime flag and DB row each tested separately).

### 12.3 Lifecycle (instrumented)
17. Cold `ACTION_VIEW` content URI; warm `onNewIntent`; rotation after cold open (no re-handling); same intent delivered twice; **two different files within 200 ms** (latest wins, first read cancelled); open → Cancel → reopen same file (works); provider throws `SecurityException` / `FileNotFoundException` / returns null; `file://` rejected; hostile `focusflow://import_confirm` does **not** reach the confirm screen; open before onboarding (staged, shown after).
18. Process death at every row of 6.6, using a fault-injecting store that throws or `Process.killProcess`es at each boundary; recovery completes before any writer runs; a second import during RESTORING and RECOVERING is `Busy`.
19. Corrupt journal; unknown `journalVersion`; three failed recovery attempts ⇒ gate reopens with the Retry/Discard screen.

### 12.4 Scheduler
20. Restore a future task ⇒ exactly one task-end alarm armed; restore an expired task ⇒ none, **and no immediate post**.
21. Delete/edit task ⇒ old alarm cancelled/replaced; registry is always a superset after a simulated crash between "add" and "schedule" and between "cancel" and "remove".
22. Reboot ⇒ reconcile re-arms (verify `BootReceiver` enqueues the work; direct-boot-aware receiver must not touch credential-encrypted storage before unlock).
23. Exact-alarm permission revoked then granted ⇒ ladder downgrades then upgrades after the permission-changed broadcast.
24. Notification permission states and FSI capability states reflected in the snapshot; alarm still armed when notifications are denied (PD-6).
25. Stale callback: alarm fires for a deleted, completed, or edited-later task ⇒ no alarm. DB read timeout ⇒ fail open. Duplicate fire for the same `(taskId, endMs)` ⇒ posted once.
26. Several simultaneous task ends ⇒ distinct notifications (distinct tags); the activity shows the right title for each after `onNewIntent`.
27. Reminder slots match Section 2.7 exactly for fixtures (start in 5 min, in 30 s, in the past, 20-minute task, 3-hour task); budget 450; the chain re-arms to the next slot after each fire; ledger prevents duplicates after restart.
28. Device matrix (9.6) executed and its results attached to the PR.

---

## 13. Work order (each milestone independently shippable)

| M | Scope | Why this order |
|---|---|---|
| **M0** | Security and routing hotfix: stop importing live-state keys; `Routes.fromPath` whitelist; remove `BROWSABLE` from content filters; `Instant`→canonical formatter | Closes the PIN-bypass and the hostile deep link now |
| **M1** | Wire model, streaming parser, validator, limits, `TsSettingsMapper` (used by import, export and the legacy-DB migration so there is one mapping) | Everything else depends on one correct schema |
| **M2** | Export (Section 10) + round-trip tests | Verifiable on its own |
| **M3** | Gate, journal, plan, phases, recovery, coordinator, `IncomingBackupDispatcher`, confirm screen, manifest and intent dispatch | The core restore feature |
| **M4** | `AlarmRepository` rewrite (identity, registry, ladder, no immediate-post), reconciler, triggers, overdue sweep | Required before restore can claim derived state is rebuilt |
| **M5** | Reminder chain scheduler | Delivers objective 7/8 for reminders |
| **M6** | Alarm presentation (Section 9) and diagnostics, then the device matrix | Closes the user-reported symptom |

**Pre-existing defects to fix while touching these files** `[KT]`: the legacy settings migration reads `appSettings` while the DB row key is `app_settings`, writes to the `FocusFlowPrefs` file while runtime reads `focusday_prefs`, maps only about eight fields, and permanently marks itself migrated even when the row was not found. Route it through `TsSettingsMapper` and re-run it only when it actually found and applied a row.

---

## Appendix A — Three-way truth (every material mismatch)

| Topic | Observed TS | Intended | Kotlin now | Decision | Test |
|---|---|---|---|---|---|
| Same-ID collision | skip, keep existing | "Existing task IDs are kept" (UI copy, changelog) | v12 proposed abort | existing wins, report | 6 |
| Live toggles in old files | spread-merged, overwrote local | never portable (exporter strips them) | stripped on export, **applied on import** | never apply | 1 |
| Duplicate IDs inside file | DB ignores, counted imported | bad file | n/a | reject file | 5 |
| Past scheduled task | stored `skipped` | closed history | not implemented | `skipped`, `updatedAt=planNow` | 8 |
| `active` imported | no writer found | unknown | n/a | treat as `scheduled` (PD-3) | 8 |
| `Task.reminders` | always `[]`, dead | derived from times | typed list | preserve opaquely | 7 |
| `defaultReminderOffsets` | never read | dead | absent | accept/ignore, export constant | 12 |
| `pendingPresets` / "temporary presets" | dead, stale comment | direct apply after confirm | absent | do not implement | — |
| Pending import | memory only, replaced by new file | same | planned on disk | memory only | 17 |
| `handledUris` | never cleared | not intended | n/a | no handled set | 17 |
| Wallpaper paths | exported and overwritten | local resource | n/a | ignore, keep local | 13 |
| `weekStartDay` | const 0, no UI | unknown | derived from profile | ignore on import; derive on export | 12 |
| Notification permission denied | skips *all* scheduling incl. native alarm | likely accidental | schedules alarm | schedule anyway (PD-6) | 24 |
| Greyout vs recurring | derived windows + user windows | recurring is authoritative | recurring written under `greyoutSchedule` with Kotlin names | Section 2.6 | 11 |
| `focusMirrorVpnEnabled` | backup ?? local | portable opt-in | `||` merge | imported value wins | 13 |
| Alarm identity | native, per task (not visible) | per task | global code 7 / id 9101 | per-task identity | 26 |
| Timestamp text | `toISOString` `.sssZ` | canonical | `Instant.toString()` | canonicalize | 9 |

## Appendix B — What the implementation agent must know (it has no TS source)

1. The exact V1 envelope and acceptance rules (2.1); nothing else is validated by TS.
2. Task fields, the five statuses, and the three-way `focusAllowedPackages` meaning (2.3).
3. TS restore uses `INSERT OR IGNORE`, skips existing IDs, downgrades past `scheduled` to `skipped`, and refuses Replace during a focus session (2.3).
4. The 21 live keys TS strips, and that older exports include them (2.2, 2.4).
5. Nested schemas, **day numbering 1=Sun…7=Sat**, `weekStartDay` is 0–6, wall-clock time fields (2.5).
6. `recurringBlockSchedules` is authoritative; `greyoutSchedule` is user windows plus derived copies (2.6).
7. Collections overwrite; Merge/Replace governs tasks only (2.4, 5).
8. Notification slot identifiers, rules and the 450 budget (2.7).
9. `Task.reminders` and `defaultReminderOffsets` are dead; `pendingPresets` is dead; `overdue` is persisted by a startup sweep (2.3, 2.8).
10. TS pending import is in memory and replaced by a new file; the TS `handledUris` bug must not be copied (2.8).
11. `onboardingComplete` and `privacyAccepted` are exported by TS but must never be applied.
12. TS native alarm code was **not** available, so TS alarm behavior is unproven.

## Appendix C — Not verified in this review (the agent must check before relying)

- `SettingsRepository` setter side effects; `VpnRepository` and `alwaysOnVpnPackages` storage; whether Kotlin has stores for `darkMode`, `protectionMode`, `keepFocusActiveUntilTaskEnd`, `autoRescheduleEnabled`, `overlayQuotes`.
- Where Kotlin derives greyout windows from recurring schedules at enforcement time.
- Whether a Kotlin overdue sweep exists (8.5).
- `targetSdk`, `minSdk`, Room `allowBackup`/data-extraction rules: Gradle and resource files are not in the archive.
- TS onboarding handling of external files, PIN-gating of TS import, and the TS `updateSettings` side effects beyond those cited.
- Android platform claims tagged `[KNOWLEDGE]` (alarm replacement semantics, 500-alarm cap, exact-alarm permission per tier, FSI heads-up-while-in-use, appop name, PendingIntent creator opt-in API name).
- Remaining attributes of the first `task_alarm` channel definition (only name, importance and description were compared).
