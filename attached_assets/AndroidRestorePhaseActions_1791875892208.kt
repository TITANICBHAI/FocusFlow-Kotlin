package com.tbtechs.focusflow.data.restore

import com.tbtechs.focusflow.data.repository.SettingsRepository
import com.tbtechs.focusflow.data.repository.TaskAlarmReconciler
import com.tbtechs.focusflow.data.repository.TaskRepository

/**
 * Android persistence and derived-state bridge for the restore phase runner.
 * Alarm state is rebuilt through the registry-backed reconciler while the
 * restore gate remains closed.
 */
class AndroidRestorePhaseActions(
    private val taskRepository: TaskRepository,
    private val settingsRepository: SettingsRepository,
    private val taskAlarmReconciler: TaskAlarmReconciler,
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
        taskAlarmReconciler.reconcileDuringRestore()
    }

    override suspend fun reconcileCurrentState() {
        settingsRepository.syncFromStoreAfterRestore()
        taskAlarmReconciler.reconcileDuringRestore()
    }

    override suspend fun persistLastResult(plan: RestorePlan, afterInterruption: Boolean) {
        settingsRepository.persistLastRestoreResult(plan)
    }
}