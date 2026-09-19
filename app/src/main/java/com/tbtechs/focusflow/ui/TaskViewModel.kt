package com.tbtechs.focusflow.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tbtechs.focusflow.data.model.Task
import com.tbtechs.focusflow.data.repository.AlarmRepository
import com.tbtechs.focusflow.data.repository.TaskRepository
import java.time.Instant
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manages the task list and exposes CRUD operations backed by Room.
 *
 * ## State
 * - [tasks]: reactive [StateFlow] fed by [TaskRepository.observeAllTasks].
 *   Emits a new list whenever any task is inserted, updated, or deleted.
 *
 * ## Mutating methods
 * All mutating calls launch a coroutine on [viewModelScope] and delegate to
 * [TaskRepository]. Status changes (completing, skipping) update the task in
 * place and stamp [Task.updatedAt].
 *
 * ## PIN enforcement on deletion
 * [deleteTask] and [clearAllTasks] accept a caller-supplied [pinHash]. When the
 * user has configured a session PIN (via Settings), the deletion must not
 * proceed unless the PIN has been verified. The PIN check is delegated to
 * [beforeTaskDelete] and [beforeClearTasks] lambdas passed at construction time
 * (typically wired to [SettingsViewModel.verifySessionPin]).
 *
 * ## Separation of concerns
 * This ViewModel only owns task records. Stopping a running focus session when a
 * task is completed/skipped is [FocusSessionViewModel]'s responsibility.
 */
