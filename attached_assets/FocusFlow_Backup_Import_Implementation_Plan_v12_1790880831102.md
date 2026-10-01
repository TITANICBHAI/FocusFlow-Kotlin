# FocusFlow Backup / Import + Alarm-Fronting Migration Plan v12

**Status:** Final planning contract candidate. Production implementation must not begin until the Decision Gate in §4 is accepted and the reviewer records the three final gate checks in §69B. The §2A source/data audit is complete as an evidence audit; all import/restore behavior below is the frozen target contract.

**Scope:** Compare the existing TS hybrid app with the pure Kotlin migration and implement/verify:

- `.focusflow` export
- in-app import
- Android File Manager / Drive `ACTION_VIEW` import
- portable feature data semantics across devices
- restore/recovery across Room + SharedPreferences
- task/reminder scheduler reconstruction
- task-end alarm/fronting behavior
- migration/regression tests

**Primary source archives**

- TS hybrid: `focusflow.zip`
- Kotlin migration: `kotlin.zip`
- Reviewer contract history: v8 → v11 reviewer feedback + plans

**Research date:** 2026-10-01

**Core principle**

```text
Observed TS behavior
        +
Intended FocusFlow semantics
        +
Current Kotlin behavior
        ↓
Explicit decision
        ↓
Testable contract
```

The implementation agent must not treat the current TS implementation, the current Kotlin implementation, or Android platform behavior as automatically equivalent to the intended product behavior.

---

# 0. Executive conclusion

The migrated Kotlin app currently has the right broad product components but is **not behaviorally complete** for this feature set.

The source audit found these concrete gaps:

```text
1. External ACTION_VIEW `.focusflow` intent is declared in the manifest,
   but MainActivity does not route the incoming content URI to import.

2. Backup restore currently persists imported tasks but supplies a no-op
   `scheduleTasks` callback.

3. NotificationRepository contains the task-reminder orchestration,
   but `NotificationScheduler` is only an interface in the supplied archive;
   no production implementation/wiring was found in AppModule.

4. TaskDao uses INSERT OR IGNORE, so raw DB conflict behavior cannot be the
   import conflict policy.

5. ForegroundTaskService currently uses one global full-screen PendingIntent
   requestCode and one global notification ID for task-end alarms.

6. The legacy settings migration writes to `FocusFlowPrefs`, while the
   SettingsRepository reads `focusday_prefs` through AppBlockerAccessibilityService.PREFS_NAME.
   This is a concrete migration-data correctness issue unless another bridge exists
   outside the supplied archive.

7. BackupCoordinator currently routes imported settings through
   SettingsViewModel.updateSettings(), which launches asynchronous field-by-field
   writes and triggers enforcement/broadcast side effects during restore.
   Restore must use a dedicated persistence path instead.

8. The TS and Kotlin backup schemas are not 1:1. Kotlin currently serializes a
   smaller/different set of settings and uses different field names in places.

9. The existing TS restore logic intentionally does not restore presetSections as
   live state; they are descriptive inventory data. This must remain explicit.

10. Android alarm/full-screen behavior is platform-conditional, so runtime testing
    must distinguish "alarm surfaced without a notification tap" from
    "TaskAlarmActivity necessarily launched".
```

**Implication:** the implementation should build a canonical import/restore pipeline and scheduler reconciliation layer rather than patching the current Kotlin callback-by-callback.

**Additional source-audit findings that are implementation blockers:**

```text
11. The legacy-settings migration has TWO concrete mismatches:
      TS settings-table key = `app_settings`
      Kotlin migration query = `appSettings`

    and:
      Kotlin migration writes to `FocusFlowPrefs`
      SettingsRepository reads `focusday_prefs`

    Therefore the supplied migration path cannot reliably move the TS settings
    blob into the Kotlin settings source of truth.

12. Kotlin currently contains two backup layers:
      data/repository/BackupManager.kt
      ui/backup/BackupCoordinator.kt
    with overlapping parse/restore responsibilities. The implementation must
    establish one canonical domain pipeline rather than extending both.

13. `NotificationScheduler` is only an interface in the supplied archive and is
    not wired in AppModule. `BackgroundFetchDependencies.install(...)` is also
    not installed in the supplied archive. Do not describe the generic
    NotificationRepository path as production-ready until a real scheduler is
    wired and exercised.

14. The current Kotlin NotificationRepository schedules a hard-coded set of
    task-relative notifications; it does not derive scheduled notifications from
    Task.reminders[]. Task.reminders is persistent data, but reminder-list fields
    and generated notification schedule identities are not currently the same
    abstraction in either source archive.

15. The TS `greyoutSchedule` and `recurringBlockSchedules` are distinct fields.
    Kotlin BackupCoordinator currently serializes recurring schedules into the
    `greyoutSchedule` wire key and restores that key back into recurring schedules.
    This is a wire/data-shape divergence and can lose user-created greyout windows
    and window-specific metadata.
```


---

# 1. Evidence and source-level findings

## 1.1 External `.focusflow` flow

### TS

`app/_layout.tsx` contains `FileImportHost`.

Observed path:

```text
Linking initial URL / URL event
        ↓
URI validation
        ↓
NativeFilePickerModule.readUri(uri)
        ↓
parseBackupJson()
        ↓
stageBackupImport(text)
        ↓
/import-confirm
```

TS accepts:

```text
content://...
file://.../*.focusflow
```

and removes a failed URI from its transient duplicate set when read/parse fails.

### Kotlin

`AndroidManifest.xml` declares `ACTION_VIEW` support for content URIs, including:

```text
application/octet-stream
*/*
```

but `MainActivity.routeFromIntent()` currently interprets application routes rather than turning an incoming document URI into an import request.

The in-app SAF picker already has a path to `ImportConfirmScreen`.

### Contract

Both entry points must converge here:

```text
URI
 ↓
bounded reader
 ↓
strict parser
 ↓
version migration
 ↓
NormalizedBackup
 ↓
PendingImport snapshot
 ↓
ImportConfirmScreen
```

---

## 1.2 Task reminder restore

The Kotlin `BackupCoordinator.import()` currently provides:

```kotlin
scheduleTasks = { /* Imported reminders remain persisted with their tasks. */ }
```

while `BackupManager.restoreFromJson()` collects scheduled imported tasks and calls that callback.

Therefore the current migration can produce:

```text
Room:
    future scheduled task exists

OS scheduler:
    no corresponding restored task-end alarm
    and possibly no generic reminder notifications
```

The TS implementation explicitly schedules future task reminders after importing persistent task rows.

---

## 1.3 Generic Kotlin notification scheduler is currently incomplete in the supplied archive

`NotificationRepository.scheduleTaskRemindersBatch()` already contains the intended orchestration:

```text
cancel old task reminder requests
↓
filter actionable tasks
↓
respect notification permission
↓
compute slot budget
↓
schedule task reminder requests
↓
schedule native task-end alarm
```

However, the supplied archive contains only the boundary:

```kotlin
interface NotificationScheduler
```

with methods such as:

```text
getScheduledNotifications()
schedule()
cancel()
cancelAll()
```

No production implementation/wiring was found in `AppModule`.

### Required source/build verification

The supplied archive contains the `NotificationScheduler` interface but no concrete implementation/wiring in `AppModule`. The implementation must verify the actual target repository/build once before coding. This is verification, not an architecture choice.

### Frozen target

If the concrete implementation is absent from the actual target repository/build, the approved target is:

```text
FocusFlowNotificationScheduler
```

Do not invent a different scheduler architecture during implementation.

### Target implementation

Create or wire the application-owned scheduler implementation:

```text
FocusFlowNotificationScheduler
    ↓
persistent ScheduledNotification registry
    ↓
AlarmManager-backed one-shot notification triggers
    ↓
NotificationScheduledReceiver
    ↓
NotificationManager
```

Task-end remains the dedicated alarm-clock/full-screen path.

This avoids adding a second backup-specific notification scheduler.

---

## 1.4 Task ID conflicts

`TaskDao.insertTask()` uses:

```text
OnConflictStrategy.IGNORE
```

This cannot define backup semantics.

Import must detect:

```text
same ID + equal semantic projection
    → duplicate / skip

same ID + divergent semantic projection
    → explicit conflict
```

The DB must not silently choose.

---

## 1.5 Task-end full-screen identity

Current Kotlin `ForegroundTaskService.postTaskEndAlarmNotification()` uses:

```text
PI_TASK_ALARM = 7
TASK_ALARM_NOTIF_ID = 9101
```

for all task-end alarm notifications.

Android PendingIntent matching does not use extras as identity. The same matching intent can therefore reuse the same PendingIntent, with `FLAG_UPDATE_CURRENT` updating extras.

This is unsafe for independently active task-end alarms.

### Required target

Use an identity that is distinct per task.

Preferred solution:

```text
FSI Intent:
    explicit TaskAlarmActivity
    action = ACTION_TASK_END
    data = focusflow://task-end/<URL-encoded restoredTaskId>
    extras = taskId, taskName, endMs
```

The `data` URI becomes part of Intent matching, so request-code hashing does not need to provide uniqueness.

For notifications, use:

```text
notificationTag = "task-end:" + restoredTaskId
notificationId = constant integer namespace
```

and cancel with the same `(tag, id)` pair.

Android documents notification identity as the `(tag, id)` pair, and PendingIntent identity is based on matching intent fields rather than extras.

---

## 1.6 Legacy settings migration namespace mismatch

The supplied Kotlin archive contains:

```text
FocusFlowDatabase.migrateSettingsBlobToSharedPrefs()
    → getSharedPreferences("FocusFlowPrefs", ...)
```

while:

```text
SettingsRepository
    → getSharedPreferences(AppBlockerAccessibilityService.PREFS_NAME, ...)
    → PREFS_NAME = "focusday_prefs"
```

This is a concrete potential migration bug.

The migration may write keys into one SharedPreferences file while runtime reads from another.

### Required fix

Use one authoritative preference namespace constant and route both migration and SettingsRepository through it.

Do not proceed with backup parity testing until this is resolved, because it contaminates the baseline state against which import/export is being judged.

---

## 1.7 Settings restore currently uses the wrong abstraction boundary

`BackupCoordinator.import()` currently calls:

```text
SettingsViewModel.updateSettings(...)
```

The supplied `SettingsViewModel.updateSettings()`:

- launches its own coroutine
- writes individual settings fields separately
- invokes side effects in `SettingsRepository`
- eventually changes the ViewModel state

This is unsuitable as the persistence transaction boundary for restore.

Examples of immediate side effects in `SettingsRepository` include:

```text
VPN sync
allowance change broadcast
widget update
other enforcement synchronization
```

### Required target

Restore uses:

```text
RestorePlan
    ↓
SettingsStoreAdapter.applyPersistentTarget()
```

which performs only persistent writes during `MUTATING`.

Then:

```text
STORES_COMMITTED
    ↓
live enforcement / widget / scheduler synchronization
```

The restore path must not accidentally trigger partially applied runtime behavior while the other store is still being mutated.

---

# 2A. Completed TS ↔ Kotlin source/data audit

This section is now populated from the actual source archives, not left as a future implementation task.

