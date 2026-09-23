# Focus Bugs

## Audit scope

This audit covers Tasks, Focus Mode, the enforcement layer, boot recovery,
notification actions, and the coordination comments in the FocusFlow source.
The detailed findings below are the original audit baseline. The current
verification pass is recorded after the workaround section and supersedes the
original open/closed state where it differs.

### Immediate workaround

If the app is currently stuck in Focus Mode, **Emergency Override** on the
Focus screen is safe to use. It follows the same PIN-gated
`stopFocusMode(hash)` path as **Stop Focus**, ends the Room session, and is
visible while `isFocusing` is true.

The standalone **Block apps while I work** toggle uses independent
`SharedPrefs` state. Stopping Focus Mode does not change that toggle, and
changing the toggle does not stop Focus Mode.

## Current verification status

The latest source comparison confirms that **23 findings have an
implementation present and are source-verified**. Runtime verification is
still pending for those items, so they remain tracked as implementation
present rather than fully verified.

### Remaining functional bug

### T6 — `SchedulerEngine` is type-correct but unwired

- **Severity:** High
- **Files:** `SchedulerEngine.kt`, `DefenseScreen.kt`, `TaskViewModel.kt`,
  `AppModule.kt`

The original type-mismatch finding is fixed. `SchedulerEngine` now aliases the
real persisted `data.model.Task`, and its status and priority constants match
the app's lowercase string values.

The engine is still never instantiated or called. The only references outside
its own file are comments describing an intended dependency. The Defense
screen persists `autoRescheduleEnabled`, but task completion, skipping, and
deletion never read that setting or invoke the engine. Therefore the toggle's
promise that freeing time moves later tasks is currently a placebo.

`compressSchedule()` and `compressDeletedTaskGap()` exist. There is no
dedicated skip-gap operation; deciding whether skipping should use the delete
gap behavior is a product decision that must be made before wiring it.

The fix requires injecting/instantiating the engine and calling it from the
relevant task mutation paths while retaining the existing task-operation lock.
The overrun path's returned auto-skip proposals should also remain subject to
user confirmation rather than silently removing tasks.

### Informational and non-bug findings

- **AB5:** Reboot alone does not repair an orphaned Room session, but this is
  no longer a code defect because `AppBootViewModel` repairs stale sessions
  during app startup, including process-death cases without a reboot.
- **AB7:** An empty per-task allow-list means all apps are allowed when no
  daily allowance rule exists. This is the documented least-strict behavior,
  not a bug.

The 23 source-verified implementation fixes are: T1–T5, F1–F7, TF1–TF6,
AB1–AB4, AB6, and the FLAG-1/2/3 comment cleanup represented by F2 and the
related coordination notes. They should not be counted as remaining code bugs
unless runtime verification finds a regression.

## Priority order

The recommended order by blast radius is:

`F1 → TF2 → TF1 → F3 → TF3 → F4 → F5 → TF4 → AB4 → T1 → T3 → F6 → TF5 → AB1 → T2 → T4 → TF6 → F7 → AB2 → AB6 → T5 → F2 → AB3 → T6 → AB5`

## Part 1 — Task-only bugs

### T1 — `extendTaskTime` has no task-status guard

- **Severity:** High
- **File:** `TaskViewModel.kt`

`extendTaskTime()` looks up a task and changes its end time without rejecting
completed or skipped tasks. `completeTask()` and `skipTask()` already reject
those statuses.

This means extending a completed task can:

1. Move its `endTime` forward in Room.
2. Schedule a new `AlarmManager` alarm.
3. Produce a “Time's Up” notification for a task that was already completed.

**Fix:** Add the same completed/skipped guard at the top of the
`taskOperationMutex.withLock` block.

```kotlin
if (task.status == "completed" || task.status == "skipped") {
    return@withLock
}
```

### T2 — `updateTask` reschedules alarms regardless of status

- **Severity:** Medium
- **File:** `TaskViewModel.kt`

After saving an edited task, `updateTask()` cancels the existing alarm and
schedules a new one whenever the end time is in the future. It does not check
whether the task is completed or skipped.

