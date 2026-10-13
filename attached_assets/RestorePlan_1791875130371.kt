package com.tbtechs.focusflow.data.restore

import com.tbtechs.focusflow.data.backup.BackupEnvelopeV1
import com.tbtechs.focusflow.data.backup.BackupTaskV1
import com.tbtechs.focusflow.data.model.CanonicalTimestamp
import com.tbtechs.focusflow.data.model.Reminder
import com.tbtechs.focusflow.data.model.Task
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray

@Serializable
enum class RestoreMode {
    MERGE,
    REPLACE,
}

@Serializable
enum class RestorePhase {
    PLANNED,
    TASKS_APPLIED,
    SETTINGS_APPLIED,
    RECONCILED,
}

@Serializable
data class RestoreCounts(
    val tasksInFile: Int = 0,
    val tasksInserted: Int = 0,
    val identicalDuplicates: Int = 0,
    val invalidTasks: Int = 0,
    val downgradedToSkipped: Int = 0,
    val settingsKeys: Int = 0,
    val unresolvedExternalResources: Int = 0,
)

@Serializable
data class RestorePlan(
    val mode: RestoreMode,
    val planNowMillis: Long,
    val deleteAllExisting: Boolean,
    val preDeleteTaskIds: List<String>,
    val tasksToInsert: List<Task>,
    val settingsPlan: JsonObject,
    val userGreyoutWindows: JsonArray,
    val recurringBlockSchedules: JsonArray,
    val warningCodes: List<String>,
    val counts: RestoreCounts,
)

@Serializable
data class RestoreJournal(
    val journalVersion: Int = JOURNAL_VERSION,
    val sessionId: String,
    val mode: RestoreMode,
    val planNowMillis: Long,
    val phase: RestorePhase,
    val attempts: Int,
    val deleteAllExisting: Boolean,
    val preDeleteTaskIds: List<String>,
    val tasksToInsert: List<Task>,
    val settingsPlan: JsonObject,
    val userGreyoutWindows: JsonArray,
    val recurringBlockSchedules: JsonArray,
    val warningCodes: List<String>,
    val counts: RestoreCounts,
) {
    fun plan() = RestorePlan(
        mode = mode,
        planNowMillis = planNowMillis,
        deleteAllExisting = deleteAllExisting,
        preDeleteTaskIds = preDeleteTaskIds,
        tasksToInsert = tasksToInsert,
        settingsPlan = settingsPlan,
        userGreyoutWindows = userGreyoutWindows,
        recurringBlockSchedules = recurringBlockSchedules,
        warningCodes = warningCodes,
        counts = counts,
    )

    companion object {
        const val JOURNAL_VERSION = 1

        fun fromPlan(sessionId: String, plan: RestorePlan) = RestoreJournal(
            sessionId = sessionId,
            mode = plan.mode,
            planNowMillis = plan.planNowMillis,
            phase = RestorePhase.PLANNED,
            attempts = 0,
            deleteAllExisting = plan.deleteAllExisting,
            preDeleteTaskIds = plan.preDeleteTaskIds,
            tasksToInsert = plan.tasksToInsert,
            settingsPlan = plan.settingsPlan,
            userGreyoutWindows = plan.userGreyoutWindows,
            recurringBlockSchedules = plan.recurringBlockSchedules,
            warningCodes = plan.warningCodes,
            counts = plan.counts,
        )
    }
}

data class RestoreConflict(
    val id: String,
    val importedTitle: String,
    val localTitle: String,
    val reason: String = "Task content differs for this ID.",
)

sealed class RestorePlanFailure {
    data class IdConflict(val conflictingTasks: List<RestoreConflict>) : RestorePlanFailure()
    data object ActiveSession : RestorePlanFailure()
}

sealed class RestorePlanResult {
    data class Success(val plan: RestorePlan) : RestorePlanResult()
    data class Failure(val failure: RestorePlanFailure) : RestorePlanResult()
}

/**
 * Equality projection from contract §5.1. Reminder differences and
 * createdAt/updatedAt differences intentionally do not make task IDs conflict.
 */
data class TaskDuplicateProjection(
    val title: String,
    val description: String,
    val startTime: String,
    val endTime: String,
    val durationMinutes: Int,
    val status: String,
    val priority: String,
    val tags: List<String>,
    val color: String,
    val focusMode: Boolean,
    val focusAllowedPackages: List<String>?,
) {
    companion object {
        fun from(task: Task) = TaskDuplicateProjection(
            title = task.title,
            description = task.description.orEmpty(),
            startTime = CanonicalTimestamp.format(CanonicalTimestamp.parse(task.startTime)),
            endTime = CanonicalTimestamp.format(CanonicalTimestamp.parse(task.endTime)),
            durationMinutes = task.durationMinutes,
            status = task.status,
            priority = task.priority,
            tags = task.tags,
            color = task.color,
            focusMode = task.focusMode,
            focusAllowedPackages = task.focusAllowedPackages,
        )

        fun from(task: BackupTaskV1): TaskDuplicateProjection = from(task.toTask())
    }
}

