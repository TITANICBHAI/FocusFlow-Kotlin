package com.tbtechs.focusflow.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tbtechs.focusflow.analytics.ANALYTICS_ALL_TIME
import com.tbtechs.focusflow.analytics.ANALYTICS_TODAY
import com.tbtechs.focusflow.analytics.ANALYTICS_WEEK
import com.tbtechs.focusflow.analytics.ANALYTICS_YESTERDAY
import com.tbtechs.focusflow.analytics.AnalyticsSnapshot
import com.tbtechs.focusflow.analytics.LifetimeStats
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkSurfaceVariant
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.SunAmber
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Kotlin port of the archived RN stats layout.
 *
 * It intentionally keeps the RN information hierarchy: a sticky four-option
 * filter row, one focused report per period, compact summary cards, and clear
 * empty/loading/permission states. It consumes the existing repositories and
 * usage-permission path instead of duplicating native UsageStats code.
 */
@Composable
fun ArchivedStatsScreen(
    statsViewModel: StatsViewModel,
    onOpenUsageAccessSettings: () -> Unit = {},
    onOpenActiveBlocks: () -> Unit = {},
    onOpenQuickBlock: (String?) -> Unit = {},
) {
    val snapshot by statsViewModel.analyticsSnapshot.collectAsState()
    val lifetime by statsViewModel.lifetimeStats.collectAsState()
    val loadState by statsViewModel.loadState.collectAsState()
    val window by statsViewModel.activeWindow.collectAsState()
    var usagePermission by remember { mutableStateOf<Boolean?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (statsViewModel.activeWindow.value != ANALYTICS_TODAY) {
            statsViewModel.setWindow(ANALYTICS_TODAY)
        }
        usagePermission = runCatching {
            com.tbtechs.focusflow.di.AppModule.usageStatsRepository.hasPermission()
        }.getOrDefault(false)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    usagePermission = runCatching {
                        com.tbtechs.focusflow.di.AppModule.usageStatsRepository.hasPermission()
                    }.getOrDefault(false)
                    statsViewModel.reload()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
    ) {
        StatsHeader(window)
        StatsFilterRow(
            activeWindow = window,
            onSelect = statsViewModel::setWindow,
        )

        when (val state = loadState) {
            StatsLoadState.Loading -> StatsLoadingState()
            is StatsLoadState.Error -> StatsErrorState(onRetry = statsViewModel::reload)
            StatsLoadState.PermissionNeeded -> {
                StatsPermissionState(onOpenUsageAccessSettings)
            }
            StatsLoadState.Ready, StatsLoadState.Unavailable -> {
                val loaded = snapshot
                if (loaded == null) {
                    StatsEmptyState(window)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = 14.dp,
                            end = 16.dp,
                            bottom = 28.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            when (window) {
                                ANALYTICS_YESTERDAY -> YesterdayReport(loaded)
                                ANALYTICS_WEEK -> WeekReport(loaded)
                                ANALYTICS_ALL_TIME -> AllTimeReport(loaded, lifetime)
                                else -> TodayReport(loaded)
                            }
                        }
                        item {
                            loaded.phoneUsage?.let { phoneUsage ->
                                DeviceUsageCard(
                                    usage = phoneUsage,
                                    onOpenQuickBlock = onOpenQuickBlock,
                                )
                            }
                        }
                        item {
                            UsageAccessCard(
                                permission = usagePermission,
                                onOpenUsageAccessSettings = onOpenUsageAccessSettings,
                            )
                        }
                        item {
                            BlockingReport(
                                snapshot = loaded,
                                onOpenActiveBlocks = onOpenActiveBlocks,
                                onOpenQuickBlock = onOpenQuickBlock,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsHeader(window: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkBackground)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text("Stats", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = DarkTextPrimary)
        Text(statsSubtitle(window), fontSize = 13.sp, color = DarkTextSecondary)
    }
}

@Composable
private fun StatsFilterRow(
    activeWindow: String,
    onSelect: (String) -> Unit,
) {
    val filters = listOf(
        ANALYTICS_TODAY to "Today",
        ANALYTICS_YESTERDAY to "Yesterday",
        ANALYTICS_WEEK to "Week",
        ANALYTICS_ALL_TIME to "All Time",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkCard)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        filters.forEach { (filter, label) ->
            val selected = activeWindow == filter
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (selected) BrandPrimary else BrandPrimary.copy(alpha = 0.12f))
                    .clickable { onSelect(filter) }
                    .padding(horizontal = 15.dp, vertical = 8.dp),
            ) {
                Text(
                    label,
                    color = if (selected) Color.White else BrandPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun TodayReport(snapshot: AnalyticsSnapshot) {
    val focusMinutes = snapshot.sessions.totalFocusMinutes.roundToInt()
    ArchivedCard {
        Icon(Icons.Outlined.Timer, null, tint = BrandPrimary, modifier = Modifier.size(28.dp))
        Text(
            formatStatsMinutes(focusMinutes),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = BrandPrimary,
        )
        Text("focus time today", color = DarkTextSecondary)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatTile("${snapshot.sessions.total}", "Sessions", BrandPrimary)
            StatTile("${snapshot.tasks.completed}", "Done", Color(0xFF55C98A))
            StatTile("${snapshot.blocking.totalAttempts}", "Blocked", SunAmber)
        }
        Spacer(Modifier.height(16.dp))
        TaskBreakdown(snapshot)
    }
}

@Composable
private fun YesterdayReport(snapshot: AnalyticsSnapshot) {
    ArchivedCard {
        Icon(Icons.Outlined.CalendarMonth, null, tint = BrandPrimary, modifier = Modifier.size(26.dp))
        Text("Yesterday's report", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DarkTextPrimary)
        Text(
            "${snapshot.tasks.completed} of ${snapshot.tasks.total} tasks completed",
            color = DarkTextSecondary,
        )
        Spacer(Modifier.height(14.dp))
        TaskBreakdown(snapshot)
        Spacer(Modifier.height(14.dp))
        snapshot.tasks.resultRows.orEmpty().take(8).forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (row.status == "completed") Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber,
                    null,
                    tint = if (row.status == "completed") Color(0xFF55C98A) else SunAmber,
                    modifier = Modifier.size(17.dp),
                )
                Text(
                    row.title,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    color = DarkTextPrimary,
                    maxLines = 1,
                )
                Text(statusLabel(row.status), fontSize = 11.sp, color = DarkTextSecondary)
            }
        }
    }
}

@Composable
private fun WeekReport(snapshot: AnalyticsSnapshot) {
    ArchivedCard {
        Text("This week", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DarkTextPrimary)
        Text(
            "${snapshot.tasks.completed} of ${snapshot.tasks.total} tasks completed",
            color = DarkTextSecondary,
        )
        Spacer(Modifier.height(14.dp))
        WeekCompletionBars(snapshot)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatTile("${snapshot.sessions.totalFocusMinutes.roundToInt()}m", "Focus", BrandPrimary)
            StatTile("${snapshot.sessions.cleanCount}", "Clean", Color(0xFF55C98A))
            StatTile("${snapshot.blocking.totalAttempts}", "Blocked", SunAmber)
        }
    }
}

@Composable
private fun AllTimeReport(snapshot: AnalyticsSnapshot, lifetime: LifetimeStats?) {
    ArchivedCard {
        Icon(Icons.Outlined.HourglassEmpty, null, tint = BrandPrimary, modifier = Modifier.size(27.dp))
        Text(
            formatStatsMinutes((lifetime?.totalFocusMinutes ?: snapshot.sessions.totalFocusMinutes).roundToInt()),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = BrandPrimary,
        )
        Text("total focus time", color = DarkTextSecondary)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatTile("${lifetime?.completedTasks ?: snapshot.tasks.completed}", "Tasks", Color(0xFF55C98A))
            StatTile("${lifetime?.totalSessions ?: snapshot.sessions.total}", "Sessions", BrandPrimary)
            StatTile("${lifetime?.currentStreakDays ?: 0}d", "Streak", SunAmber)
        }
        Spacer(Modifier.height(14.dp))
        WeekCompletionBars(snapshot)
    }
}