Editing a completed task with a future end time therefore creates a ghost
“Time's Up” notification.

**Fix:** Only schedule when the task is not completed or skipped.

```kotlin
if (endMs > System.currentTimeMillis() &&
    task.status !in setOf("completed", "skipped")) {
    alarmRepository.scheduleAlarm(...)
}
```

### T3 — Alarm operations are outside the mutex

- **Severity:** Medium
- **File:** `TaskViewModel.kt`

`addTask()` and `updateTask()` protect the Room write with
`taskOperationMutex`, but perform alarm scheduling after the lock is released.

This creates two problems:

- **Crash window:** the process can die after the Room write and before the
  alarm is scheduled. There is no boot-time recovery for missing task alarms.
- **Rapid-edit race:** two updates can cancel and schedule alarms out of order,
  leaving the alarm for an older edit.

**Fix:** Move the alarm cancellation and scheduling operations inside
`taskOperationMutex.withLock` in both methods.

### T4 — Single-task lookups load the entire tasks table

- **Severity:** Medium
- **Files:** `TaskViewModel.kt`, `TaskDao.kt`

`completeTask()`, `skipTask()`, and `extendTaskTime()` all use:

```kotlin
taskRepository.getAllTasks().firstOrNull { it.id == taskId }
```

`getAllTasks()` emits the full table, even though each operation needs one row.
On schedules with 50 or more tasks, every status mutation loads unnecessary
data from Room.

**Fix:** Add a targeted DAO query and use it in all three callers.

```kotlin
@Query("SELECT * FROM tasks WHERE id = :taskId LIMIT 1")
suspend fun getTaskById(taskId: String): TaskEntity?
```

### T5 — `addTask` does not validate `endTime > startTime`

- **Severity:** Low
- **File:** `TaskViewModel.kt`

A task with `endTime <= startTime` is inserted successfully. It appears
overdue immediately, receives no alarm, and can show the “Time's Up” banner
as soon as it is created.

**Fix:** Validate the time range before insertion and surface a UI error where
appropriate.

```kotlin
val start = Instant.parse(task.startTime)
val end = Instant.parse(task.endTime)
if (!end.isAfter(start)) return
```

### T6 — `SchedulerEngine` is type-correct but unwired

- **Severity:** High
- **Files:** `SchedulerEngine.kt`, `DefenseScreen.kt`, `TaskViewModel.kt`,
  `AppModule.kt`

The original type mismatch is fixed: the engine now aliases the real domain
`Task`, and its string status/priority constants match the persisted model.
However, the engine is still never instantiated or called anywhere.

`autoRescheduleEnabled` is persisted by Settings but never read by task
completion, skipping, or deletion. The Defense screen therefore exposes a
user-facing toggle whose promised behavior does nothing.

`compressSchedule()` and `compressDeletedTaskGap()` exist, but no dedicated
skip-gap operation exists. The skip behavior must be decided before wiring
the engine into task mutations.

There is also a safety concern: `rebalanceAfterOverrun()` can automatically
skip medium-priority tasks when `cumulativeShift` exceeds
`maxAutoShiftMinutes`, without user confirmation. Wiring the engine without a
review step could silently remove tasks.

## Part 2 — Focus Mode-only bugs

### F1 — `ACTION_TASK_ENDED` is broadcast but received by nobody

- **Severity:** Critical
- **Files:** `ForegroundTaskService.kt`, `FocusSessionViewModel.kt`

When the focus timer reaches zero, the service:

1. Calls `clearFocusActive()`.
2. Sends `ACTION_TASK_ENDED`.
3. Posts the “Time's Up” notification.
4. Goes idle.

`clearFocusActive()` only changes `SharedPrefs`; it does not end the Room
focus session. `ACTION_TASK_ENDED` is defined and sent, but no receiver exists
anywhere in the source.

As a result:

- `focus_sessions.is_active` remains `1`.
- `observeActiveFocusSession()` continues emitting the session.
- `FocusSessionViewModel` keeps `isFocusing` true.
- The Focus screen displays an ever-growing overdue timer.
- App blocking stops while the UI still says Focus Mode is active.

