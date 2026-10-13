package com.tbtechs.focusflow.ui.backup

import com.tbtechs.focusflow.data.restore.RestorePhase
import com.tbtechs.focusflow.data.restore.RestoreUiState

internal data class RestoreProgressDisplay(
    val message: String,
    val step: Int? = null,
    val totalSteps: Int = 4,
)

internal fun RestoreUiState.Running.progressDisplay(): RestoreProgressDisplay =
    when {
        refreshingAfterDiscard -> RestoreProgressDisplay("Refreshing app state…")
        phase == null -> RestoreProgressDisplay("Checking restore record…")
        else -> when (phase) {
            RestorePhase.PLANNED -> RestoreProgressDisplay("Restoring tasks…", step = 1)
            RestorePhase.TASKS_APPLIED ->
                RestoreProgressDisplay("Restoring portable settings…", step = 2)
            RestorePhase.SETTINGS_APPLIED ->
                RestoreProgressDisplay("Updating schedules and alarms…", step = 3)
            RestorePhase.RECONCILED -> RestoreProgressDisplay("Finalizing restore…", step = 4)
        }
    }

internal fun RestoreUiState.progressDisplayOrNull(): RestoreProgressDisplay? =
    (this as? RestoreUiState.Running)?.progressDisplay()
