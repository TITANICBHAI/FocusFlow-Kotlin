package com.tbtechs.focusflow.enforcement.health

import android.content.Context
import android.util.Log
import com.tbtechs.focusflow.data.repository.LauncherController
import com.tbtechs.focusflow.data.repository.UsageStatsRepository
import com.tbtechs.focusflow.ui.permissions.PermissionDefinition
import com.tbtechs.focusflow.ui.permissions.PermissionId
import com.tbtechs.focusflow.ui.permissions.permissionDefinitions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object EnforcementHealthReader {
    private const val TAG = "EnforcementHealthReader"

    internal fun requiredPermissionIds(
        definitions: List<PermissionDefinition> = permissionDefinitions,
    ): List<PermissionId> =
        definitions.filterNot { it.optional }.map { it.id }

    suspend fun read(context: Context): EnforcementHealth = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val usageStats = UsageStatsRepository(appContext)
        val launcher = LauncherController(appContext)
        val required = requiredPermissionIds()
        val granted = mutableSetOf<PermissionId>()

        for (permissionId in required) {
            val isGranted = try {
                when (permissionId) {
                    PermissionId.ACCESSIBILITY -> usageStats.hasAccessibilityPermission()
                    PermissionId.USAGE -> usageStats.hasPermission()
                    PermissionId.OVERLAY -> launcher.hasOverlayPermission()
                    else -> error(
                        "No enforcement-health check exists for required permission $permissionId",
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "Could not verify required permission $permissionId", error)
                false
            }
            if (isGranted) granted += permissionId
        }

        EnforcementHealth.fromRequiredPermissions(required, granted)
    }
}
