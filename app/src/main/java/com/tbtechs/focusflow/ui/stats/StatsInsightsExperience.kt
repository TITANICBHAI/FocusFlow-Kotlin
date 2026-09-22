package com.tbtechs.focusflow.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.tbtechs.focusflow.ui.focus.ActiveStatusIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tbtechs.focusflow.analytics.ANALYTICS_THREE_MONTHS
import com.tbtechs.focusflow.analytics.ANALYTICS_ALL_TIME
import com.tbtechs.focusflow.analytics.ANALYTICS_TODAY
import com.tbtechs.focusflow.analytics.ANALYTICS_WEEK
import com.tbtechs.focusflow.analytics.ANALYTICS_YESTERDAY
import com.tbtechs.focusflow.analytics.AnalyticsWindow
import com.tbtechs.focusflow.di.AppModule
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkSurfaceVariant
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import kotlinx.coroutines.launch

/** The superseding Stats experience; do not substitute UsageInsights or WeeklyReport. */
@Composable
fun StatsInsightsExperience(
    statsViewModel: StatsViewModel = viewModel(factory = StatsViewModel.Factory),
    onOpenUsageAccessSettings: () -> Unit = {},
    onOpenActiveBlocks: () -> Unit = {},
    onOpenQuickBlock: (String?) -> Unit = {},
    focusDayRating: Boolean = false,
) {
    val snapshot by statsViewModel.analyticsSnapshot.collectAsState()
    val insights by statsViewModel.insightCards.collectAsState()
    val weeklyStandout by statsViewModel.weeklyStandout.collectAsState()
    val achievements by statsViewModel.achievementState.collectAsState()
    val lifetime by statsViewModel.lifetimeStats.collectAsState()
    val state by statsViewModel.loadState.collectAsState()
    val window by statsViewModel.activeWindow.collectAsState()
    val selectedDate by statsViewModel.selectedRatingDate.collectAsState()
    val currentRating by statsViewModel.currentRating.collectAsState()
    val ratableDates by statsViewModel.ratableDates.collectAsState()
    val suggestedChips by statsViewModel.suggestedChips.collectAsState()
    val activeFindings by statsViewModel.activeFindings.collectAsState()
    val pendingQuestion by statsViewModel.pendingQuestion.collectAsState()
    val needsColdStart by statsViewModel.needsColdStart.collectAsState()
    val settingsRepository = remember { AppModule.settingsRepository }
    val scope = rememberCoroutineScope()
    var localNoticeDismissed by remember {
        mutableStateOf(settingsRepository.getString("local_analytics_notice_dismissed") == "true")
    }

    if (needsColdStart) {
        ColdStartSheet(onComplete = statsViewModel::saveColdStartAnswers)
    }

    Column(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
        Row(
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth()
                .background(DarkBackground)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Column(modifier = androidx.compose.ui.Modifier.weight(1f)) {
                Text("Stats", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = DarkTextPrimary)
                Text(windowSubtitle(window), fontSize = 13.sp, color = DarkTextSecondary)
            }
            ActiveStatusIndicator(
                onOpenActiveBlocks = onOpenActiveBlocks,
            )
        }
        AnalyticsWindowTabs(activeWindow = window, onSelect = statsViewModel::setWindow)
        DayRatingBar(
            selectedDate = selectedDate,
            currentRating = currentRating,
            ratableDates = ratableDates,
            suggestedChips = suggestedChips,
            onSelectDate = statsViewModel::selectRatingDate,
            onLoadChips = statsViewModel::loadChipsForDate,
            requestFocus = focusDayRating,
            onSubmit = statsViewModel::submitRating,
        )
        if (window == ANALYTICS_THREE_MONTHS && !localNoticeDismissed) {
            LocalOnlyNotice(onDismiss = {
                scope.launch {
                    settingsRepository.putString("local_analytics_notice_dismissed", "true")
                    localNoticeDismissed = true
                }
            })
        }
        when (state) {
            StatsLoadState.Loading -> LoadingStats()
            StatsLoadState.PermissionNeeded -> PermissionGate(onOpenUsageAccessSettings)
            StatsLoadState.Unavailable -> UnavailableGate()
            is StatsLoadState.Error -> ErrorStats(onRetry = statsViewModel::reload)
            StatsLoadState.Ready -> snapshot?.let { loaded ->
                LazyColumn(
                    modifier = androidx.compose.ui.Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    item {
                        if (window == ANALYTICS_WEEK) {
                            weeklyStandout?.let { InsightCardView(it) }
                        }
                        DataHealthNotice(loaded)
                        if (loaded.tasks.total == 0 && loaded.sessions.total == 0 && loaded.blocking.totalAttempts == 0 && window != ANALYTICS_THREE_MONTHS) {
                            EmptyStatsState(window)
                        }
                        FocusTimeHero(loaded)
                        insights
                            .filter { it.id != weeklyStandout?.id }
                            .forEach { InsightCardView(it) }
                        when (window) {
                            ANALYTICS_TODAY -> {
                                TaskSummary(loaded)
                                TaskResultList(loaded, title = "TODAY'S TASKS")
                                ProductivityHeatmap(loaded)
                            }
                            ANALYTICS_ALL_TIME -> {
                                AllTimeStats(
                                    snapshot = loaded,
                                    lifetime = lifetime,
                                    earnedAchievementCount = achievements?.earnedIds?.size ?: 0,
                                )
                                TaskSummary(loaded)
                                ProductivityHeatmap(loaded)
                            }
                            ANALYTICS_WEEK -> {
                                PresenceStrip(loaded)
                                ProductivityHeatmap(loaded)
                                TaskSummary(loaded)
                            }
                            ANALYTICS_YESTERDAY -> TaskResultList(loaded)
                            ANALYTICS_THREE_MONTHS -> {
                                PhoneUsageSummary(loaded, onOpenQuickBlock)
                                TrendChart(loaded)
                            }
                        }
                        TemptationStats(loaded, onOpenQuickBlock)
                        if (window != ANALYTICS_THREE_MONTHS) {
                            achievements?.let { AchievementRow(it) }
                        }
                        FindingsSection(
                            activeFindings = activeFindings,
                            pendingQuestion = pendingQuestion,
                            dayCount = statsViewModel.dataHealthDayCount,
                            ratingCount = statsViewModel.totalRatingCount,
                            onMarkSeen = statsViewModel::markFindingSeen,
                            onIntentional = statsViewModel::acknowledgeFindingIntentional,
                            onAware = statsViewModel::acknowledgeFindingAware,
                            onAnswerQuestion = statsViewModel::answerClarifyingQuestion,
                        )
                    }
                }
            } ?: LoadingStats()
        }
    }
}