This is the architectural root cause of the reported stuck state. The existing
PIN-free `stopServiceInternal()` path is suitable for an authorized system
transition, but it currently has no automatic orphan-session trigger.

**Fix:** Register a non-exported receiver in `FocusSessionViewModel`, end the
active Room session when the broadcast arrives, and unregister it in
`onCleared()`.

```kotlin
private val taskEndedReceiver = object : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        viewModelScope.launch {
            focusSessionRepository.getActiveFocusSession()?.let { session ->
                focusSessionRepository.endFocusSession(session.taskId)
            }
            _focusSession.value = null
        }
    }
}
```

Register it with `ContextCompat.RECEIVER_NOT_EXPORTED` using an
`IntentFilter(ForegroundTaskService.ACTION_TASK_ENDED)`.

### F2 — The FLAG-1 comment is factually wrong

- **Severity:** Low
- **Type:** Documentation
- **File:** `FocusSessionViewModel.kt`

The FLAG-1 comment says `focusSession` is backed by a `MutableStateFlow` rather
than a reactive Room flow and claims the DAO has no active-session flow.

The current code does the opposite:

- `FocusSessionDao.observeActiveSession()` returns
  `Flow<FocusSessionEntity?>`.
- `FocusSessionRepository.observeActiveFocusSession()` exposes it.
- `FocusSessionViewModel.init` collects it into `_focusSession`.

The stale comment could cause future developers to add an unnecessary manual
reload and miss the real F1 issue.

**Fix:** Delete FLAG-1 or rewrite it to describe the current reactive
implementation.

### F3 — `stopFocusModeAwait` has no exception handling

- **Severity:** Critical
- **File:** `FocusSessionViewModel.kt`

`stopFocusModeAwait()` calls `setFocusActive()` before ending the session,
clearing the active task, or stopping the service. If a PIN is configured and
no hash is supplied, `setFocusActive()` throws
`SessionPinRequiredException`.

Because `stopFocusMode()` launches the function without a `try/catch`, the
coroutine ends silently. The result is:

- Room session remains active.
- `SharedPrefs` remains unchanged.
- The service keeps running.
- `_focusSession` remains non-null.
- The user receives no error or PIN prompt.

This affects the Done button, the schedule screen check button, and
`stopAfterConfirmation()`. The explicit Stop Focus button is the only path
that currently gates on the PIN first.

**Fix:** Catch `SessionPinRequiredException` and emit a UI event that opens
the PIN dialog.

```kotlin
fun stopFocusMode(pinHash: String? = null) {
    viewModelScope.launch {
        try {
            stopFocusModeAwait(pinHash)
        } catch (e: SessionPinRequiredException) {
            _stopRequiresPinEvent.emit(Unit)
        }
    }
}
```

### F4 — Focus can start on a completed or skipped task

- **Severity:** High
- **File:** `FocusSessionViewModel.kt`

`startFocusMode()` resolves a task but does not reject completed or skipped
statuses. It can therefore create an active Room session for a task that is
already done.

With `keepFocusActiveUntilTaskEnd = true`, Done does not stop the session, and
`completeTask()` becomes a no-op. The only escape is the PIN-gated Stop Focus
path.

**Fix:**

```kotlin
if (task.status in setOf("completed", "skipped")) return@launch
```

### F5 — `startFocusMode` has no double-session guard

- **Severity:** High
- **File:** `FocusSessionViewModel.kt`

Two rapid activation taps can create two active Room rows. The active-session
query returns only the newest row, while `endSession(taskId)` ends by task ID.
Ending the visible session can leave the older row orphaned and active.

**Fix:** Return early when either the in-memory state or the repository already
contains an active session.

```kotlin
if (_focusSession.value != null) return@launch
if (focusSessionRepository.getActiveFocusSession() != null) return@launch
```

### F6 — Orphaned sessions inflate today's focus statistics

- **Severity:** High
- **File:** `FocusSessionRepository.kt`

`getTodayFocusMinutes()` uses the current time for sessions whose `endedAt` is
null, capped at six hours per session. Orphans created by F1, F5, or a crash
can therefore add up to six false hours each.

