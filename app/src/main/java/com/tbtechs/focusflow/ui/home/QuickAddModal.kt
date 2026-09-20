package com.tbtechs.focusflow.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
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
import com.tbtechs.focusflow.ui.theme.LocalFocusFlowDimensions
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import java.util.UUID

private val durationOptions = listOf(15, 30, 45, 60, 90, 120)
internal val priorityOptions = listOf("low", "medium", "high", "critical")
private val colorOptions = listOf("Primary", "Secondary", "Tertiary", "Error")

/**
 * Screenshot 6b: New Task form modal
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickAddModal(
    onDismiss: () -> Unit,
    onSave: (Task) -> Unit,
    settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val dimensions = LocalFocusFlowDimensions.current
    val settings by settingsViewModel.settings.collectAsState()
    val presets = settings.launcherPresets
    val initialStart = remember { LocalDateTime.now().plusMinutes(5) }
    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(initialStart.toLocalDate().toString()) }
    var time by remember { mutableStateOf(initialStart.toLocalTime().toString().take(5)) }
    var duration by remember { mutableStateOf(60) }
    var customDuration by remember { mutableStateOf(false) }
    var customDurationValue by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("medium") }
    var selectedColor by remember { mutableStateOf("Primary") }
    var tags by remember { mutableStateOf("") }
    var focusMode by remember { mutableStateOf(false) }
    var useGlobalApps by remember { mutableStateOf(true) }
    var allowedPackages by remember { mutableStateOf("") }
    var showAllowedApps by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }

    val savedTaskColor = colorForName(selectedColor).toArgb().toColorString()
    val parsedDate = runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now())
    val parsedTime = runCatching { LocalTime.parse(time) }.getOrDefault(LocalTime.now())
    val globalAllowedCount = settings.allowedFocusPackages.size
    val allowedAppsLabel = if (useGlobalApps) {
        if (globalAllowedCount == 0) {
            "Using global setting (all apps allowed)"
        } else {
            "Using global setting ($globalAllowedCount app${if (globalAllowedCount == 1) "" else "s"})"
        }
    } else {
        val count = allowedPackages.toPackageList().size
        if (count == 0) "All apps allowed" else "$count app${if (count == 1) "" else "s"} allowed"
    }

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
                    "New task",
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

            // Title & Notes
            HomeTextField(title, { title = it }, "Title")
            HomeTextField(notes, { notes = it }, "Notes (optional)", singleLine = false)

            // Date & Time pickers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(DarkSurfaceVariant)
                        .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
                        .clickable { showDatePicker = true }
                        .padding(14.dp),
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CalendarToday, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Date", fontSize = 11.sp, color = DarkTextMuted)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(date, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DarkTextPrimary)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(DarkSurfaceVariant)
                        .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
                        .clickable { showTimePicker = true }
                        .padding(14.dp),
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Schedule, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Start time", fontSize = 11.sp, color = DarkTextMuted)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(time, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DarkTextPrimary)
                    }
                }
            }

            // Duration section
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
                    (durationOptions.map(Int::asDurationLabel) + "Custom").forEach { choice ->
                        val isSelected = if (customDuration) choice == "Custom" else duration.asDurationLabel() == choice
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) BrandPrimary.copy(alpha = 0.2f) else DarkSurfaceVariant)
                                .border(1.dp, if (isSelected) BrandPrimary else DarkBorder, RoundedCornerShape(12.dp))
                                .clickable {
                                    customDuration = choice == "Custom"
                                    if (!customDuration) duration = durationOptions.first { it.asDurationLabel() == choice }
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
                    HomeTextField(customDurationValue, { customDurationValue = it }, "Custom duration (minutes)")
                }
            }

            // Pomodoro toggle
            ToggleCard(
                title = "Pomodoro mode",
                description = if (settings.pomodoroEnabled) {
                    "On — ${settings.pomodoroWorkMinutes}m work / ${settings.pomodoroBreakMinutes}m break"
                } else {
                    "Off — one continuous focus session"
                },
                checked = settings.pomodoroEnabled,
                onCheckedChange = { enabled ->
                    settingsViewModel.updateSettings(settings.copy(pomodoroEnabled = enabled))
                },
            )

            // Priority section
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
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) priorityBadgeBg(opt) else DarkSurfaceVariant)
                                .border(1.dp, if (isSelected) priorityColor(opt) else DarkBorder, RoundedCornerShape(12.dp))
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

            // Color section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "COLOR ACCENT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkTextMuted,
                    letterSpacing = 0.8.sp,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    colorOptions.forEach { colName ->
                        val isSelected = selectedColor == colName
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) BrandPrimary.copy(alpha = 0.2f) else DarkSurfaceVariant)
                                .border(1.dp, if (isSelected) BrandPrimary else DarkBorder, RoundedCornerShape(12.dp))
                                .clickable { selectedColor = colName }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                colName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isSelected) BrandPrimary else DarkTextPrimary,
                            )
                        }
                    }
                }
            }

            // Tags
            HomeTextField(tags, { tags = it }, "Tags (comma separated, e.g. work, design)")

            // Focus Mode
            ToggleCard(
                title = "Enable Focus Mode",
                description = "Block distractions during this task",
                checked = focusMode,
                onCheckedChange = { focusMode = it },
            )

            if (focusMode) {
                ToggleCard(
                    title = "Use Global Allowed List",
                    description = "Use the allowed apps list configured in Settings",
                    checked = useGlobalApps,
                    onCheckedChange = { useGlobalApps = it },
                )
                if (!useGlobalApps) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(DarkSurfaceVariant)
                            .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
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
                                Text(allowedAppsLabel, fontSize = 12.sp, color = DarkTextSecondary)
                            }
                            Text("Edit", fontSize = 13.sp, color = BrandPrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            if (showError) {
                Text(
                    "Enter a title and a valid date, time, and duration.",
                    color = Color(0xFFF87171),
                    fontSize = 13.sp,
                )
            }

            Spacer(Modifier.height(8.dp))

            // Action Buttons
            Button(
                onClick = {
                    val finalDuration = if (customDuration) customDurationValue.toIntOrNull() else duration
                    val start = runCatching {
                        LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time))
                            .atZone(ZoneId.systemDefault())
                            .toInstant()
                    }.getOrNull()
                    if (
                        title.isBlank() ||
                        start == null ||
                        runCatching { LocalDate.parse(date).isBefore(LocalDate.now()) }.getOrDefault(true) ||
                        finalDuration == null ||
                        finalDuration <= 0
                    ) {
                        showError = true
                    } else {
                        onSave(
                            Task(
                                id = UUID.randomUUID().toString(),
                                title = title.trim(),
                                description = notes.trim().ifBlank { null },
                                startTime = start.toString(),
                                endTime = start.plusSeconds(finalDuration * 60L).toString(),
                                durationMinutes = finalDuration,
                                status = "scheduled",
                                priority = priority,
                                tags = tags.split(',').map(String::trim).filter(String::isNotBlank),
                                color = savedTaskColor,
                                focusMode = focusMode,
                                focusAllowedPackages = if (!focusMode || useGlobalApps) null else allowedPackages.toPackageList(),
                                createdAt = Instant.now().toString(),
                                updatedAt = Instant.now().toString(),
                            ),
                        )
                        onDismiss()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("Save Task", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }

            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 44.dp),
            ) {
                Text("Cancel", color = DarkTextSecondary, fontSize = 14.sp)
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = parsedDate
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            shape = RoundedCornerShape(24.dp),
            colors = DatePickerDefaults.colors(
                containerColor = DarkCard,
            ),
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            date = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                                .toString()
                        }
                        showDatePicker = false
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                ) { Text("OK", color = BrandPrimary) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDatePicker = false },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                ) { Text("Cancel", color = DarkTextSecondary) }
            },
        ) {
            DatePicker(
                state = pickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = DarkCard,
                    titleContentColor = DarkTextPrimary,
                    headlineContentColor = DarkTextPrimary,
                    weekdayContentColor = DarkTextSecondary,
                    subheadContentColor = DarkTextSecondary,
                    yearContentColor = DarkTextPrimary,
                    currentYearContentColor = BrandPrimary,
                    selectedYearContentColor = Color.White,
                    selectedYearContainerColor = BrandPrimary,
                    dayContentColor = DarkTextPrimary,
                    selectedDayContentColor = Color.White,
                    selectedDayContainerColor = BrandPrimary,
                    todayContentColor = BrandPrimary,
                    todayDateBorderColor = BrandPrimary,
                ),
            )
        }
    }

    if (showTimePicker) {
        val pickerState = rememberTimePickerState(
            initialHour = parsedTime.hour,
            initialMinute = parsedTime.minute,
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
                        time = String.format(Locale.US, "%02d:%02d", pickerState.hour, pickerState.minute)
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
}

@Composable
private fun ToggleCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurfaceVariant)
            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DarkTextPrimary)
                Text(description, fontSize = 12.sp, color = DarkTextSecondary)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
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
}

@Composable
internal fun colorForName(name: String) = when (name) {
    "Secondary" -> Color(0xFF10B981)
    "Tertiary" -> Color(0xFFF59E0B)
    "Error" -> Color(0xFFEF4444)
    else -> BrandPrimary
}

internal fun Int.toColorString(): String = String.format(Locale.US, "#%08X", this)
internal fun String.toPackageList(): List<String> = split(',').map(String::trim).filter(String::isNotBlank)
