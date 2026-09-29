package com.tbtechs.focusflow.ui.stats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.tbtechs.focusflow.analytics.ANALYTICS_TODAY
import com.tbtechs.focusflow.analytics.AnalyticsWindow

/**
 * The visible Stats route uses the archived four-period report as its primary
 * experience. The newer behavioural-intelligence view is available as Extra.
 */
@Composable
fun StatsScreen(
    statsViewModel: StatsViewModel,
    onOpenUsageAccessSettings: () -> Unit = {},
    onOpenActiveBlocks: () -> Unit = {},
    onOpenQuickBlock: (String?) -> Unit = {},
    focusDayRating: Boolean = false,
) {
    var showExtra by rememberSaveable { mutableStateOf(false) }
    var archivedWindow by rememberSaveable { mutableStateOf<AnalyticsWindow>(ANALYTICS_TODAY) }

    if (showExtra) {
        StatsInsightsExperience(
            statsViewModel = statsViewModel,
            onOpenUsageAccessSettings = onOpenUsageAccessSettings,
            onOpenActiveBlocks = onOpenActiveBlocks,
            onOpenQuickBlock = onOpenQuickBlock,
            focusDayRating = focusDayRating,
            screenTitle = "Extra",
            onOpenArchived = {
                statsViewModel.setWindow(archivedWindow)
                showExtra = false
            },
        )
    } else {
        ArchivedStatsScreen(
            statsViewModel = statsViewModel,
            onOpenUsageAccessSettings = onOpenUsageAccessSettings,
            onOpenActiveBlocks = onOpenActiveBlocks,
            onOpenQuickBlock = onOpenQuickBlock,
            onOpenExtra = {
                archivedWindow = statsViewModel.activeWindow.value
                showExtra = true
            },
        )
    }
}

/** Preserved current analytics implementation, isolated from the RN-style route. */
@Composable
fun CurrentStatsScreen(
    statsViewModel: StatsViewModel,
    onOpenUsageAccessSettings: () -> Unit = {},
    onOpenActiveBlocks: () -> Unit = {},
    onOpenQuickBlock: (String?) -> Unit = {},
) = StatsInsightsExperience(
    statsViewModel = statsViewModel,
    onOpenUsageAccessSettings = onOpenUsageAccessSettings,
    onOpenActiveBlocks = onOpenActiveBlocks,
    onOpenQuickBlock = onOpenQuickBlock,
)