## 2A.1 Settings field-level matrix

Legend:

```text
PORTABLE        = current TS V1 exporter includes the field in settings payload
LOCAL/RUNTIME   = current TS V1 exporter explicitly omits the field
DERIVED         = recreated from persistent truth rather than imported as live state
EXTERNAL       = field points at a device/resource boundary and needs a restore action
```

| TS field | TS source / V1 export | TS restore | Kotlin current model/source | Kotlin current export/restore | Intended V1 policy | Gap / implementation decision |
|---|---|---|---|---|---|---|
| `darkMode` | `AppSettings`; exported | spread overwrite | `AppSettings.darkModeEnabled` | not in backup adapter / not restored | OVERWRITE | map stable wire name `darkMode`; target Kotlin field `darkModeEnabled` |
| `defaultDuration` | exported | overwrite | `defaultDurationMinutes` | exported as `defaultDurationMinutes` under different wire name | OVERWRITE | emit/read TS V1 name; normalize internally |
| `defaultReminderOffsets` | exported | overwrite | no equivalent | missing | OVERWRITE | add normalized field; do not silently drop |
| `focusModeEnabled` | explicitly omitted | preserve local | no exact equivalent | local-only | PRESERVE_LOCAL | do not export from Kotlin V1 |
| `allowedInFocus` | exported | overwrite | `allowedFocusPackages` | exported/restored under TS wire key | OVERWRITE | stable V1 adapter |
| `pomodoroEnabled` | explicitly omitted | preserve local | `pomodoroEnabled` | currently exported/restored | PRESERVE_LOCAL | Kotlin must not make this portable unless product overrides TS semantics |
| `pomodoroDuration` | exported | overwrite | `pomodoroWorkMinutes` | exported under different name | OVERWRITE | map wire names; preserve units |
| `pomodoroBreak` | exported | overwrite | `pomodoroBreakMinutes` | exported under different name | OVERWRITE | map wire names |
| `notificationsEnabled` | explicitly omitted | preserve local | no single equivalent; several notification settings exist | partly exported | PRESERVE_LOCAL | do not treat Kotlin notification preferences as one-for-one TS field |
| `onboardingComplete` | exported | overwrite | `SetupPersistenceManager` flag | not exported/restored | **PRESERVE_LOCAL** | Target-device setup gate; incoming value is compatibility metadata only and never bypasses Kotlin onboarding. |
| `privacyAccepted` | exported | overwrite | `SetupPersistenceManager` flag | not exported/restored | **PRESERVE_LOCAL** | Target-install consent remains local; incoming value is not applied. |
| `protectionMode` | exported | overwrite | `SetupPersistenceManager` flag | not exported/restored | **OVERWRITE** | Restore valid `standard`/`iron` user preference; invalid enum rejects before mutation. |
| `standaloneBlockPackages` | omitted | preserve local | `standaloneBlockPackages` | not portable | PRESERVE_LOCAL | preset inventory may describe it; do not activate/import runtime state |
| `standaloneBlockUntil` | omitted | preserve local | `standaloneBlockUntilMs` | not portable | PRESERVE_LOCAL | no live timer resurrection |
| `alwaysOnEnforcementEnabled` | omitted | preserve local | `alwaysBlockEnabled` | currently exported/restored | PRESERVE_LOCAL | stop treating current Kotlin wire field as portable |
| `alwaysOnPackages` | exported | overwrite | `alwaysBlockPackages` | exported/restored under TS name | OVERWRITE | exact package IDs |
| `alwaysOnVpnPackages` | exported | overwrite | no direct AppSettings field | missing from current adapter/model surface | OVERWRITE | add explicit normalized/persistence mapping; verify VPN repository getter/source |
| `focusMirrorVpnEnabled` | exported | overwrite when present | `focusMirrorVpnEnabled` | currently exported; current merge incorrectly ORs | OVERWRITE | imported value wins when field present |
| `autoCopyToAlwaysOn` | omitted | preserve local | `autoCopyToAlwaysOn` | current Kotlin model/export path includes it indirectly | PRESERVE_LOCAL | do not import as active behavior |
| `autoCopiedAlwaysOnPackages` | omitted | preserve local | no exact equivalent | missing | PRESERVE_LOCAL | runtime bookkeeping stays local |
| `lastShownStreakMilestone` | exported | overwrite | no Kotlin equivalent found | missing | **READ_IGNORE_LEGACY** | No active Kotlin persistence target; keep local. |
| `pendingAchievementCelebration` | exported | overwrite | no Kotlin equivalent found | missing | **READ_IGNORE_LEGACY** | Pending UI event must not cross devices in Kotlin V1. |
| `allowedAppPresets` | exported | overwrite | Kotlin `launcherPresets` is actually persisted under `allowed_app_presets` and consumed by multiple preset UI surfaces | current adapter missing | **OVERWRITE_TO_ALLOWED_PRESET_STORE** | Treat as distinct allowed-preset collection. Do not use it as block-preset storage. |
| `blockPresets` | exported | overwrite | Kotlin has no separate `BlockPreset` persistent model; some UI reuses `launcherPresets`/`AllowedAppPreset` | missing | **OVERWRITE_TO_BLOCK_PRESET_STORE** | Add a distinct Kotlin block-preset model/store; never conflate with `allowed_app_presets`. |
| `dailyAllowanceEntries` | exported | overwrite | `dailyAllowanceConfigJson` | exported/restored with current JSON shape | OVERWRITE | validate `package` vs TS `packageName` wire compatibility; normalize |
| `blockedWords` | exported | overwrite | `blockedWords` | exported/restored | OVERWRITE | exact list policy, bounded |
| `aversionDimmerEnabled` | omitted | preserve local | field exists | currently exported by current Kotlin domain serializer paths in places | PRESERVE_LOCAL | no active deterrent state migration |
| `aversionVibrateEnabled` | omitted | preserve local | field exists | current domain field; not TS-portable | PRESERVE_LOCAL | same |
| `aversionSoundEnabled` | omitted | preserve local | field exists | current domain field; not TS-portable | PRESERVE_LOCAL | same |
| `weeklyReportEnabled` | omitted | preserve local | no direct AppSettings field; notification prefs differ | partly exported as other fields | PRESERVE_LOCAL | do not infer equivalence from weekly-review settings |
| `weekStartDay` | exported | overwrite | active Kotlin stats/profile behavior reads `user_profile.weekUsageReportStartDay` | not represented in `AppSettings`/backup adapter | **OVERWRITE** | Normalize TS integer `0..6` to Kotlin profile strings `sun..sat` and persist through the existing `user_profile` source used by StatsViewModel. |
| `greyoutSchedule` | exported | overwrite | Kotlin currently parses this key into `RecurringBlockSchedule` | semantically mismatched | **OVERWRITE_AS_GREYOUT_WINDOWS** | Preserve TS `GreyoutWindow[]` wire objects independently. |
| `systemGuardEnabled` | omitted | preserve local | field exists | currently exported | PRESERVE_LOCAL | current Kotlin backup must not import live enforcement toggle |
| `blockInstallActionsEnabled` | omitted | preserve local | field exists | not TS-portable | PRESERVE_LOCAL | same |
| `blockYoutubeShortsEnabled` | omitted | preserve local | field exists | not TS-portable | PRESERVE_LOCAL | same |
| `blockInstagramReelsEnabled` | omitted | preserve local | field exists | not TS-portable | PRESERVE_LOCAL | same |
| `vpnBlockEnabled` | omitted | preserve local | `networkBlockEnabled` | currently exported as `vpnBlockEnabled` | PRESERVE_LOCAL | stop importing live VPN activation state; retain persistent package policy separately |
| `standaloneVpnPackages` | omitted | preserve local | no direct model equivalent | missing | PRESERVE_LOCAL | runtime/device-local |
| `vpnSelfHealEnabled` | omitted | preserve local | field exists | not TS-portable | PRESERVE_LOCAL | same |
| `pinProtectionEnabled` | omitted | preserve local | `pinProtectionEnabled` is derived from PinManager | not backup data | PRESERVE_LOCAL | never restore a PIN/security state from backup |
| `launcherTheme` | exported | overwrite | `launcherTheme` | currently missing from backup adapter | OVERWRITE | add stable wire mapping |
| `focusToolPackages` | exported | overwrite | `focusToolPackages` | currently missing from backup adapter | OVERWRITE | add stable wire mapping |
| `launcherEnabled` | omitted | preserve local | no exact AppSettings field | missing | PRESERVE_LOCAL | default-launcher/runtime state stays device-local |
| `launcherHiddenPackages` | exported | overwrite | `launcherHiddenPackages` | missing from backup adapter | OVERWRITE | exact package IDs |
| `launcherPinnedPackages` | exported | overwrite | `LauncherActivity` only declares unused preference constant; no active end-to-end read/write path found | not in AppSettings/adapter | **READ_IGNORE_LEGACY** | Current Kotlin archive does not establish this as an active feature; do not invent a V1 mapping. |
| `launcherDockPackages` | exported | overwrite | `LauncherActivity` declares preference constant; no active AppSettings-backed path found | missing from AppSettings/adapter | **READ_IGNORE_LEGACY** | Unwired in supplied Kotlin archive; do not invent a portability contract. |
| `launcherWallpaperUri` | exported | overwrite | `launcherWallpaperUri` | missing from backup adapter | EXTERNAL + OVERWRITE/CLEAR | do not restore dangling local URI |
| `launcherClockStyle` | exported | overwrite | setter exists, but no active `readAppSettings()`/launcher consumption found | missing from AppSettings/adapter | **READ_IGNORE_LEGACY** | Unwired in supplied Kotlin archive; do not invent a portability contract. |
| `launcherBlockUninstall` | exported | overwrite | field exists | missing from backup adapter | OVERWRITE | field-level mapping |
| `launcherLockDuringStandalone` | exported | overwrite | field exists | missing from backup adapter | OVERWRITE | field-level mapping |
| `keepFocusActiveUntilTaskEnd` | exported | overwrite | field exists | missing from backup adapter | OVERWRITE | persistent behavior preference, not current session |
| `autoRescheduleEnabled` | exported | overwrite | field exists | missing from backup adapter | OVERWRITE | persistent behavior preference |
| `overlayWallpaper` | exported | overwrite | no equivalent | missing | EXTERNAL + OVERWRITE/CLEAR | define local-file resource policy |
| `overlayQuotes` | exported | overwrite | no equivalent | missing | OVERWRITE | add persistent mapping |
| `recurringBlockSchedules` | exported | overwrite | active Kotlin feature exists | current adapter incorrectly encodes it under `greyoutSchedule` | **OVERWRITE_AS_RECURRING_SCHEDULES** | Preserve separate V1 wire field; rebuild derived GreyoutWindows after persistence. |
| `userProfile` | exported inside settings | overwrite | separate `user_profile` JSON string | current adapter exports/restores separately | OVERWRITE | keep separate persistence target but stable V1 wire field |
| `beginnerMode` | exported | overwrite | no Kotlin references found | missing | **READ_IGNORE_LEGACY** | TS-only UX field in supplied archives; accept for compatibility, ignore on Kotlin V1 import. |
| `tipsCardDismissed` | exported | overwrite | no Kotlin references found | missing | **READ_IGNORE_LEGACY** | TS-only UX state; ignore on Kotlin V1. |
| `tipsCardFirstShownAt` | exported | overwrite | no Kotlin references found | missing | **READ_IGNORE_LEGACY** | TS-only UX timestamp; ignore on Kotlin V1. |
| `pendingPresets` | exported | overwrite by TS spread | no Kotlin references found | missing | **READ_IGNORE_LEGACY** | TS transient import metadata; never activates target configuration and is not persisted in Kotlin V1. |

