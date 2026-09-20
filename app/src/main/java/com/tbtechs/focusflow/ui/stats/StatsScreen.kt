package com.tbtechs.focusflow.ui.stats

import androidx.compose.runtime.Composable

/**
 * The visible stats route follows the archived RN screen's simpler model:
 * Today, Yesterday, Week, and All Time.
 *
 * The newer analytics experience is intentionally kept below as
 * [CurrentStatsScreen] instead of being deleted, so it remains available for
 * a later comparison or reintroduction.
 */
@Composable
fun StatsScreen(
    statsViewModel: StatsViewModel,
    onOpenUsageAccessSettings: () -> Unit = {},
    onOpenActiveBlocks: () -> Unit = {},
    onOpenQuickBlock: (String?) -> Unit = {},
) = ArchivedStatsScreen(
    statsViewModel = statsViewModel,
    onOpenUsageAccessSettings = onOpenUsageAccessSettings,
    onOpenActiveBlocks = onOpenActiveBlocks,
    onOpenQuickBlock = onOpenQuickBlock,
)

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