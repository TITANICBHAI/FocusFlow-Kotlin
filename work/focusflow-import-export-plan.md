# FocusFlow Import / Export — Kotlin Rebuild Plan

## Source references available to the agent
| Reference | Role |
|---|---|
| `removed.zip` (in your workspace) | Authoritative current Kotlin codebase; the only codebase reference you need |
| This document | Complete behavioral and format spec; treat it as the implementation contract |

The TS hybrid app and the flat-zip reference bundle are **not available to the agent**. Everything you need to know about the required feature behavior is embedded in this document. Do not assume anything beyond what is stated here and what you find in `removed.zip`.

---

## 1. Read these files from `removed.zip` before writing anything

| File | What to extract |
|---|---|
| `data/backup/BackupJsonLimits.kt` | Size constants (`MAX_FILE_BYTES = 8 MiB`, `MAX_TASKS`, etc.) and the `BackupJsonPreflight` scanner — do not re-implement either |
| `data/backup/TsSettingsAdapter.kt` | The complete field-name constant table (TS key names) and `toSharedPreferencesValues()` — reuse both during import |
| `data/backup/LegacySettingsPolicy.kt` | The `deviceLocalKeys` set — this drives the portability filter on both export and import |
| `data/restore/RestoreGate.kt` | Coroutine mutex — every write during restore must go through it |
| `data/model/AppSettings.kt` | Current Kotlin settings shape and all field names |
| `data/model/Task.kt` | Task domain model; already `@Serializable` |
| `data/repository/SettingsRepository.kt` | Read/write API for SharedPreferences |
| `data/repository/TaskRepository.kt` | `insertTask`, `deleteTask`, `deleteAllTasks`, `getAllTasks` |
| `data/repository/FocusSessionRepository.kt` | `getActiveFocusSession()` — active-session guard |
| `data/repository/TaskAlarmReconciler.kt` | `reconcile()` — call this once after all task inserts, never per-task |
| `ui/SettingsViewModel.kt` | Has a `RestoreGate` instance and `updateSettings()` — understand how settings mutations are currently serialized |
| `ui/TaskViewModel.kt` | `addTask()`, `deleteTask()`, `clearAllTasks()` |
| `ui/navigation/Routes.kt` | Route constants you will extend |
| `ui/navigation/FocusFlowNavGraph.kt` | NavHost structure you will extend |
| `ui/settings/SettingsScreen.kt` | Where the Export/Import entry points will live |
| `MainActivity.kt` | Intent routing — you will extend `routeFromIntent` |

---

## 2. The `.focusflow` file format

`.focusflow` files are UTF-8 JSON with a versioned envelope. This format must be preserved exactly — it is the compatibility contract between devices and between app versions.

```json
{
  "kind": "FocusFlowBackupV1",
  "version": 1,
  "exportedAt": "2025-06-15T14:30:00.000Z",
  "exportedAtHuman": "6/15/2025, 2:30:00 PM",
  "appVersion": "c1.0.9",
  "platform": { "os": "android" },
  "settings": { /* portable settings — see §2.1 */ },
  "tasks": [ /* Task objects — see §2.2 */ ],
  "presetSections": [ /* section inventory — see §2.3 */ ],
  "summary": {
    "taskCount": 42,
    "blockedWordCount": 5,
    "greyoutWindowCount": 2,
    "dailyAllowanceCount": 3
  }
}
```

### 2.1 — The `settings` object

The `settings` field uses **TypeScript/JS field names** (camelCase, matching the `TsSettingsAdapter` constants in `removed.zip`), not Kotlin SharedPreferences key strings. On export, serialize `AppSettings` into this JS-named format. On import, run the JSON through `TsSettingsAdapter.parseLegacySettingsJson` → `toSharedPreferencesValues` exactly as the one-time DB migration already does — this reuses tested, proven adapter code.

**Portable fields** (include in the backup): everything in `AppSettings` that is not in the device-local list below.

**Device-local fields** (strip from the backup on export; do not overwrite on import): these represent live enforcement state that belongs to the device, not to the user's configuration:

```
standaloneBlockActive       standaloneBlockPackages       standaloneBlockVpnPackages
standaloneBlockUntilMs      alwaysBlockEnabled            networkBlockEnabled
focusModeEnabled            pomodoroEnabled               notificationsEnabled
weeklyReportEnabled         launcherEnabled               aversionDimmerEnabled
aversionVibrateEnabled      aversionSoundEnabled          systemGuardEnabled
blockInstallActionsEnabled  blockYoutubeShortsEnabled     blockInstagramReelsEnabled
vpnSelfHealEnabled          pinProtectionEnabled          autoCopyToAlwaysOn
```

This list matches `LegacySettingsPolicy.deviceLocalKeys` in `removed.zip`. Cross-reference both when building `PortableSettingsPolicy`.

**Exception:** `focusMirrorVpnEnabled` is **portable** and must survive backup/restore. It is an opt-in VPN policy that represents the user's intent, not a runtime enforcement toggle.

### 2.2 — Task objects

Tasks serialize directly from the `Task` data class in `removed.zip` (already `@Serializable`). Fields map 1:1 because the Kotlin `Task` model was designed to mirror the TS type. Use `kotlinx.serialization.json` for the task array.

### 2.3 — Preset sections

The `presetSections` array is a human-readable inventory of what's configured in each feature area. It is **not used to activate protections** — it is descriptive only, for portability and diagnostics. Import does not read this field. Export always writes it.

Seven sections, in this order:

| `id` | `name` | `configured = true when...` | Key data in `details` |
|---|---|---|---|
| `"focus-mode"` | `"Focus Mode"` | `allowedInFocus` is non-empty or `launcherPresets` has entries | `appPackages = allowedInFocus`, `itemCount = launcherPresets.size`, `details.allowedAppPresets = launcherPresets` |
| `"standalone-block"` | `"Standalone Block"` | `standaloneBlockPackages` or `standaloneBlockVpnPackages` non-empty | `appPackages`, `vpnPackages`, `details.runtimeState = "local-only"` |
| `"always-on"` | `"Always-On Blocking"` | `alwaysBlockPackages` or `alwaysOnVpnPackages` non-empty | `appPackages = alwaysBlockPackages`, `vpnPackages = alwaysOnVpnPackages` |
| `"daily-allowance"` | `"Daily Allowance"` | `dailyAllowanceConfigJson` non-null and non-empty | `itemCount`, `details.entries` from parsed JSON |
| `"keyword-blocker"` | `"Keyword Blocker"` | `blockedWords` non-empty | `itemCount = blockedWords.size`, `details.keywords = blockedWords` |
| `"block-schedules"` | `"Block Schedules"` | `recurringBlockSchedules` non-empty | `itemCount`, `details.windows = recurringBlockSchedules` |
| `"defense"` | `"Defense"` | `blockPresets` non-empty | `itemCount`, `details.blockPresets`, `details.overlayQuotes`, `details.overlayWallpaper` |

Derive these values from the `AppSettings` fields available in `removed.zip`. Use empty lists / `configured = false` when a field is absent or null.

---

## 3. Validation rules on import

Apply in this order before any restore operation:

1. File must not exceed 8 MiB — use `BackupJsonPreflight.readUtf8Bounded(inputStream)` from `removed.zip`
2. Content must be valid UTF-8 — the preflight enforces this
3. Run `BackupJsonPreflight.validateAndStripBom(text)` — enforces depth, node count, and JSON syntax bounds
4. Parse the resulting string as JSON
5. Root must be an object with `kind == "FocusFlowBackupV1"` — reject anything else with a clear message
6. `settings` must be a non-null object
7. `tasks` must be an array

All of steps 1–3 are already implemented in `removed.zip`. Do not re-implement them.

---

## 4. New files to create

### `data/backup/BackupEnvelope.kt`
Data classes for the envelope. No Android dependencies. Use `kotlinx.serialization.json.JsonObject` for the `settings` field to preserve raw JSON fidelity across round-trips.