### Audit conclusion

The Kotlin backup adapter is **materially incomplete** relative to the TS V1 wire contract. The largest structural losses are:

```text
missing / incomplete:
    defaultReminderOffsets
    allowedAppPresets / blockPresets
    alwaysOnVpnPackages
    weekStartDay
    launcherPinnedPackages
    launcherDockPackages
    launcherClockStyle
    overlayWallpaper / overlayQuotes
    recurringBlockSchedules as a separate wire field
    greyoutSchedule as actual GreyoutWindow data
    several profile/progress/UX fields
```

and several **TS-local fields are currently being treated as portable by Kotlin**, especially enforcement toggles.

The implementation agent must use this matrix as the wire adapter contract; Kotlin internal names must never define the `.focusflow` V1 schema.

## 2A.1A Data-policy freeze

The source audit is complete; no implementation-choice placeholders remain for the rows above.

- `onboardingComplete` and `privacyAccepted` are target-local setup/consent state. Kotlin V1 accepts the wire fields for compatibility but does not apply them.
- `protectionMode` is a portable user preference and overwrites the target when the value is `standard` or `iron`.
- `launcherPinnedPackages`, `launcherDockPackages`, `launcherClockStyle`, `beginnerMode`, `tipsCardDismissed`, `tipsCardFirstShownAt`, `pendingPresets`, `lastShownStreakMilestone`, and `pendingAchievementCelebration` are `READ_IGNORE_LEGACY` because the supplied Kotlin source does not establish an active equivalent end-to-end.
- `allowedAppPresets` and `blockPresets` are distinct collections. The current Kotlin `launcherPresets` / `allowed_app_presets` store is an allowed-preset store, not a safe substitute for both.
- `greyoutSchedule` and `recurringBlockSchedules` remain distinct wire structures.

## 2A.2 Task / reminder matrix

| Data | TS source | Kotlin source | Portable? | Derived/runtime state | Migration rule |
|---|---|---|---|---|---|
| `Task.id` | `Task` + SQLite `tasks.id` | `Task` + Room primary key | Yes | No | preserve unused ID; equivalent collision = duplicate; divergent collision = `ID_CONFLICT` before mutation |
| `Task.title` | `tasks.title` | `TaskEntity.title` | Yes | No | preserve |
| `Task.description` | `tasks.description` | `TaskEntity.description` | Yes | No | preserve nullable semantics |
| `Task.startTime` | ISO string in SQLite | ISO string in Room | Yes | scheduler derives future trigger | preserve exact instant |
| `Task.endTime` | ISO string | ISO string | Yes | task-end alarm derived | preserve exact instant; compare against captured `now` |
| `Task.durationMinutes` | integer | integer | Yes | No | preserve |
| `Task.status` | string enum | string alias | Yes | current focus session is separate | normalize known values; past scheduled normalization explicit |
| `Task.priority` | string enum | string alias | Yes | No | normalize known values |
| `Task.tags` | JSON array | JSON in Room | Yes | No | canonical array normalization |
| `Task.reminders[]` | JSON array in `tasks.reminders` | typed list serialized in entity | Yes | OS reminder registrations are derived | preserve reminder objects; do not assume `Reminder.id` alone represents an AlarmManager notification |
| `Reminder.id` | persistent field | persistent field | Yes | No | preserve identity when task ID preserved; do not change it merely because parent task ID changes |
| `Reminder.taskId` | persistent FK-like field | persistent field | Yes | No | rewrite through task-ID map when parent task ID changes |
| `Reminder.offsetMinutes` | persistent | persistent | Yes | scheduler-specific | preserve |
| `Reminder.type` | persistent | persistent | Yes | scheduler-specific | normalize known values |
| `Reminder.notifId` | persistent optional field | persistent optional field | Conditional | device notification identity may be derived/recreated | treat current notification ID as derived/device-specific unless feature audit proves portability |
| `Task.focusAllowedPackages` | persistent field with `undefined` / `[]` semantics | nullable list with same documented semantics | Yes | active session uses resolved snapshot | preserve `null` vs empty-list distinction exactly |
| task-end AlarmManager registration | `TaskAlarmModule` derived | `AlarmRepository` derived | No | yes | reconstruct from committed task truth |
| generated pre/mid/end notification requests | `notificationService.ts` hard-coded schedule | `NotificationRepository.kt` hard-coded schedule | No | yes | recreate from task truth using one canonical scheduler implementation; `Task.reminders[]` does not currently drive these requests |

### Important reminder conclusion

The existing source archives do **not** support the assumption that `Task.reminders[]` is the canonical source for the generated Android notification schedule. The scheduler currently generates fixed task-relative notifications independently of that field. Therefore:

```text
persistent Reminder objects
    ≠
current scheduled NotificationRequests
```

The migration must preserve the former and deliberately reconstruct the latter according to the established product behavior. A future feature that makes `Reminder[]` authoritative must be a separate product/schema decision.

## 2A.3 Feature-level dependency matrix

| Feature | Persistent source in TS | Exported | Restored | Runtime/derived state that must NOT be imported | Kotlin current state | Target handling |
|---|---|---:|---:|---|---|---|
| Focus tasks | SQLite `tasks` | Yes | Yes | active session + alarms | Room `tasks` | migrate semantically |
| Focus session | `focus_sessions` / active session | No | No | active session + live enforcement | Room `focus_sessions` + prefs | keep local; never resurrect |
| Blocked words | `AppSettings.blockedWords` + native sync | Yes | Yes | current service activation only | SharedPrefs | portable persistent config; resync after commit |
| Focus allow-list | `allowedInFocus` | Yes | Yes | active session resolved allow-list | `allowedFocusPackages` | portable global policy; active-session precedence retained |
| Always-On | `alwaysOnPackages` | Yes | Yes | `alwaysOnEnforcementEnabled` live switch is omitted | `alwaysBlockPackages` | import list; preserve local active switch unless product overrides |
| Always-On VPN | `alwaysOnVpnPackages` | Yes | Yes | VPN service process | missing direct model | add persistent source + reconcile VPN separately |
| Standalone block | packages + expiry in settings, but omitted from portable settings | Inventory only | No | active block/timer/VPN | modeled in SharedPrefs | keep device-local; preset inventory remains descriptive |
| Daily allowance | `dailyAllowanceEntries` | Yes | Yes | current usage counters/session allowance runtime | JSON config in SharedPrefs | restore config; usage counters remain local unless audit proves portability |
| Greyout windows | `greyoutSchedule` | Yes | Yes | current accessibility enforcement | `GreyoutRepository` / native prefs | preserve actual windows separately from recurring schedules |
| Recurring block schedules | `recurringBlockSchedules` | Yes | Yes | active generated windows are derived | `RecurringBlockSchedule` | restore schedules, then rebuild GreyoutWindows |
| Defense | block presets + overlay config | Yes | TS restore does not activate presetSections | active enforcement | Kotlin subset | restore persistent config only |
| VPN | explicit packages + policy fields | Partially / some runtime toggles omitted | Partially | live VPN connection | `VpnRepository` | restore persistent target config; reconcile service |
| Launcher | persistent preferences | Yes except launcherEnabled | Yes via settings spread | default-home/runtime registration | partial model | map field-by-field; local resource/launcher role remains device-specific |
| Profile | `settings.userProfile` | Yes | Yes | none | separate `user_profile` | preserve JSON structure |
| Security/PIN | PIN enabled omitted | No | No | PIN itself/security session | `PinManager` | never import |
| Notifications | some persistent prefs are exported; master `notificationsEnabled` omitted | mixed | mixed | active scheduled notification instances | `NotificationRepository` incomplete wiring | explicit field mapping; scheduler state derived |
| Analytics/history | separate DB tables | No | No | current insights/notifications | many Room tables | keep local; don't silently include in backup |

---

# 2. Contract philosophy

The system has three truths:

```text
A. observed TS behavior
B. intended FocusFlow product semantics
C. current Kotlin behavior
```

For each field or behavior:

```text
Observed TS
→ intended semantics
→ Kotlin gap
→ decision
→ test
```

Also distinguish:

```text
persistent truth
≠
derived OS state
≠
runtime/session state
```

For example:

```text
task row
    = persistent truth

reminder record
    = persistent truth embedded in/owned by task data

AlarmManager registration
    = derived OS state

notification
    = runtime side effect / derived presentation

active FocusSession
    = runtime/session state
```

---

# 3. Data portability model

Classify at the smallest useful data-object/field level.

Allowed classifications:

```text
PORTABLE
LOCAL
RUNTIME
DERIVED
EXTERNAL_RESOURCE
HISTORICAL_LOCAL
```

A feature may contain multiple categories.

Example:

```text
Focus mode
    selected focus packages → PORTABLE
    active session           → RUNTIME
    current service state    → RUNTIME/LOCAL
```

---

# 4. Decision Gate — mandatory before production implementation

The following are the proposed frozen decisions for reviewer acceptance.

