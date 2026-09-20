package com.tbtechs.focusflow.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.data.model.Task
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkSurfaceVariant
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.LocalFocusFlowDimensions
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Screenshot 6d: Task Details screen / modal
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskDetailModal(
    task: Task,
    onDismiss: () -> Unit,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    onExtend: () -> Unit,
    onStartFocus: () -> Unit,
    onEdit: () -> Unit,
) {
    val dimensions = LocalFocusFlowDimensions.current
    val canAct = task.status !in setOf("completed", "skipped")
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        containerColor = DarkBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = dimensions.modalPadding, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(dimensions.sectionSpacing),
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    task.title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkTextPrimary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = DarkTextSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // Description / Notes
            task.description?.takeIf { it.isNotBlank() }?.let { notes ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(DarkSurfaceVariant)
                        .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "NOTES",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkTextMuted,
                            letterSpacing = 0.8.sp,
                        )
                        Text(
                            notes,
                            fontSize = 14.sp,
                            color = DarkTextPrimary,
                        )
                    }
                }
            }

            // Schedule Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkCard)
                    .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                    .padding(14.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "SCHEDULE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkTextMuted,
                        letterSpacing = 0.8.sp,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Schedule, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "${task.startTime.asLocalTime()} – ${task.endTime.asLocalTime()} · ${task.durationMinutes.asDurationLabel()}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DarkTextPrimary,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CalendarToday, contentDescription = null, tint = DarkTextSecondary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            task.startTime.asLocalDate(),
                            fontSize = 13.sp,
                            color = DarkTextSecondary,
                        )
                    }
                }
            }

            // Priority & Status Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(DarkCard)
                        .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("PRIORITY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkTextMuted, letterSpacing = 0.8.sp)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(priorityBadgeBg(task.priority))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                task.priority.replaceFirstChar(Char::titlecase),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = priorityColor(task.priority),
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(DarkCard)
                        .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("STATUS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkTextMuted, letterSpacing = 0.8.sp)
                        Text(
                            task.status.replaceFirstChar(Char::titlecase),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = when (task.status.lowercase()) {
                                "completed" -> Color(0xFF34D399)
                                "skipped" -> DarkTextMuted
                                else -> DarkTextPrimary
                            },
                        )
                    }
                }
            }

            // Tags
            if (task.tags.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("TAGS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkTextMuted, letterSpacing = 0.8.sp)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        task.tags.forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkSurfaceVariant)
                                    .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Text("#$tag", fontSize = 12.sp, color = BrandPrimary, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            // Focus Mode Details
            if (task.focusMode) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(DarkCard)
                        .border(1.dp, BrandPrimary.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Shield, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Focus Mode Enabled", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DarkTextPrimary)
                            Text(
                                if (task.focusAllowedPackages == null) "Using global allowed apps list"
                                else "${task.focusAllowedPackages.size} custom allowed app(s)",
                                fontSize = 11.sp,
                                color = DarkTextSecondary,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 44.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, tint = DarkTextPrimary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Edit", color = DarkTextPrimary, fontSize = 13.sp)
                }

                if (canAct) {
                    Button(
                        onClick = onComplete,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Done", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = onExtend,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 44.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    ) {
                        Icon(Icons.Outlined.Alarm, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Extend", color = DarkTextPrimary, fontSize = 13.sp)
                    }

                    if (task.focusMode) {
                        Button(
                            onClick = onStartFocus,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Outlined.Shield, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Focus", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            if (canAct) {
                TextButton(
                    onClick = onSkip,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 44.dp),
                ) {
                    Text("Skip Task", color = DarkTextMuted, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun String.asLocalDate(): String = runCatching {
    Instant.parse(this).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))
}.getOrDefault(this)