The lifetime aggregate also counts orphaned rows in `total_sessions` without
matching focus minutes, lowering the calculated average.

**Fix:** Repair stale active sessions on boot and when the session list loads.

```kotlin
suspend fun repairOrphanedSessions() {
    val cutoff = Instant.now().minus(12, ChronoUnit.HOURS).toString()
    focusSessionDao.endOrphanedSessions(cutoff, Instant.now().toString())
}
```

```sql
UPDATE focus_sessions
SET is_active = 0, ended_at = :now
WHERE is_active = 1
  AND ended_at IS NULL
  AND started_at < :cutoff
```

### F7 — SharedPrefs are written before the service starts

- **Severity:** Low
- **File:** `FocusSessionViewModel.kt`

`startFocusMode()` writes `focus_active=true`, the active task, and the
allowed packages before starting the foreground service.

During that gap, `AppBlockerAccessibilityService` can observe
`focus_active=true` and block apps before the foreground notification appears.
On Android 12 and later this can cause a foreground-service start exception or
leave the user briefly blocked without context.

**Fix:** Start the service first and write the blocking state only after the
service is available.

## Part 3 — Intertwined task and Focus Mode bugs

### TF1 — Extending a task does not update the foreground service

- **Severity:** Critical
- **File:** `TaskViewModel.kt`

`extendTaskTime()` updates Room and reschedules `AlarmManager`, but never calls
`ForegroundServiceController.updateNotification()`. The running service keeps
the original `endTimeMs` in memory.

The original end time then causes the service to stop blocking and post a
“Time's Up” notification. The rescheduled alarm posts another notification at
the new end time.

**Fix:** Update the foreground service after scheduling the new alarm.

```kotlin
foregroundServiceController.updateNotification(
    taskId = taskId,
    taskName = task.title,
    endTimeMs = newEndMs,
    nextName = null,
)
```

### TF2 — The default keep-alive setting plus F1 creates a permanent stuck state

- **Severity:** Critical
- **Files:** `TaskViewModel.kt`, `ForegroundTaskService.kt`,
  `FocusSessionViewModel.kt`, `HomeScreen.kt`

The failure sequence is:

1. The user presses Done.
2. The task becomes completed.
3. `keepFocusActiveUntilTaskEnd=true` prevents `stopFocusMode()`.
4. The service reaches zero, clears `focus_active`, posts the notification, and
   goes idle.
5. F1 leaves the Room session active.
6. The reactive session flow keeps `isFocusing=true`.
7. The UI shows a completed task as overdue.
8. Done, Skip, and Extend cannot end the session because their task operations
   are no-ops or lack a session cleanup path.

**Fixes required together:**

- Fix F1 so the task-ended broadcast ends the Room session.
- Make Done stop Focus Mode when the task is overdue, regardless of the
  keep-alive setting.
- Fix TF3 so completed tasks cannot remain the active banner task.

### TF3 — HomeScreen active task has no status filter

- **Severity:** High
- **File:** `HomeScreen.kt`

The current lookup selects the session's task by ID without excluding
completed or skipped tasks. An orphaned session therefore wins over the normal
running-task lookup.

The banner then displays “TIME'S UP” for the completed task, and its buttons
cannot resolve the stuck session.

**Fix:**

```kotlin
val activeTask = remember(todayTasks, focusSession) {
    todayTasks
        .filter { it.status !in setOf("completed", "skipped") }
        .firstOrNull { it.id == focusSession?.taskId }
        ?: todayTasks.firstOrNull(Task::isRunningNow)
}
```

### TF4 — Completing or skipping a task does not cancel its AlarmManager alarm

- **Severity:** High
- **File:** `TaskViewModel.kt`

`completeTask()` calls `dismissAlarm()`, which dismisses the notification
activity and sends `ACTION_DISMISS_ALARM`, but it does not cancel the pending
`AlarmManager` entry. The alarm can still reach
`TaskEndAlarmReceiver` and post “Time's Up” for a completed task.

The same issue exists for `skipTask()`. It is especially visible when the
foreground service is still running with the default keep-alive behavior.