| Decision | Frozen contract |
|---|---|
| External `ACTION_VIEW` | Accept content URIs; `.focusflow` content validation is authoritative; `file://` supported only through dedicated safe reader, otherwise deterministic rejection |
| External snapshot | Read, validate, normalize, and persist a durable PendingImport artifact before confirmation UI |
| Second external file while one import is pending | Reject the second request with an explicit “import already in progress” state; do not queue |
| Restore admission | Atomically acquire the global restore write gate before CurrentDeviceState snapshot |
| Second restore | Reject; do not queue/coalesce |
| Normal writers during restore | All persistent writers that can overlap RestorePlan scope use the same global gate |
| Startup recovery | Open global writer gate only after incomplete RestoreSession is recovered to terminal state |
| PREPARED process death | Recover session; no persistent mutation has begun, then continue exact plan |
| MUTATING process death | Resume exact persisted RestorePlan; do not abandon partial state |
| STORES_COMMITTED process death | Resume RECONCILING; do not re-plan |
| RECONCILING process death | Resume RECONCILING; do not reopen normal writers until COMPLETED |
| Unrecoverable post-mutation failure | `RECOVERY_BLOCKED`; conflicting writes remain blocked; no “abort and leave partial state” outcome |
| Restore gate release | Only after `COMPLETED` or `ABORTED_PRE_MUTATION`; not at `STORES_COMMITTED` or `RECOVERY_BLOCKED` |
| Settings import during active FocusSession | Allowed; active session itself is not restored; session-specific allow-list remains authoritative; imported persistent enforcement settings trigger the existing live sync path after persistent commit |
| Task replace during active FocusSession | Prohibited |
| Merge task ID unused | Preserve incoming task ID |
| Merge same ID + equivalent projection | Duplicate; skip |
| Merge same ID + divergent projection | Whole restore fails before mutation with `ID_CONFLICT`; no silent overwrite/remap |
| Duplicate task IDs inside incoming backup | Reject the entire backup before `MUTATING`; no first-wins/last-wins behavior |
| Deliberate repeated import | Allowed; no persistent backup-level dedupe |
| Same Android intent duplicate | Transiently dedupe per active import session |
| Different imports rapidly | Second rejected while first is pending/active |
| Task ID remapping | Only occurs if explicitly required by a future policy; when it occurs, mapping is persisted in RestorePlan |
| Reminder identity | `Reminder.id` is independent identity; preserve it; remap only `Reminder.taskId` when parent task ID changes |
| Settings conflicts | Backup wins only for fields explicitly classified `PORTABLE/APPLIED` in §2A.1; `PRESERVE_LOCAL` and `READ_IGNORE_LEGACY` fields are not overwritten. Field-specific policy is authoritative. |
| Setup fields | `onboardingComplete` and `privacyAccepted` are target-local in Kotlin V1; `protectionMode` is portable |
| TS-only fields without active Kotlin equivalent | Accept for V1 parsing, normalize as ignored, never apply or silently reinterpret |
| `focusMirrorVpnEnabled` | Imported value wins when present |
| `presetSections` | Descriptive inventory only; not applied as live configuration in V1 |
| `pendingPresets` | TS transient import metadata is accepted/ignored; it is never activated or persisted by Kotlin V1 |
| Package match | Exact package ID only; never substitute by app label |
| Missing package | Preserve as dormant/unresolved by default; field-specific omit is allowed only where explicitly defined and must appear in RestorePlan/RestoreResult |
| External resource unavailable | Do not retain a dangling path; record explicit action such as `CLEAR` or `UNRESOLVED` |
| Unsupported V1 version | Reject before mutation |
| Summary | Informational only; never authoritative |
| Platform metadata | Informational only in V1 |
| appVersion | Diagnostic/export metadata; not schema compatibility |
| Unknown optional fields | Accept for compatible V1; do not promise re-export preservation |
| Unknown incompatible enum | Reject the smallest affected object/field whose meaning cannot be normalized; if that makes the Normalize/RestorePlan invalid, reject the whole import before mutation |
| Duplicate JSON keys | Reject |
| UTF-8 | Strict decode; invalid bytes reject |
| Input limits | Values in §16 are hard acceptance limits unless reviewer changes them |
| EXACT reminder timing | Acceptance threshold is a FocusFlow product test threshold, not an Android guarantee |
| Active device + FSI | No tap required; Android may choose heads-up instead of launching Activity |
| Locked/screen-off + permitted FSI | Request FSI; verify actual visible/resumed outcome on supported device state |
| FSI unavailable | Notification fallback; never claim Activity launch is mandatory |
| Alarm reconciliation | Desired-state application for known FocusFlow-owned identities; do not claim complete AlarmManager observability |
| `FLAG_NO_CREATE` | PendingIntent token probe only; not proof of AlarmManager registration |
| Alarm identity | Deterministic per restored task/reminder identity |
| Scheduler registry | Persistent desired-state ledger is part of the persistent RestorePlan target; it is stored in Room with task/reminder persistence and therefore reaches its exact target before `STORES_COMMITTED` |
| Registry ordering | Write desired/tombstone state before OS side effect; mark applied only after side effect succeeds |
| Reboot | Reconcile task/reminder alarms after BOOT_COMPLETED/USER_UNLOCKED as appropriate |
| Exact-alarm revoke | Detect on startup/resume/settings-return; do not rely on the grant-only broadcast |
| Target SDK | Must be recorded from the actual Gradle/build output before final Android matrix sign-off |

---

# 5. Global RestoreSession state machine

The authoritative state machine is:

```text
PREVIEW_NOT_ADMITTED
        |
        | user confirms
        v
   acquire gate
        |
        v
    PREPARED
        |
        | mutation begins
        v
    MUTATING
        |
        | all required persistent stores reach exact targets
        v
STORES_COMMITTED
        |
        v
   RECONCILING
        |
        v
   COMPLETED
```

Recovery transitions:

```text
PREPARED
    + process death
    → RECOVERING_PREPARED
    → MUTATING

MUTATING
    + process death
    → RECOVERING_MUTATION
    → MUTATING

STORES_COMMITTED
    + process death
    → RECOVERING_RECONCILE
    → RECONCILING

RECONCILING
    + process death
    → RECOVERING_RECONCILE
    → RECONCILING
```

Pre-mutation failure:

```text
PREPARED
    + validation/preflight failure
    → ABORTED_PRE_MUTATION
```

Post-mutation infrastructure failure:

```text
MUTATING / RECOVERING
    + unrecoverable persistent recovery failure
    → RECOVERY_BLOCKED
```

## Mechanical phase-transition rule

The implementation must enforce this exact ordering:

```text
PREPARED session is durable
        ↓
start-MUTATING transaction
    • set RestoreSession.phase = MUTATING
    • perform the first Room-side persistent mutations
    • update persistent scheduler-registry target rows as required
        ↓
commit that Room transaction
        ↓
only now may SharedPreferences / other non-transactional stores be changed
```

Therefore the crash boundary is mechanically:

```text
phase = PREPARED
    → no persistent mutation has occurred

phase = MUTATING
    → persistent mutation may already have occurred
```

If the process dies after the Room transaction commits but before another store is applied, startup sees `MUTATING` and resumes the exact persisted RestorePlan.

## Recovery rule

Once mutation begins:

```text
Do not release the gate with partial persistent state.
```

This migration uses:

```text
resume exact durable plan
```

rather than designing a compensating rollback engine.

`RECOVERY_BLOCKED` is a maintenance/repair state, not a normal user state.

---

# 6. Startup recovery barrier

Application startup must establish the persistence gate before any normal mutation path can execute.

Target:

```text
Application.onCreate
    ↓
initialize database/recovery infrastructure
    ↓
create global RestoreWriteGate in CLOSED state
    ↓
load RestoreSession
    ├─ no session / terminal
    │      → open gate
    │
    └─ incomplete session
           ↓
       recover exact durable plan
           ↓
       reach terminal restore state
           ↓
       finish required reconciliation
           ↓
       open gate
    ↓
normal writers/services/UI
```

### Critical bootstrap ordering

The current Kotlin startup also performs:

```text
prepareLegacyDatabase
AppModule.init
migrateSettingsBlobToSharedPrefs
NotificationChannels.createAll
DayRatingNotificationScheduler.ensureScheduled
```

The final implementation must make startup sequencing recovery-aware.

In particular, the legacy settings migration must not mutate the settings store after a partially applied restore.

Recommended bootstrap:

```text
1. low-level schema migration only
2. initialize persistence/recovery infrastructure
3. acquire startup writer barrier
4. resolve legacy settings migration if and only if it is not in conflict
   with an active RestoreSession
5. recover active RestoreSession
6. finish scheduler reconciliation for restore
7. release gate
8. start normal mutation-capable components
```

If the one-time legacy settings migration is proven to have completed already, it must be a no-op.

---

# 7. Restore admission and concurrency

Correct order:

```text
user confirms
    ↓
atomically acquire RestoreWriteGate
    ↓
capture CurrentDeviceState once
    ↓
normalize current state
    ↓
build deterministic RestorePlan
    ↓
persist RestoreSession + exact plan + normalized input
    ↓
MUTATING
```

There must not be:

```text
check “no restore”
    ↓
later create restore
```

as separate operations.

That would race.

## Global writer requirement

The gate covers all conflicting persistent writers, not only imports:

```text
TaskRepository writes
SettingsRepository writes
task auto-reschedule
recurring task persistence
cleanup jobs
daily allowance persistence
alarm callbacks that mutate task state
startup repair
scheduler registry mutations
legacy migration code that can overlap restore
focus-session persistence where it overlaps RestorePlan scope
```

Prefer enforcement at the persistence boundary so a new caller cannot silently bypass the rule.

---

# 8. PendingImport lifecycle

## External file path

```text
ACTION_VIEW URI
    ↓
bounded byte read
    ↓
strict UTF-8 decode
    ↓
strict JSON parse
    ↓
schema validation
    ↓
version migration
    ↓
NormalizedBackup
    ↓
durable app-private PendingImport snapshot
    ↓
ImportConfirmScreen
```

## Snapshot contents

At minimum:

```text
normalized backup artifact
source provider authority
reported MIME
display name if available
backup fingerprint
createdAt
schema version
```

Do not keep the provider URI as the authoritative restore input after snapshot creation.

## Lifecycle

```text
cancel
    → delete pending artifact

pre-mutation validation failure
    → delete pending artifact

success
    → delete after terminal COMPLETED

recoverable process death
    → retain until RestoreSession terminal

abandoned terminal artifact
    → garbage collect
```

Default abandoned-artifact retention proposal:

```text
24 hours
```

---

# 9. NormalizedBackup

The wire format must not leak into restore semantics.

```text
V1 JSON
    ↓
BackupParser
    ↓
V1 migration/normalization adapter
    ↓
NormalizedBackup
    ↓
RestorePlanner
```

Future:

```text
V1 → NormalizedBackup
V2 → NormalizedBackup
V3 → NormalizedBackup
```

`RestorePlanner` must not branch on wire version.

---

# 10. RestorePlan

`RestorePlan` is immutable for the life of a RestoreSession and is the exact recovery artifact.

It contains:

```text
sessionId
normalized backup reference
import options
CurrentDeviceState snapshot/reference
frozen now
frozen zoneId
settings mutations
settings conflict decisions
task import decisions
task replacement deletions
task ID map, if any
relationship remapping
package/resource actions
persistent scheduler desired-state mutations
plan schema/version
plan integrity hash
```

## RestorePlanner invariants

Input:

```text
NormalizedBackup
+
CurrentDeviceStateSnapshot
+
ImportOptions
```

Output:

```text
exactly one deterministic RestorePlan
```

No side effects:

```text
no DB writes
no SharedPreferences writes
no AlarmManager calls
no NotificationManager calls
no navigation
```

`CurrentDeviceState.now` and `zoneId` are explicit inputs.

The planner never calls the system clock itself.

---

# 11. RestorePlan integrity

Avoid circular hashing.

Use:

```text
canonicalPlanPayload
    = canonical serialization of RestorePlan
      with integrityHash field omitted

planIntegrityHash
    = SHA-256(schemaMarker || canonicalPlanPayloadBytes)
```

Persist:

```text
plan
+
planIntegrityHash
```

and verify both before replay.

Likewise:

```text
backupFingerprint
    = SHA-256(exact normalized-backup artifact bytes)
```

The backup fingerprint is for restore-session integrity, not product-level deduplication.

---

# 12. Cross-store recovery model

Current Kotlin persistence is split across at least:

```text
Room
SharedPreferences
```

There is no true global ACID transaction spanning them.

The contract therefore uses:

```text
durable exact RestorePlan
+
per-store idempotent target application
+
global RestoreSession state machine
```

## Store adapters

```text
RoomStoreAdapter
SettingsStoreAdapter
```