@Composable
private fun AnalyticsWindowTabs(activeWindow: AnalyticsWindow, onSelect: (AnalyticsWindow) -> Unit) {
    val windows = listOf(
        ANALYTICS_YESTERDAY to "Yesterday",
        ANALYTICS_TODAY to "Today",
        ANALYTICS_WEEK to "Week",
        ANALYTICS_THREE_MONTHS to "3 Months",
        ANALYTICS_ALL_TIME to "All Time",
    )
    Row(
        modifier = androidx.compose.ui.Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        windows.forEach { (window, label) ->
            Box(
                modifier = androidx.compose.ui.Modifier
                    .clip(CircleShape)
                    .background(if (activeWindow == window) BrandPrimary else DarkSurfaceVariant)
                    .clickable { onSelect(window) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (activeWindow == window) Color.White else DarkTextSecondary,
                )
            }
        }
    }
}

@Composable
private fun LoadingStats() = Column(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
    CircularProgressIndicator()
    Text("Reading your local history…", color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ErrorStats(onRetry: () -> Unit) = Column(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
    Text("Stats unavailable", style = MaterialTheme.typography.headlineSmall)
    Text("FocusFlow could not read this period. Return to the view to try again.")
    androidx.compose.material3.Button(onClick = onRetry) { Text("Try again") }
}

private fun windowSubtitle(window: AnalyticsWindow): String = when (window) {
    ANALYTICS_YESTERDAY -> "The day that just ended"
    ANALYTICS_TODAY -> "Your focus so far today"
    ANALYTICS_ALL_TIME -> "The record you are building"
    ANALYTICS_THREE_MONTHS -> "What your device use has held over time"
    else -> "The pattern forming this week"
}
