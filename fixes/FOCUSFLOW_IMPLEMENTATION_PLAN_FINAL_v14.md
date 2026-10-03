# FocusFlow — Backup / Import / Alarm Implementation Contract  
## Final Plan (v14)

**Purpose:** Self-contained specification for the coding agent.  
The coding agent does **not** have the TypeScript source. Every relevant TS fact is
written here explicitly. Read §TS before touching any wire-format code.

**Supersedes:** v13-FINAL and all earlier plans (v12, both v13 drafts). This is the
single implementation authority. Do not reconcile against any earlier document.  
**Where the two v13 drafts disagreed,** every ruling is recorded here with explicit
reasoning. Legacy TS behaviour is not automatically preserved — best practice and
product correctness take priority.

**Evidence tags:**
- `[TS]` — verified from TS source (`backupService.ts`, `types.ts`, `database.ts`,
  `notificationService.ts`, `import-confirm.tsx`, `_layout.tsx`, `AppContext.tsx`)
- `[KT]` — verified from Kotlin archive
- `[DOC]` — Android documentation
- `[KNOWLEDGE]` — platform knowledge; agent must confirm against current docs before
  relying on it
- `[DEVICE]` — requires a real device test; cannot be settled from source

---

## 0. Executive summary — what is broken and why it matters

```
KT-1  External ACTION_VIEW intent is declared but MainActivity never routes it
      to the import flow.

KT-2  BackupCoordinator.import() supplies a no-op scheduleTasks callback.
      Restored future tasks get no alarms and no reminder notifications.

KT-3  NotificationScheduler is an interface with no production implementation.
      NotificationRepository is never constructed in AppModule. Reminder
      notifications do not exist in the Kotlin app today.

KT-4  ForegroundTaskService uses one global PendingIntent request code
      (PI_TASK_ALARM = 7) and one global notification ID (9101) for all
      task-end alarms. Two simultaneous alarms corrupt each other's data.

KT-5  AlarmRepository.scheduleAlarm() fires an alarm immediately when the
      trigger time is in the past. This would fire stale alarms after restore.

KT-6  Legacy settings migration: queries key 'appSettings' (correct: 'app_settings'),
      writes to FocusFlowPrefs (correct: focusday_prefs), and permanently marks
      itself complete even when the row was not found — silently swallowing the TS
      user's settings on every device.

KT-7  BackupCoordinator routes imported settings through
      SettingsViewModel.updateSettings() (async field-by-field, triggers live
      enforcement). Restore must use a dedicated single-commit path.

KT-8  BackupCoordinator serialises recurringBlockSchedules under wire key
      'greyoutSchedule' with Kotlin-internal field names. This is both wrong on
      the wire and loses the TS user's actual recurringBlockSchedules data.

KT-9  mergePortableBackup() reads 'vpnBlockEnabled', 'systemGuardEnabled',
      'pomodoroEnabled', 'alwaysOnEnforcementEnabled' from imported JSON and
      applies them. An untrusted file can therefore silently disable protection.
      This is a security defect.

KT-10 Routes.fromPath() is not whitelisted. A hostile deep-link URI of the form
      focusflow://import_confirm routes directly to the confirmation screen.

KT-11 The manifest content-scheme intent filters include BROWSABLE, offering
      FocusFlow as a handler for arbitrary browser links that happen to deliver
      content URIs.

KT-12 Kotlin writes task timestamps with Instant.toString() (variable fraction
      digits). TS writes toISOString() (always .sssZ). The DAO compares ISO
      strings lexicographically, so mixed formats mis-order equal-second values.
```

---

## 1. Ruling table — every significant disagreement resolved

Where the two v13 drafts disagreed, the final ruling is here.
All rulings are based on product correctness, not on "TS did it this way."

