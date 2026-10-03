package com.tbtechs.focusflow.data.restore

import com.tbtechs.focusflow.data.repository.AlarmRepository
import com.tbtechs.focusflow.data.repository.SettingsRepository
import com.tbtechs.focusflow.data.repository.TaskRepository
import java.time.Instant

/**
 * Android persistence and current M2 side-effect bridge for the M3 phase
 * runner. M4 can replace the alarm loop with its registry-backed reconciler
 * without changing the durable journal protocol.
 */
class AndroidRestorePhaseActions(
    private val taskRepository: TaskRepository,
    private val settingsRepository: SettingsRepository,
    private val alarmRepository: AlarmRepository,
) : RestorePhaseActions {

    override suspend fun applyTasks(plan: RestorePlan) {
        taskRepository.applyRestoreTaskPlan(plan)
    }

    override suspend fun applySettings(plan: RestorePlan) {
        // The journal runner retries this phase three times. Reapplying the
        // absolute plan is safe if the primary preference commit succeeded but
        // its protection-mode mirror did not.
        settingsRepository.applyPortableRestoreSettings(plan.settingsPlan)
    }

    override suspend fun reconcile(plan: RestorePlan) {
        settingsRepository.syncFromStoreAfterRestore()

        if (plan.mode == RestoreMode.REPLACE) {
            plan.preDeleteTaskIds.forEach { taskId ->
                check(alarmRepository.cancelAlarm(taskId)) {
                    "An alarm for a replaced task could not be cancelled."
                }
            }
        }

        val now = System.currentTimeMillis()
        taskRepository.getAllTasks()
            .asSequence()
            .filter { it.status == "scheduled" || it.status == "active" }
            .forEach { task ->
                val endMillis = runCatching { Instant.parse(task.endTime).toEpochMilli() }
                    .getOrElse { throw IllegalStateException("A restored task has an invalid end time.") }
                if (endMillis > now) {
                    check(alarmRepository.scheduleAlarm(task.id, task.title, endMillis)) {
                        "A restored task alarm could not be scheduled."
                    }
                } else {
                    check(alarmRepository.cancelAlarm(task.id)) {
                        "An obsolete restored task alarm could not be cancelled."
                    }
                }
            }
    }

    override suspend fun reconcileCurrentState() {
        settingsRepository.syncFromStoreAfterRestore()
        val tasks = taskRepository.getAllTasks()
        val now = System.currentTimeMillis()
        tasks.forEach { task ->
            if (task.status == "scheduled" || task.status == "active") {
                val endMillis = runCatching { Instant.parse(task.endTime).toEpochMilli() }
                    .getOrElse { return@forEach }
                if (endMillis > now) {
                    check(alarmRepository.scheduleAlarm(task.id, task.title, endMillis)) {
                        "A task alarm could not be scheduled during recovery."
                    }
                } else {
                    check(alarmRepository.cancelAlarm(task.id)) {
                        "An expired task alarm could not be cancelled during recovery."
                    }
                }
            }
        }
    }

    override suspend fun persistLastResult(plan: RestorePlan, afterInterruption: Boolean) {
        settingsRepository.persistLastRestoreResult(plan)
    }
}