### Room

Use one SQLite transaction for the complete Room portion of the plan.

### SharedPreferences

Because the settings store uses one SharedPreferences namespace, apply the target persistent settings through one coherent `Editor.commit()` boundary.

Do not invoke dozens of asynchronous field setters.

### Cross-store semantics

```text
MUTATING:
    each store is driven toward its exact target

if process dies:
    recovery replays the same target operations

STORES_COMMITTED:
    all required persistent stores match RestorePlan target

RECONCILING:
    derived OS state is repaired

COMPLETED:
    reconciliation finished and gate can reopen
```

No “best effort and leave partial state” outcome is allowed after mutation begins.

---

# 13. RestorePlan canonical serialization

The canonical serialized form used for `RestorePlan` integrity must omit the integrity field itself:

```text
canonicalPlanPayload = canonical serialization of RestorePlan with integrityHash omitted
integrityHash = SHA-256("FocusFlowRestorePlanV1\0" || canonicalPlanPayload)
```

Store the resulting hash beside the plan or in the excluded integrity field. Recovery verifies the hash before replay.


For deterministic hashes and test fixtures:

```text
UTF-8
stable field ordering
stable object encoding
stable ordering for semantically unordered collections
explicit number representation
explicit null policy
```

The canonical representation used for `planIntegrityHash` must be specified in one implementation utility and reused by tests.

---

# 14. Task restore semantics

## Merge

```text
incoming task ID absent
    → preserve incoming ID

incoming task ID present
    ↓
same TaskDuplicateProjection
    → duplicate / skip

different TaskDuplicateProjection
    → ID_CONFLICT
    → abort before persistent mutation
```

No silent database `IGNORE`.

## Replace

```text
existing exported task scope
    → delete

backup task scope
    → restore
```

Task replacement is prohibited while a FocusSession is active.

---

# 15. TaskDuplicateProjection

Define the projection exactly.

Include semantic task state:

```text
title
description
startTime
endTime
durationMinutes
status
priority
tags
reminders
color
focusMode
focusAllowedPackages
```

Exclude mutation/history metadata unless feature audit proves it is semantic identity:

```text
createdAt
updatedAt
```

## Canonicalization rules

Unless the TS audit proves otherwise:

### Strings

```text
preserve exact UTF-8 string
case-sensitive
no implicit trim
no Unicode normalization
```

### Set-like collections

For:

```text
package lists
tags
```

use:

```text
remove blank
deduplicate
sort canonically
```

### Reminder collection

Reminder ordering is non-semantic.

Sort for comparison by:

```text
Reminder.id
then offset/type as stable tie-breakers
```

Do not compare raw array position.

### Status

Include `status` because completed/skipped/scheduled are materially different persistent task states and should not silently collapse during repeated import.

---

# 16. Reminder identity

Distinguish:

```text
Reminder.id
Reminder.taskId
```

`Reminder.id` is an independent identity.

Therefore:

```text
task ID preserved
    → Reminder.id preserved
    → Reminder.taskId unchanged

task ID remapped
    → Reminder.id preserved
    → Reminder.taskId updated
```

Do not generate a new Reminder ID merely because the parent task ID changed.

---

# 17. Task-owned replacement graph

Default V1 rule:

```text
replace only persistent data actually represented by the portable task graph
```

Definitely task-owned:

```text
Task row
embedded reminder data
task-owned recurrence configuration
task-owned relations that are exported
task-owned scheduler desired entries
```

Do not automatically delete unexported historical/analytics/session data merely because it references a task.

The supplied TS backup does not export the complete:

```text
focus_sessions
focus_overrides
daily_completion
analytics / achievement history
```

Therefore the default decision is:

```text
historical_local data
    → PRESERVE_LOCAL
```

If any of those records require referential cleanup for correctness, audit and specify that separately; never silently fold them into “replace tasks.”

---

# 18. Settings portability and field-level policy

The current TS exporter explicitly omits runtime/device-local fields and then merges the portable settings over the importing device's current settings.

For V1, preserve that semantic model unless the reviewer explicitly overrides a field.

## Default policy

```text
portable scalar
    → OVERWRITE from backup

portable collection/object
    → REPLACE_COLLECTION / OVERWRITE from backup

omitted runtime/local field
    → PRESERVE_LOCAL

optional imported focusMirrorVpnEnabled
    → OVERWRITE when present
```

Do not implement a generic union merge for settings unless the field-specific contract says so.

## TS V1 portable groups — authoritative summary

The field-level matrix in §2A.1 is authoritative. The following is only a condensed view and must remain consistent with the matrix above.

```text
APPLY PORTABLE DATA
    defaultDuration
    defaultReminderOffsets
    allowedInFocus
    pomodoroDuration
    pomodoroBreak
    protectionMode
    alwaysOnPackages
    alwaysOnVpnPackages
    focusMirrorVpnEnabled
    allowedAppPresets
    blockPresets
    dailyAllowanceEntries
    blockedWords
    weekStartDay
    greyoutSchedule
    recurringBlockSchedules
    launcherTheme
    focusToolPackages
    launcherHiddenPackages
    launcherBlockUninstall
    launcherLockDuringStandalone
    keepFocusActiveUntilTaskEnd
    autoRescheduleEnabled
    overlayQuotes
    userProfile
    explicitly mapped persistent notification preferences

PRESERVE LOCAL / DO NOT APPLY
    focusModeEnabled
    pomodoroEnabled
    notificationsEnabled (TS master switch)
    standaloneBlockPackages / standaloneBlockUntil
    autoCopyToAlwaysOn / autoCopiedAlwaysOnPackages
    enforcement activation switches
    standaloneVpnPackages
    vpnBlockEnabled
    vpnSelfHealEnabled
    pinProtectionEnabled / active PIN state
    launcherEnabled / default-launcher role
    onboardingComplete
    privacyAccepted

READ-IGNORE LEGACY
    launcherPinnedPackages
    launcherDockPackages
    launcherClockStyle
    beginnerMode
    tipsCardDismissed
    tipsCardFirstShownAt
    pendingPresets
    lastShownStreakMilestone
    pendingAchievementCelebration
```

## Kotlin-specific mismatch

The Kotlin domain settings model is not 1:1 with the TS field names.

Examples:

```text
TS allowedInFocus
    ↔ Kotlin allowedFocusPackages

TS alwaysOnPackages
    ↔ Kotlin alwaysBlockPackages

TS alwaysOnEnforcementEnabled
    ↔ Kotlin alwaysBlockEnabled

TS vpnBlockEnabled
    ↔ Kotlin networkBlockEnabled
```

The wire adapter must use the stable V1 wire names, not Kotlin internal names.

---

# 19. `focusMirrorVpnEnabled` migration bug

The current Kotlin merge code uses logical OR:

```text
imported || current
```

while TS uses nullish precedence:

```text
imported ?? current ?? false
```

Therefore:

```text
current = true
imported = false
```

produces:

```text
TS → false
Kotlin current implementation → true
```

This is a concrete migration semantic divergence.

### Required V1 rule

```text
when imported field is present:
    imported value wins

when missing:
    preserve current value
```

---

# 20. Preset sections

TS exports:

```text
presetSections
```

but the TS restore function does not consume them as active configuration.

Therefore V1 should treat them as:

```text
descriptive inventory / preview metadata
```

not:

```text
instructions to activate protection
```

This is especially important for:

```text
standalone block
always-on
defense
```

because live state is deliberately device-local/runtime.

---

# 21. Active Focus Session + settings import

Settings import is allowed during an active FocusSession.

Semantics:

```text
active FocusSession object
    → never restored from backup

session-specific allow-list
    → remains authoritative for active task

portable global settings
    → persistent target updated

live enforcement
    → re-synchronized after persistent commit
```

This matches the TS behavior where enforcement synchronization considers the current session's allow-list separately from global allowed-app configuration.

---

# 22. Package-reference policy

Only exact package IDs are allowed.

```text
backup package ID
    ↓
PackageManager exact lookup
```

Never substitute by:

```text
display label
app name
similar app
package prefix
```

Unresolved package actions must be explicit in RestorePlan:

```text
KEEP_DORMANT
or
OMIT
```

and reflected in RestoreResult.

---

# 23. External resource policy

For fields such as wallpaper/local URIs:

```text
portable resource available
    → restore

device-local resource unavailable
    → CLEAR / UNRESOLVED according to field policy
```

Never intentionally restore a dangling local path.

The selected action must appear in RestorePlan and RestoreResult.

---

# 24. Time / timezone contract

## Absolute timestamps

Keep:

```text
startTime
endTime
createdAt
updatedAt
```

as absolute instants using the existing ISO-8601 wire representation.

## Recurring schedules

Recurring wall-clock schedules need an explicit timezone rule.

Proposed V1:

```text
schedule follows importing device's current local timezone
```

unless a particular schedule already stores an authoritative timezone identity.

The final audit must confirm which current FocusFlow schedule models actually carry timezone information.

Planner inputs include:

```text
CurrentDeviceState.now
CurrentDeviceState.zoneId
```

---

# 25. Past scheduled task normalization

This must be explicit, not “according to TS semantics.”

Proposed V1 rule:

```text
status = scheduled
AND endTime < captured now
    → status = skipped
    → updatedAt = current plan timestamp
    → no future reminder/alarm
```

No recurrence advancement should be performed implicitly during backup restore unless the feature audit shows that the TS product contract requires it.

The normalization must be covered by fixtures.

---

# 26. `.focusflow` V1 wire contract

Keep existing V1 compatibility:

```json
{
  "kind": "FocusFlowBackupV1",
  "version": 1
}
```

Do not rename the V1 wire `kind`.

For a future V2, a cleaner family/version design may be introduced without breaking V1 files.

## File type

```text
extension = .focusflow
MIME = application/octet-stream
```

Export filename:

```text
focusflow-YYYY-MM-DDTHH-mm-ss.focusflow
```

The same extension/MIME/kind/version definition should be used across:

```text
export
ACTION_VIEW handler
file picker
parser
tests
```

---

# 27. Wire-format parsing rules

Required:

```text
strict UTF-8
duplicate key rejection
unknown optional field tolerance
explicit enum behavior
explicit null behavior
explicit number representation
stable field names
```

### Duplicate keys

```text
duplicate key
    → reject
```

Do not rely on a parser's silent “last value wins” behavior.

### Unknown fields

For compatible V1:

```text
unknown optional field
    → ignore during normalization
```

No guarantee of re-export preservation.

### Unknown enum values

Default contract:

```text
unknown enum with semantic impact
    → reject affected object/import
```

Do not let Kotlin serializer defaults silently choose a meaning.

---

# 28. Bounded ingestion

Initial acceptance limits:

```text
MAX_FILE_BYTES                  = 8 MiB
MAX_TASK_COUNT                  = 5,000
MAX_TOTAL_REMINDERS             = 25,000
MAX_REMINDERS_PER_TASK          = 50
MAX_STRING_LENGTH_BYTES         = 16 KiB
MAX_TAGS_PER_TASK               = 100
MAX_BLOCKED_WORD_COUNT          = 10,000
MAX_SCHEDULE_COUNT              = 1,000
MAX_PACKAGE_REFERENCES_PER_LIST = 2,000
MAX_PRESET_COUNT                = 500

MAX_JSON_DEPTH                  = 32
MAX_OBJECT_MEMBER_COUNT         = 256 per object
MAX_TOTAL_OBJECT_MEMBERS       = 25,000
MAX_TOTAL_ARRAY_ELEMENTS        = 50,000
```