class TaskViewModel(
    private val taskRepository: TaskRepository,
    private val alarmRepository: AlarmRepository,
    private val beforeTaskDelete: suspend (taskId: String, pinHash: String?) -> Unit = { _, _ -> },
    private val beforeClearTasks: suspend (pinHash: String?) -> Unit = { _ -> },
) : ViewModel() {

    // ─── State ────────────────────────────────────────────────────────────────

    /**
     * Reactive stream of all tasks ordered by start time.
     * Room re-emits on every write; UI collectors see updates without explicit refresh.
     *
     * Backing call: [TaskRepository.observeAllTasks] → [TaskDao.observeAllTasks]
     */
    val tasks: StateFlow<List<Task>> = taskRepository.observeAllTasks()
        .stateIn(
            scope          = viewModelScope,
            started        = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue   = emptyList(),
        )

    // ─── Mutating methods ─────────────────────────────────────────────────────
    //
    // Room serializes individual writes, but that is not enough to protect the
    // UI snapshot: a refresh can read before a mutation commits and publish an
    // older list after the mutation. Keep reads and writes behind the same
    // boundary and resolve each action from the latest persisted snapshot.
    private val taskOperationMutex = Mutex()

    /**
     * Inserts [task] into the database. Duplicate IDs are silently ignored
     * (INSERT OR IGNORE semantics from [TaskRepository.insertTask]).
     *
     * Backing call: [TaskRepository.insertTask] → [TaskDao.insertTask]
     */
    fun addTask(task: Task) {
        viewModelScope.launch {
            taskOperationMutex.withLock { taskRepository.insertTask(task) }
            val endMs = runCatching {
                Instant.parse(task.endTime).toEpochMilli()
            }.getOrNull() ?: return@launch
            if (endMs > System.currentTimeMillis()) {
                alarmRepository.scheduleAlarm(task.id, task.title, endMs)
            }
        }
    }

    /**
     * Updates [task] by primary key.
     *
     * Backing call: [TaskRepository.updateTask] → [TaskDao.updateTask]
     */
    fun updateTask(task: Task) {
        viewModelScope.launch {
            taskOperationMutex.withLock { taskRepository.updateTask(task) }
            // Cancel old alarm then reschedule for updated end time
            alarmRepository.cancelAlarm(task.id)
            val endMs = runCatching {
                Instant.parse(task.endTime).toEpochMilli()
            }.getOrNull() ?: return@launch
            if (endMs > System.currentTimeMillis()) {
                alarmRepository.scheduleAlarm(task.id, task.title, endMs)
            }
        }
    }

    /**
     * Deletes the task with [taskId].
     *
     * Calls [beforeTaskDelete] before mutating Room. If [beforeTaskDelete]
     * throws (e.g. invalid PIN), the deletion does not proceed.
     *
     * Backing call: [TaskRepository.deleteTask] → [TaskDao.deleteTask]
     */
    fun deleteTask(taskId: String, pinHash: String? = null) {
        viewModelScope.launch {
            taskOperationMutex.withLock {
                beforeTaskDelete(taskId, pinHash)
                alarmRepository.cancelAlarm(taskId)
                alarmRepository.dismissAlarm(taskId)
                taskRepository.deleteTask(taskId)
            }
        }
    }

    /**
     * Deletes all tasks from the database.
     *
     * Calls [beforeClearTasks] before mutating Room. If [beforeClearTasks]
     * throws, the clear does not proceed.
     *
     * Backing call: [TaskRepository.clearAllTasks] → [TaskDao.clearAllTasks]
     */
    fun clearAllTasks(pinHash: String? = null) {
        viewModelScope.launch {
            taskOperationMutex.withLock {
                beforeClearTasks(pinHash)
                val allTasks = taskRepository.getAllTasks()
                allTasks.forEach {
                    alarmRepository.cancelAlarm(it.id)
                    alarmRepository.dismissAlarm(it.id)
                }
                taskRepository.clearAllTasks()
            }
        }
    }

    /**
     * Deletes all tasks except the one with [excludedTaskId].
     * Used by Settings when an active focus session must be preserved.
     */
    fun clearAllTasksExcept(excludedTaskId: String, pinHash: String? = null) {
        viewModelScope.launch {
            taskOperationMutex.withLock {
                beforeClearTasks(pinHash)
                val allTasks = taskRepository.getAllTasks()
                allTasks.filter { it.id != excludedTaskId }.forEach {
                    alarmRepository.cancelAlarm(it.id)
                    alarmRepository.dismissAlarm(it.id)
                }
                taskRepository.clearAllTasksExcept(excludedTaskId)
            }
        }
    }

    /**
     * Marks the task with [taskId] as "completed" and stamps [updatedAt].
     * No-op if the task is already completed or skipped.
     *
     * Backing call: [TaskRepository.updateTask] (status mutation happens in ViewModel)
     */
    fun completeTask(taskId: String) {
        viewModelScope.launch {
            taskOperationMutex.withLock {
                val task = taskRepository.getAllTasks().firstOrNull { it.id == taskId } ?: return@withLock
                if (task.status == "completed" || task.status == "skipped") return@withLock
                alarmRepository.cancelAlarm(taskId)
                alarmRepository.dismissAlarm(taskId)
                taskRepository.updateTask(task.copy(status = "completed", updatedAt = Instant.now().toString()))
            }
        }
    }

    /**
     * Marks the task with [taskId] as "skipped" and stamps [updatedAt].
     * No-op if the task is not found in [tasks].
     *
     * Backing call: [TaskRepository.updateTask] (status mutation happens in ViewModel)
     */
    fun skipTask(taskId: String) {
        viewModelScope.launch {
            taskOperationMutex.withLock {
                val task = taskRepository.getAllTasks().firstOrNull { it.id == taskId } ?: return@withLock
                if (task.status == "completed" || task.status == "skipped") return@withLock
                alarmRepository.cancelAlarm(taskId)
                alarmRepository.dismissAlarm(taskId)
                taskRepository.updateTask(task.copy(status = "skipped", updatedAt = Instant.now().toString()))
            }
        }
    }

    /**
     * Extends the end time and duration of the task with [taskId] by [minutes].
     * No-op if the task is not found in [tasks].
     *
     * Backing call: [TaskRepository.updateTask] (time mutation happens in ViewModel)
     */
    fun extendTaskTime(taskId: String, minutes: Int) {
        viewModelScope.launch {
            taskOperationMutex.withLock {
                val task = taskRepository.getAllTasks().firstOrNull { it.id == taskId } ?: return@withLock
                val newEnd = Instant.parse(task.endTime).plusMillis(minutes * 60_000L)
                taskRepository.updateTask(
                    task.copy(
                        endTime = newEnd.toString(),
                        durationMinutes = task.durationMinutes + minutes,
                        updatedAt = Instant.now().toString(),
                    ),
                )
                // Reschedule end-time alarm for the new extended time
                alarmRepository.cancelAlarm(taskId)
                val newEndMs = newEnd.toEpochMilli()
                if (newEndMs > System.currentTimeMillis()) {
                    alarmRepository.scheduleAlarm(taskId, task.title, newEndMs)
                }
            }
        }
    }
}