@Composable
private fun TaskBreakdown(snapshot: AnalyticsSnapshot) {
    Text("Task breakdown", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DarkTextPrimary)
    BreakdownRow("Completed", snapshot.tasks.completed, Color(0xFF55C98A), snapshot.tasks.total)
    BreakdownRow("Remaining", (snapshot.tasks.total - snapshot.tasks.completed - snapshot.tasks.skipped - snapshot.tasks.missed).coerceAtLeast(0), BrandPrimary, snapshot.tasks.total)
    BreakdownRow("Skipped", snapshot.tasks.skipped, DarkTextMuted, snapshot.tasks.total)
    BreakdownRow("Missed", snapshot.tasks.missed, Color(0xFFE06B6B), snapshot.tasks.total)
}

@Composable
private fun BreakdownRow(label: String, value: Int, color: Color, total: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.width(74.dp), fontSize = 11.sp, color = DarkTextSecondary)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(DarkBorder),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(if (total == 0) 0f else (value.toFloat() / total).coerceIn(0f, 1f))
                    .height(7.dp)
                    .background(color),
            )
        }
        Text(value.toString(), modifier = Modifier.width(28.dp), fontSize = 11.sp, color = color)
    }
}

@Composable
private fun WeekCompletionBars(snapshot: AnalyticsSnapshot) {
    val days = listOf("S", "M", "T", "W", "T", "F", "S")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(116.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEachIndexed { index, day ->
            val bucket = snapshot.tasks.byDayOfWeek[index]
            val total = bucket?.total ?: 0
            val rate = if (total == 0) 0f
            else ((bucket?.completed ?: 0).toFloat() / total).coerceIn(0f, 1f)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .width(24.dp)
                        .height((20 + 72 * rate).dp)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(if (rate > 0f) BrandPrimary else DarkBorder),
                )
                Spacer(Modifier.height(5.dp))
                Text(day, fontSize = 11.sp, color = DarkTextSecondary)
            }
        }
    }
}