These are FocusFlow acceptance limits, not Android guarantees.

Boundary tests:

```text
limit - 1
limit
limit + 1
```

Read bytes in bounded fashion before allocating an unbounded String.

Use a strict UTF-8 `CharsetDecoder` with malformed/unmappable input reported as errors.

---

# 29. External URI/provider compatibility

Minimum provider matrix:

```text
Android Files
Google Drive
one additional document provider
```

Two file origins:

```text
FocusFlow export → provider
manual copy/upload → provider
```

Record:

```text
provider authority
scheme
MIME
display filename
ACTION_VIEW offered?
URI received?
read succeeds?
preview opens?
```

## MIME vs extension

Content envelope validation is authoritative once FocusFlow receives the file.

Examples:

```text
backup.focusflow + text/plain + valid FocusFlow JSON
    → accept

backup.pdf + application/pdf + valid FocusFlow JSON
    → accept if FocusFlow receives it and content is valid

invalid FocusFlow envelope
    → reject
```

The manifest's MIME strategy affects discoverability; it must not be treated as trust.

## `file://`

V1:

```text
support only through dedicated safe reader
otherwise deterministic unsupported-source error
```

No accidental path manipulation.

---

# 30. External Android lifecycle

Required cases:

```text
cold ACTION_VIEW
warm ACTION_VIEW / onNewIntent
same URI twice rapidly
two different URI files rapidly
cancel
success
invalid file
provider read failure
provider disappears
process killed on confirmation
```

Transient intent dedupe is separate from product duplicate handling.

### Same URI

```text
same Android delivery
    → transiently coalesce

after cancel
    → same file may be reopened

after success
    → same file may be deliberately reopened again
```

### Different URI

```text
pending/active import exists
    → reject second import
```

Do not queue multiple destructive imports in V1.

---

# 31. Export behavior

TS uses Android `ACTION_CREATE_DOCUMENT`.

Target flow:

```text
build backup
    ↓
ACTION_CREATE_DOCUMENT
    ↓
user chooses destination
    ↓
write content URI
    ↓
close successfully
    ↓
report provider write success
```

User-facing success means:

```text
write + close succeeded
```

Optional reopen/readback is an automated verification test.

---

# 32. Scheduler architecture

The scheduler is derived state.

```text
committed persistent truth
        +
current device capability
        ↓
DesiredSchedulerState
        ↓
SchedulerReconciler
        ↓
OS scheduler / notifications
```

Do not model AlarmManager as a queryable database.

---

# 32A. Scheduler-registry boundary decision

**Frozen decision: Option A.**

The scheduler registry is persistent desired-state bookkeeping, not an ephemeral reconciliation cache.

Therefore:

```text
Task/Reminder persistent state
        +
Scheduler ownership/desired-state rows
        ↓
STORES_COMMITTED
        ↓
AlarmManager / notification reconciliation
```

`STORES_COMMITTED` means the database contains the exact desired scheduler ledger target. It does **not** mean the Android OS has already installed every derived registration.

### Crash-safe ordering

For scheduling:

```text
1. persist desired owned identity = PENDING/OWNED
2. commit persistent transaction
3. apply OS notification/alarm side effect
4. mark registry application state = APPLIED when the side effect call succeeds
```

If the process dies between steps 3 and 4:

```text
registry still declares ownership/desire
→ replay is deterministic
→ scheduling same identity replaces/reasserts the derived registration
```

For cancellation:

```text
1. persist tombstone/undesired state
2. commit
3. cancel known OS registration
4. remove/tombstone-clean registry entry after successful application
```

This makes reconciliation robust without claiming that AlarmManager can enumerate all existing registrations.

---

# 33. Scheduler registry

Use a persistent FocusFlow-owned identity ledger.

Suggested row:

```text
alarmKey
kind
taskId
reminderId if applicable
triggerAt
scheduler type
desired operation
reconciliation state
last success/failure
```

The registry is:

```text
ownership / desired-state metadata
```

not:

```text
proof that AlarmManager contains the alarm
```

---

# 34. Registry crash protocol

For create/update:

```text
persist registry = DESIRED/APPLY_PENDING
        ↓
perform OS scheduling side effect
        ↓
mark registry = APPLIED
```

Crash after the registry write but before OS scheduling:

```text
startup reconciliation
    → schedule same identity
```

Crash after OS scheduling but before registry APPLIED:

```text
startup reconciliation
    → replay same identity
    → Android's matching alarm identity replaces/updates prior schedule
    → mark APPLIED
```

For cancellation:

```text
persist registry = CANCEL_PENDING / tombstone
        ↓
cancel known owned OS identity
        ↓
remove registry entry
```

Crash after cancellation but before registry removal:

```text
startup reconciliation
    → repeat deterministic cancel
    → remove tombstone/entry
```

---

# 35. `FLAG_NO_CREATE` semantics

Use:

```text
PendingIntent.get...(..., FLAG_NO_CREATE)
```

only to answer:

```text
does a matching PendingIntent token exist?
```

Do not state:

```text
PendingIntent exists
→ AlarmManager registration exists
```

because those are different observations.

Known owned alarms are reconciled by deterministic identity.

Do not claim arbitrary AlarmManager enumeration.

---

# 36. Orphan alarm scope

Because AlarmManager does not provide a general application-owned alarm enumeration API, FocusFlow can only guarantee cleanup for:

```text
known FocusFlow-owned identities in the persistent registry
```

That is the correct boundary.

Do not use:

```text
AlarmManager.cancelAll()
```

for task-end reconciliation because other alarm categories may exist.

---

# 37. Reminder scheduler ownership

Task-end alarm:

```text
AlarmRepository
```

Generic task reminders:

```text
FocusFlowNotificationScheduler
```

All reminder identities must be persisted/reconciled.

Example:

```text
<taskId>-10m
<taskId>-5m
<taskId>-1m
<taskId>-0m
<taskId>-15m
<taskId>-30m
```

Use the existing TS slot/identifier semantics where compatible.

The generic scheduler should use a durable scheduled-notification registry rather than scanning an in-memory list.

---

# 38. Task-end alarm identity

Preferred exact identity:

```text
taskEnd:<restoredTaskId>
```

For the full-screen activity Intent:

```text
component = TaskAlarmActivity
action    = ACTION_TASK_END
data     = focusflow://task-end/<encoded-task-id>
extras   = taskId, taskName, endMs
```

This avoids depending on:

```text
stableInt(taskId)
```

being collision-free.

For the notification:

```text
tag = "task-end:" + taskId
id  = fixed integer in task-end namespace
```

Use:

```text
NotificationManager.notify(tag, id, notification)
NotificationManager.cancel(tag, id)
```

Android documents `(tag, id)` as the notification identity pair.

---

# 39. Alarm API choice

For task-end user-facing alarm:

```text
setAlarmClock()
```

is the preferred exact user-facing API when FocusFlow has the required exact-alarm capability.

Fallback:

```text
setExactAndAllowWhileIdle()
```

only when the product policy permits it and exact capability is available.

Do not silently downgrade an `EXACT` contract to inexact delivery.

If the product policy allows inexact reminders, those can use:

```text
setAndAllowWhileIdle()
```

or the appropriate scheduler strategy.

The final implementation must record the requested reminder timing class:

```text
EXACT
ACCEPTABLE_INEXACT
```

---

# 40. Exact timing test contract

Android does not provide a universal “10 seconds” guarantee.

If FocusFlow chooses:

```text
N = 10 seconds
```

then it means:

```text
FocusFlow controlled-test acceptance threshold
```

not:

```text
Android platform guarantee
```

Test:

```text
scheduled trigger time
→ receiver callback time
```

with controlled clock conditions.

Exclude/record:

```text
manual wall-clock changes
time-zone changes during measurement
device reboot during measurement
test device power-state transitions
```

---

# 41. Full-screen alarm/fronting contract

Do not use:

```text
FSI capability available
→ TaskAlarmActivity MUST always become visible
```

because Android may show heads-up presentation while the user is actively using the device.

Use this state model:

```text
DEVICE / SYSTEM STATE
        ↓
capability tuple
        ↓
expected surface
```

### Active device

```text
user actively using phone
→ alarm must surface without notification tap
→ Android may choose heads-up instead of FSI activity launch
```

### Locked/screen-off/background

```text
FSI permitted
→ attach full-screen PendingIntent
→ verify actual activity visibility/resume on supported devices
```

### FSI unavailable

```text
notification fallback
→ do not claim Activity launch is mandatory
```

### Notification permission denied/channel blocked

```text
explicit capability/failure state
```

---

# 42. Alarm capability tuple

Diagnostics:

```text
targetSdk
OS version
manufacturer/model

canPostNotifications
canUseFullScreenIntent
notificationChannelExists
notificationChannelImportance
notificationChannelBlocked

deviceScreenOn
keyguardLocked
appVisible

canScheduleExactAlarms
alarmAPIChosen

receiverFired
notificationPosted
FSIAttached
activityLaunchRequested
TaskAlarmActivity.created
TaskAlarmActivity.resumed/visible
```

A `startActivity()` call returning without exception is not proof of user-visible success.

The final acceptance is based on actual observed user-visible behavior interpreted through the device-state matrix.

---

# 43. Current alarm-fronting investigation

The user's reported symptom is:

```text
alarm scheduled
→ notification appears
→ alarm screen only appears after tapping notification
```

This remains an investigation target, not a source-proven regression.

The current Kotlin path is:

```text
TaskEndAlarmReceiver
    ↓
ForegroundTaskService.postTaskEndAlarmNotification()
    ↓
high-importance channel
    ↓
full-screen PendingIntent
    ↓
notification
    ↓
direct startActivity() attempt
```

The implementation must determine whether the observed failure is caused by:

```text
FSI permission
notification permission
channel state
global PendingIntent identity
target SDK behavior
background activity restrictions
device lock/screen state
OEM battery/power policy
or another FocusFlow issue
```

Compare:

```text
legacy TS runtime behavior, when executable
Kotlin current build
Kotlin migrated build
```

Do not infer TS native alarm behavior from the absence of native source in the TS archive.

---

# 44. Stale alarm callback protection

A task-end alarm may survive in derived state after the task has changed.

Before posting an alarm, `TaskEndAlarmReceiver` should validate:

```text
task exists
AND
task is still actionable
AND
task status is still scheduled/actionable
AND
current persistent endTime == alarm Intent endMs
```

If not:

```text
suppress stale notification/FSI
cancel known obsolete identity
return
```

This prevents:

```text
old alarm
→ restore/delete/edit task
→ stale TaskAlarmActivity
```

from surfacing.

For future edits to an end time:

```text
persistent truth changes
→ old task-end identity canceled
→ new task-end identity scheduled
```

---

# 45. Scheduler reconciler inputs

