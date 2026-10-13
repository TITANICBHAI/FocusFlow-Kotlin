# FocusFlow Import / Export — Flat Source Bundle

This archive intentionally has **no directory hierarchy**. Every source file is at the ZIP root; its filename encodes its original path using `__` separators. Parentheses in original paths are represented as underscores. See the inventory below for the exact original paths.

## Feature flows

### Export
1. `app/(tabs)/settings.tsx` calls `exportBackup`.
2. `src/services/backupService.ts` gathers all tasks and a portable projection of settings, creates the versioned `FocusFlowBackupV1` JSON envelope, names it with `.focusflow`, and calls the native save dialog.
3. `src/native-modules/NativeFilePickerModule.ts` bridges to `NativeFilePickerModule.kt`, which uses Android `ACTION_CREATE_DOCUMENT` and writes UTF-8 content to the user-chosen URI through `ContentResolver`.

### Import from Settings / returning-user profile
1. Settings or `app/user-profile.tsx` calls `pickAndImportBackup`.
2. The native picker selects any file type because `.focusflow` has no registered MIME type.
3. `backupService.ts` validates JSON/envelope, merges settings with current settings, and either merges tasks by ID or deletes existing tasks before importing. Past-due scheduled tasks become skipped; restored task insertion skips per-task alarm scheduling, then batch-schedules only tasks that remain scheduled.
4. `AppContext`, SQLite, and `notificationService` provide state persistence and task/reminder operations.

### Open a `.focusflow` file from Files / Drive / another provider
1. `android-native/manifest_additions.xml` and the related patch in `android-native/install.sh` associate Android VIEW intents with `.focusflow` files.
2. `app/_layout.tsx` receives the URI, asks the native bridge to read the URI via `ContentResolver`, validates the envelope, and stores its text in `pendingBackupImport.ts` memory storage.
3. Only an opaque import ID is passed to `app/import-confirm.tsx`; that screen previews counts and lets the user choose merge or replace before calling the same `restoreFromJson` engine.

## Important implementation details visible in the source

- Backups are JSON with `kind: "FocusFlowBackupV1"` and `version: 1`; the extension is `.focusflow`.
- Export strips several live/device-specific enforcement switches from the portable settings projection while retaining preset/configuration data; imported settings merge over the current device settings.
- Replace mode is blocked when a focus session is active. Replacement uses the context's `deleteTask` callback for existing rows, so task deletion behavior (including PIN checks, schedule compression, and reminders) is relevant.
- Import validates the envelope kind, settings object, and tasks array, but the current validation is intentionally shallow rather than a full schema validator.
- The bundle contains no dedicated import/export test file: the only test file in the supplied archive concerns an unrelated blocked-app dismissal policy.
- The provided archive does not include the generated Android app project or `MainActivity` source; Android association/configuration inputs available in the archive are included.

## Inventory

01. `src__services__backupService.ts`  
    Original: `src/services/backupService.ts`  
    Role: Canonical backup schema, portable settings projection, export, validation, merge/replace import, task restore rules, warnings.

02. `src__services__pendingBackupImport.ts`  
    Original: `src/services/pendingBackupImport.ts`  
    Role: In-memory staging/consumption/discard handoff for files opened directly from Android.

03. `src__native-modules__NativeFilePickerModule.ts`  
    Original: `src/native-modules/NativeFilePickerModule.ts`  
    Role: TypeScript API to select files, save backup content, and read incoming Android URIs.

04. `app___tabs___settings.tsx`  
    Original: `app/(tabs)/settings.tsx`  
    Role: Settings UI handlers for Export Backup and Import Backup, including merge vs replace selection.

05. `app__import-confirm.tsx`  
    Original: `app/import-confirm.tsx`  
    Role: Review/confirm UI for direct-open imports, including destructive replace warning and result summaries.

06. `app___layout.tsx`  
    Original: `app/_layout.tsx`  
    Role: Android incoming-file URI host, format pre-validation, staging, and route registration.

07. `app__user-profile.tsx`  
    Original: `app/user-profile.tsx`  
    Role: Returning-user/onboarding shortcut that imports a backup and marks onboarding complete.

08. `android-native__app__src__main__java__com__tbtechs__focusflow__modules__NativeFilePickerModule.kt`  
    Original: `android-native/app/src/main/java/com/tbtechs/focusflow/modules/NativeFilePickerModule.kt`  
    Role: Native Android Storage Access Framework read/write and ContentResolver URI implementation.

09. `android-native__app__src__main__java__com__tbtechs__focusflow__modules__FocusDayPackage.kt`  
    Original: `android-native/app/src/main/java/com/tbtechs/focusflow/modules/FocusDayPackage.kt`  
    Role: Registers NativeFilePickerModule with the React Native bridge.