```kotlin
data class BackupEnvelope(
    val kind: String,
    val version: Int,
    val exportedAt: String,
    val exportedAtHuman: String,
    val appVersion: String?,
    val platform: BackupPlatform,
    val settings: JsonObject,      // TS field names; parsed via TsSettingsAdapter on import
    val tasks: List<Task>,
    val presetSections: List<BackupPresetSection>,
    val summary: BackupSummary,
)

data class BackupPlatform(val os: String)   // always "android" on export

data class BackupPresetSection(
    val id: String,
    val name: String,
    val configured: Boolean,
    val appPackages: List<String>?,
    val vpnPackages: List<String>?,
    val itemCount: Int?,
    val details: JsonObject?,
)

data class BackupSummary(
    val taskCount: Int,
    val blockedWordCount: Int,
    val greyoutWindowCount: Int,
    val dailyAllowanceCount: Int,
)
```

---

### `data/backup/PortableSettingsPolicy.kt`
Thin object. Uses `TsSettingsAdapter` constants and `LegacySettingsPolicy` from `removed.zip`.

```kotlin
object PortableSettingsPolicy {

    fun toPortableJson(settings: AppSettings): JsonObject {
        // 1. Serialize AppSettings → JsonObject using kotlinx.serialization
        // 2. Map Kotlin field names → TsSettingsAdapter key names
        // 3. Remove all keys whose Kotlin field name is in the device-local list (§2.1)
        // 4. Ensure focusMirrorVpnEnabled is included (it is portable)
    }
}
```

For the field-name mapping, consult `TsSettingsAdapter`'s constants in `removed.zip` — it already has the authoritative Kotlin↔TS key correspondence used by the migration.

---

### `data/backup/BackupSerializer.kt`
Pure Kotlin, no Android Context. Builds and parses envelopes.

**`fun buildEnvelope(settings: AppSettings, tasks: List<Task>, appVersion: String?): BackupEnvelope`**
- `exportedAt` = `Instant.now().toString()`
- `exportedAtHuman` = human-readable local date-time string
- `platform.os = "android"`
- `settings` = `PortableSettingsPolicy.toPortableJson(settings)`
- `tasks` = the full task list (passed in by the caller, who fetched it from `TaskRepository`)
- `presetSections` = `buildPresetSections(settings)` — see §2.3 for the exact content of each section
- `summary` — counts derived from `settings` and `tasks`

**`fun serializeToJson(envelope: BackupEnvelope): String`**
- Pretty-printed JSON, `indent = 2` (matching the TS output)
- Tasks serialized via `kotlinx.serialization.json`

**`fun buildSuggestedFilename(): String`**
- `"focusflow-" + Instant.now().toString().replace(":", "-").take(19) + ".focusflow"`

**`fun parseAndValidate(json: String): BackupParseResult`**
- Apply validation rules from §3 (the preflight calls are already in `removed.zip`)
- Returns `BackupParseResult.Success(envelope)` or `BackupParseResult.Failure(message: String)`

---

### `data/backup/BackupRestoreEngine.kt`
The restore logic. All writes go through `RestoreGate`. This is the class that replaces the bug-prone old implementation.

```kotlin
class BackupRestoreEngine(
    private val settingsRepository: SettingsRepository,
    private val taskRepository: TaskRepository,
    private val focusSessionRepository: FocusSessionRepository,
    private val alarmReconciler: TaskAlarmReconciler,
    private val restoreGate: RestoreGate,
)
```

**`suspend fun restore(envelope: BackupEnvelope, currentSettings: AppSettings, replaceTasks: Boolean): RestoreResult`**

Execute these steps in order:

**Step 1 — Active session guard (only when `replaceTasks = true`)**
```kotlin
if (replaceTasks) {
    val active = focusSessionRepository.getActiveFocusSession()
    if (active != null) {
        return RestoreResult.Failure(
            "Cannot replace tasks while a Focus Session is running. " +
            "Stop the current session first."
        )
    }
}
```

