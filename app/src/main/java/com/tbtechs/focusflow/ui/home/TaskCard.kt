package com.tbtechs.focusflow.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.data.model.Task
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Redesigned TaskCard for Screenshots 6c & 6f.
 */
@Composable
fun TaskCard(
    task: Task,
    isActive: Boolean,
    onOpen: () -> Unit,
    onComplete: (String) -> Unit,
    onSkip: (String) -> Unit,
    onExtend: (Task) -> Unit,
) {
    val complete = task.status == "completed"
    val closed = complete || task.status == "skipped"
    val accent = taskAccent(task.color)
    var nowMs by remember(task.id) { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(task.id, closed) {
        if (closed) return@LaunchedEffect
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(30_000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
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
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accent),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = task.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (closed) DarkTextMuted else DarkTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(priorityBadgeBg(task.priority))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = task.priority.replaceFirstChar(Char::titlecase),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = priorityColor(task.priority),
                        )
                    }
                }

                Text(
                    text = "${task.startTime.asLocalTime()} – ${task.endTime.asLocalTime()} · ${task.durationMinutes.asDurationLabel()}",
                    fontSize = 13.sp,
                    color = DarkTextSecondary,
                )

                if (!closed) {
                    Text(
                        text = if (isActive) task.timeRemainingLabel(nowMs) else task.timeUntilStartLabel(nowMs),
                        fontSize = 11.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = DarkTextSecondary,
                    )
                }
            }

            if (!closed) {
                Box(
                    modifier = Modifier
                        .padding(end = 12.dp, top = 12.dp, bottom = 12.dp)
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, RefBorder, RoundedCornerShape(10.dp))
                        .clickable {
                            if (isActive) onComplete(task.id) else onSkip(task.id)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isActive) Icons.Outlined.Check else Icons.Outlined.SkipNext,
                        contentDescription = if (isActive) "Complete task" else "Skip task",
                        tint = if (isActive) Color.White else DarkTextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun taskAccent(raw: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(raw)) }
        .getOrDefault(BrandPrimary)

@Composable
internal fun priorityColor(priority: String): Color = when (priority.lowercase()) {
    "critical" -> Color(0xFFF87171)
    "high" -> Color(0xFFFBBF24)
    "medium" -> RefBlue
    else -> Color(0xFFCBD5E1)
}

@Composable
internal fun priorityBadgeBg(priority: String): Color = when (priority.lowercase()) {
    "critical" -> Color(0xFFEF4444).copy(alpha = 0.15f)
    "high" -> Color(0xFFF59E0B).copy(alpha = 0.15f)
    "medium" -> RefBlue.copy(alpha = 0.15f)
    else -> Color(0xFFCBD5E1).copy(alpha = 0.15f)
}

internal fun String.asLocalTime(): String = runCatching {
    Instant.parse(this).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a"))
}.getOrDefault(this)

internal fun Int.asDurationLabel(): String = when {
    this < 60 -> "${this}m"
    this % 60 == 0 -> "${this / 60}h"
    else -> "${this / 60}h ${this % 60}m"
}

private fun Task.timeRemainingLabel(nowMs: Long): String = runCatching {
    val minutes = Duration.ofMillis(Instant.parse(endTime).toEpochMilli() - nowMs).toMinutes()
    if (minutes < 0) "Overdue by ${-minutes}m" else "${minutes}m remaining"
}.getOrDefault("")

private fun Task.timeUntilStartLabel(nowMs: Long): String = runCatching {
    val minutes = Duration.ofMillis(Instant.parse(startTime).toEpochMilli() - nowMs).toMinutes()
    when {
        minutes <= 0 -> "Starting now"
        minutes < 60 -> "Starts in ${minutes}m"
        else -> "Starts in ${minutes / 60}h ${minutes % 60}m"
    }
}.getOrDefault("")