The reconciler uses:

```text
committed persistent task/reminder truth
+
current device capabilities
+
owned scheduler registry
+
reconstructible OS token observations where available
```

Not:

```text
RestorePlan capability snapshot
```

The RestorePlan's persistent decisions remain immutable.

Current capability is evaluated at reconciliation time.

Example:

```text
RestorePlan created:
    exact alarm capability = true

permission revoked before reconciliation

→ persistent restore remains unchanged
→ current reconciler sees exact capability unavailable
→ keep task-end alarm in `DEFERRED_EXACT_UNAVAILABLE`; do not downgrade the EXACT task-end surface to an inexact AlarmManager trigger
```

---

# 46. Reboot lifecycle

Android cancels AlarmManager alarms across reboot.

Required path:

```text
BOOT_COMPLETED / USER_UNLOCKED
    ↓
startup/reconciliation barrier
    ↓
load persistent truth
    ↓
load scheduler registry
    ↓
reconcile task/reminder alarms
```

Current Kotlin `BootReceiver` already has boot/user-unlocked hooks but presently focuses on enforcement and day-rating scheduling.

Add the FocusFlow scheduler reconciliation entry point.

If device is credential-locked at `BOOT_COMPLETED`:

```text
defer durable reconciliation until USER_UNLOCKED
```

Use WorkManager as a backstop if appropriate, not as a replacement for precise user-facing task-end alarms.

---

# 47. Exact-alarm permission lifecycle

Grant path:

```text
ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
    ↓
re-check canScheduleExactAlarms()
    ↓
reconcile
```

Revocation path:

```text
no grant broadcast to rely on
    ↓
detect on:
    startup
    resume
    return from system settings
    other explicit capability probes
    ↓
reconcile
```

When exact-alarm permission is revoked, future exact alarms are removed by Android, so persistent truth must remain authoritative and reconciliation must be able to recreate them after capability returns.

---

# 48. Notification channel and permission lifecycle

FSI depends on appropriate notification channel state.

Diagnostics distinguish:

```text
notification permission
channel existence
channel importance
channel blocked
FSI permission/capability
```

A user-lowered channel cannot simply be programmatically raised back to HIGH.

Therefore:

```text
user-lowered channel
→ report capability state
→ do not claim FocusFlow silently restored the channel
```

---

# 49. Normal task/reminder lifecycle must share scheduler model

The registry is not backup-only.

Normal operations must maintain the same desired-state model.

```text
create task
    → schedule desired identities

edit task end time
    → cancel obsolete identity
    → schedule new identity

delete task
    → remove persistent reminder
    → cancel known owned identity
    → remove registry entry

remove reminder
    → cancel its notification identity
    → remove registry entry
```

This ensures restore reconciliation and ordinary runtime behavior use the same identity model.

---

# 50. RestoreResult semantics

Counters are **final logical outcomes**, not attempt counters.

Example:

```text
tasksImported
tasksSkipped
taskDuplicates
taskConflicts
tasksReplaced

schedulerScheduled
schedulerCanceled
schedulerDeferred
schedulerFailures
```

They must not double count after replay.

Therefore:

```text
RestoreResult
    = derived from immutable RestorePlan
      + final reconciliation state
```

rather than incrementing on every replay attempt.

---

# 51. Package/resource results

RestorePlan records exact actions:

```text
KEEP_DORMANT
OMIT
CLEAR
UNRESOLVED
```

RestoreResult reports what actually happened.

Example:

```text
KEEP_DORMANT
→ unresolvedPackages += 1

OMIT
→ omittedPackages += 1

CLEAR resource
→ clearedResources += 1

UNRESOLVED resource
→ unresolvedResources += 1
```

Preview and completion results must describe the actual action, not merely the source backup count.

---

# 52. Error taxonomy

User-facing categories:

```text
UNSUPPORTED_FILE
INVALID_OR_CORRUPT_BACKUP
UNSUPPORTED_VERSION
DUPLICATE_KEY
INVALID_ENCODING
INPUT_TOO_LARGE
URI_READ_FAILED
PROVIDER_UNAVAILABLE
RESTORE_CONFLICT
ACTIVE_SESSION_PROHIBITED
PRECHECK_FAILED
PERSISTENCE_FAILED
RECOVERY_BLOCKED
SCHEDULER_RECONCILIATION_FAILED
```

Internal logs must avoid:

```text
full backup payload
full content URI
sensitive task content
```

Prefer:

```text
provider authority
MIME
size
session ID
operation
result
short hash prefix if useful
```

---

# 53. Target SDK/build facts

The supplied Kotlin archive does not establish the final Gradle target SDK configuration.

Before final Android behavior sign-off, record from the actual build:

```text
compileSdk
targetSdk
minSdk
build variant
relevant manifest permissions
```

This is required because FSI/exact-alarm/background-launch behavior is version/target sensitive.

---

# 54. Test strategy

## 54.1 Data compatibility

Mandatory:

```text
TS-generated V1 fixture
→ Kotlin import
→ semantic equivalence
```

Where both directions are intentionally supported:

```text
Kotlin export
→ TS import
→ semantic equivalence
```

Round-trip:

```text
export
→ import
→ export
→ normalized semantic compare
```

Not byte-for-byte.

---

# 55. Golden fixtures

Check in:

```text
minimal-v1.focusflow
full-settings-v1.focusflow
tasks-v1.focusflow
future-reminders-v1.focusflow
cross-feature-v1.focusflow
unknown-fields-v1.focusflow
invalid-utf8-v1.focusflow
duplicate-keys-v1.focusflow
unsupported-version-v2.focusflow
oversized.fixture
id-conflict-v1.focusflow
```

Also add:

```text
focusMirrorVpn-false-over-true-v1.focusflow
repeated-import-v1.focusflow
past-scheduled-v1.focusflow
missing-package-v1.focusflow
external-resource-missing-v1.focusflow
```

---

# 56. Task conflict tests

Required:

```text
unused ID
same ID + exact equivalent projection
same ID + divergent title
same ID + divergent endTime
same ID + status difference
same ID + reminder difference
duplicate IDs within incoming backup
repeated deliberate import
```

For divergent conflict:

```text
RestoreResult = RESTORE_CONFLICT
persistent state unchanged
scheduler unchanged
```

because there is no V1 conflict-resolution UI.

---

# 57. Restore lifecycle tests

```text
cold external ACTION_VIEW
warm onNewIntent
same URI twice
different URI twice
cancel + reopen
process death during confirmation
process death PREPARED
process death MUTATING
process death STORES_COMMITTED
process death RECONCILING
startup recovery before normal writer
normal task write during restore
normal settings write during restore
second restore during restore
```

---

# 58. Cross-store failure tests

Inject:

```text
Room failure
SharedPreferences commit failure
process death between store commits
process death after stores committed
process death before scheduler registry update
process death after OS schedule before registry APPLIED
process death after OS cancel before registry deletion
```

Acceptance:

```text
no mutation before MUTATING
recover exact plan after mutation starts
writers remain blocked while non-terminal
terminal state eventually deterministic
```

---

# 59. Scheduler tests

Required:

```text
future task restore
expired scheduled task restore
completed task restore
skipped task restore
multiple reminders per task
two task-end alarms close together
task edit after scheduling
task delete after scheduling
reminder delete
reboot
USER_UNLOCKED
exact-alarm grant
exact-alarm revoke
permission restored
channel lowered
notification permission denied
provider/OS scheduler failure
```

---

# 60. Multi-alarm cross-talk test

This is mandatory because of the current Kotlin globals:

```text
PI_TASK_ALARM = 7
TASK_ALARM_NOTIF_ID = 9101
```

Test:

```text
Task A end at T+30s
Task B end at T+45s

trigger A
trigger B
```

Verify:

```text
A activity gets A
B activity gets B
A notification does not update B
B notification does not update A
dismiss A does not dismiss B
```

---

# 61. Alarm-fronting runtime matrix

| Device state | Exact capability | Notification state | FSI capability | Expected |
|---|---|---|---|---|
| FocusFlow foreground | yes | enabled/high | yes | Alarm surfaces without tap; heads-up/in-app presentation accepted |
| Another app foreground | yes | enabled/high | yes | Alarm surfaces without tap; Android may choose heads-up |
| Screen off + locked | yes | enabled/high | yes | FSI requested; actual alarm surface observed |
| Screen off + locked | yes | enabled/high | no | Notification fallback; no false FSI claim |
| Screen on | no exact permission | enabled/high | yes | Product fallback; no false EXACT timing promise |
| Notification permission denied | any | blocked | any | explicit failure/capability state |
| Channel user-lowered | any | enabled | any | diagnostic state exposed; no silent channel-upgrade claim |
| OEM power restriction | varies | enabled | varies | classify device/OEM restriction separately |

---

# 62. Alarm-fronting instrumentation

Record:

```text
receiver fired
notification posted
fullScreenIntent attached
activity launch requested
TaskAlarmActivity created
TaskAlarmActivity resumed
TaskAlarmActivity visible
```

Do not equate:

```text
startActivity() returned
```

with:

```text
activity became visible
```

---

# 63. Restore UI

Both in-app and external import should converge on one confirmation UI.

Recommended:

```text
Import backup

Tasks: N
Settings/data: M
Blocked words: K
Schedules: S
Unresolved package references: P

[✓] Portable settings
[✓] Tasks & reminders

Task import:
( ) Merge with existing tasks
( ) Replace existing tasks

Impact:
    Add N tasks
    Replace M tasks
    Preserve historical local data
    X package references unresolved

[Cancel] [Import]
```

For ID conflicts discovered during planning:

```text
Import cannot continue

N task ID conflicts were found.
No data has been changed.

[Cancel]
```

No partial mutation to show a user-facing conflict.

---

# 64. RestorePlanner / UI boundary

UI supplies:

```kotlin
ImportOptions(
    importSettings = true,
    importTasks = true,
    taskMode = MERGE
)
```

Planner returns:

```kotlin
RestorePlan
```

UI never implements:

```text
settings merge
task dedupe
task replacement
alarm scheduling
transaction rules
```

---

# 65. Implementation sequence

## Phase A — Source/data audit — completed before implementation

The field-level matrix in §2A is the authoritative source/data evidence. Implementation proceeds only after reviewer acceptance of the matrix plus §4. A matrix row may describe observed legacy behavior, but the intended-policy column and §4 must contain the actual Kotlin V1 behavior with no implementation-agent choice left open.

## Phase A.1 — Mandatory baseline fixes discovered by source audit

Before backup/import behavior is considered operational, fix and test:

```text
1. TS SQLite settings key mismatch:
      `app_settings` is the authoritative TS key.
      Kotlin migration currently queries `appSettings`.

2. SharedPreferences namespace mismatch:
      current Kotlin SettingsRepository reads `focusday_prefs`.
      current migration writes migrated settings to `FocusFlowPrefs`.
      The migration must target the actual Kotlin source of truth.

3. Verify the legacy settings migration reads the existing hybrid DB before
   marking its migration marker. A missing/incorrect key must not permanently
   mark migration complete.

4. Establish one production NotificationScheduler implementation/wiring.
   The current archive exposes the interface but does not provide a wired
   production implementation through AppModule.

5. Establish the background task reminder gateway wiring if the periodic
   worker remains responsible for rearming notifications. The supplied archive
   does not call BackgroundFetchDependencies.install(...).

6. Retire duplicate backup restore logic by making one canonical domain flow;
   do not keep extending both BackupManager and BackupCoordinator independently.
```