| Topic | Ruling | Reason |
|---|---|---|
| Same-ID divergent collision | **Abort entire import before mutation. ID_CONFLICT error. User told which tasks conflict (up to 20 titles). Suggest Replace mode.** | A backup that partially restores while silently dropping some tasks is more dangerous than a refused import. "Import succeeded" while an important task wasn't imported is deceptive. The abort contract is more internally consistent: the user gets a clear diagnostic and a clear resolution path (Replace). This was the deliberate v12 decision; not reverted. |
| PendingImport durability | **Durable — persisted to `noBackupFilesDir/pending-import/` before the confirmation UI.** | The entire v12 architecture was built around process-death recovery. Making PendingImport memory-only violates that principle. A process killed between "user opened file" and "user confirmed" loses the pending import — the user must re-open the file, which is fine in isolation but is inconsistent with the rest of the design. Durable is the coherent choice. |
| `setAndAllowWhileIdle()` fallback for task-end alarms | **Removed for task-end alarms. DEFERRED_EXACT_UNAVAILABLE policy only.** | FocusFlow task-end semantics are about exact timing. An inexact alarm delivering at 10:17 for a 10:00 task is a different product guarantee than an exact one. Telling the user "your 10:00 task alarm was restored" when Android will deliver it "around that time" is misleading. Persist the task; mark DEFERRED; surface the capability prompt; retry when exact permission returns. The ladder is kept only for advisory reminder notifications (§8.4). |
| `greyoutSchedule` wire | **Split: import no-`scheduleId` windows separately; discard `scheduleId` windows; restore `recurringBlockSchedules` from its own wire key.** | Source-proven: user-created windows carry no `scheduleId`; derived copies carry one. These have different portability semantics and must not be conflated. |
| `launcherDockPackages` / `launcherClockStyle` | **Portable — apply.** | `LauncherActivity` reads `launcher_dock_packages` from SharedPrefs `[KT]`; SettingsRepository persists both keys `[KT]`. My earlier READ_IGNORE_LEGACY was wrong. |
| `Task.reminders[]` | **Opaque round-trip. No scheduler integration.** | Source-proven `[TS]`: `createTask` always initialises `reminders: []`. Notifications are derived solely from task `(id, startTime, endTime)`. Per-reminder registry machinery is complexity with no function in V1. |
| `weekStartDay` | **ACCEPT_AND_IGNORE.** | TS never writes this field from the UI (always 0 = Sunday) `[TS]`. Importing it would force Sunday on all users, overwriting their Kotlin setting. |
| `defaultReminderOffsets` | **ACCEPT_AND_IGNORE.** | Source-proven dead in TS notification service `[TS]`. No Kotlin consumer defined. Revisit in V2 when a consumer exists. |
| `overlayWallpaper` / `launcherWallpaperUri` | **UNRESOLVED — local value preserved; explicitly recorded in RestorePlan and RestoreResult; user notified via `externalResourcesUnresolved` count.** | Never mistake a device-local URI for portable resource content. The backup value references a file on another device; restoring a dangling URI would silently corrupt the setting. The correct policy is an explicit UNRESOLVED record, not silent ignore. The local value is kept (not CLEAR, because destroying the user's existing local wallpaper preference is also wrong). The difference from silent ignore: the user is told "1 resource could not be imported." |
| Task statuses | **Five: `scheduled` `active` `completed` `skipped` `overdue`.** | Source-proven from `types.ts` `[TS]`. |
| Timestamp format | **Normalize to `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'` at import AND at all Kotlin task writes.** | `Instant.toString()` (Kotlin) vs `toISOString()` (TS) produce different formats. DAO lexicographic comparison misorders equal-second values. Fix at the formatter level, not just at import time. |
| Immediate past-trigger alarm | **Never fire a stale alarm.** `scheduleAlarm()` must be a no-op when `triggerAtMs ≤ now`. | `[KT]` Current code fires immediately on past triggers. This would fire stale alarms after restoring old tasks. |
| Reminder notifications | **ReminderChainScheduler: one AlarmManager alarm for the entire reminder chain.** | Per-alarm approach risks hitting the per-app AlarmManager cap (~500), creates orphan identities, and is harder to reconcile after restore. Chain is architecturally superior. |
| `PD-8` PIN gate for weakening imports | **Implement.** | Correct security behaviour. An external file must not silently downgrade enforcement. |
| `BROWSABLE` in manifest | **Remove from both content filters.** | Content URIs do not come from browsers. |
| Routes whitelist | **Implement `Routes.fromPath` with an explicit external-linkable whitelist.** | `IMPORT_CONFIRM` and any in-memory-state-dependent route must never be reachable from an external intent. |

---

## TS — TypeScript source facts (coding agent has no TS source)

### TS.1 V1 envelope `[TS backupService.ts]`

```jsonc
{
  "kind": "FocusFlowBackupV1",           // exact string — reject if different
  "version": 1,                           // TS writes 1; if present and ≠ 1 → reject
  "exportedAt": "2026-06-15T14:30:00.000Z",   // toISOString() — always .sssZ
  "exportedAtHuman": "15/06/2026, 14:30:00",  // toLocaleString() — informational only
  "appVersion": "1.1.2",                 // optional string — diagnostic only
  "platform": { "os": "android" },       // informational; not used for behaviour
  "settings": { /* AppSettings portable subset */ },   // required object
  "tasks": [ /* Task[] */ ],             // required array
  "presetSections": [ /* inventory only; never applied as config */ ],
  "summary": {                            // informational; never authoritative
    "taskCount": 0,
    "blockedWordCount": 0,
    "greyoutWindowCount": 0,
    "dailyAllowanceCount": 0
  }
}
```

Kotlin acceptance rules: parse JSON; top-level is object; `kind === "FocusFlowBackupV1"`;
`settings` is object; `tasks` is array; `version` present and ≠ 1 → reject; missing
`version` → treat as 1. All other top-level fields are informational.

### TS.2 The 21 live-state keys TS strips on export `[TS backupService.ts]`

```
standaloneBlockPackages   standaloneBlockUntil      standaloneVpnPackages
autoCopiedAlwaysOnPackages alwaysOnEnforcementEnabled focusModeEnabled
pomodoroEnabled            notificationsEnabled      weeklyReportEnabled
launcherEnabled            aversionDimmerEnabled     aversionVibrateEnabled
aversionSoundEnabled       systemGuardEnabled        blockInstallActionsEnabled
blockYoutubeShortsEnabled  blockInstagramReelsEnabled vpnBlockEnabled
autoCopyToAlwaysOn         vpnSelfHealEnabled        pinProtectionEnabled
```

**Critical:** older V1 files (e.g. appVersion 1.0.6) include these keys because the
exporter did not strip them then. Kotlin must NEVER apply any of these 21 keys,
regardless of whether they appear in an imported file (KT-9 is a security defect).
An old-export fixture containing them is mandatory test data.

`focusMirrorVpnEnabled` is re-added after the strip, forced to
`settings.focusMirrorVpnEnabled ?? false` — it IS portable.

### TS.3 Task object `[TS types.ts]`

| Field | Type | Notes |
|---|---|---|
| `id` | string | nanoid-style ~21 chars from `A-Za-z0-9_-`. Primary key. |
| `title` | string | required |
| `description` | string? | optional; absent/null → `""` |
| `startTime`, `endTime` | string | ISO-8601 UTC, always `.sssZ` in TS |
| `durationMinutes` | integer | |
| `status` | enum | `scheduled` `active` `completed` `skipped` `overdue` |
| `priority` | enum | `low` `medium` `high` `critical` |
| `tags` | string[] | |
| `reminders` | Reminder[] | **always `[]` in practice**; preserve opaquely; never schedule from it |
| `color` | string | hex e.g. `#22c55e`; missing → `#6366f1` |
| `focusMode` | boolean | missing → false |
| `focusAllowedPackages` | string[]? | **three-way**: absent/null = use global allow-list; `[]` = all apps allowed; `[…]` = that list. Must round-trip exactly including null vs absent. |
| `createdAt`, `updatedAt` | string | ISO-8601 UTC |

`Reminder` shape (preserve opaquely — never schedule from it):
`{ id, taskId, offsetMinutes:number, type:"pre-start"|"at-start"|"post-start", notifId?:string }`

### TS.4 TS task restore behaviour `[TS backupService.ts]`

1. `INSERT OR IGNORE` keyed by `id`. A missing/falsy `id` → task skipped and counted.
2. An `id` already present locally → skipped; **existing wins** (what UI copy says).
3. Duplicate IDs inside the file: TS loop does not detect them; DB ignores the repeat
   but still counts it as imported. This is a TS counting bug. **Kotlin must detect
   duplicate IDs in the file and reject the whole file** (not silently ignore).
4. `scheduled` or `active` task whose `endTime` is in the past → stored as `skipped`,
   `updatedAt = planNow` (canonical format).
5. All inserts use `skipAlarms: true`. After all inserts, future `scheduled` tasks are
   passed once to the scheduler.
6. `replaceTasks` refuses while a focus session is active (runtime flag **or** DB row).
   Then `DELETE FROM tasks` (single DAO call) + inserts, in one transaction. History
   tables are never touched.

### TS.5 Settings portable/omit boundary `[TS backupService.ts]`

Everything in AppSettings is exported **except** the 21 live-state keys above.
`focusMirrorVpnEnabled` is re-added explicitly.

TS import behaviour: `{ ...localSettings, ...backup.settings, focusMirrorVpnEnabled: backup.value ?? local ?? false }`.
Collections are overwritten wholesale, never unioned.

### TS.6 focusMirrorVpnEnabled restore semantics `[TS backupService.ts]`

```typescript
focusMirrorVpnEnabled: backup.settings.focusMirrorVpnEnabled
    ?? localSettings.focusMirrorVpnEnabled
    ?? false
```

`??` is JS nullish-coalesce. `false ?? anything = false` (false is NOT null/undefined).

**Correct Kotlin rule:** if the key is present in the backup (including as `false`),
the imported value wins. If the key is absent, preserve local.

**Current Kotlin bug (KT-9 related):** uses `||` (logical OR). When `imported = false`
and `current = true`, result is incorrectly `true`. Fix: `if (imported.has("focusMirrorVpnEnabled")) imported.optBoolean(...) else currentValue`.

### TS.7 Greyout windows vs recurring schedules `[TS AppContext._recurringSchedulesToGreyoutWindows]`

`recurringBlockSchedules` is the **authoritative, user-authored** list.

`settings.greyoutSchedule` holds TWO kinds of entries:
- **(a) User-created windows** — no `scheduleId` field. These are real portable user data.
- **(b) Derived windows** — have a `scheduleId` matching a recurring schedule. These are
  generated copies; the recurring schedule is the source of truth.

The native enforcement layer is fed:
`[...userWindowsWithNoScheduleId, ...derivedWindowsGeneratedFromRecurringSchedules]`

Derived windows are generated as: for each `RecurringBlockSchedule` with
`enabled && packages.length > 0`, **one window per package:**
```
{ pkg: package, startHour, startMin, endHour, endMin, days: sched.days,
  scheduleId: sched.id, scheduleName: sched.name, vpnEnabled: sched.vpnEnabled ?? false }
```

**Import rule (see §5.3):** import only type-(a) windows (no `scheduleId`); discard
type-(b) windows; restore `recurringBlockSchedules` from its own wire key; regenerate
derived windows from the imported recurring schedules.

**Current Kotlin bug (KT-8):** BackupCoordinator writes recurring schedules under the
`greyoutSchedule` wire key using Kotlin-internal field names (`startMinute`/`endMinute`/
`daysOfWeek`). Fix: use distinct wire keys with TS field names.

### TS.8 Notification schedule `[TS notificationService.ts]`

Slot identifiers per task with status **not in** `{completed, skipped, overdue}`:
```
<taskId>-pre-600000   10 min before startTime
<taskId>-pre-300000    5 min before startTime
<taskId>-pre-60000     1 min before startTime
<taskId>-pre0          at startTime
<taskId>-mid900000    15 min after startTime
<taskId>-mid1800000   30 min after startTime
<taskId>-almost        endTime − 60 s
<taskId>-end           at endTime  (task-end alarm, not reminder notification)
```

Scheduling rules:
- Skip any slot whose trigger is < 1 s from now.
- Mid check-ins only if fire time is before `endTime` AND at least 10 min remain.
- Global budget: 450 slots total (earliest first).
- Task-end alarm is scheduled separately by AlarmRepository, not as a reminder slot.

`[KT]` `NotificationScheduler` is an interface with no production implementation.
`NotificationRepository` is not constructed in `AppModule`. Reminder notifications
do not exist in the Kotlin app today (KT-3). This is new work.

### TS.9 Other TS behaviour that affects the plan `[TS backupService.ts, _layout.tsx]`

- **Startup overdue sweep:** unfinished tasks whose `endTime` has passed are stored as
  `overdue` in the DB. Kotlin must implement the same sweep (§8.6). This is distinct
  from import: import downgrades past `scheduled` → `skipped` (closed history); the
  sweep marks locally unresolved tasks `overdue` (open, user-visible).
- **PendingImport is in-memory** (`pendingBackupImport.ts`). Staging a new file replaces
  the previous one. Nothing is written to disk before the user confirms.
- **`handledUris` set in TS was never cleared,** meaning re-opening a cancelled file
  was silently ignored. This is a TS bug. Do not copy it — re-opening after cancel must
  work.
- **`pendingPresets`** and the doc comment "imports land as TEMPORARY presets" are dead
  code in TS. No TS code reads or writes `pendingPresets`. Direct-apply is the real
  behaviour. Do not implement this field.
- **`weekStartDay`** defaults to 0 (Sunday) and no TS UI writes it. Its value in a TS
  backup is always 0. Importing it would force Sunday on all users.
- **`focusSessionLength`** writes through to `defaultDuration`/`pomodoroDuration` when
  saved *via the UI*. Import must **not** re-apply that write-through. Import
  `defaultDuration` and `pomodoroDuration` directly.
- **`Task.reminders`** is initialised to `[]` by `createTask` and never populated by
  any TS code path. Notifications are derived from task times only.
- **`defaultReminderOffsets`** is defined in AppSettings and exported but never read
  by the notification service. It is dead.
- **TS native alarm source was not in the archive.** TS alarm runtime behaviour is
  therefore source-unproven.

### TS.10 TS nested object shapes `[TS types.ts]`

```
GreyoutWindow:
  { pkg?, pkgs?[], startHour, startMin, endHour, endMin, days[],
    scheduleId?, scheduleName?, vpnEnabled? }

RecurringBlockSchedule:
  { id, name, packages[], days[], startHour, startMin, endHour, endMin,
    enabled, vpnEnabled?, vpnPackages?[] }

DailyAllowanceEntry:
  { packageName, mode:"count"|"time_budget"|"interval",
    countPerDay?, budgetMinutes?, intervalMinutes?, intervalHours? }

AllowedAppPreset:  { id, name, packages[] }   // packages [] = all apps allowed
BlockPreset:       { id, name, packages[] }

UserProfile (all fields optional):
  { name, occupation, dailyGoalHours, wakeUpTime:"HH:MM", sleepTime:"HH:MM",
    focusGoals[], chronotype, focusSessionLength, breakStyle,
    distractionTriggers[], motivationStyle[], weeklyReviewDay }
  chronotype: morning|midday|afternoon|evening|night|flexible
  breakStyle: short_frequent|balanced|long_infrequent|no_break
  weeklyReviewDay: sun|mon|tue|wed|thu|fri|sat
```

**Day numbering (critical):** `days[]` in both `GreyoutWindow` and
`RecurringBlockSchedule` uses **Calendar.DAY_OF_WEEK: 1 = Sunday … 7 = Saturday.**
`weekStartDay` (separate field) uses a different convention: integer 0 = Sunday … 6 = Saturday.
Overnight windows (endHour < startHour) cross midnight; preserve without normalisation.
Test explicitly on Sunday (1) and Saturday (7).

### TS.11 Legacy settings SQLite key `[KT FocusFlowDatabase.kt]`

Correct key: `SELECT value FROM settings WHERE key = 'app_settings' LIMIT 1`
Current buggy code queries: `'appSettings'` — wrong.

---

## 2. Product decisions

Each default is implemented now. The owner confirms or changes via the named constant.

| ID | Question | Default | Reason |
|---|---|---|---|
| **PD-1** | Same-ID divergent collision | **Abort entire import before any mutation. `PlanFailure.IdConflict`. Conflict screen lists up to 20 task titles with ids. Suggest Replace mode.** | Identical projection → identicalDuplicate/skip (import continues). Divergent projection → abort. A backup that partially restores while silently dropping some tasks is more dangerous than a refused import. "Import succeeded" while an important task was not imported is deceptive. The abort contract is the deliberate v12 decision, not overridden. |
| **PD-2** | Exact-alarm unavailable | Keep ladder: `setAlarmClock` → `setExactAndAllowWhileIdle` → `setAndAllowWhileIdle`. Surface the active tier in diagnostics. | Better to have an inexact alarm than no alarm. Disclosure lets the user fix permissions. |
| **PD-3** | Imported task with status `active` | Treat like `scheduled`: future end → `scheduled`; past end → `skipped`. Never import as live. | `active` is runtime state of the source device. |
| **PD-4** | TS 365-day task-history prune | Not ported in V1. Import history verbatim. | `[KT]` no equivalent found. |
| **PD-5** | Task-end alarm missed (device was off) | No late alarm. Status handled by startup overdue sweep (§8.6). | TS behaviour. A "you missed X" notification is a new feature. |
| **PD-6** | Notification permission denied | Still schedule task-end alarm; surface capability in diagnostics. | `[TS]` skips all scheduling including the native alarm when permission is denied — looks like accidental coupling. |
| **PD-7** | Intent filter breadth | Keep `application/octet-stream` and `*/*` fallback; **remove `BROWSABLE`** from content filters; reject non-backup content fast with a clear message. | Content URIs do not come from browsers. |
| **PD-8** | Import that weakens protection while Defense PIN is active | If `pinProtectionEnabled` is on locally, require PIN at confirm when the import would remove any entry from `alwaysOnPackages`, `alwaysOnVpnPackages`, `blockedWords`, `recurringBlockSchedules`, `dailyAllowanceEntries`, or turn `focusMirrorVpnEnabled` from true to false. | An external file must not silently downgrade enforcement. |
| **PD-9** | External file opened before onboarding completes | Stage in memory; show "Backup ready to import" only after onboarding completes. Do not render backup content behind a locked PIN gate. | |
| **PD-10** | FSI unavailable UX | One-time prompt per app version + persistent diagnostics row. Not a data-integrity matter. | |
| **PD-11** | Kotlin-only preferences (notification toggles, `bedTime`, thresholds, `taskRemindersEnabled`, `autoFocusEnabled`) | Not exported, ignored on import. V1 stays byte-compatible with TS. | If Kotlin-to-Kotlin portability is desired later, add them under `settings.kotlin` (harmlessly ignored by TS's spread). |

---

## 3. Data-boundary classification

| Data | Class | In V1 file | Import behaviour |
|---|---|---|---|
| Task rows (all 5 statuses) | persistent user data | yes | Merge / Replace rules (§5) |
| `Task.reminders[]` | persistent, inert | yes | Preserve opaquely; never schedule from it |
| Analytics / history tables | persistent, not portable in V1 | no | **Never touched**, including by Replace |
| Portable settings (§3.1 APPLY list) | persistent user data | yes | Overwrite per key |
| 21 live-state keys | device-local runtime | stripped on TS export, present in old exports | **Never applied** regardless of presence |
| Active focus session row / `focus_active` | runtime | no | Never imported. Active session blocks Replace |
| PIN hash / defense password | device-local secret | no | Never |
| `onboardingComplete`, `privacyAccepted` | device-local consent | TS exports them | **Never applied** |
| `launcherWallpaperUri`, `overlayWallpaper` | external resource (local file path) | TS exports | **UNRESOLVED — local value preserved; record in RestorePlan and RestoreResult; count in `externalResourcesUnresolved`** |
| Package-name references | portable identifiers | yes | Keep even if not installed; validate syntax only |
| AlarmManager alarms, PendingIntents, notifications, channels, alarm registry, reminder ledger | derived OS state | no | Never exported/imported; rebuilt by reconciler |
| `PendingImport` | validated backup model — durable before confirmation | n/a | Persisted to `noBackupFilesDir/pending-import/` using `AtomicFile`; cleared after completion, cancel, or successful restore; recovered on startup if present |
| `RestoreJournal` | transient, `noBackupFilesDir` | n/a | Deleted on completion |

---

### 3.1 Settings matrix — authoritative wire adapter contract

#### APPLY (overwrite local; absent key = leave local unchanged)

| TS V1 wire key | Kotlin target | Notes |
|---|---|---|
| `darkMode` | `darkModeEnabled` | key rename |
| `defaultDuration` | `defaultDurationMinutes` | key rename; Kotlin currently exports under wrong name |
| `pomodoroDuration` | `pomodoroWorkMinutes` | key rename; Kotlin currently exports under wrong name |
| `pomodoroBreak` | `pomodoroBreakMinutes` | key rename; Kotlin currently exports under wrong name |
| `allowedInFocus` | `allowedFocusPackages` | key rename |
| `alwaysOnPackages` | `alwaysBlockPackages` | key rename |
| `alwaysOnVpnPackages` | `alwaysOnVpnPackages` (new field — see §B.2) | VERIFY VPN repository path |
| `focusMirrorVpnEnabled` | `focusMirrorVpnEnabled` | imported value wins when present; see §TS.6 |
| `blockedWords` | `PREF_BLOCKED_WORDS` via `setBlockedWords` | |
| `dailyAllowanceEntries` | `dailyAllowanceConfigJson` | validate `packageName` vs TS `packageName` parity |
| `allowedAppPresets` | existing allowed-presets store | |
| `blockPresets` | dedicated block-preset store (new — see §B.3) | never write into allowed-presets store |
| `recurringBlockSchedules` | `recurring_block_schedules` prefs key | map `days` 1-based→0-based; `startMin`→`startMinute`; `endMin`→`endMinute` |
| `greyoutSchedule` (user windows only — no `scheduleId`) | `user_greyout_windows` prefs key (new) | see §5.3 greyout rule |
| `userProfile` | `user_profile` JSON string | Replace TS-known fields; keep Kotlin-only fields (e.g. `weekUsageReportStartDay`) |
| `launcherTheme` | `launcher_theme` | |
| `focusToolPackages` | `focus_tool_packages` | |
| `launcherHiddenPackages` | `launcher_hidden_packages` | |
| `launcherDockPackages` | `launcher_dock_packages` | **portable** — `[KT]` LauncherActivity reads this key |
| `launcherClockStyle` | `launcher_clock_style` | **portable** — `[KT]` SettingsRepository persists this |
| `launcherBlockUninstall` | `launcher_block_uninstall` | |
| `launcherLockDuringStandalone` | existing Kotlin key | |
| `keepFocusActiveUntilTaskEnd` | `keepFocusActiveUntilTaskEnd` | |
| `autoRescheduleEnabled` | `autoRescheduleEnabled` | |
| `overlayQuotes` | `overlay_quotes` (new — see §B.4) | |
| `protectionMode` | `SetupPersistenceManager.KEY_PROTECTION_MODE` | valid values: `standard` / `iron` only; unknown enum → reject before mutation |

#### NEVER APPLY (even if present in file — includes old exports that carry them)

All 21 live-state keys listed in §TS.2, plus:
`onboardingComplete`, `privacyAccepted`

#### ACCEPT AND IGNORE (parse; accept for compatibility; do not persist)

`weekStartDay` (TS always 0; importing would force Sunday),
`defaultReminderOffsets` (dead in TS notification service),
`launcherWallpaperUri` / `overlayWallpaper` (external resource; keep local),
`launcherPinnedPackages` (declared but never synced in TS),
`beginnerMode`, `tipsCardDismissed`, `tipsCardFirstShownAt`,
`lastShownStreakMilestone`, `pendingAchievementCelebration`, `pendingPresets`
(dead TS code or session-local state)

**Collections:** always overwrite wholesale. The Merge/Replace switch governs tasks
only. Setting conflicts: APPLY always uses backup value for APPLY keys (absent = keep
local). Invalid setting value (wrong JSON type, out of range) → ignore that key, add
a warning, continue.

**Kotlin-only preferences** (PD-11): not exported; ignored on import.

---

## 4. Wire parsing, validation, limits

### 4.1 File-level failures (reject whole file; zero mutation)

Not JSON; invalid/malformed UTF-8; top-level not an object; `kind` mismatch; `settings`
not an object; `tasks` not an array; `version` present and ≠ 1; **any duplicate object
key at any depth**; any limit below exceeded; **duplicate task `id` within `tasks[]`**
(signals tampering or bad merge; reject as non-lossy).

### 4.2 Parser requirements

- Stream or tokenize; never build an unbounded tree before checking limits.
- Strict UTF-8 (`CharsetDecoder` with `REPORT`). Strip a leading BOM. Reject lone
  surrogate escapes.
- **Duplicate key detection:** `org.json` is last-wins and cannot be used alone.
  A streaming reader with per-object key tracking meets the requirement.
- Count bytes from the `InputStream`; do not trust `OpenableColumns.SIZE`.

### 4.3 Limits (constants; change via named constants only)

```
MAX_FILE_BYTES          = 8 MiB
MAX_DEPTH               = 12
MAX_TASKS               = 20,000
MAX_STRING_CHARS        = 20,000 (title ≤ 1,000; tag ≤ 100; package name ≤ 255; id ≤ 128)
MAX_PACKAGES_PER_LIST   = 5,000
MAX_BLOCKED_WORDS       = 5,000 (each word ≤ 200 chars)
MAX_ALLOWANCE_ENTRIES   = 1,000
MAX_RECURRING_SCHEDULES = 500
MAX_GREYOUT_WINDOWS     = 5,000
MAX_PRESETS             = 500
MAX_TAGS_PER_TASK       = 100
MAX_REMINDERS_PER_TASK  = 100
MAX_JSON_NODES          = 2,000,000
```

### 4.4 Per-task validation (failure → that task is `invalid`; import continues)

- `id`: string 1–128 chars, no control characters.
- `title`: string, non-empty after trim.
- `description`: string; null/absent → `""`.
- `startTime`, `endTime`, `createdAt`, `updatedAt`: ISO-8601 with `Z` or offset; reject
  unparsable. Require `endTime ≥ startTime`. **Normalize to `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'` (UTC).**
- `durationMinutes`: integer in `[0, 100000]`.
- `status` ∈ {scheduled, active, completed, skipped, overdue}; unknown → invalid task.
- `priority` ∈ {low, medium, high, critical}; unknown → invalid task.
- `tags`: array within limits.
- `reminders`: array; elements that fail to decode are dropped with a warning (not the task).
- `color`: string ≤ 32 chars; missing → `#6366f1`.
- `focusMode`: boolean; missing → false.
- `focusAllowedPackages`: absent/null → null; array → list (keep `[]` distinct from null).
- Package names: `^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$`; invalid entries dropped
  from their list with a warning; list is not invalidated.
- Schedules: `startHour` 0–23, `startMin` 0–59, `days` elements 1–7 (distinct),
  `packages` non-empty for an enabled schedule. A failing schedule/window is dropped
  with a warning.

### 4.5 Timestamp canonicalization — fix everywhere `[KT]`

Introduce one shared formatter used by **all** Kotlin task writes:
```kotlin
object CanonicalTimestamp {
    private val FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .withZone(ZoneOffset.UTC)
    fun format(instant: Instant): String = FORMATTER.format(instant)
    fun parse(s: String): Instant = Instant.parse(s)  // standard ISO-8601 parser
}
```

All four task timestamp fields are normalized to this format at import **and** at all
Kotlin task-create/edit writes. The DAO must not compare ISO strings lexicographically
in any code path that relies on ordering.

---

## 5. Import semantics

`[KT]` facts: `TaskDao.insertTask` = `OnConflictStrategy.IGNORE`; there is a
`DELETE FROM tasks` DAO call; **no Room entity has a foreign key referencing `tasks`**
(confirmed in schema), so deleting task rows cannot cascade into history tables.
Re-verify after any schema migration that adds a new FK.

### 5.1 Preview (read-only, in-memory, no mutation)

Produces `ImportPreview` from the validated backup and current local task IDs:

```
tasksInFile, newTasks, identicalDuplicates,
conflictingTasks (List<ConflictingTask> — up to 20 with id + title + reason),
invalidByReason, pastScheduledToSkipped, settingsSectionsPresent,
externalResourcesUnresolved, weakeningDetected (PD-8), warnings
```

**Identical projection** = equal on `title, description, startTime, endTime,
durationMinutes, status, priority, tags, color, focusMode, focusAllowedPackages`
after timestamp normalization. Exclude `createdAt`, `updatedAt`, `reminders`.
For a Merge-mode duplicate whose projected fields match, reminder differences are
intentionally ignored by `TaskDuplicateProjection` in V1; the existing task wins and
its reminder array is preserved.
Compare *before* the past-scheduled downgrade.

**Conflicting** = same `id`, different projection. This is surfaced in preview so the
user sees the conflict before the import begins. `ImportPreview.hasConflicts: Boolean`
drives a blocking UI state.

If `preview.hasConflicts` is true: the Merge confirmation button is disabled.
User sees "N tasks already exist with different content. Use Replace to overwrite."
Replace mode bypasses this block (existing tasks are deleted first, so no collision).

Preview counts are advisory; the plan is authoritative.

### 5.2 Plan generation (pure function; unit-testable without Android)

Inputs: `validatedBackup`, `localTaskIds: Set<String>`,
`localTaskProjections: Map<String, TaskProjection>`, `activeSession: Boolean`,
`mode: MERGE|REPLACE`, `planNowMillis: Long`.

Output: `RestorePlan` (exactly what the journal stores) **or** `PlanFailure.IdConflict`.

**Pre-mutation conflict check (Merge mode only):**
```
conflicting = validatedBackup.tasks
    .filter { it.id in localTaskIds }
    .filter { projectionOf(it) != localTaskProjections[it.id] }

if (conflicting.isNotEmpty()) → return PlanFailure.IdConflict(conflicting)
```

`PlanFailure.IdConflict` causes `RestoreCoordinator.begin` to return immediately
without writing the journal and without touching any persistent store.

For every valid backup task in Replace mode, or every valid backup task not in the
local set in Merge mode:

| Backup status | Rule |
|---|---|
| `scheduled` or `active` (PD-3) | `endTime > planNow` → store `scheduled`; else store `skipped`, `updatedAt = planNow` |
| `completed`, `skipped`, `overdue` | store verbatim |

Identical-projection tasks (same `id`, same content) → counted in `identicalDuplicates`;
not inserted (INSERT OR IGNORE handles them silently).

### 5.3 Greyout window rule (from §TS.7)

At plan generation:
1. Extract from backup `greyoutSchedule` only windows **with no `scheduleId`**
   (user-created). Validate each (§4.4 schedule rules). Store as
   `plan.userGreyoutWindows`.
2. Store `plan.recurringBlockSchedules` from backup `recurringBlockSchedules` wire key.
3. At SETTINGS_APPLIED phase: write user windows to `user_greyout_windows` prefs key.
4. At RECONCILING phase: regenerate derived windows from the newly-persisted
   `recurringBlockSchedules` and write `[userWindows + derivedWindows]` to
   `GreyoutRepository`.

Discard backup `greyoutSchedule` windows that carry a `scheduleId` — they are
derived copies whose source (the recurring schedule) is being imported separately.

### 5.4 Merge (mode = MERGE)

- Retained local set = all current local task IDs.
- One Room transaction: `INSERT OR IGNORE` for all new tasks. Settings applied per §3.1.
- Nothing else is deleted.
- Re-importing the same file repeatedly is a no-op for tasks and re-applies the same settings.

### 5.5 Replace tasks (mode = REPLACE)

The label is "Replace tasks." It never means "replace everything."

- **Precondition:** no active focus session (runtime flag **or** active row in
  `focus_sessions`). If violated: refuse, zero mutation, explain to user.
- **PD-8 check:** if PIN is active and the import weakens enforcement, require PIN.
- **One transaction:** `DELETE FROM tasks` + inserts. The journal records the pre-delete
  ID set so the reconciler knows which alarms to cancel.
- **Untouched:** analytics, history tables, focus session history, PIN, device-local
  settings, enforcement state.
- Settings applied identically to Merge.
- Derived state fixed by the reconcile phase, not by per-task calls.

### 5.6 Result object

```kotlin
data class RestoreResult(
    val tasksInserted:              Int,
    val identicalDuplicates:        Int,
    val invalidByReason:            Map<String, Int>,
    val downgradedToSkipped:        Int,
    val settingsApplied:            List<String>,
    val settingsIgnoredCount:       Int,
    val externalResourcesUnresolved: Int,   // wallpaper URIs and other local-path resources
    val warningCodes:               List<String>,
    val alarmsArmed:                Int,
    val alarmsDeferred:             Int,    // DEFERRED_EXACT_UNAVAILABLE task-end alarms
    val alarmsCancelled:            Int,
    val capabilityWarnings:         List<String>,
)
```

Counts and key names only. Never persist or log task titles or backup content.

`PlanFailure.IdConflict` is surfaced separately in the UI (blocking state, not a result).
It is never written to the journal (no mutation occurred).

---

## 6. Restore engine — gate → journal → idempotent phases

Room and SharedPreferences cannot commit atomically. The design uses an
**absolute, idempotent plan** replayed from a durable journal. Every step sets state
to a fixed value; replaying is harmless.

### 6.1 RestoreGate

Process-wide singleton; first object created in `AppModule`. States: `OPEN`, `RECOVERING`, `RESTORING`.

```kotlin
suspend fun <T> write(owner: String, block: suspend () -> T): T
    // Normal writers call this. Suspends while state ≠ OPEN. Counts in-flight writers.

suspend fun tryBeginRestore(): Boolean
    // Drains in-flight writers; atomically transitions OPEN → RESTORING.
    // Returns false if state ≠ OPEN.

fun reopen()
    // Only RestoreCoordinator or recovery calls this.
```

**Must pass through `write()`:** every `TaskRepository` mutator; `TaskViewModel` actions;
`NotificationActionReceiver` (complete/extend/skip); every setter for keys in §3.1 APPLY
list; greyout, recurring-schedule, and VPN-package setters; the overdue sweep; the
reconciler.

**Receivers:** `goAsync()` + `withTimeoutOrNull(8_000) { gate.write { … } }`. On timeout:
drop the action and log; notification stays visible. Boot/permission/time receivers
do NOT do work inline — they enqueue unique WorkManager `reconcile` work that waits
on the gate with a long timeout.

**Restore bypasses** `write()` by calling internal appliers (§6.3), never public setters.

### 6.1b Durable PendingImport

File: `<noBackupFilesDir>/pending-import/import.json`. Written with `AtomicFile`
before the confirmation UI is shown. Contains the full validated, normalized backup
model (same content that was validated in §4; same format as the journal's task list).

**Lifecycle:**
- Written after validation passes; before confirmation screen is navigated to.
- If process dies during confirmation → the file remains; on next startup, `PendingImportStore` detects it and navigates back to the confirmation screen automatically.
- On confirm → RestoreJournal is written; PendingImport file is deleted.
- On cancel → PendingImport file is deleted.
- On startup: if PendingImport file exists but no RestoreJournal exists → user is shown the confirmation screen again (re-confirm the same import).
- If both PendingImport and RestoreJournal exist → RestoreJournal takes precedence (recovery path); PendingImport file is deleted.
- On completion of restore → deleted as part of phase cleanup.
- **Latest file wins:** if a new external file arrives while a PendingImport exists on disk and no journal exists → overwrite the PendingImport with the new file; navigate back to confirmation screen.

### 6.2 RestoreJournal

File: `<noBackupFilesDir>/restore/journal.json`. Written with `AtomicFile`
(write → fsync → rename). Never in a backed-up directory.

```json
{
  "journalVersion": 1,
  "sessionId": "uuid",
  "mode": "MERGE|REPLACE",
  "planNowMillis": 0,
  "phase": "PLANNED|TASKS_APPLIED|SETTINGS_APPLIED|RECONCILED",
  "attempts": 0,
  "deleteAllExisting": false,
  "preDeleteTaskIds": ["..."],
  "tasksToInsert": [ /* normalized Task[] */ ],
  "settingsPlan": { "key": "normalized value" },
  "userGreyoutWindows": [ /* GreyoutWindow[] without scheduleId */ ],
  "recurringBlockSchedules": [ /* RecurringBlockSchedule[] */ ],
  "warningCodes": [],
  "counts": {}
}
```

The phase is advanced (journal rewritten atomically) **after** each step commits.
Journal holds backup content at rest only for the restore duration; deleted on
completion and on startup cleanup of any terminal-phase journal.

### 6.3 Phase actions (all idempotent)

**1. TASKS → TASKS_APPLIED**
One Room transaction. Replace: `DELETE FROM tasks` + inserts. Merge: `INSERT OR IGNORE`.
Replaying Merge after partial success produces the same state (prior inserts are ignored
and are identical to the plan). Advance journal phase after commit.

**2. TASKS_APPLIED → SETTINGS_APPLIED**
Group the settings plan by SharedPreferences file. For each file, write all keys in
**one `editor.commit()`** (not `apply()` — `apply()` returns before disk write; a crash
could lose data the journal already marked done). Check the boolean result; retry 3×
before failing. Writes go through `SettingsApplier`, not public setters. Advance phase.

**3. SETTINGS_APPLIED → RECONCILED**
- Run all side-effect syncs that public setters normally trigger (regenerate derived
  greyout windows per §5.3; notify enforcement services to re-read prefs; VPN/native sync).
  PR must list each public setter's side effects and show which `syncFromStore()` call
  replaces them.
- Run full `reconcile(reason = "restore")` with the **real** current time (not `planNowMillis`).
- Advance phase.

**4. RECONCILED → done**
Delete the journal. `reopen()` the gate. Persist a small `last_restore_result` for
one-time display.

### 6.4 Admission (RestoreCoordinator.begin)

1. `gate.tryBeginRestore()` → false = `Busy` (UI: "A restore is already running").
2. Replace → active-session check. Violation → `Refused`; gate reopened; no mutation.
3. PD-8 PIN check.
4. **Build plan (§5.2)**:
   - Merge mode: check for `PlanFailure.IdConflict`. If conflict → gate reopened; no
     journal written; no mutation; return `IdConflict(conflictingTasks)` to UI.
     UI shows conflict screen with up to 20 task titles and "Use Replace to overwrite."
   - No conflict → proceed.
5. Write journal (phase = PLANNED). If journal write fails → gate reopened; no mutation.
6. **Delete durable PendingImport file** (journal now owns the recovery path).
7. Run phases in an **application-scoped** coroutine on `Dispatchers.IO`. Not a
   ViewModel scope; backgrounding or rotation must not cancel it.
8. A second import while not OPEN → `Busy`; URI discarded.

### 6.5 Startup recovery

- `Application.onCreate` (main thread): check file existence only for journal and
  pending-import. **No parsing, no Room, no prefs work here.**
  - Journal exists → gate = `RECOVERING`
  - Journal absent, PendingImport exists → gate = `OPEN`; flag `hasPendingImport = true`
  - Neither → gate = `OPEN`
- If `hasPendingImport`: after DB open, navigate to confirmation screen automatically
  (re-present the same import for the user to confirm or cancel).
- If `RECOVERING`: launch `RestoreRecovery.run()` on an application-scoped IO coroutine.
  A process started by a receiver (no Activity) takes the same path.
- `run()`: read journal; execute remaining phases from `phase`. On success: delete
  journal; `reopen()`; set "completed after interruption" result.
- On phase failure: increment `attempts` in the journal (rewrite atomically).
  After 3 attempts: enter **`RECOVERY_BLOCKED`**. The gate **stays closed**.
  The journal is **retained**. Surface a non-dismissable "Restore could not be
  completed" screen with **Retry** (re-runs `RestoreRecovery.run()`) and
  **Discard** (explicit user action; see Discard semantics below).
  **Do not call `reopen()` after a post-mutation recovery failure.** Mutation may
  have already occurred; releasing the gate would let normal writers run against
  partial persistent state.
- **Discard semantics (user-initiated only, after `RECOVERY_BLOCKED`):**
  The user acknowledges that the import may be incomplete. The journal is deleted.
  `reopen()` is called. A full `reconcile()` runs to repair derived state. The app
  becomes usable. This is a user choice, not an automatic fallback.
- **Corrupt or unreadable journal (cannot parse; unknown `journalVersion`):**
  Do **not** silently delete the journal. Move it to
  `noBackupFilesDir/restore/journal.quarantine` (rename, not delete).
  Enter `RECOVERY_BLOCKED`. Gate stays closed. Surface the same Retry / Discard
  screen with an additional message: "The restore record could not be read. Retry
  may not succeed." Discard proceeds as above (deletes quarantine file; reconcile;
  reopen). Quarantine preserves the artifact for diagnostic purposes.
- Normal writers started during `RECOVERING` suspend on the gate (UI) or are
  enqueued/dropped (receivers).

### 6.6 Crash-point table (test spec)

| Crash point | State on restart | Expected |
|---|---|---|
| Before PendingImport written | no files | Normal startup; nothing changed |
| After PendingImport written, before confirm | PendingImport file only | Confirmation screen re-presented; user can confirm or cancel |
| After confirm, before journal written | PendingImport file only | Gate opens; PendingImport re-presents confirmation screen |
| After journal written (PLANNED), before tasks | journal PLANNED; PendingImport deleted | Recovery replays all phases |
| Mid tasks transaction | journal PLANNED, SQLite rolled back | Replay tasks |
| After tasks commit, before phase write | journal PLANNED | Replay tasks (no-op), advance |
| TASKS_APPLIED, some prefs committed | journal TASKS_APPLIED | Replay all settings (idempotent) |
| SETTINGS_APPLIED, before/inside reconcile | journal SETTINGS_APPLIED | Re-run reconcile |
| RECONCILED, before journal delete | journal RECONCILED | Delete journal only |
| Torn journal or PendingImport rewrite | old or new version | AtomicFile rename is atomic; either is valid |
| Second import during any restore state | any | Busy; no state change |
| Writer during RECOVERING | any | Suspended/dropped; no interleaving |

---

## 7. External `.focusflow` file handling

### 7.1 Manifest changes (P0 — apply independently)

- **Remove `BROWSABLE`** from both `content` intent filters.
- Keep `application/octet-stream` filter and `*/*` fallback (PD-7).
- **Do not add a `file://` scheme filter.** A `file://` URI receives the error
  "Unsupported file location. Use Import in Settings."

### 7.2 Routes.fromPath whitelist (P0 — apply independently, KT-10)

`Routes.fromPath` must use an explicit **allowlist** of externally deep-linkable routes.
`IMPORT_CONFIRM` and any route that depends on `IncomingBackupDispatcher` in-memory
state are **never** reachable from an external intent. The confirm screen is reachable
only when `IncomingBackupDispatcher` state is `Ready`.

### 7.3 MainActivity dispatch

```kotlin
// In onCreate (not onStart, to avoid duplicate on rotation):
if (savedInstanceState == null) handleIntent(intent)

// In onNewIntent:
setIntent(intent)
handleIntent(intent)

private fun handleIntent(i: Intent?) {
    when {
        i?.action == Intent.ACTION_VIEW && i.data?.scheme == "content" ->
            dispatcher.onContentUri(i.data!!)
        i?.action == Intent.ACTION_VIEW && i.data?.scheme == "file" ->
            showError("Unsupported file location. Use Import in Settings.")
        else -> existingWhitelistedDeepLinkRouting(i)
    }
}
```

The `savedInstanceState == null` guard prevents re-handling after rotation or
process-death recreation. **No global handled-URI set** (the TS set was never cleared
and was a bug; re-opening after cancel must work).

### 7.4 IncomingBackupDispatcher (application scope)

States: `Idle | Reading | Ready(PendingImport) | Failed(reason) | Busy`

**`onContentUri(uri: Uri)`:**
- If gate ≠ `OPEN` → emit `Busy`; discard.
- Cancel any in-flight read (**latest file wins** — replaces any previous staged import).
- Read **eagerly** on IO dispatcher. URI grant is tied to the receiving task; never defer
  or persist the URI itself.
- Error mapping: `SecurityException → PermissionDenied`, `FileNotFoundException → NotFound`,
  `IOException → ReadFailed`, null stream → `ProviderNull`.
- Stream with hard byte counter (§4.3). Parse (§4). Build preview (§5.1). Emit `Ready(PendingImport)`.

**`PendingImport`** holds: validated normalized model + display label (`OpenableColumns.DISPLAY_NAME`,
truncated to 80 chars, display only). **The URI is not retained after reading.**

**Durability:** After validation passes, the normalized model is written to
`noBackupFilesDir/pending-import/import.json` using `AtomicFile` before the
confirmation screen is navigated to. This is the file described in §6.1b.

**Navigation:** `Ready` navigates to the confirm screen only when: DB is open;
onboarding complete; privacy accepted; PIN gate (if any) unlocked. Otherwise stay
`Ready` and show a "Backup ready to import" banner (PD-9).

**Cancellation:** → delete durable PendingImport file → `Idle`. Re-opening the same
file afterwards works (no stale handled-URI set).

**Replacement while on confirm screen:** overwrite the PendingImport file with the
new validated model; re-render confirmation with new preview;
show "Replaced by the newly opened file."

**Process death:** On restart, `Application.onCreate` detects the PendingImport file
(no journal) and navigates back to the confirmation screen. The user sees the import
exactly as they left it and can confirm or cancel.

**In-app SAF picker** (`ACTION_OPEN_DOCUMENT`) calls the same `onContentUri`.

**Logging:** never log the URI, filename, or any content. Log reason code, byte count, counts.

---

## 8. Scheduler and derived Android state

**Principle:** AlarmManager registrations, PendingIntents, posted notifications and
channels are derived from `(DB, clock, capabilities)`. They are never exported,
imported, or trusted as authoritative. A reconciler recreates them from persistent truth.

`PendingIntent.FLAG_NO_CREATE` returning non-null is **not proof** that an AlarmManager
registration exists. A token outlives a fired or cancelled alarm. Reconcile always
(re)schedules every desired alarm; scheduling is idempotent.

### 8.1 Task-end alarm identity

**All three PendingIntents** that carry a task-end reference must use a data URI for
uniqueness, not request-code hashing. Java `String.hashCode()` collisions are real;
two tasks with the same hash would silently overwrite each other's alarm data.

**Alarm broadcast PI (AlarmRepository.buildAlarmPendingIntent):**
```kotlin
Intent(ctx, TaskEndAlarmReceiver::class.java).apply {
    action = "com.tbtechs.focusflow.TASK_END_ALARM"
    data = Uri.parse("focusflow-internal://task-end/${Uri.encode(taskId)}")
    `package` = ctx.packageName
    putExtra(EXTRA_TASK_ID, taskId)
    putExtra(EXTRA_TRIGGER_AT_MS, triggerAtMs)
}
// requestCode = 0 — data URI provides uniqueness
PendingIntent.getBroadcast(ctx, 0, intent, FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT)
```

**showPi for setAlarmClock (AlarmRepository.scheduleAlarm):**
```kotlin
Intent(ctx, TaskAlarmActivity::class.java).apply {
    action = "com.tbtechs.focusflow.SHOW_TASK_ALARM"
    data = Uri.parse("focusflow-internal://task-end/${Uri.encode(taskId)}")
    putExtra(EXTRA_TASK_ID, taskId)
}
// requestCode = 0
PendingIntent.getActivity(ctx, 0, intent, FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT)
```

**FSI PendingIntent (ForegroundTaskService.postTaskEndAlarmNotification):**
```kotlin
Intent(ctx, TaskAlarmActivity::class.java).apply {
    action = "com.tbtechs.focusflow.SHOW_TASK_ALARM"
    data = Uri.parse("focusflow-internal://task-end/${Uri.encode(taskId)}")
    putExtra(EXTRA_TASK_ID, taskId)
    putExtra(EXTRA_TASK_NAME, taskName)
    putExtra(EXTRA_END_MS, endMs)
}
// requestCode = 0
PendingIntent.getActivity(ctx, 0, intent, FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT)
```

Remove `requestCodeFor()` hash helper for task-end alarms. Audit all callers; if any
other alarm category (reminder chain) uses it without collision risk, retain it there only.

**Notification identity (per task):**
```kotlin
val notifTag = "task-end:$taskId"
val notifId  = TASK_END_NOTIF_NAMESPACE_ID   // one fixed Int in this namespace

nm.notify(notifTag, notifId, notification)
// Dismiss:
nm.cancel(notifTag, notifId)
```

**`AlarmRepository.dismissAlarm(taskId)`** and
**`TaskAlarmActivity.stopAlarmAndFinish()`** both currently call
`nm.cancel(TASK_ALARM_NOTIF_ID)` with the global fixed ID. After the identity fix,
both must use `nm.cancel("task-end:$taskId", TASK_END_NOTIF_NAMESPACE_ID)`.

### 8.2 Task-end alarm scheduling

**Task-end alarms are an exact-timing product commitment. No inexact fallback.**

```
API 31+: check canScheduleExactAlarms()
    tier 1: setAlarmClock()                   — Doze-immune; requires SCHEDULE_EXACT_ALARM on 31+
    tier 2: setExactAndAllowWhileIdle()        — fires within ~10 s even in Doze
    tier 3: DEFERRED_EXACT_UNAVAILABLE         — mark in registry; surface prompt; retry when capability returns
                                              [setAndAllowWhileIdle() is NOT used for task-end alarms]
each tier: catch SecurityException; try next tier
```

`AlarmScheduleResult`:
```kotlin
sealed class AlarmScheduleResult {
    object Scheduled                : AlarmScheduleResult()
    object PastTrigger              : AlarmScheduleResult()  // triggerAtMs ≤ now; no-op
    object DeferredExactUnavailable : AlarmScheduleResult()
    data class Failed(val cause: Throwable) : AlarmScheduleResult()
}
```

When `DeferredExactUnavailable`:
- Persist the task row as normal (persistent truth is intact).
- Record `DEFERRED_EXACT_UNAVAILABLE` in the alarm registry for this taskId.
- Surface a diagnostics entry and a one-time capability prompt ("FocusFlow needs
  exact alarm permission to alert you when tasks end. Tap to grant.").
- `reconcile()` re-attempts on permission grant (§8.7 trigger: exact-alarm permission change).

Record active tier in `AlarmCapabilitySnapshot`. Verify exact permission matrix
against current Android docs before ship.

**Advisory reminder notifications** (§8.4) may use `setAndAllowWhileIdle()` as the
third tier because reminder timing is approximate by nature ("5 minutes before" does
not need to be delivered within a second).

**Past trigger → no-op (KT-5 fix):**
```kotlin
if (triggerAtMs <= System.currentTimeMillis()) return AlarmScheduleResult.PastTrigger
```
Never fire an alarm for a past time. A restore or reconcile that encounters a past
trigger falls through to the overdue sweep (§8.6), not to an immediate alarm.

**Alarm registry (in `focusflow_alarm_state` prefs — separate from enforcement prefs):**
```kotlin
// Key: "alarm_registry" — stored as Set<String> of taskIds
// Rule: add taskId BEFORE scheduling; remove AFTER cancelling.
// The registry is a superset of possibly-registered tasks — safe direction after crash.
// Always commit() not apply().
```

**Horizon:** arm at most the earliest 100 future task-end alarms. After the receiver
fires, trigger a reconcile to extend the window.

### 8.3 TaskEndAlarmReceiver

Must use `goAsync()` with an 8-second budget.

**Fire-time validation (Room read):**
```
task exists
AND status ∈ {scheduled, active}
AND now ≥ endMs − 5000
```
On validation failure → suppress notification; cancel owned PendingIntent; return.
On Room read timeout or exception → **fail open** (post the alarm; mark `unvalidated`
in diagnostics). A spurious alarm is better than a missed one.

**Dedupe ledger:** prefs key `alarm_posted` as a map `taskId → endMs`, pruned after 48 h.
If the same `(taskId, endMs)` was already posted → skip.

After posting, trigger `reconcile("fired")` to extend the 100-alarm horizon.

### 8.4 Reminder notifications (new work — KT-3)

Derived from task `(id, startTime, endTime)` only. **Not from `Task.reminders[]`.**

**ReminderPlanner (pure function):**
Tasks + `now` → ordered `Slot(id, taskId, kind, triggerMs, text)`.
Slot IDs use the format in §TS.8. Budget: 450 slots total (earliest first).
Tasks with status in `{completed, skipped, overdue}` produce no slots.
Reuse the slot-generation logic already in `NotificationRepository`.

**ReminderChainScheduler:**
Arms **one** AlarmManager alarm with fixed identity
`data = Uri.parse("focusflow-internal://reminder-chain")`.
The alarm is set for the earliest slot with `triggerMs > now`.
Avoids per-app alarm cap; no orphan problem (single PendingIntent identity).

**ReminderReceiver (on fire, goAsync):**
1. Recompute plan from DB with real current time.
2. Post every slot with `triggerMs ≤ now + 1000` not already in the ledger.
3. Record them in the ledger (prefs map `slotId → triggerMs`; pruned after 48 h).
4. Re-arm the chain for the next slot.

**Notification identity:** `(tag = slotId, id = 1)`.
Cancel by the same pair.

**Gate:** controlled by the device-local `taskRemindersEnabled` preference (PD-11;
not on the wire). If deferred, `reconcile()` calls `ReminderChainScheduler.rearm(null)`.

### 8.5 reconcile(reason: String)

```
serialize with a Mutex; wait for gate OPEN (or run inside RECONCILING)
now = clock.now()
overdueSweep(now)                            // §8.6 — persists 'overdue'; commit before alarms
desired = tasks with status in {scheduled, active} and endTime > now (earliest 100)
registry = load alarm_registry
for id in registry − desired.ids: cancelTaskEnd(id)
registry.addAll(desired.ids); commit()
for t in desired: scheduleTaskEnd(t)         // idempotent replace; ladder per §8.2
registry.setExact(desired.ids); commit()
ReminderChainScheduler.rearm(ReminderPlanner.plan(now))
cancel reminder notifications for tasks no longer in {scheduled, active}
record AlarmCapabilitySnapshot (§8.7)
```

### 8.6 Overdue sweep

Run before reconcile. Mark tasks with status `scheduled` or `active` whose `endTime`
has passed as `overdue`; persist with one `editor.commit()`. This is distinct from
import: import downgrade (`scheduled` → `skipped`) is for closed history; sweep
(`scheduled/active` → `overdue`) is for locally unresolved tasks.

`[KT]` Search for any existing `"overdue"` writer in the data and ViewModel layers
(`grep -rn '"overdue"' java/`). Implement the sweep if absent.

### 8.7 Reconcile triggers

| Trigger | Mechanism |
|---|---|
| App start / `Activity.onStart` | `reconcile("start")` |
| Device boot / unlock / app update | Extend existing `[KT]` `BootReceiver` (already `directBootAware`; already handles `BOOT_COMPLETED`, `USER_UNLOCKED`, `MY_PACKAGE_REPLACED`). Must enqueue unique WorkManager `reconcile` work. Must not touch Room or credential-encrypted prefs until `UserManager.isUserUnlocked`. |
| Exact-alarm permission change | `AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` — new manifest receiver entry; none exists today `[KT]`. |
| `ACTION_TIME_CHANGED` | `reconcile` (task times are absolute instants; TZ changes need no recalculation). |
| Notification permission regained | `reconcile` on next `onResume`. |
| Task create / edit / delete / complete / skip / extend | Existing call sites → `reconcile` for that task (or route through the registry-aware `AlarmRepository`). |
| Restore | Phase RECONCILING. |
| After task-end alarm fires | `reconcile("fired")` via receiver. |

### 8.8 AlarmCapabilitySnapshot

Recorded at schedule time and at fire time; surfaced in diagnostics:
```
postNotificationsGranted, channelImportance, channelIsBlocked,
canUseFullScreenIntent (API 34+), canDrawOverlays,
canScheduleExactAlarms (API 31+), batteryOptimizationExempt,
targetSdk, deviceInteractive, keyguardLocked, alarmTierUsed
```

---

## 9. Alarm presentation (reported symptom: notification appears; alarm UI only after tap)

### 9.1 Source-proven Kotlin runtime path `[KT]`

```
AlarmManager → TaskEndAlarmReceiver.onReceive()
    (no goAsync today — this is KT-3 adjacent; must add goAsync)
    → ForegroundTaskService.postTaskEndAlarmNotification()
        → ensure channel 'task_alarm' (IMPORTANCE_HIGH)
           [two channel-creation sites with different attributes exist — first creation wins]
        → global FSI PendingIntent (request code 7) — KT-4
        → nm.notify(TASK_ALARM_NOTIF_ID = 9101, ...) — KT-4
            .setFullScreenIntent(pi, true)
            .PRIORITY_MAX, CATEGORY_ALARM, ongoing, auto-cancel
        → then startActivity(activityIntent) inside try/catch (swallows all exceptions)
```

Manifest `[KT]`: declares `SCHEDULE_EXACT_ALARM` (not `USE_EXACT_ALARM`),
`USE_FULL_SCREEN_INTENT`, `POST_NOTIFICATIONS`, `SYSTEM_ALERT_WINDOW`.
`TaskAlarmActivity`: `singleInstance`, `noHistory`, `showWhenLocked`, `turnScreenOn`,
`excludeFromRecents`, `taskAffinity=""`.

### 9.2 Failure-point analysis

| Stage | What can fail | Class | Discriminating test |
|---|---|---|---|
| Trigger | Alarm tier downgrade → late delivery | source + `[DEVICE]` | User says notification appears on time → trigger is working |
| Receiver | No `goAsync`; process cold-start | source | Add timestamps (§9.4) |
| Two channel definitions | **First-creation-wins; channel is sticky.** Different names/attributes between sites | `[KT]` source-proven | `adb shell dumpsys notification --noredact` channel block |
| **FSI capability** | API 34+: `USE_FULL_SCREEN_INTENT` granted at install by default; Play can revoke for non-alarm apps; user can revoke. **No code today checks it.** | Android-constrained | `NotificationManager.canUseFullScreenIntent()` |
| **FSI ≠ guaranteed launch** | While the user is actively using the device, Android may present FSI as heads-up and not launch the activity. This is expected behaviour; a tap then opens the activity. | Android-constrained | Test specifically with screen **off**, **locked**, and **in active use** |
| Direct `startActivity` fallback | `[DOC]` Background activity launches are blocked without an exemption (`SYSTEM_ALERT_WINDOW`, visible window, or system-sent PI such as a notification tap). A blocked launch does not throw; current code swallows exceptions. | Android-constrained | `adb logcat \| grep "background activity launch"` |
| PendingIntent identity | Global request code 7 with `UPDATE_CURRENT` (KT-4) — being fixed | source | After fix: two-alarm cross-talk test (§11.8) |

**Why "after tap" is plausible without a Kotlin bug:** a tap is a user-initiated, system-sent PendingIntent, always permitted to start an activity. The same code path with FSI downgraded or blocked would show exactly this symptom.

**TS comparison:** the TS archive contains no native alarm source. TS runtime behaviour
is source-unproven. Do not assume TS alarm fronting is better or worse without device
testing on the same device/package.

### 9.3 Required changes

1. **Fix global PendingIntent identity** — per §8.1.
2. **Consolidate channel definitions** — ensure only one `task_alarm` channel definition
   site with authoritative attributes. Remove or unify the second site.
3. **Capability probe** — `AlarmCapabilitySnapshot` at schedule and fire time (§8.8).
4. **Settings affordances** — a diagnostics row showing the snapshot; when FSI is off
   on API 34+, a one-time prompt (PD-10) that opens
   `Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`.
5. **Keep `startActivity` fallback only when `canDrawOverlays()` is true.** Never
   rely on it. Log the result instead of swallowing exceptions.
6. **API 35+ consideration:** creating the activity PendingIntent may need
   `ActivityOptions.makeBasic().setPendingIntentCreatorBackgroundActivityStartMode(MODE_BACKGROUND_ACTIVITY_START_ALLOWED)`.
   Verify the exact API and SDK requirement against current docs before implementing `[DOC]`.
7. **`goAsync()` in receiver** — add now (§8.3).

### 9.4 Runtime instrumentation (add before device-matrix testing)

```
timestamp: receiver.onReceive()
timestamp: nm.notify() call
timestamp: TaskAlarmActivity.onCreate()
timestamp: TaskAlarmActivity.onResume()
bool: TaskAlarmActivity became visible (WindowManager callback or onWindowFocusChanged)
```

`startActivity()` returning without exception is NOT proof of user-visible success.

### 9.5 Device-matrix test (required before sign-off — §11.9)

Test all combinations of: screen state (off-locked, on-locked, on-unlocked-in-use);
notification permission (granted, denied); FSI capability (granted, revoked on API 34+);
exact-alarm permission (granted, denied). Record `AlarmCapabilitySnapshot` for each run.

---

## 10. Export

```
build AppSettings portable payload:
    include all fields in §3.1 APPLY list
    use TS V1 wire key names (not Kotlin internal names — KT-8)
    include greyoutSchedule = [user_greyout_windows] + [derived windows from recurringBlockSchedules]
    include recurringBlockSchedules verbatim under key 'recurringBlockSchedules'
    omit all 21 live-state keys (§TS.2)
    omit onboardingComplete, privacyAccepted
    include focusMirrorVpnEnabled explicitly (even if false)
    omit launcherWallpaperUri / overlayWallpaper

wrap in V1 envelope (§TS.1):
    kind: "FocusFlowBackupV1"
    version: 1
    exportedAt: CanonicalTimestamp.format(now)
    settings: portablePayload
    tasks: all task rows, timestamps normalized via CanonicalTimestamp
    presetSections: inventory only (never applied on import)
    summary: counts

write via ACTION_CREATE_DOCUMENT; success = write + close succeeded
filename: focusflow-YYYY-MM-DDTHH-mm-ss.focusflow
```

**Wire key corrections required in `BackupCoordinator.toBackupJson()`:**

```kotlin
// Change (Kotlin internal names → TS V1 wire names):
"defaultDuration"         ← was "defaultDurationMinutes"
"pomodoroDuration"        ← was "pomodoroWorkMinutes"
"pomodoroBreak"           ← was "pomodoroBreakMinutes"
"recurringBlockSchedules" ← was "greyoutSchedule" (and field names inside must use startMin/endMin/days)

// Remove from export (PRESERVE_LOCAL — must not appear in file):
// alwaysOnEnforcementEnabled, vpnBlockEnabled, systemGuardEnabled, pomodoroEnabled
// (they are in PORTABLE_OMITTED_KEYS but should never be added in the first place)

// Add missing APPLY fields:
"darkMode", "keepFocusActiveUntilTaskEnd", "autoRescheduleEnabled",
"launcherTheme", "focusToolPackages", "launcherHiddenPackages",
"launcherDockPackages", "launcherClockStyle",
"launcherBlockUninstall", "launcherLockDuringStandalone",
"alwaysOnVpnPackages", "overlayQuotes",
"allowedAppPresets", "blockPresets"
```

---

## 11. Test suite

### 11.1 Compatibility

```
TS V1 fixture (min-fields)      → Kotlin import → RestoreResult matches spec
TS V1 fixture (full-fields)     → Kotlin import → all APPLY keys applied
TS V1 old-export (with live keys) → live keys NOT applied
Kotlin export → TS import        → round-trip parity where supported
Kotlin export → Kotlin import → export → normalised semantic compare (not byte-for-byte)
```

### 11.2 Parser / validation

```
File at MAX_FILE_BYTES − 1       → accept
File at MAX_FILE_BYTES           → accept
File at MAX_FILE_BYTES + 1       → reject BEFORE any mutation
Duplicate JSON key               → reject BEFORE any mutation
Duplicate task ID in file        → reject BEFORE any mutation
Invalid UTF-8                    → reject
version: 2                       → reject
Missing kind                     → reject
kind: "wrong"                    → reject
Missing settings                 → reject
Missing tasks                    → reject
Unknown top-level field          → accept (ignored)
Unknown task field               → accept (task preserved)
Unknown enum in status           → that task invalid; rest continue
Invalid timestamp                → that task invalid; rest continue
Past scheduled task              → stored as skipped, updatedAt = planNow
active status task, future       → stored as scheduled
active status task, past         → stored as skipped
```

### 11.3 Settings matrix

```
All APPLY keys present → each applied to correct Kotlin target
All 21 live-state keys present in old export → none applied
focusMirrorVpnEnabled: false in backup, local = true → result = false
focusMirrorVpnEnabled: absent from backup, local = true → result = true
protectionMode: "iron" → SetupPersistenceManager updated
protectionMode: "unknown" → rejected before mutation
weekStartDay: 5 in backup → NOT applied (local value preserved)
overlayWallpaper in backup → NOT applied (local value preserved)
```

### 11.4 Greyout windows

```
greyoutSchedule with no-scheduleId windows → stored in user_greyout_windows
greyoutSchedule with scheduleId windows → discarded
recurringBlockSchedules imported → GreyoutRepository rebuilt correctly
Import recurringBlockSchedules + user windows → enforcement = combined result
Round-trip: user windows survive export → import
```

### 11.5 Task merge/replace

```
ID absent locally → inserted
ID present, identical projection → identicalDuplicate; import continues
ID present, divergent projection → PlanFailure.IdConflict; ZERO mutation; journal never written;
                                   gate reopened; conflict screen shown with task titles
Multiple divergent tasks → all included in IdConflict list; all zero mutation
Duplicate IDs inside backup → whole file rejected (validation, before plan generation)
Replace, no active session → no conflict check needed (existing tasks deleted first);
                             delete all + insert in one transaction
Replace, active session → refused; zero mutation
Replace + PD-8 weakening → refused; zero mutation without PIN
```

### 11.5b PendingImport durability

```
File read → validated → PendingImport file written → process killed before confirm
    → on restart: PendingImport detected → confirm screen re-presented
    → user confirms → journal written → restore proceeds normally

File read → validated → PendingImport file written → process killed before confirm
    → on restart: PendingImport detected → user cancels → file deleted → Idle

File read → PendingImport written → new file opened → PendingImport overwritten
    → confirm screen shows new file

PendingImport exists on disk + journal also exists (crash mid-restore)
    → journal takes precedence → PendingImport deleted → recovery path

Cancel from confirm screen → PendingImport file deleted
Same file re-opened after cancel → works (no stale handled-URI set)
```

### 11.6 Crash recovery

```
Process death at every crash-point in §6.6 table → replays correctly
Settings partial-apply (one file committed, one not) → idempotent replay applies remaining
Writer attempts during RECOVERING → suspended until gate OPEN
```

### 11.7 Scheduler

```
Restore future task, exact permission granted → alarm scheduled at tier 1 or 2
Restore future task, exact permission absent → DEFERRED_EXACT_UNAVAILABLE in registry; no OS alarm; diagnostics surfaced
DEFERRED alarm + permission granted → reconcile re-arms at tier 1; registry updated to APPLIED
Restore past scheduled task → status=skipped; NO alarm
Restore completed task → NO alarm
Restore multiple future tasks → each has alarm with distinct URI identity; no cross-talk
Past trigger at scheduleAlarm() call time → AlarmScheduleResult.PastTrigger; no OS call
Edit task end time → old alarm cancelled; new alarm scheduled; registry updated
Delete task → alarm cancelled; registry cleaned
Reboot → BootReceiver enqueues reconcile; alarms re-armed after USER_UNLOCKED
Exact-alarm permission revoked → existing OS alarms fired or cancelled by Android;
                                 registry retains DESIRED entries; reconcile re-marks DEFERRED; diagnostics shown
Exact-alarm permission granted → reconcile re-arms all DEFERRED entries at tier 1
ReminderChainScheduler → chain alarm re-armed after each fire (uses setAndAllowWhileIdle as tier 3)
Reminder slot budget 450 → 451st slot dropped
AlarmCapabilitySnapshot recorded at schedule time and fire time
```

### 11.8 Multi-alarm cross-talk (KT-4 regression fix)

```
Task A ends at T+30s; Task B ends at T+45s
A receiver fires → A notification has A's data; A activity shows A's name
B receiver fires → B notification has B's data; B activity shows B's name
Dismiss A → A notification cancelled; B notification unaffected
Dismiss B → B notification cancelled
onNewIntent for A while B is showing → activity shows B then A's correct task
```

### 11.9 Alarm-fronting device matrix

As specified in §9.5. Required before PR merge. Results attached to the PR.

### 11.10 Legacy migration

```
Fresh install (no settings table) → migration marks complete; no error
TS DB with key 'app_settings' → data migrated to focusday_prefs
Migration on a device where buggy migration already ran → data migrated correctly (upgrade path, §B.1)
app_settings row absent despite table existing → no marker written; retried next launch
```

---

## 12. Work order (milestones are independently shippable)

| M | Scope | Why first |
|---|---|---|
| **M0** | Security and routing hotfix: (1) never apply 21 live-state keys in mergePortableBackup(); (2) Routes.fromPath whitelist; (3) remove BROWSABLE; (4) introduce CanonicalTimestamp and use it at all task writes. | Closes KT-9, KT-10, KT-12 now, before any backup code runs. |
| **M1** | Wire model, streaming parser, validator, limits, `TsSettingsAdapter` (single adapter used by import, export, and legacy migration). | Everything else depends on one correct schema. |
| **M2** | Export (§10) + round-trip tests. | Verifiable independently. |
| **M3** | Gate, journal, plan, phases, recovery, RestoreCoordinator, IncomingBackupDispatcher, manifest dispatch, confirm screen. | Core restore feature. |
| **M4** | AlarmRepository rewrite (per-task identity, registry, ladder, no-past-trigger-fire), reconciler, BootReceiver extension, overdue sweep, exact-alarm receiver. | Required before restore can claim derived state is rebuilt. |
| **M5** | ReminderChainScheduler + ReminderPlanner + ReminderReceiver. | Delivers reminder objective. |
| **M6** | Alarm presentation fixes (§9.3), diagnostics, device-matrix test. | Closes the reported symptom. |

**Pre-existing defects to fix while touching those files:**
- Legacy migration three-sub-bug (§B.1) — fix in M1 (shares `TsSettingsAdapter`).
- `TaskViewModel`/`TaskRepository` timestamp writes → use `CanonicalTimestamp` — M0.
- `ForegroundTaskService` duplicate channel definition → consolidate in M4.

---

## 13. Reviewer sign-off checklist

Production coding may not begin until all items are checked.

```
Security
[ ] 21 live-state keys are NEVER applied regardless of file content (old or new export)
[ ] Routes.fromPath uses an explicit external-linkable allowlist
[ ] BROWSABLE removed from both content intent filters
[ ] PD-8 PIN check implemented for weakening imports
[ ] Backup content (task titles, settings values) is never logged or persisted except in journal

Wire adapter
[ ] toBackupJson() emits TS V1 wire names (defaultDuration, pomodoroDuration, pomodoroBreak,
    recurringBlockSchedules, greyoutSchedule as combined windows, all missing APPLY fields added)
[ ] mergePortableBackup() reads TS V1 wire names for all APPLY fields
[ ] 21 live-state keys absent from mergePortableBackup() read path
[ ] focusMirrorVpnEnabled uses presence-check (not ||) semantics
[ ] greyoutSchedule: user-only windows → user_greyout_windows; recurringBlockSchedules → own key
[ ] launcherDockPackages and launcherClockStyle are applied
[ ] weekStartDay and defaultReminderOffsets are NOT applied (ACCEPT_AND_IGNORE)
[ ] wallpaper paths are UNRESOLVED (local value kept; count recorded; user notified) — not applied, not silently ignored

Data integrity
[ ] Duplicate JSON keys → file-level reject (before plan generation)
[ ] Duplicate task IDs in file → file-level reject (before plan generation)
[ ] All 5 task statuses handled (including active, overdue)
[ ] Past scheduled/active task → skipped, updatedAt = planNow
[ ] active task status imported → treated as scheduled
[ ] Same-ID identical projection → identicalDuplicate; import continues
[ ] Same-ID divergent projection (Merge) → PlanFailure.IdConflict; ZERO mutation; journal never written; conflict screen shown
[ ] Multiple divergent tasks → all in IdConflict list; all zero mutation
[ ] Replace bypasses conflict check (existing tasks deleted first in same transaction)
[ ] Replace blocks on active session
[ ] History tables never touched by Replace
[ ] protectionMode valid-enum-only gate active before mutation
[ ] External resource fields (wallpaper) → UNRESOLVED; local value preserved; count in externalResourcesUnresolved; user notified

Timestamps
[ ] CanonicalTimestamp formatter introduced
[ ] All four task timestamp fields normalized at import
[ ] All Kotlin task-create/edit paths use CanonicalTimestamp
[ ] No DAO path compares ISO strings lexicographically for ordering

Alarm identity
[ ] ALL THREE task-end PendingIntents use data URI identity (broadcast PI, showPi, FSI PI)
[ ] requestCode = 0 for all three; requestCodeFor() hash removed/audited
[ ] Notification identity is per-task (tag = "task-end:<taskId>", id = NAMESPACE_ID)
[ ] dismissAlarm() uses per-task (tag, id) cancel
[ ] TaskAlarmActivity.stopAlarmAndFinish() uses per-task (tag, id) cancel
[ ] Past trigger at scheduleAlarm() → immediate no-op
[ ] Multi-alarm cross-talk test (§11.8) passes

Scheduler
[ ] Alarm registry in separate focusflow_alarm_state prefs file
[ ] Registry add before schedule; remove after cancel; always commit()
[ ] TaskEndAlarmReceiver uses goAsync() with 8-second budget
[ ] Fire-time validation: task exists, status ∈ {scheduled, active}, timing within 5 s
[ ] Fail-open on Room read timeout (post alarm; mark unvalidated)
[ ] Dedupe ledger suppresses duplicate (taskId, endMs) posts
[ ] ReminderChainScheduler has single fixed-identity alarm
[ ] Reminder slots derived from task times only (not from Task.reminders[])
[ ] Budget 450 slots enforced
[ ] BootReceiver enqueues reconcile work; does not touch Room before USER_UNLOCKED
[ ] Exact-alarm permission-change manifest receiver added
[ ] Overdue sweep runs before reconcile; persists changes

Restore engine
[ ] RestoreGate is process-wide singleton; first object in AppModule
[ ] All conflicting persistent writers call gate.write()
[ ] Startup recovery precedes normal writers
[ ] PendingImport file written to noBackupFilesDir/pending-import/ with AtomicFile BEFORE confirm screen
[ ] On startup: PendingImport only (no journal) → confirm screen re-presented
[ ] On startup: journal exists → journal takes precedence; PendingImport deleted
[ ] Journal written atomically (AtomicFile); never in backed-up directory
[ ] IdConflict check runs before journal is written; gate reopened on conflict
[ ] All journal crash-points tested (§6.6 crash-point table including PendingImport scenarios)
[ ] Settings applied via single editor.commit() per prefs file (no apply())
[ ] Settings syncs (greyout rebuild, enforcement notify) run in RECONCILING not SETTINGS phase
[ ] RECONCILING uses real current time (not planNowMillis)
[ ] Retry-3 then Retry/Discard UI on phase failure

External file handling
[ ] Cold ACTION_VIEW works
[ ] Warm onNewIntent works
[ ] Cancel + re-open same file works (no stale URI set)
[ ] Latest file wins while staging (PendingImport file overwritten; confirm screen re-rendered)
[ ] Restore running → second import shows Busy
[ ] file:// URI → error message only
[ ] PD-9: file staged before onboarding → queued until onboarding complete

Alarm DEFERRED policy
[ ] setAndAllowWhileIdle() is NOT called for task-end alarms
[ ] DeferredExactUnavailable recorded in registry when tiers 1+2 both fail
[ ] Diagnostics surface the deferred state + one-time capability prompt
[ ] Reconcile re-arms all DEFERRED entries when exact-alarm permission is granted
[ ] Reminder notifications (not task-end) may use setAndAllowWhileIdle() as tier 3

Legacy migration
[ ] SQL key = 'app_settings'
[ ] Target prefs file = focusday_prefs (AppBlockerAccessibilityService.PREFS_NAME)
[ ] No marker written when row not found
[ ] Upgrade path for already-migrated devices correct (§B.1)

Alarm presentation
[ ] Single task_alarm channel definition site with authoritative attributes
[ ] AlarmCapabilitySnapshot recorded at schedule and fire time
[ ] Diagnostics row in UI shows capability snapshot
[ ] FSI-off prompt on API 34+ per PD-10
[ ] startActivity fallback gated on canDrawOverlays(); logs instead of swallowing exceptions
[ ] Device-matrix test (§9.5) executed and results attached to PR

Testing
[ ] Golden fixtures exist for all scenarios in §11 (old-export, full-fields, greyout, cross-talk)
[ ] targetSdk, minSdk, compileSdk recorded in the PR
```

---

## Appendix A — Three-way truth table

| Topic | Observed TS | Intended | Kotlin now | Decision |
|---|---|---|---|---|
| Same-ID collision (identical) | skip | skip | INSERT OR IGNORE | **Skip; identicalDuplicate count** |
| Same-ID collision (divergent) | skip (TS bug; silent data loss) | **Abort before mutation** | v12: abort | **PlanFailure.IdConflict; ZERO mutation; journal never written; conflict screen with task titles. Not: "TS does it this way"; the v12 abort contract is correct because a partial restore with silent drops is more dangerous than a refused import with a clear resolution path.** |
| Live toggles in old files | spread-merged; overwrote local | never portable (exporter strips them) | stripped on export; APPLIED on import (KT-9) | **Never apply any of 21 live-state keys — security defect** |
| Duplicate IDs inside file | DB silently ignores (TS counting bug) | bad file | not detected | **Reject whole file before any mutation** |
| Past scheduled task | stored skipped | closed history | not implemented | **skipped; updatedAt = planNow** |
| active status imported | no TS writer found | runtime state of source device | n/a | **treat as scheduled** |
| Task.reminders[] | always [] in practice | derived from task times | typed, preserved | **preserve opaquely; never schedule from; no per-reminder registry** |
| defaultReminderOffsets | defined; never read | dead | absent | **accept/ignore on import** |
| pendingPresets | dead code | direct apply after confirm | absent | **do not implement** |
| PendingImport | in-memory, latest-wins | **Durable** | planned for disk | **Durable (AtomicFile to noBackupFilesDir). Reason: architecture is built around process-death recovery; memory-only is inconsistent with that principle.** |
| handledUris set | never cleared (TS bug) | cancel + reopen must work | n/a | **no handled-URI set; cancel deletes durable file; re-open works** |
| Wallpaper paths | exported and applied | external resource, not portable | n/a | **UNRESOLVED — local value preserved; explicitly recorded in RestorePlan and RestoreResult; user notified. Not silent ignore.** |
| weekStartDay | always 0; no UI writes it | unknown | derived from profile | **ACCEPT_AND_IGNORE on import — importing always-0 would force Sunday on all users** |
| setAndAllowWhileIdle() for task-end | TS fallback (possibly accidental) | exact timing is a product commitment | Kotlin ladder includes it | **REMOVED for task-end. DEFERRED_EXACT_UNAVAILABLE. Retry on permission grant. Reminder notifications may use it.** |
| FSI notification permission | skips all scheduling incl. alarm | likely accidental coupling | schedules alarm | **schedule alarm anyway (PD-6)** |
| Greyout vs recurring | user windows + derived in one list | recurring is authoritative | recurring under wrong wire key | **§5.3 split: no-scheduleId → user_greyout_windows; recurringBlockSchedules → own key** |
| focusMirrorVpnEnabled | backup ?? local (nullish) | portable; backup wins when present | OR (wrong) | **presence-check: imported value wins when key is present, including false** |
| Alarm identity | per task (native; unverifiable) | per task | global code 7 / id 9101 | **per-task data URI + (tag, id) for all three PendingIntents** |
| Timestamps | toISOString() .sssZ | canonical | Instant.toString() (variable) | **canonicalize everywhere via CanonicalTimestamp** |
| Immediate past-trigger alarm | no evidence | never stale fire | fires immediately | **no-op when triggerAtMs ≤ now (AlarmScheduleResult.PastTrigger)** |

---

## Appendix B — New Kotlin model requirements

### B.1 Legacy settings migration fix

Three bugs in `FocusFlowDatabase.migrateSettingsBlobToSharedPrefs()`:

**Bug 1** — queries `'appSettings'`; correct TS key is `'app_settings'`.  
**Bug 2** — writes to `FocusFlowPrefs`; SettingsRepository reads `focusday_prefs`.  
**Bug 3** — writes the `_settings_blob_migrated` marker even when no row was found
(due to Bug 1), permanently silencing the migration on every existing device.

```kotlin
fun migrateSettingsBlobToSharedPrefs(context: Context, db: FocusFlowDatabase) {
    val target = context.getSharedPreferences(
        AppBlockerAccessibilityService.PREFS_NAME,  // "focusday_prefs"
        Context.MODE_PRIVATE,
    )
    // Guard: already migrated to correct namespace
    if (target.contains("_settings_blob_migrated")) return
    // (Legacy marker in FocusFlowPrefs does NOT prevent re-run — intentional upgrade path)

    val tableExists = db.openHelper.readableDatabase.query(
        "SELECT name FROM sqlite_master WHERE type='table' AND name='settings'"
    ).use { it.moveToFirst() }

    if (!tableExists) {
        target.edit().putBoolean("_settings_blob_migrated", true).commit()
        return
    }

    // Bug 1 fix: correct key
    val cursor = db.openHelper.readableDatabase.query(
        "SELECT value FROM settings WHERE key = 'app_settings' LIMIT 1"
    )
    val json = cursor.use { if (it.moveToFirst()) it.getString(0) else null }

    if (json == null) {
        // Table exists but no row found — do NOT write marker; allow retry next launch
        Log.w(TAG, "migrateSettings: 'app_settings' row not found; will retry")
        return   // Bug 3 fix: no marker write
    }

    // Parse json and route through TsSettingsAdapter to get the normalized map
    // Write each field putIfAbsent to avoid overwriting any values the user
    // already set through the Kotlin app
    val editor = target.edit()
    val mapped = TsSettingsAdapter.normalizeForLegacyMigration(JSONObject(json))
    for ((key, value) in mapped) {
        if (!target.contains(key)) editor.putString(key, value)
    }
    editor.putBoolean("_settings_blob_migrated", true)
    editor.commit()   // Bug 2 fix: writes to focusday_prefs
    Log.i(TAG, "Settings blob migrated from SQLite to focusday_prefs")
}
```

**Upgrade path for already-migrated devices:** Old marker is in `FocusFlowPrefs` only.
New code checks `focusday_prefs` — marker absent → migration re-runs with correct key
and correct namespace. `putIfAbsent` semantics preserve any values the user already
configured in the Kotlin app. The stale data in `FocusFlowPrefs` is harmless
(SettingsRepository never reads it).

### B.2 alwaysOnVpnPackages

```kotlin
// Kotlin AppSettings addition:
val alwaysOnVpnPackages: List<String> = emptyList()

// SharedPreferences key: "always_on_vpn_packages"
// SettingsRepository setter: fun setAlwaysOnVpnPackages(packages: List<String>)
// V1 wire key: "alwaysOnVpnPackages"
```

VERIFY whether `AppBlockerAccessibilityService` needs to read this key for VPN
enforcement (analogous to `always_block_packages`).

### B.3 blockPresets

```kotlin
data class BlockPreset(val id: String, val name: String, val packages: List<String>)

// Kotlin AppSettings addition:
val blockPresets: List<BlockPreset> = emptyList()

// SharedPreferences key: "block_presets"
// V1 wire key: "blockPresets"
// NEVER write into the allowed_app_presets store
```

### B.4 overlayQuotes

```kotlin
val overlayQuotes: List<String> = emptyList()
// SharedPreferences key: "overlay_quotes"
// V1 wire key: "overlayQuotes"
```

### B.5 user_greyout_windows

```
SharedPreferences key: "user_greyout_windows"
Content: JSON array of GreyoutWindow objects without scheduleId
Purpose: stores user-created direct block windows (not derived from recurring schedules)
Updated by: backup restore (§5.3); direct user editing of greyout windows (if the UI supports it)
GreyoutRepository combines this array + derived windows from recurringBlockSchedules
```

---

## Appendix C — What the agent must verify before ship (not in the archive)

```
targetSdk, minSdk, compileSdk — needed for FSI/exact-alarm/BAL behaviour
SettingsRepository setter side effects (which syncFromStore() calls exist)
VpnRepository and how alwaysOnVpnPackages is used at enforcement time
Whether Kotlin has stores for darkMode, protectionMode, keepFocusActiveUntilTaskEnd,
    autoRescheduleEnabled, overlayQuotes (or whether new SharedPrefs keys are needed)
Where Kotlin derives greyout windows from recurring schedules at enforcement time
Whether a Kotlin overdue sweep exists (grep -rn '"overdue"' java/)
Remaining attributes of the first 'task_alarm' channel definition (full comparison needed)
Android platform claims tagged [KNOWLEDGE]:
    alarm replacement semantics on equal PendingIntent
    500-alarm per-app cap
    exact-alarm permission tiers (which permission, which API level)
    FSI heads-up-while-in-use behaviour
    API 35 PendingIntent creator background-activity-start-mode API name
    AppOp name for USE_FULL_SCREEN_INTENT
```
