package com.tbtechs.focusflow.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.analytics.AnalyticsSnapshot
import com.tbtechs.focusflow.analytics.LifetimeStats
import kotlin.math.roundToInt

@Composable
fun FocusTimeHero(snapshot: AnalyticsSnapshot) {
    val minutes = snapshot.sessions.totalFocusMinutes.roundToInt()
    StatsHeroCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("FOCUS TIME", style = MaterialTheme.typography.labelLarge)
                    Text(
                        formatMinutes(minutes),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatValue("${snapshot.sessions.total}", "sessions")
                StatValue("${snapshot.sessions.cleanCount}", "clean")
                StatValue("${snapshot.sessions.avgDurationMinutes.roundToInt()}m", "average")
                StatValue("${snapshot.tasks.completed}/${snapshot.tasks.total}", "tasks done")
            }
        }
    }
}

@Composable
fun ProductivityHeatmap(snapshot: AnalyticsSnapshot) {
    val days = listOf("S", "M", "T", "W", "T", "F", "S")
    StatsCard {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("WEEKLY PRODUCTIVITY", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                days.forEachIndexed { day, label ->
                    val bucket = snapshot.tasks.byDayOfWeek[day]
                    val rate = if ((bucket?.total ?: 0) == 0) 0.0
                    else (bucket?.completed ?: 0).toDouble() / (bucket?.total ?: 1)
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(
                                        alpha = when {
                                            bucket == null || bucket.total == 0 -> 0.10f
                                            rate >= 0.8 -> 0.90f
                                            rate >= 0.5 -> 0.60f
                                            else -> 0.30f
                                        },
                                    ),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = bucket?.total?.toString() ?: "0",
                                color = if (rate >= 0.5) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Scheduled tasks by local day. Brighter cells mean a higher completion rate.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun TemptationStats(
    snapshot: AnalyticsSnapshot,
    onQuickBlock: (String?) -> Unit,
) {
    val blocking = snapshot.blocking
    StatsCard {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Block, null, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(8.dp))
                Text("TEMPTATION & DISTRACTION", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(8.dp))
            if (blocking.totalAttempts == 0) {
                Text(
                    "No blocked-app attempts were recorded in this period.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "${blocking.totalAttempts} blocked-app attempts",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                blocking.peakHour?.let {
                    Text("Peak activity was around ${hourLabel(it)}.")
                }
                Spacer(Modifier.height(8.dp))
                blocking.byApp.entries
                    .sortedByDescending { it.value.count }
                    .take(4)
                    .forEach { (packageName, app) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.appName)
                                Text(
                                    "${app.count} attempts",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                                Button(
                                    onClick = { onQuickBlock(packageName) },
                                    modifier = Modifier.height(40.dp),
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                Text("Block")
                            }
                        }
                    }
            }
        }
    }
}

@Composable
fun AllTimeStats(
    snapshot: AnalyticsSnapshot,
    lifetime: LifetimeStats?,
    earnedAchievementCount: Int,
) {
    StatsCard {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.EmojiEvents, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("ALL-TIME PROGRESS", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatValue(formatMinutes((lifetime?.totalFocusMinutes ?: snapshot.sessions.totalFocusMinutes).roundToInt()), "focus")
                StatValue("${lifetime?.completedTasks ?: snapshot.tasks.completed}", "tasks")
                StatValue("${lifetime?.totalSessions ?: snapshot.sessions.total}", "sessions")
                StatValue("${lifetime?.currentStreakDays ?: 0}d", "streak")
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "$earnedAchievementCount achievements earned",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatValue(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatMinutes(minutes: Int): String = when {
    minutes < 60 -> "${minutes}m"
    minutes % 60 == 0 -> "${minutes / 60}h"
    else -> "${minutes / 60}h ${minutes % 60}m"
}