object RestorePlanBuilder {
    fun conflicts(
        backup: BackupEnvelopeV1,
        localTasks: List<Task>,
    ): List<RestoreConflict> {
        val localById = localTasks.associateBy(Task::id)
        return backup.tasks.mapNotNull { imported ->
            val local = localById[imported.id] ?: return@mapNotNull null
            if (TaskDuplicateProjection.from(imported) == TaskDuplicateProjection.from(local)) {
                null
            } else {
                RestoreConflict(imported.id, imported.title, local.title)
            }
        }
    }

    fun build(
        backup: BackupEnvelopeV1,
        localTasks: List<Task>,
        mode: RestoreMode,
        planNowMillis: Long,
        restoreSettings: Boolean = true,
        restoreTasks: Boolean = true,
        activeSession: Boolean = false,
        warningCodes: List<String> = emptyList(),
        invalidTaskCount: Int = 0,
        unresolvedExternalResources: Int = 0,
    ): RestorePlanResult {
        if (mode == RestoreMode.REPLACE && restoreTasks && activeSession) {
            return RestorePlanResult.Failure(RestorePlanFailure.ActiveSession)
        }

        val conflicts = if (mode == RestoreMode.MERGE && restoreTasks) {
            conflicts(backup, localTasks)
        } else {
            emptyList()
        }
        if (conflicts.isNotEmpty()) {
            return RestorePlanResult.Failure(RestorePlanFailure.IdConflict(conflicts))
        }

        val localById = localTasks.associateBy(Task::id)
        var duplicateCount = 0
        var downgradedCount = 0
        val tasksToInsert = if (!restoreTasks) {
            emptyList()
        } else {
            backup.tasks.mapNotNull { imported ->
                val local = localById[imported.id]
                if (mode == RestoreMode.MERGE && local != null) {
                    // This includes different reminder arrays: preserve the local row.
                    duplicateCount += 1
                    return@mapNotNull null
                }

                var task = imported.toTask()
                if (task.status == "scheduled" || task.status == "active") {
                    val endMillis = runCatching { Instant.parse(task.endTime).toEpochMilli() }
                        .getOrElse { return@mapNotNull null }
                    task = if (endMillis > planNowMillis) {
                        task.copy(status = "scheduled")
                    } else {
                        downgradedCount += 1
                        task.copy(
                            status = "skipped",
                            updatedAt = CanonicalTimestamp.format(
                                Instant.ofEpochMilli(planNowMillis),
                            ),
                        )
                    }
                }
                task
            }
        }

        val settingsPlan = if (restoreSettings) backup.settings else JsonObject(emptyMap())
        val userWindows = if (restoreSettings) {
            backup.settings[com.tbtechs.focusflow.data.backup.TsSettingsAdapter.GREYOUT_SCHEDULE]
                ?.jsonArray
                ?.filterIsInstance<JsonObject>()
                ?.filterNot { it["scheduleId"]?.stringValue() != null }
                ?.let(::JsonArray)
                ?: JsonArray(emptyList())
        } else {
            JsonArray(emptyList())
        }
        val recurringSchedules = if (restoreSettings) {
            backup.settings[
                com.tbtechs.focusflow.data.backup.TsSettingsAdapter.RECURRING_BLOCK_SCHEDULES
            ] as? JsonArray ?: JsonArray(emptyList())
        } else {
            JsonArray(emptyList())
        }

        return RestorePlanResult.Success(
            RestorePlan(
                mode = mode,
                planNowMillis = planNowMillis,
                deleteAllExisting = mode == RestoreMode.REPLACE && restoreTasks,
                preDeleteTaskIds = if (mode == RestoreMode.REPLACE && restoreTasks) {
                    localTasks.map(Task::id)
                } else {
                    emptyList()
                },
                tasksToInsert = tasksToInsert,
                settingsPlan = settingsPlan,
                userGreyoutWindows = userWindows,
                recurringBlockSchedules = recurringSchedules,
                warningCodes = warningCodes,
                counts = RestoreCounts(
                    tasksInFile = backup.tasks.size,
                    tasksInserted = tasksToInsert.size,
                    identicalDuplicates = duplicateCount,
                    invalidTasks = invalidTaskCount,
                    downgradedToSkipped = downgradedCount,
                    settingsKeys = settingsPlan.size,
                    unresolvedExternalResources = unresolvedExternalResources,
                ),
            ),
        )
    }
}

private fun BackupTaskV1.toTask() = Task(
    id = id,
    title = title,
    description = description,
    startTime = startTime,
    endTime = endTime,
    durationMinutes = durationMinutes,
    status = status,
    priority = priority,
    tags = tags,
    reminders = reminders.map { it.copy() },
    color = color,
    focusMode = focusMode,
    focusAllowedPackages = if (focusAllowedPackagesPresent) focusAllowedPackages else null,
    createdAt = wire["createdAt"]?.stringValue() ?: startTime,
    updatedAt = wire["updatedAt"]?.stringValue() ?: startTime,
)

private fun JsonPrimitive.stringValue(): String? =
    content.takeIf { isString }

private fun JsonElement.stringValue(): String? = (this as? JsonPrimitive)?.stringValue()