**Step 2 — Restore settings**
```kotlin
restoreGate.write("BackupRestoreEngine.settings") {
    val source = TsSettingsAdapter.parseLegacySettingsJson(envelope.settings.toString())
    val prefs  = TsSettingsAdapter.toSharedPreferencesValues(source)
    prefs
        .filter { (key, _) -> LegacySettingsPolicy.mayMigrateKey(key) }
        .forEach { (key, value) ->
            when (value) {
                is LegacyPreferenceValue.StringValue  -> settingsRepository.putString(key, value.value)
                is LegacyPreferenceValue.BooleanValue -> /* settingsRepository.putBoolean(key, value.value) */
                is LegacyPreferenceValue.IntValue     -> /* settingsRepository.putInt(key, value.value) */
            }
        }
}
```

**Step 3 — Delete existing tasks (replace mode only)**
```kotlin
if (replaceTasks) {
    restoreGate.write("BackupRestoreEngine.deleteAll") {
        taskRepository.deleteAllTasks()
    }
}
```

**Step 4 — Insert tasks**

Fetch the authoritative existing-task ID set from the database (not from any in-memory view):
```kotlin
val existingIds: Set<String> = if (replaceTasks) emptySet()
    else taskRepository.getAllTasks().map { it.id }.toHashSet()
```

For each task in `envelope.tasks`:
- Skip if the task object is null, missing an `id`, or malformed
- Skip if `task.id` is in `existingIds` (merge mode)
- If `task.status == "scheduled"` and `task.endTime` is in the past → set `status = "skipped"`, update `updatedAt`
- Insert via `taskRepository.insertTask(task)` wrapped in `restoreGate.write`
- If the task was inserted as `"scheduled"`, add it to `tasksToSchedule`

**Step 5 — Alarm reconcile (once, after all inserts)**
```kotlin
if (tasksToSchedule.isNotEmpty()) {
    alarmReconciler.reconcile(reason = "backup-restore")
}
```
**Do not call alarm scheduling inside the insert loop.** One reconcile pass after all inserts is sufficient and avoids the repeated recovery events that occurred in the old implementation.

**Step 6 — Return result**
```kotlin
return RestoreResult.Success(
    tasksImported = importedCount,
    tasksSkipped  = skippedCount,
    warnings      = warningList,
)
```

---

### `data/backup/BackupFileManager.kt`
File I/O via Android ContentResolver. No React Native bridge; no third-party libraries.

```kotlin
object BackupFileManager {

    /**
     * Reads a content:// or file:// URI via ContentResolver.
     * Enforces the 8 MiB byte cap using BackupJsonPreflight.readUtf8Bounded.
     * Throws BackupJsonFormatException on oversize or malformed UTF-8.
     */
    fun readUri(contentResolver: ContentResolver, uri: Uri): String {
        val stream = contentResolver.openInputStream(uri)
            ?: throw IOException("Cannot open: $uri")
        return stream.use { BackupJsonPreflight.readUtf8Bounded(it) }
    }

    /**
     * Writes UTF-8 content to a content:// URI produced by ACTION_CREATE_DOCUMENT.
     */
    fun writeToUri(contentResolver: ContentResolver, uri: Uri, content: String) {
        contentResolver.openOutputStream(uri)?.use { out ->
            out.write(content.toByteArray(Charsets.UTF_8))
            out.flush()
        } ?: throw IOException("Cannot write to: $uri")
    }
}
```

---

### `ui/backup/BackupViewModel.kt`
New ViewModel. Owns the export and import state machines. Calls `BackupRestoreEngine` for the actual restore; calls `SettingsViewModel.refreshSettingsFromStore()` after import so the UI reflects the imported settings.

```kotlin
sealed class ExportState {
    object Idle : ExportState()
    object Building : ExportState()
    data class Success(val uri: String) : ExportState()
    data class Error(val message: String) : ExportState()
}

sealed class ImportState {
    object Idle : ImportState()
    object Reading : ImportState()
    data class PendingConfirm(val envelope: BackupEnvelope) : ImportState()
    object Restoring : ImportState()
    data class Success(val tasksImported: Int, val tasksSkipped: Int, val warnings: List<String>) : ImportState()
    data class Error(val message: String) : ImportState()
}
```

