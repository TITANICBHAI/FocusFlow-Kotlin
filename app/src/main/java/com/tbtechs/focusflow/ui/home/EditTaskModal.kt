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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import com.tbtechs.focusflow.ui.theme.LocalFocusFlowDimensions
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
    settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
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
    val savedTaskColor = task.color
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(RefBackground)
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(bottom = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RefHeader)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                    Text("Cancel", color = RefSecondary, fontSize = 16.sp)
                }
                Text(
                    "Edit Task",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = RefText,
                )
                TextButton(onClick = ::saveTask, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                    Text("Save", color = BrandPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Title
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
            HomeTextField(title, { title = it; showError = false }, "Task title")

            // Notes
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .border(2.dp, RefBorder, RoundedCornerShape(18.dp))
                    .clickable { notesExpanded = !notesExpanded }
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Description, contentDescription = "Notes", tint = RefSecondary, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(14.dp))
                Text("Notes", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = RefText)
                Spacer(Modifier.width(8.dp))
                Text("Optional", fontSize = 13.sp, color = RefSecondary, modifier = Modifier.weight(1f))
                Icon(
                    if (notesExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                    contentDescription = if (notesExpanded) "Collapse notes" else "Expand notes",
                    tint = RefSecondary,
                    modifier = Modifier.size(26.dp),
                )
            }
            if (notesExpanded) {
                HomeTextField(
                    notes,
                    { notes = it },
                    "Add details...",
                    singleLine = false,
                    multilineMinHeight = 112.dp,
                )
            }

            // Start Time Selector
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(RefCard)
                    .border(1.dp, RefBorder, RoundedCornerShape(18.dp))
                    .clickable { showTimePicker = true }
                    .padding(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Schedule, contentDescription = "Start time", tint = RefSecondary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(time.asDisplayTime(), fontSize = 18.sp, color = RefText)
                    }
                    Text("Change", fontSize = 14.sp, color = BrandPrimary, fontWeight = FontWeight.SemiBold)
                }
            }

            // Duration
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReferenceSectionLabel("Duration")
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    (editDurationOptions.map(Int::asDurationLabel) + "Custom").forEach { choice ->
                        val isSelected = if (customDuration) choice == "Custom" else duration.asDurationLabel() == choice
                        ReferencePill(
                            text = choice,
                            selected = isSelected,
                            onClick = {
                                    customDuration = choice == "Custom"
                                    if (!customDuration) {
                                        duration = editDurationOptions.first { it.asDurationLabel() == choice }
                                        customDurationValue = duration.toString()
                                    }
                                },
                        )
                    }
                }
                if (customDuration) {
                    Spacer(Modifier.height(4.dp))
                    HomeTextField(customDurationValue, { customDurationValue = it }, "Custom minutes")
                }
            }

            // Priority
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReferenceSectionLabel("Priority")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    priorityOptions.forEach { opt ->
                        val isSelected = priority.lowercase() == opt
                        ReferencePill(
                            text = opt.replaceFirstChar(Char::titlecase),
                            selected = isSelected,
                            modifier = Modifier.weight(1f),
                            fontSize = 12.sp,
                            horizontalPadding = 4.dp,
                            selectedColor = if (opt == "medium") RefBlue else BrandPrimary,
                            onClick = { priority = opt },
                        )
                    }
                }
            }

            // Tags
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReferenceSectionLabel("Tags")
                if (tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        tags.forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(RefCard)
                                    .border(1.dp, RefBorder, RoundedCornerShape(8.dp))
                                    .clickable { tags = tags - tag }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("#$tag", fontSize = 14.sp, color = BrandPrimary, fontWeight = FontWeight.Medium)
                                    Spacer(Modifier.width(4.dp))
                                    Text("×", fontSize = 16.sp, color = RefMuted)
                                }
                            }
                        }
                    }
                }
                HomeTextField(
                    value = newTag,
                    onValueChange = { newTag = it },
                    label = "Add a tag",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addTag() }),
                )
                Text(
                    "Press return to add each tag.",
                    fontSize = 15.sp,
                    color = RefSecondary,
                )
            }

            // Focus Mode Toggle
            ReferenceToggleCard(
                title = "Focus Mode",
                description = "Block distracting apps during this task",
                checked = focusMode,
                onCheckedChange = { focusMode = it },
            )

            if (focusMode) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(RefCard)
                        .border(1.dp, RefBorder, RoundedCornerShape(14.dp))
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
                            Text("Allowed Apps", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = RefText)
                            Text(allowedAppsDescription, fontSize = 14.sp, color = RefSecondary)
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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .border(2.dp, RefRed.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                    .clickable { showDeleteConfirmation = true }
                    .padding(vertical = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete task", tint = RefRed, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Delete Task", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = RefRed)
                }
            }

            Spacer(Modifier.height(16.dp))
            }
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
            shape = RoundedCornerShape(24.dp),
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
                TextButton(
                    onClick = {
                        time = "%02d:%02d".format(pickerState.hour, pickerState.minute)
                        showTimePicker = false
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                ) { Text("OK", color = BrandPrimary) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showTimePicker = false },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                ) { Text("Cancel", color = DarkTextSecondary) }
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
            shape = RoundedCornerShape(24.dp),
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
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                ) {
                    Text("Delete", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmation = false },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                ) { Text("Cancel", color = DarkTextSecondary) }
            },
        )
    }
}

private fun String.asDisplayTime(): String = runCatching {
    LocalTime.parse(this).format(DateTimeFormatter.ofPattern("h:mm a"))
}.getOrDefault(this)