@Composable
private fun UsageAccessCard(
    permission: Boolean?,
    onOpenUsageAccessSettings: () -> Unit,
) {
    if (permission == true) return
    ArchivedCard {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Settings, null, tint = BrandPrimary, modifier = Modifier.size(21.dp))
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text("Device usage is not connected", fontWeight = FontWeight.SemiBold, color = DarkTextPrimary)
                Text(
                    "Grant Usage Access when you want FocusFlow to explain phone-use patterns. Your focus stats above continue to work without it.",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = DarkTextSecondary,
                )
            }
            Button(
                onClick = onOpenUsageAccessSettings,
                contentPadding = PaddingValues(horizontal = 10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
            ) {
                Text("Open", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DeviceUsageCard(
    usage: AnalyticsSnapshot.PhoneUsage,
    onOpenQuickBlock: (String?) -> Unit,
) {
    ArchivedCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Timer, null, tint = Color(0xFF63A7FF), modifier = Modifier.size(21.dp))
            Text(
                "Observed device time",
                modifier = Modifier.padding(start = 8.dp),
                fontWeight = FontWeight.SemiBold,
                color = DarkTextPrimary,
            )
        }
        val totalMinutes = usage.byHour.values.sum().roundToInt()
        Text(
            formatStatsMinutes(totalMinutes),
            modifier = Modifier.padding(top = 10.dp),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF63A7FF),
        )
        Text(
            "Android app foreground time — FocusFlow home-screen time excluded.",
            fontSize = 12.sp,
            color = DarkTextSecondary,
        )
        usage.peakPeriod?.let {
            Text("Heaviest use is around $it.", fontSize = 12.sp, color = DarkTextSecondary)
        }
        HourlyUsageChart(usage.byHour)
        if (usage.apps.isNotEmpty()) {
            Text(
                "Most-used apps",
                modifier = Modifier.padding(top = 12.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = DarkTextPrimary,
            )
            usage.apps.take(5).forEach { app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(
                                enabled = app.packageName != null,
                                onClick = { app.packageName?.let(onOpenQuickBlock) },
                            )
                            .padding(vertical = 4.dp),
                    ) {
                        Text(app.appName, color = DarkTextPrimary, maxLines = 1)
                        Text(
                            "${app.minutes.roundToInt()} minutes foreground",
                            fontSize = 11.sp,
                            color = DarkTextSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HourlyUsageChart(byHour: Map<Int, Double>) {
    val maxMinutes = byHour.values.maxOrNull()?.coerceAtLeast(0.0) ?: 0.0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "Observed time by hour",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = DarkTextPrimary,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(82.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            (0..23).forEach { hour ->
                val minutes = byHour[hour] ?: 0.0
                val fraction = if (maxMinutes > 0.0) {
                    (minutes / maxMinutes).toFloat().coerceIn(0f, 1f)
                } else {
                    0f
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height((4f + 58f * fraction).dp)
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(
                            if (minutes > 0.0) Color(0xFF63A7FF)
                            else DarkBorder.copy(alpha = 0.7f),
                        ),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf("12a", "4a", "8a", "12p", "4p", "8p").forEach { label ->
                Text(label, fontSize = 10.sp, color = DarkTextMuted)
            }
        }
    }
}

@Composable
private fun BlockingReport(
    snapshot: AnalyticsSnapshot,
    onOpenActiveBlocks: () -> Unit,
    onOpenQuickBlock: (String?) -> Unit,
) {
    if (snapshot.blocking.totalAttempts == 0 && snapshot.blocking.topApp == null) return
    ArchivedCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Security, null, tint = SunAmber, modifier = Modifier.size(22.dp))
            Text("Blocked attempts", modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.SemiBold, color = DarkTextPrimary)
        }
        Text(
            "${snapshot.blocking.totalAttempts} app opening${if (snapshot.blocking.totalAttempts == 1) "" else "s"} intercepted in this period.",
            modifier = Modifier.padding(top = 6.dp),
            color = DarkTextSecondary,
        )
        snapshot.blocking.topApp?.let { app ->
            val topPackage = snapshot.blocking.byApp.entries
                .firstOrNull { (_, entry) -> entry == app }
                ?.key
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            enabled = topPackage != null,
                            onClick = { topPackage?.let(onOpenQuickBlock) },
                        )
                        .padding(vertical = 4.dp),
                ) {
                    Text(app.appName, color = DarkTextPrimary, fontWeight = FontWeight.Medium)
                    Text("${app.count} attempts", fontSize = 11.sp, color = DarkTextSecondary)
                }
            }
        }
        Button(
            onClick = onOpenActiveBlocks,
            modifier = Modifier.padding(top = 10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandPrimary),
        ) {
            Text("Open active blocks")
        }
    }
}