**Fix:** Call `alarmRepository.cancelAlarm(taskId)` inside the mutex block in
both `completeTask()` and `skipTask()`.

### TF5 — `startFocusMode` races with task completion

- **Severity:** Medium
- **Files:** `FocusSessionViewModel.kt`, `TaskViewModel.kt`

`startFocusMode()` reads from `observeAllTasks().first()` without the task
operation mutex. It can read a pre-update snapshot while `completeTask()` is
changing the same task.

The result can be a focus session for a task that is completed in Room
immediately afterward.

**Fix:** The F4 status check is required, and a shared or dedicated mutex should
be considered for the task lookup/session creation path.

### TF6 — `clearAllTasksExcept` stops the preserved task's active session

- **Severity:** Medium
- **File:** `TaskViewModel.kt`

`clearAllTasksExcept()` calls `beforeClearTasks()` and unconditionally stops
the active focus session before deleting all other tasks. If the active session
belongs to `excludedTaskId`, the preserved task remains in the database but
loses its focus session.

**Fix:** Only stop the session when its task ID differs from the preserved ID.

```kotlin
val active = focusSessionViewModel.focusSession.value
if (active != null && active.taskId != excludedTaskId) {
    focusSessionViewModel.stopFocusModeAwait(pinHash)
}
```

## Part 4 — Enforcement, boot, and notification-action chain

### AB1 — Retry checks ignore the task-end cutoff

- **Severity:** Medium
- **File:** `AppBlockerAccessibilityService.kt`

The main enforcement path treats focus as active only when `task_end_ms` has
not passed. `scheduleRetryCheck()` reads the raw `focus_active` flag instead.

In the short window after the task ends but before the foreground service
clears the flag, a retry can re-block or re-kick an app.

**Fix:** Extract one end-time-aware `focusActive` helper and use it in both
paths.

### AB2 — Home-screen reminders use the raw focus flag

- **Severity:** Low
- **File:** `AppBlockerAccessibilityService.kt`

`postHomeScreenReminder()` has the same raw-flag gap as AB1 and can post a
“Focus session is running” reminder after the task end time.

**Fix:** Reuse the shared end-time-aware focus check.

### AB3 — `ACTION_NOTIF_ACTION` is dead bridge code

- **Severity:** Low
- **File:** `NotificationActionReceiver.kt`

The receiver sends `ACTION_NOTIF_ACTION` for immediate handling, but the
broadcast is not received anywhere. The working replay path is the pending
notification action in `SharedPrefs`, consumed by `MainActivity` through
`onNewIntent` and its `LaunchedEffect`.

**Fix:** Remove or rewrite the misleading comment and either remove the
vestigial broadcast or add an intentional receiver. Do not remove the
`SharedPrefs` fallback unless a replacement is verified.

### AB4 — Pending notification actions expire silently after five minutes

- **Severity:** High
- **File:** `MainActivity.kt`

Actions older than five minutes are dropped without a toast, retry, or visible
log. A slow cold start or delayed unlock can therefore make Done, Extend, or
Skip appear to do nothing.

**Fix:** Log expiry and show feedback such as:

> That action expired — open the task to try again.

### AB5 — Reboot does not clear an F1 orphaned session

- **Severity:** Informational
- **File:** `BootReceiver.kt`

Boot recovery reads `SharedPrefs` but does not repair Room sessions. In the
reported stuck state, `clearFocusActive()` has already set `focus_active=false`,
so boot correctly starts the service idle while the orphaned Room row survives.

The session returns when the app launches again. Reboot is not a workaround
until F1 or an equivalent orphan-repair path is fixed.

### AB6 — `clearFocusActive()` clears only one of roughly seven session keys

- **Severity:** Low
- **File:** `ForegroundTaskService.kt`

The method clears `focus_active` but leaves `task_end_ms`, `task_name`,
`next_task_name`, `task_duration_ms`, and `task_last_written_ms` stale.

This currently self-heals because a new session rewrites the fields, so it is
not independently harmful. It does explain why AB1 and AB2 can read
stale-but-real values during the race window.