**`fun beginExport(contentResolver: ContentResolver, uri: Uri)`**
- Collect tasks from `TaskRepository.getAllTasks()`
- Collect settings from `SettingsRepository.readAppSettings()` or the current `SettingsViewModel` state
- Call `BackupSerializer.buildEnvelope(...)` then `serializeToJson(...)`
- Call `BackupFileManager.writeToUri(contentResolver, uri, json)`
- Emit `ExportState.Success` or `ExportState.Error`

**`fun beginImport(contentResolver: ContentResolver, uri: Uri)`**
- Emit `ImportState.Reading`
- Call `BackupFileManager.readUri(contentResolver, uri)`
- Call `BackupSerializer.parseAndValidate(text)`
- On failure → emit `ImportState.Error(message)`
- On success → emit `ImportState.PendingConfirm(envelope)` — hold the envelope in the ViewModel state

**`fun confirmImport(replaceTasks: Boolean)`**
- Requires `importState.value` is `PendingConfirm`
- Emit `ImportState.Restoring`
- Call `BackupRestoreEngine.restore(envelope, currentSettings, replaceTasks)`
- On success → call `settingsViewModel.refreshSettingsFromStore()` then emit `ImportState.Success`
- On failure → emit `ImportState.Error(message)`

**`fun cancelImport()`**
- Discard the pending envelope
- Emit `ImportState.Idle`

**`fun resetExport()`** / **`fun resetImport()`** — return to Idle after the caller has consumed the result.

---

### `ui/backup/ImportConfirmScreen.kt`
New Compose screen. Navigated to from SettingsScreen when `importState` reaches `PendingConfirm`.

**UI elements:**
- Top bar: "Restore Backup" title, back arrow that calls `BackupViewModel.cancelImport()`
- Summary card showing:
  - Exported date (`envelope.exportedAtHuman`)
  - App version that created the file (`envelope.appVersion`, or "unknown")
  - Tasks: `envelope.summary.taskCount`
  - Blocked words: `envelope.summary.blockedWordCount`
  - Schedule windows: `envelope.summary.greyoutWindowCount`
  - Daily allowances: `envelope.summary.dailyAllowanceCount`
- "Replace all existing tasks" switch — **off by default**
  - When turned on, show a warning: "This will permanently delete all your current tasks."
- Primary button: "Restore" → calls `BackupViewModel.confirmImport(replaceTasks)`
  - Disabled and shows `CircularProgressIndicator` while `importState == Restoring`
- On `ImportState.Success`: show AlertDialog with import count summary, dismiss navigates to `Routes.SETTINGS`
- On `ImportState.Error`: show AlertDialog with the error message, dismiss returns to this screen (user can try again or cancel)

---

### `BackupImportIntentRelay.kt` (small singleton, package: `com.tbtechs.focusflow`)
Holds a pending file URI between `MainActivity.routeFromIntent()` and the point where `SettingsScreen` can read it.

```kotlin
object BackupImportIntentRelay {
    @Volatile private var pendingUri: Uri? = null

    fun stage(uri: Uri) { pendingUri = uri }
    fun consume(): Uri? = pendingUri.also { pendingUri = null }
}
```

---

## 5. Files to modify

### `ui/navigation/Routes.kt`
Add:
```kotlin
const val IMPORT_CONFIRM = "import_confirm"
```
Add `IMPORT_CONFIRM` to `architectureRoutes`. Do **not** add it to `externalLinkableRoutes` — this screen is only reachable from within the app's import flow.

---

### `ui/navigation/FocusFlowNavGraph.kt`
Add the composable alongside the existing pattern:
```kotlin
composable(Routes.IMPORT_CONFIRM) {
    ScreenBoundary(Routes.IMPORT_CONFIRM) {
        ImportConfirmScreen(
            onDone   = { navigate(Routes.SETTINGS) },
            onCancel = ::goBack,
        )
    }
}
```
Wire `BackupViewModel` at this scope, or hoist it to `MainActivity` and pass it as a parameter matching the pattern used by `SettingsViewModel`.

