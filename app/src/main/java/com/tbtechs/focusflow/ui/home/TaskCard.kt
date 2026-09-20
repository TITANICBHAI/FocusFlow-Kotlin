package com.tbtechs.focusflow.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.data.model.Task
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkSurfaceVariant
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.LocalFocusFlowDimensions
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Redesigned TaskCard for Screenshots 6c & 6f.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskCard(
    task: Task,
    isActive: Boolean,
    onOpen: () -> Unit,
    onComplete: (String) -> Unit,
    onSkip: (String) -> Unit,
    onExtend: (Task) -> Unit,
    onStartFocus: (String) -> Unit,
) {
    val dimensions = LocalFocusFlowDimensions.current
    val complete = task.status == "completed"
    val closed = complete || task.status == "skipped"
    val accent = taskAccent(task.color)
    var nowMs by remember(task.id) { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(task.id, isActive) {
        if (!isActive) return@LaunchedEffect
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val progress = taskProgress(task, nowMs)
    val cardBorder = if (isActive) BrandPrimary.copy(alpha = 0.5f) else DarkBorder

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimensions.screenPadding, vertical = 6.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(DarkCard)
            .border(1.dp, cardBorder, MaterialTheme.shapes.medium)
            .alpha(if (closed) 0.55f else 1f)
            .clickable(onClick = onOpen)
            .semantics { role = Role.Button },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            // Left color strip
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(accent),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(dimensions.cardPadding),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Header row: Title, focus shield icon, priority badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (task.focusMode) {
                        Icon(
                            Icons.Outlined.Shield,
                            contentDescription = "Focus mode",
                            tint = BrandPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = task.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (closed) DarkTextSecondary else DarkTextPrimary,
                        textDecoration = if (complete) TextDecoration.LineThrough else null,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    // Priority Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(priorityBadgeBg(task.priority))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = task.priority.replaceFirstChar(Char::titlecase),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = priorityColor(task.priority),
                        )
                    }
                }

                // Time Range & Duration
                Text(
                    text = "${task.startTime.asLocalTime()} – ${task.endTime.asLocalTime()} · ${task.durationMinutes.asDurationLabel()}",
                    fontSize = 13.sp,
                    color = DarkTextSecondary,
                )

                // Tags
                if (task.tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        task.tags.forEach { tag ->
                            Text(
                                text = "#$tag",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = BrandPrimary,
                            )
                        }
                    }
                }

                // Status or Active Progress
                when {
                    isActive -> {
                        Spacer(Modifier.height(2.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = accent,
                            trackColor = DarkSurfaceVariant,
                        )
                        Text(
                            text = task.timeRemainingLabel(nowMs),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandPrimary,
                        )
                    }
                    task.status == "scheduled" -> {
                        Text(
                            text = task.timeUntilStartLabel(),
                            fontSize = 12.sp,
                            color = DarkTextMuted,
                        )
                    }
                    closed -> {
                        Text(
                            text = if (complete) "✓ Completed" else "✕ Skipped",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (complete) Color(0xFF10B981) else DarkTextMuted,
                        )
                    }
                }
            }

            // Right actions column
            if (isActive) {
                Column(
                    modifier = Modifier
                        .padding(end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    IconButton(
                        onClick = { onComplete(task.id) },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(Icons.Outlined.Check, "Complete task", tint = Color(0xFF34D399), modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = { onExtend(task) },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(Icons.Outlined.Alarm, "Extend task", tint = BrandPrimary, modifier = Modifier.size(18.dp))
                    }
                    if (task.focusMode) {
                        IconButton(
                            onClick = { onStartFocus(task.id) },
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(Icons.Outlined.Shield, "Start focus", tint = BrandPrimary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            } else if (task.status == "scheduled") {
                Column(
                    modifier = Modifier
                        .padding(end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    IconButton(
                        onClick = { onSkip(task.id) },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(Icons.Outlined.SkipNext, "Skip task", tint = DarkTextMuted, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun taskAccent(raw: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(raw)) }
        .getOrDefault(BrandPrimary)

private fun taskProgress(task: Task, nowMs: Long): Float = runCatching {
    val start = Instant.parse(task.startTime).toEpochMilli()
    val end = Instant.parse(task.endTime).toEpochMilli()
    if (end <= start) return@runCatching 1f
    ((nowMs - start).toFloat() / (end - start).toFloat()).coerceIn(0f, 1f)
}.getOrDefault(0f)

@Composable
internal fun priorityColor(priority: String): Color = when (priority.lowercase()) {
    "critical" -> Color(0xFFF87171)
    "high" -> Color(0xFFFBBF24)
    "medium" -> BrandPrimary
    else -> Color(0xFF60A5FA)
}

@Composable
internal fun priorityBadgeBg(priority: String): Color = when (priority.lowercase()) {
    "critical" -> Color(0xFFEF4444).copy(alpha = 0.15f)
    "high" -> Color(0xFFF59E0B).copy(alpha = 0.15f)
    "medium" -> BrandPrimary.copy(alpha = 0.15f)
    else -> Color(0xFF3B82F6).copy(alpha = 0.15f)
}

internal fun String.asLocalTime(): String = runCatching {
    Instant.parse(this).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a"))
}.getOrDefault(this)

internal fun Int.asDurationLabel(): String = when {
    this < 60 -> "${this}m"
    this % 60 == 0 -> "${this / 60}h"
    else -> "${this / 60}h ${this % 60}m"
}

private fun Task.timeRemainingLabel(nowMs: Long = System.currentTimeMillis()): String = runCatching {
    val minutes = Duration.ofMillis(Instant.parse(endTime).toEpochMilli() - nowMs).toMinutes()
    if (minutes < 0) "Overdue by ${-minutes}m" else "${minutes}m remaining"
}.getOrDefault("")

private fun Task.timeUntilStartLabel(): String = runCatching {
    val minutes = Duration.between(Instant.now(), Instant.parse(startTime)).toMinutes()
    when {
        minutes <= 0 -> "Starting now"
        minutes < 60 -> "Starts in ${minutes}m"
        else -> "Starts in ${minutes / 60}h ${minutes % 60}m"
    }
}.getOrDefault("")