**Fix:** Optionally clear the full session key set for hygiene. Combine this
with AB1 and AB2 rather than treating it as the primary correctness fix.

### AB7 — Empty per-task allow-list blocks nothing by design

- **Severity:** N/A
- **Status:** Not a bug

When `focusAllowedPackages` is empty or null and no daily-allowance entries are
configured, no apps are added to the block list. This matches the project's
least-strict default: an empty per-task allow-list means all apps are allowed.

## Part 5 — FLAG comment audit

The `FocusSessionViewModel.kt` header contains FLAG-1 through FLAG-5 notes
intended for later pipeline stages. Three are stale:

### FLAG-1 — Stale

It claims the active session is not backed by a reactive Room flow and
proposes adding the DAO flow that already exists. `loadActiveSession()` is
dead code with no callers.

### FLAG-2 — Stale

It claims `focusViolationApp` has no backing source. The accessibility service
already writes `PREF_CURRENT_VIOLATION_APP`, and the ViewModel already listens,
seeds the initial value, and clears it at session end.

### FLAG-3 — Stale with a harmless consistency issue

`AppModule` already provides `ForegroundServiceController`, but
`FocusSessionViewModel` constructs a second instance directly. The class is
stateless, so this is harmless, but converging on the injected instance would
make the dependency graph clearer.

### FLAG-4 — Accurate

It correctly describes the current task resolution through
`observeAllTasks().first()`, although it does not cover the TF5 mutex race.

### FLAG-5 — Accurate

It correctly describes the `allowed_packages` fallback path. Only minor
cleanup is suggested.

Stale coordination comments can cause later work to re-implement fixes that
already shipped or add a manual reload that masks the real F1 session-ending
gap. Clean up FLAG-1 and FLAG-2 while working in this file.

## Summary table

| ID | Severity | Area | Finding |
|---|---|---|---|
| T1 | High | Tasks | `extendTaskTime` has no status guard and can alarm completed tasks |
| T2 | Medium | Tasks | `updateTask` reschedules alarms regardless of status |
| T3 | Medium | Tasks | Alarm operations outside the mutex create a crash gap and race |
| T4 | Medium | Tasks | Status mutations scan the full table instead of using `getTaskById` |
| T5 | Low | Tasks | No `endTime > startTime` validation on insert |
| T6 | High | Tasks | `SchedulerEngine` is type-correct but unwired; Auto-reschedule is a placebo |
| F1 | Critical | Focus | `ACTION_TASK_ENDED` has no receiver, so the Room session never ends |
| F2 | Low | Focus | FLAG-1 incorrectly says the session flow is non-reactive |
| F3 | Critical | Focus | PIN exceptions silently abort `stopFocusModeAwait` |
| F4 | High | Focus | Focus can start on completed or skipped tasks |
| F5 | High | Focus | No double-session guard allows orphaned sessions |
| F6 | High | Focus | Orphans can inflate today's focus minutes by up to six hours each |
| F7 | Low | Focus | SharedPrefs enable blocking before the foreground service starts |
| TF1 | Critical | Both | Extending a task does not update the service end time |
| TF2 | Critical | Both | Default keep-alive plus F1 creates the permanent stuck state |
| TF3 | High | Both | `activeTask` has no completed/skipped status filter |
| TF4 | High | Both | Done/Skip do not cancel the pending AlarmManager alarm |
| TF5 | Medium | Both | Focus activation can race with task completion |
| TF6 | Medium | Both | Clearing other tasks can stop the preserved task's session |
| AB1 | Medium | Focus | Retry checks skip the `task_end_ms` cutoff |
| AB2 | Low | Focus | Home-screen reminders use the raw focus flag |
| AB3 | Low | Both | `ACTION_NOTIF_ACTION` is an unreceived bridge broadcast |
| AB4 | High | Both | Pending notification actions expire silently after five minutes |
| AB5 | Info | Focus | Reboot does not clear an F1 orphaned session |
| AB6 | Low | Focus | `clearFocusActive()` clears only one session preference key |
| AB7 | N/A | Focus | Empty allow-list blocks nothing by design |