@Composable
private fun ArchivedCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkCard)
            .padding(16.dp),
        content = content,
    )
}

@Composable
private fun StatTile(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 11.sp, color = DarkTextSecondary)
    }
}

@Composable
private fun StatsLoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = BrandPrimary)
            Spacer(Modifier.height(14.dp))
            Text("Reading your local history…", color = DarkTextSecondary)
        }
    }
}

@Composable
private fun StatsErrorState(onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Outlined.Analytics, null, tint = DarkTextMuted, modifier = Modifier.size(42.dp))
            Text("Stats unavailable", fontSize = 20.sp, color = DarkTextPrimary)
            Text(
                "FocusFlow could not read this period. Try again without leaving the tab.",
                color = DarkTextSecondary,
            )
            Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) {
                Text("Try again")
            }
        }
    }
}

@Composable
private fun StatsPermissionState(onOpenUsageAccessSettings: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ArchivedCard {
            Icon(Icons.Outlined.Settings, null, tint = BrandPrimary, modifier = Modifier.size(28.dp))
            Text("Connect device usage", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DarkTextPrimary)
            Text(
                "This report needs Android Usage Access to read phone behaviour. Nothing leaves the device.",
                color = DarkTextSecondary,
            )
            Button(onClick = onOpenUsageAccessSettings, modifier = Modifier.padding(top = 12.dp)) {
                Text("Grant Usage Access")
            }
        }
    }
}

@Composable
private fun StatsEmptyState(window: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ArchivedCard {
            Icon(Icons.Outlined.Analytics, null, tint = DarkTextMuted, modifier = Modifier.size(38.dp))
            Text(
                if (window == ANALYTICS_YESTERDAY) "Nothing recorded yesterday" else "No activity recorded yet",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = DarkTextPrimary,
            )
            Text(
                "Insights will appear here once FocusFlow has a task, session, or blocked-app attempt to read.",
                color = DarkTextSecondary,
            )
        }
    }
}

private fun statsSubtitle(window: String): String = when (window) {
    ANALYTICS_YESTERDAY -> "The day that just ended"
    ANALYTICS_WEEK -> "Your week in focus"
    ANALYTICS_ALL_TIME -> "The record you are building"
    else -> "Your focus so far today"
}

private fun formatStatsMinutes(minutes: Int): String {
    if (minutes <= 0) return "0m"
    val hours = minutes / 60
    val remaining = minutes % 60
    return if (hours > 0) "${hours}h ${remaining}m" else "${remaining}m"
}

private fun statusLabel(status: String): String = when (status) {
    "completed" -> "Done"
    "skipped" -> "Skipped"
    "overdue" -> "Missed"
    else -> "Not completed"
}