10. `android-native__manifest_additions.xml`  
    Original: `android-native/manifest_additions.xml`  
    Role: Android VIEW intent filters for opening .focusflow backups from file/document providers.

11. `android-native__install.sh`  
    Original: `android-native/install.sh`  
    Role: Native setup/install script containing .focusflow intent-filter fallback and bridge registration guidance.

12. `src__context__AppContext.tsx`  
    Original: `src/context/AppContext.tsx`  
    Role: Settings/task state API used by restore callbacks; persistence, runtime sync, and task lifecycle behavior.

13. `src__data__database.ts`  
    Original: `src/data/database.ts`  
    Role: SQLite task/settings read/write/delete operations used by backup/restore.

14. `src__data__defaultSettings.ts`  
    Original: `src/data/defaultSettings.ts`  
    Role: Default settings used when importing partial/older settings and when settings are absent.

15. `src__data__types.ts`  
    Original: `src/data/types.ts`  
    Role: AppSettings, Task, session, and related data contracts serialized by the backup.

16. `src__services__allowanceUsageCache.ts`  
    Original: `src/services/allowanceUsageCache.ts`  
    Role: Context-side cache invalidation coupled to settings changes.

17. `src__services__taskService.ts`  
    Original: `src/services/taskService.ts`  
    Role: Task helper contracts directly imported by AppContext.

18. `src__services__taskOperationQueue.ts`  
    Original: `src/services/taskOperationQueue.ts`  
    Role: Serializes task operations used by add/delete/refresh during restore.

19. `src__services__schedulerEngine.ts`  
    Original: `src/services/schedulerEngine.ts`  
    Role: Schedule compression logic used if replacing/deleting tasks through AppContext.

20. `src__services__notificationService.ts`  
    Original: `src/services/notificationService.ts`  
    Role: Reminder cancellation and scheduling used by restore callbacks and task mutations.

21. `src__services__focusService.ts`  
    Original: `src/services/focusService.ts`  
    Role: Focus-mode bridge logic referenced by AppContext task/session handling.

22. `src__native-modules__SharedPrefsModule.ts`  
    Original: `src/native-modules/SharedPrefsModule.ts`  
    Role: Native preference synchronization called during settings updates.

23. `src__native-modules__SessionPinModule.ts`  
    Original: `src/native-modules/SessionPinModule.ts`  
    Role: Session PIN checks that can affect deletion through AppContext in replace mode.

24. `src__utils__recurringScheduleUtils.ts`  
    Original: `src/utils/recurringScheduleUtils.ts`  
    Role: Schedule mapping used when imported settings are synchronized to runtime policy.

25. `src__native-modules__ForegroundServiceModule.ts`  
    Original: `src/native-modules/ForegroundServiceModule.ts`  
    Role: Native foreground-task state bridge referenced by AppContext.

26. `src__native-modules__TaskAlarmModule.ts`  
    Original: `src/native-modules/TaskAlarmModule.ts`  
    Role: Native alarm bridge used by reminder/task services.

27. `src__services__eventBridge.ts`  
    Original: `src/services/eventBridge.ts`  
    Role: Native-to-JS events referenced by AppContext lifecycle/state wiring.

28. `src__native-modules__AversionsModule.ts`  
    Original: `src/native-modules/AversionsModule.ts`  
    Role: Native bridge used when imported protection/deterrent settings are synchronized.

29. `src__native-modules__GreyoutModule.ts`  
    Original: `src/native-modules/GreyoutModule.ts`  
    Role: Native bridge used when imported scheduled blocking settings are synchronized.

30. `src__native-modules__NetworkBlockModule.ts`  
    Original: `src/native-modules/NetworkBlockModule.ts`  
    Role: Native bridge used when imported VPN/blocking settings are synchronized.

31. `src__services__startupLogger.ts`  
    Original: `src/services/startupLogger.ts`  
    Role: Error/warning logging for incoming-file handling and context synchronization.

32. `src__services__setupPersistence.ts`  
    Original: `src/services/setupPersistence.ts`  
    Role: Secondary persistence path written by settings updates.

33. `src__utils__withTimeout.ts`  
    Original: `src/utils/withTimeout.ts`  
    Role: Timeout helper directly used by AppContext lifecycle/runtime operations.

## Scope note

This is a focused feature bundle, not a standalone buildable project. It includes all import/export entry points in the supplied archive, the native file I/O/Android file association path, and the main state/storage/reminder modules directly involved in restore. Unrelated UI/runtime modules that happen to be imported elsewhere in the full app have not been copied wholesale.
