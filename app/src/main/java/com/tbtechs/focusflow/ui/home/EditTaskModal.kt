package com.tbtechs.focusflow.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tbtechs.focusflow.data.model.Task
import com.tbtechs.focusflow.ui.SettingsViewModel
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkSurfaceVariant
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val editDurationOptions = listOf(25, 45, 60, 90, 120)

/**
 * Screenshot 6e: Edit Task modal screen
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditTaskModal(
    task: Task,
    onDismiss: () -> Unit,
    onSave: (Task) -> Unit,
    onDelete: () -> Unit,
    settingsViewModel: SettingsViewModel = viewModel(),
) {
    val settings by settingsViewModel.settings.collectAsState()
    val presets = settings.launcherPresets
    val zone = remember { ZoneId.systemDefault() }
    val taskDate = remember(task.id, task.startTime) {
        runCatching { Instant.parse(task.startTime).atZone(zone).toLocalDate() }
            .getOrDefault(LocalDate.now(zone))
    }
    val initialTime = remember(task.id, task.startTime) {
        runCatching { Instant.parse(task.startTime).atZone(zone).toLocalTime().withSecond(0).withNano(0) }
            .getOrDefault(LocalTime.now(zone).withSecond(0).withNano(0))
    }

    var title by remember(task.id) { mutableStateOf(task.title) }
    var notes by remember(task.id) { mutableStateOf(task.description.orEmpty()) }
    var notesExpanded by remember(task.id) { mutableStateOf(task.description != null) }
    var time by remember(task.id) { mutableStateOf(initialTime.toString().take(5)) }
    var duration by remember(task.id) { mutableStateOf(task.durationMinutes) }
    var customDuration by remember(task.id) { mutableStateOf(task.durationMinutes !in editDurationOptions) }
    var customDurationValue by remember(task.id) { mutableStateOf(task.durationMinutes.toString()) }
    var priority by remember(task.id) { mutableStateOf(task.priority) }
    var tags by remember(task.id) { mutableStateOf(task.tags) }
    var newTag by remember(task.id) { mutableStateOf("") }
    var focusMode by remember(task.id) { mutableStateOf(task.focusMode) }
    var allowedPackages by remember(task.id) {
        mutableStateOf(task.focusAllowedPackages?.joinToString(", ").orEmpty())
    }
    var showAllowedApps by remember(task.id) { mutableStateOf(false) }
    var showTimePicker by remember(task.id) { mutableStateOf(false) }
    var showDeleteConfirmation by remember(task.id) { mutableStateOf(false) }
    var showError by remember(task.id) { mutableStateOf(false) }

    val usesGlobalApps = task.focusAllowedPackages == null
    val globalAllowedCount = settings.allowedFocusPackages.size
    val allowedAppsDescription = if (usesGlobalApps && allowedPackages.isBlank()) {
        if (globalAllowedCount > 0) {
            "Using global list ($globalAllowedCount app${if (globalAllowedCount == 1) "" else "s"})"
        } else {
            "Using global list (all apps allowed)"
        }
    } else {
        val count = allowedPackages.toPackageList().size
        if (count == 0) "All apps allowed for this task"
        else "$count custom app${if (count == 1) "" else "s"} allowed"
    }
    val savedTaskColor = priorityColor(priority).toArgb().toColorString()
    val currentTime = runCatching { LocalTime.parse(time) }.getOrDefault(initialTime)

    fun addTag() {
        val candidate = newTag.trim().removePrefix("#")
        if (candidate.isNotBlank() && candidate !in tags) tags = tags + candidate
        newTag = ""
    }

    fun saveTask() {
        val finalDuration = if (customDuration) customDurationValue.toIntOrNull() else duration
        val start = runCatching {
            LocalDateTime.of(taskDate, LocalTime.parse(time))
                .atZone(zone)
                .toInstant()
        }.getOrNull()
        if (title.isBlank() || start == null || finalDuration == null || finalDuration < 5) {
            showError = true
            return
        }

        val end = start.plusSeconds(finalDuration * 60L)
        onSave(
            task.copy(
                title = title.trim(),
                description = notes.trim().ifBlank { null },
                startTime = start.toString(),
                endTime = end.toString(),
                durationMinutes = finalDuration,
                priority = priority,
                tags = tags,
                reminders = task.reminders,
                color = savedTaskColor,
                focusMode = focusMode,
                focusAllowedPackages = if (!focusMode) {
                    null
                } else if (usesGlobalApps && allowedPackages.isBlank()) {
                    null
                } else {
                    allowedPackages.toPackageList()
                },
                updatedAt = Instant.now().toString(),
            ),
        )
        onDismiss()
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Edit task",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkTextPrimary,
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

            // Title
            HomeTextField(title, { title = it; showError = false }, "Task title")

            // Notes
            if (notesExpanded) {
                HomeTextField(notes, { notes = it }, "Notes (optional)", singleLine = false)
            } else {
                OutlinedButton(
                    onClick = { notesExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                ) {
                    Text("+ Add Notes", color = DarkTextSecondary, fontSize = 13.sp)
                }
            }

            // Start Time Selector
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurfaceVariant)
                    .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                    .clickable { showTimePicker = true }
                    .padding(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Schedule, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Start time", fontSize = 11.sp, color = DarkTextMuted)
                        Text(time.asDisplayTime(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DarkTextPrimary)
                    }
                    Text("Change", fontSize = 13.sp, color = BrandPrimary, fontWeight = FontWeight.SemiBold)
                }
            }

            // Duration
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "DURATION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkTextMuted,
                    letterSpacing = 0.8.sp,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    (editDurationOptions.map(Int::asDurationLabel) + "Custom").forEach { choice ->
                        val isSelected = if (customDuration) choice == "Custom" else duration.asDurationLabel() == choice
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) BrandPrimary.copy(alpha = 0.2f) else DarkSurfaceVariant)
                                .border(1.dp, if (isSelected) BrandPrimary else DarkBorder, RoundedCornerShape(8.dp))
                                .clickable {
                                    customDuration = choice == "Custom"
                                    if (!customDuration) {
                                        duration = editDurationOptions.first { it.asDurationLabel() == choice }
                                        customDurationValue = duration.toString()
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text(
                                choice,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) BrandPrimary else DarkTextPrimary,
                            )
                        }
                    }
                }
                if (customDuration) {
                    Spacer(Modifier.height(4.dp))
                    HomeTextField(customDurationValue, { customDurationValue = it }, "Custom minutes")
                }
            }

            // Priority
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "PRIORITY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkTextMuted,
                    letterSpacing = 0.8.sp,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    priorityOptions.forEach { opt ->
                        val isSelected = priority.lowercase() == opt
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) priorityBadgeBg(opt) else DarkSurfaceVariant)
                                .border(1.dp, if (isSelected) priorityColor(opt) else DarkBorder, RoundedCornerShape(8.dp))
                                .clickable { priority = opt }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                opt.replaceFirstChar(Char::titlecase),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) priorityColor(opt) else DarkTextPrimary,
                            )
                        }
                    }
                }
            }

            // Tags
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "TAGS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkTextMuted,
                    letterSpacing = 0.8.sp,
                )
                if (tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        tags.forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DarkSurfaceVariant)
                                    .border(1.dp, DarkBorder, RoundedCornerShape(6.dp))
                                    .clickable { tags = tags - tag }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("#$tag", fontSize = 12.sp, color = BrandPrimary, fontWeight = FontWeight.Medium)
                                    Spacer(Modifier.width(4.dp))
                                    Text("×", fontSize = 14.sp, color = DarkTextMuted)
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        HomeTextField(newTag, { newTag = it }, "Add a tag")
                    }
                    Button(
                        onClick = ::addTag,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Add")
                    }
                }
            }

            // Focus Mode Toggle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurfaceVariant)
                    .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                    .padding(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Focus Mode", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DarkTextPrimary)
                        Text("Block distracting apps during this task", fontSize = 12.sp, color = DarkTextSecondary)
                    }
                    Switch(
                        checked = focusMode,
                        onCheckedChange = { focusMode = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = BrandPrimary,
                            uncheckedThumbColor = DarkTextMuted,
                            uncheckedTrackColor = DarkCard,
                            uncheckedBorderColor = DarkBorder,
                        ),
                    )
                }
            }

            if (focusMode) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurfaceVariant)
                        .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                        .clickable { showAllowedApps = true }
                        .padding(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Shield, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Allowed Apps", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DarkTextPrimary)
                            Text(allowedAppsDescription, fontSize = 12.sp, color = DarkTextSecondary)
                        }
                        Text("Customize", fontSize = 13.sp, color = BrandPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (showError) {
                Text(
                    "Enter a title, valid time, and a duration of at least 5 minutes.",
                    color = Color(0xFFF87171),
                    fontSize = 13.sp,
                )
            }

            Spacer(Modifier.height(8.dp))

            // Actions
            Button(
                onClick = ::saveTask,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Save Changes", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
                onClick = { showDeleteConfirmation = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF87171)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null, tint = Color(0xFFF87171), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Delete Task", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Cancel", color = DarkTextSecondary, fontSize = 14.sp)
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showTimePicker) {
        val pickerState = rememberTimePickerState(
            initialHour = currentTime.hour,
            initialMinute = currentTime.minute,
            is24Hour = false,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("Select start time", fontWeight = FontWeight.Bold) },
            text = {
                TimePicker(
                    state = pickerState,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = DarkSurfaceVariant,
                        selectorColor = BrandPrimary,
                        containerColor = DarkCard,
                        periodSelectorBorderColor = DarkBorder,
                        periodSelectorSelectedContainerColor = BrandPrimary,
                        periodSelectorUnselectedContainerColor = DarkSurfaceVariant,
                        periodSelectorSelectedContentColor = Color.White,
                        periodSelectorUnselectedContentColor = DarkTextSecondary,
                        timeSelectorSelectedContainerColor = BrandPrimary.copy(alpha = 0.2f),
                        timeSelectorUnselectedContainerColor = DarkSurfaceVariant,
                        timeSelectorSelectedContentColor = BrandPrimary,
                        timeSelectorUnselectedContentColor = DarkTextPrimary,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    time = "%02d:%02d".format(pickerState.hour, pickerState.minute)
                    showTimePicker = false
                }) { Text("OK", color = BrandPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel", color = DarkTextSecondary) }
            },
        )
    }

    if (showAllowedApps) {
        AllowedAppsDialog(
            value = allowedPackages,
            onDismiss = { showAllowedApps = false },
            onSave = { allowedPackages = it; showAllowedApps = false },
            presets = presets,
            onSavePreset = { preset ->
                settingsViewModel.updateSettings(settings.copy(launcherPresets = settings.launcherPresets + preset))
            },
            onDeletePreset = { presetId ->
                settingsViewModel.updateSettings(
                    settings.copy(launcherPresets = settings.launcherPresets.filterNot { it.id == presetId }),
                )
            },
        )
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("Delete Task", fontWeight = FontWeight.Bold) },
            text = { Text("Delete “${task.title}”? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete()
                    },
                ) {
                    Text("Delete", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel", color = DarkTextSecondary) }
            },
        )
    }
}

private fun String.asDisplayTime(): String = runCatching {
    LocalTime.parse(this).format(DateTimeFormatter.ofPattern("h:mm a"))
}.getOrDefault(this)