## Phase B — Fix migration-baseline defects

Before import implementation:

```text
fix SharedPreferences namespace mismatch
record targetSdk/build facts
verify SettingsRepository source of truth
verify generic NotificationScheduler production wiring
```

## Phase C — Infrastructure

Implement:

```text
RestoreWriteGate
RestoreSessionStore
PendingImportStore
NormalizedBackup
RestorePlanner
RestorePlan canonicalizer/hash
startup recovery barrier
```

## Phase D — External import

Implement:

```text
ACTION_VIEW cold start
ACTION_VIEW onNewIntent
bounded read
strict parser
PendingImport snapshot
shared ImportConfirm UI
```

## Phase E — Persistent restore

Implement:

```text
Room transaction
SharedPreferences target commit
task conflict planning
task replacement
relationship mapping
cross-store recovery
```

## Phase F — Scheduler

Implement:

```text
Scheduler registry
FocusFlowNotificationScheduler
task reminder persistence
task-end deterministic identity
per-task full-screen PendingIntent
per-task notification identity
stale callback validation
reconciliation
```

## Phase G — Reboot / capability recovery

Implement:

```text
BOOT_COMPLETED / USER_UNLOCKED reconciliation
exact-alarm grant
exact-alarm revoke detection
notification/FSI capability diagnostics
```

## Phase H — Runtime alarm investigation

Compare:

```text
TS runtime, when executable
Kotlin current
Kotlin after changes
```

using the runtime matrix.

## Phase I — Regression suite

Run:

```text
golden fixtures
compatibility
conflicts
recovery
provider matrix
scheduler
alarm-fronting
reboot
```

---

# 66. Reviewer sign-off checklist

Implementation is not complete until:

```text
[ ] Decision Gate has no unresolved implementation-choice placeholders

[ ] TS/Kotlin field-level data matrix exists
[ ] portable/local/runtime/derived classification is frozen
[ ] Kotlin wire adapter uses stable V1 field names
[ ] focusMirrorVpnEnabled false-over-true behavior is corrected
[ ] legacy settings migration uses the actual runtime preference namespace
[ ] Settings restore does not use asynchronous ViewModel field-by-field writes

[ ] external ACTION_VIEW works cold-start
[ ] external ACTION_VIEW works warm/onNewIntent
[ ] cancel allows same file to reopen
[ ] second distinct external import is deterministically rejected
[ ] content envelope validation is authoritative
[ ] file:// behavior is deterministic
[ ] provider matrix passes

[ ] RestoreWriteGate is acquired before device snapshot
[ ] only one restore session can be admitted atomically
[ ] all conflicting persistent writers use same gate
[ ] startup recovery precedes normal writers
[ ] PREPARED/MUTATING/STORES_COMMITTED/RECONCILING recovery transitions tested
[ ] RECOVERY_BLOCKED keeps writers blocked
[ ] no post-mutation partial-state abandon path exists
[ ] exact RestorePlan is durable
[ ] RestorePlan integrity hash is non-circular
[ ] unsupported/invalid input performs zero persistent mutation

[ ] task ID equivalent duplicate is skipped
[ ] task ID divergent conflict blocks import before mutation
[ ] TaskDuplicateProjection is canonical and tested
[ ] reminder identity semantics are tested
[ ] replace scope is explicitly audited
[ ] historical local data is not silently deleted

[ ] settings conflict policy is frozen field-by-field
[ ] active-focus settings import matches contract
[ ] onboarding/privacy fields do not bypass target setup state
[ ] protectionMode valid enum is restored
[ ] active-focus replace is prohibited
[ ] package resolution uses exact package IDs
[ ] unresolved package/resource actions appear in RestorePlan and Result

[ ] input limits are enforced at exact boundaries
[ ] UTF-8 is strict
[ ] duplicate keys are rejected
[ ] unknown enum behavior is explicit
[ ] summary is informational only
[ ] platform/appVersion semantics are explicit

[ ] generic notification scheduler is actually wired/implemented
[ ] task-end alarms are recreated after restore
[ ] reminder notification identities are persistent/deterministic
[ ] scheduler registry crash ordering is tested
[ ] FLAG_NO_CREATE is not used as proof of AlarmManager existence
[ ] known obsolete FocusFlow-owned identities are cancelable
[ ] normal task/reminder deletion updates scheduler ownership

[ ] task-end PendingIntent identity is per task
[ ] task-end notification identity is per task
[ ] multi-task cross-talk test passes
[ ] stale task-end callback is suppressed
[ ] reboot rehydrates alarms
[ ] exact-alarm grant path reconciles
[ ] exact-alarm revocation is detected without relying on grant broadcast
[ ] channel/permission/FSI capability are diagnosed separately

[ ] active-device heads-up behavior is accepted per Android policy
[ ] locked/screen-off FSI behavior tested where permitted
[ ] actual TaskAlarmActivity visibility/resume measured
[ ] user notification tap is not required to surface an alarm
    except where documented platform/OEM capability prevents stronger presentation
[ ] user-visible behavior is not inferred from startActivity() return

[ ] TS V1 → Kotlin semantic compatibility passes
[ ] Kotlin export → TS import passes where supported
[ ] export/import round-trip semantic equivalence passes
[ ] unsupported version proves zero mutation
[ ] process-death recovery is deterministic
```

---

# 67. Final architecture target

```text
                 ┌────────────────────────────┐
                 │ In-app SAF picker           │
                 │ ACTION_OPEN_DOCUMENT        │
                 └─────────────┬──────────────┘
                               │
                 ┌─────────────┴──────────────┐
                 │ External ACTION_VIEW        │
                 │ Files / Drive / provider    │
                 └─────────────┬──────────────┘
                               ▼
                    ExternalImportCoordinator
                               │
                         bounded reader
                               │
                         strict parser
                               │
                      version migration
                               │
                       NormalizedBackup
                               │
                     PendingImportStore
                               │
                       ImportConfirm UI
                               │
                         user confirms
                               │
                      acquire RestoreWriteGate
                               │
                    CurrentDeviceState snapshot
                               │
                        RestorePlanner
                       (pure/deterministic)
                               │
                          RestorePlan
                               │
                     durable RestoreSession
                               │
                            MUTATING
                       ┌─────────┴─────────┐
                       │                   │
                    Room DB           SharedPrefs
                       │                   │
                       └─────────┬─────────┘
                                 ▼
                         STORES_COMMITTED
                                 │
                                 ▼
                       SchedulerReconciler
                         ┌───────┴────────┐
                         │                │
              NotificationScheduler   AlarmRepository
                         │                │
                         ▼                ▼
                 task reminder       task-end alarm
                 scheduling          + FSI notification
                         │                │
                         └───────┬────────┘
                                 ▼
                             COMPLETED
                                 │
                                 ▼
                         release write gate
```

The core product model remains:

```text
.focusflow
    =
portable FocusFlow persistent configuration
+
task/reminder data
+
metadata needed to reconstruct derived reminders

NOT:
    active runtime enforcement/session snapshot
    AND NOT:
    raw AlarmManager state dump
```

---

# 68. Official Android references

- PendingIntent identity / `FLAG_NO_CREATE`  
  https://developer.android.com/reference/kotlin/android/app/PendingIntent

- Intent matching / `filterEquals`  
  https://developer.android.com/reference/kotlin/android/content/Intent#filterEquals(android.content.Intent)

- AlarmManager / exact alarms / reboot  
  https://developer.android.com/reference/kotlin/android/app/AlarmManager

- Schedule alarms guide  
  https://developer.android.com/develop/background-work/services/alarms

- Notification full-screen behavior  
  https://developer.android.com/reference/kotlin/android/app/Notification.Builder

- NotificationManager FSI capability and `(tag, id)` identity  
  https://developer.android.com/reference/kotlin/android/app/NotificationManager

- Background activity launch security  
  https://developer.android.com/guide/components/activities/secure-bal

---

## 69A. v10 reviewer focus

The reviewer should specifically validate these source-derived findings before implementation:

```text
[ ] `app_settings` vs `appSettings` migration key is resolved correctly.
[ ] `FocusFlowPrefs` vs `focusday_prefs` namespace mismatch is resolved.
[ ] The source/data matrix in §2A is accepted row-by-row.
[ ] TS `greyoutSchedule` and `recurringBlockSchedules` remain distinct V1 fields.
[ ] Kotlin does not treat TS-local enforcement toggles as portable accidentally.
[ ] One production NotificationScheduler implementation is actually wired.
[ ] Background reminder rearming has a real production gateway if still required.
[ ] `Task.reminders[]` is not incorrectly equated with generated notification registrations.
[ ] Scheduler registry is explicitly part of the persistent restore target.
[ ] Data-policy matrix has no unresolved conditional choices.
```

The implementation agent must not proceed merely because the architecture passes review; the **source/data semantics** must be accepted first.

---

# 69. Reviewer decision request

This revision is intended to be the **last planning pass before implementation**.

The reviewer should verify three things separately:

```text
A. Does the source audit accurately describe TS/Kotlin?

B. Are the proposed product semantics acceptable?

C. Are the implementation constraints mechanically testable?
```

Do not approve implementation merely because the architecture looks correct.

Approval means:

```text
the remaining decisions are frozen
+
the source/data matrix exists
+
the test fixtures exist
+
the implementation agent has no material semantic choice left to invent
```


## 69B. Final pre-implementation gate — the last three required decisions

Before production implementation begins, the reviewer must record these three checks explicitly:

```text
[ ] EXACT unavailable policy accepted:
    persist task/reminder data, no inexact task-end alarm,
    mark DEFERRED_EXACT_UNAVAILABLE, surface/retry when capability returns.

[ ] PREPARED→MUTATING crash boundary accepted:
    RestoreSession is durably MUTATING before any persistent side effect;
    preferred Room implementation couples the phase change with first Room mutation.

[ ] NotificationScheduler source/build verification accepted:
    verify actual target repository/build; if concrete implementation is absent,
    implement/wire the pre-approved FocusFlowNotificationScheduler.
```

No implementation-agent discretion remains for these three points.

### Why the EXACT-unavailable policy is intentionally strict

The existing TS native path contains a coarse `setAndAllowWhileIdle()` fallback. That is useful evidence of legacy behavior, but it conflicts with the frozen Kotlin V1 contract that task-end timing is an `EXACT` product semantic. The migration therefore treats the inability to obtain exact-alarm capability as a **degraded capability state**, not as permission to silently downgrade the product contract. Android documents that modern exact-alarm access is capability-gated and recommends checking `canScheduleExactAlarms()` before scheduling; when access is unavailable, the app should degrade gracefully or direct the user to the special-access settings. citeturn245674search0turn245674search3

This preserves persistent user data while avoiding a misleading guarantee that an inexact task-end alarm is equivalent to the requested task-end alarm.
