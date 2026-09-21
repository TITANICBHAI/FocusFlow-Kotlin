package com.tbtechs.focusflow.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import com.tbtechs.focusflow.data.repository.InstalledAppInfo
import com.tbtechs.focusflow.data.repository.InstalledAppsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledAppsLoadState(
    val apps: List<InstalledAppInfo> = emptyList(),
    val loading: Boolean = true,
    val error: Throwable? = null,
)

/**
 * Shared progressive app catalog loader.
 *
 * This owns only discovery state. Allowances, VPN selections, Always-On selections, and
 * Allowed During Focus selections stay in their respective feature screens.
 */
@Composable
fun rememberInstalledApps(
    repository: InstalledAppsRepository,
    batchSize: Int = 24,
): InstalledAppsLoadState {
    var state by remember(repository, batchSize) {
        mutableStateOf(InstalledAppsLoadState())
    }

    LaunchedEffect(repository, batchSize) {
        state = InstalledAppsLoadState()
        try {
            withContext(Dispatchers.IO) {
                repository.getInstalledAppsBatched(batchSize) { batch ->
                    withContext(Dispatchers.Main.immediate) {
                        state = state.copy(
                            apps = (state.apps + batch)
                                .distinctBy { it.packageName }
                                .sortedBy { it.appName.lowercase() },
                        )
                    }
                }
            }
        } catch (exception: Exception) {
            state = state.copy(error = exception)
        } finally {
            state = state.copy(loading = false)
        }
    }

    return state
}