---

### `ui/settings/SettingsScreen.kt`
Add a "Backup & Restore" section. Placement: after the Profile/Account row, before the About/Support rows.

**Export row:**
```kotlin
// Declare at the top of the composable:
val createDocumentLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/octet-stream")
) { uri: Uri? ->
    if (uri != null) backupViewModel.beginExport(context.contentResolver, uri)
}

// In the settings list:
SettingsRow(
    icon  = Icons.Outlined.Upload,      // or Save/Backup
    label = "Export backup",
    sub   = "Save your settings and tasks to a .focusflow file",
    onClick = {
        createDocumentLauncher.launch(BackupSerializer.buildSuggestedFilename())
    }
)
```

**Import row:**
```kotlin
val openDocumentLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
) { uri: Uri? ->
    if (uri != null) backupViewModel.beginImport(context.contentResolver, uri)
}

SettingsRow(
    icon  = Icons.Outlined.Download,    // or Restore
    label = "Import backup",
    sub   = "Restore settings and tasks from a .focusflow file",
    onClick = { openDocumentLauncher.launch(arrayOf("*/*")) }  // */* so .focusflow is visible
)
```

**React to import state:**
```kotlin
val importState by backupViewModel.importState.collectAsState()

LaunchedEffect(importState) {
    if (importState is ImportState.PendingConfirm) {
        navigate(Routes.IMPORT_CONFIRM)
    }
}
```

**React to export state** with a snackbar or `AlertDialog` for Success/Error; call `backupViewModel.resetExport()` after display.

**React to intent-staged imports** (file opened from the Files app):
```kotlin
LaunchedEffect(Unit) {
    val uri = BackupImportIntentRelay.consume()
    if (uri != null) {
        backupViewModel.beginImport(context.contentResolver, uri)
    }
}
```

---

### `MainActivity.kt`
Extend `routeFromIntent` to handle `.focusflow` file-open intents from external apps:

```kotlin
private fun routeFromIntent(intent: Intent?): String {
    // Existing: day-rating action
    if (intent?.action == LauncherActivity.ACTION_OPEN_DAY_RATING) return Routes.FOCUS

    // New: .focusflow file opened from Files, Downloads, Drive, etc.
    if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
        BackupImportIntentRelay.stage(intent.data!!)
        return Routes.SETTINGS  // land on Settings; SettingsScreen reads the relay on appearance
    }

    return Routes.fromPath(intent?.data?.path)
}
```

No other changes needed in `MainActivity`.

---

### `AndroidManifest.xml`
Add intent filters to `MainActivity` so `.focusflow` files can be opened from file managers, Gmail, Google Drive, and any other document provider:

```xml
<!-- Files sent with application/octet-stream MIME (most common) -->
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:mimeType="application/octet-stream" />
</intent-filter>

<!-- Fallback: provider sends no MIME type or an unrecognised one -->
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:mimeType="*/*" />
    <data android:pathPattern=".*\\.focusflow" />
</intent-filter>
```

No `<provider>` element and no FileProvider are needed. `ACTION_CREATE_DOCUMENT` writes via `ContentResolver` directly to the URI that Android provides — the app never exposes its own file paths.

---

## 6. What NOT to do

These are the specific failure modes from the old Kotlin implementation. Build around them.

| Do not | Because |
|---|---|
| Write to `SettingsRepository` or `TaskRepository` outside `restoreGate.write()` during restore | Concurrent writes race against SharedPreferences observers; this is the most likely cause of the Step 3 hang |
| Use the in-memory task list for collision detection | The in-memory list may be a partial view; call `taskRepository.getAllTasks()` for the authoritative set |
| Schedule alarms inside the task-insert loop | Calling the alarm system per-task during a batch restore fires the app's startup recovery logic repeatedly, causing the repeating recovery dialogs |
| Copy the old Kotlin import implementation | It was removed because it had the bugs above. Build fresh from this spec |
| Check active session from SharedPreferences flags | Call `focusSessionRepository.getActiveFocusSession()` against the Room DB; it repairs orphaned rows and gives a reliable answer |
| Run restore on the main thread | `BackupRestoreEngine.restore()` is a `suspend fun`; launch it on `Dispatchers.IO` from `BackupViewModel` |
| Re-implement `BackupJsonPreflight` | It exists in `removed.zip`; call it |

---

## 7. Implementation sequence

Work in this order. Each phase is independently testable before the next begins.

### Phase 1 — Data models and serialization (zero Android dependencies)
1. `data/backup/BackupEnvelope.kt`
2. `data/backup/PortableSettingsPolicy.kt`
3. `data/backup/BackupSerializer.kt`

**Test:** Unit-test round-trip `AppSettings → buildEnvelope → serializeToJson → parseAndValidate`. Verify portable fields survive; device-local fields are absent; `focusMirrorVpnEnabled` is present. Verify that a JSON string with `kind: "WrongValue"` fails validation.

### Phase 2 — Restore engine
4. `data/backup/BackupRestoreEngine.kt`

**Test:** With fake repositories: merge mode skips duplicate IDs; replace mode deletes then inserts; past-scheduled tasks become `"skipped"`; active session blocks replace mode; `alarmReconciler.reconcile()` is called exactly once; every write is inside `restoreGate.write()`.

### Phase 3 — File I/O
5. `data/backup/BackupFileManager.kt`

**Test:** Verify 8 MiB rejection, UTF-8 enforcement (delegates to `BackupJsonPreflight`), write-then-read round-trip produces identical bytes.

### Phase 4 — ViewModel
6. `BackupImportIntentRelay.kt`
7. `ui/backup/BackupViewModel.kt`

### Phase 5 — UI
8. `ui/backup/ImportConfirmScreen.kt`
9. Export/Import section in `ui/settings/SettingsScreen.kt`

### Phase 6 — Navigation and plumbing
10. `ui/navigation/Routes.kt` — add `IMPORT_CONFIRM`
11. `ui/navigation/FocusFlowNavGraph.kt` — add composable
12. `MainActivity.kt` — extend `routeFromIntent`
13. `AndroidManifest.xml` — add intent filters

---

## 8. Acceptance checklist

A correct implementation satisfies all of the following.

### Export
- [ ] "Export backup" row appears in Settings
- [ ] Tapping it opens the system Save dialog with the suggested filename (`focusflow-YYYY-MM-DDTHH-MM-SS.focusflow`)
- [ ] File can be saved to Downloads, Google Drive, and other SAF targets
- [ ] Exported file is valid JSON with `kind: "FocusFlowBackupV1"` and `version: 1`
- [ ] File does not contain device-local fields (standalone block state, PIN, VPN toggle, etc.)
- [ ] File contains all tasks, all portable settings, preset sections, and a correct summary
- [ ] Cancelling the Save dialog returns to Settings without error or state corruption

### Import (triggered from Settings)
- [ ] "Import backup" row appears in Settings
- [ ] Tapping it opens the system file picker showing all file types
- [ ] Selecting a valid `.focusflow` file navigates to ImportConfirmScreen
- [ ] ImportConfirmScreen shows exported date, app version, and all four summary counts
- [ ] "Replace tasks" toggle is off by default; enabling it shows a deletion warning
- [ ] Confirming with merge mode: existing tasks are kept; only new task IDs are inserted
- [ ] Confirming with replace mode: all existing tasks are deleted; backup tasks are inserted
- [ ] Past-scheduled tasks from the backup are imported as `"skipped"`, not `"scheduled"`
- [ ] Future scheduled tasks receive alarm reminders after import (single reconcile pass)
- [ ] Settings are merged: portable fields are updated, device-local fields remain unchanged
- [ ] Attempting replace mode while a Focus Session is active shows an error; no data is modified
- [ ] Cancelling at ImportConfirmScreen discards the envelope without modifying any data
- [ ] Invalid JSON, wrong `kind`, missing `settings`, or missing `tasks` show a clear error message

### Import (triggered by opening a `.focusflow` file externally)
- [ ] Opening a `.focusflow` file from the Files app, Gmail, or Drive launches the app
- [ ] App lands on Settings and immediately navigates to ImportConfirmScreen with the file's content
- [ ] All import behavior from that point is identical to the in-app flow

### Stability
- [ ] Import completes without hanging at any step (no Step 3 hang)
- [ ] No recovery dialogs appear after a successful import
- [ ] Importing during a Focus Session (merge mode) completes without disrupting the session

---

## 9. Files the agent must not touch

These are not related to Import/Export. Any change to them is out of scope.

- `enforcement/AppBlockerAccessibilityService.kt`
- `enforcement/ForegroundTaskService.kt`
- `data/repository/VpnRepository.kt`, `AlarmRepository.kt`
- `analytics/` — all files
- `notifications/` — all files (call `alarmReconciler.reconcile()` but do not modify notification logic)
- `ui/focus/FocusScreen.kt`, `ui/active/ActiveScreen.kt`, `ui/stats/` — all files
- `data/local/FocusFlowDatabase.kt` — no schema changes are needed
- `data/backup/BackupJsonLimits.kt` — already correct; read and use, do not modify
- `data/backup/TsSettingsAdapter.kt` — read and reuse; do not modify
- `data/backup/LegacySettingsMigration.kt`, `LegacySettingsPolicy.kt` — read and reuse; do not modify

---

## 10. No React Native bridge — pure Kotlin only

The current app is a pure Kotlin/Compose application. It has no React Native runtime, no JS bridge, and no bridge module infrastructure. You must not introduce any of the following:

- `ReactContextBaseJavaModule` or any `React*` import
- `NativeFilePickerModule.kt` (this was a React Native bridge module for the old hybrid app — it is irrelevant here)
- `FocusDayPackage.kt` (React Native module registry — irrelevant here)
- Any `@ReactMethod` annotation

File picking and saving are handled entirely with Android's built-in Activity Result API:
- **Save:** `ActivityResultContracts.CreateDocument("application/octet-stream")`
- **Open:** `ActivityResultContracts.OpenDocument()`
- **Read/write content URIs:** `ContentResolver.openInputStream(uri)` / `openOutputStream(uri)`

This is simpler and more reliable than the bridge approach. No third-party libraries are needed.

---

## 11. If you hit an edge case not covered by this plan

Ask for the specific file you need by naming it exactly like this:

> "I need `tshybrid.zip / src/services/backupService.ts`"

or

> "I need `FocusFlow-import-export-files-flat.zip / app__import-confirm.tsx`"

The two available reference zips and their contents:

**`tshybrid.zip`** — the full older TypeScript/hybrid app source:
```
src/services/backupService.ts          ← complete export/import logic and format spec
src/services/pendingBackupImport.ts    ← intent-staging pattern
src/native-modules/NativeFilePickerModule.ts  ← TS-side bridge API (for understanding only)
src/data/types.ts                      ← Task, AppSettings, and all type definitions
src/context/AppContext.tsx             ← how settings/tasks were accessed in context
src/data/database.ts                   ← DB read/write functions used by backupService
```

**`FocusFlow-import-export-files-flat.zip`** — extracted import/export files from the hybrid project:
```
services__backupService.ts             ← same as tshybrid.zip version
services__pendingBackupImport.ts
app__import-confirm.tsx                ← the import confirmation screen (TS/Expo Router)
app-tabs__settings.tsx                 ← settings screen with export/import buttons
app__user-profile.tsx                  ← user profile screen
app__root-layout.tsx                   ← root layout / intent handling
android__NativeFilePickerModule.kt     ← bridge module (ignore; not applicable to pure Kotlin)
android__FocusDayPackage.kt            ← bridge registry (ignore; not applicable to pure Kotlin)
test__backupService.test.ts            ← unit tests for backupService
docs__FOCUSFLOW_FEATURES_PLAN_TRACKING.md
```

Only ask for a file if this plan genuinely does not cover what you need. Prefer reading `removed.zip` first — especially `TsSettingsAdapter.kt` — before reaching for the reference zips.
