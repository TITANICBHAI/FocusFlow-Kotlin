package com.tbtechs.focusflow.ui.backup

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.data.model.AppSettings
import com.tbtechs.focusflow.data.repository.BackupEnvelope
import com.tbtechs.focusflow.data.repository.BackupParseResult
import com.tbtechs.focusflow.data.repository.RestoreResult
import kotlinx.coroutines.launch

@Composable
fun ImportConfirmScreen(
    source: Uri?,
    backupCoordinator: BackupCoordinator,
    currentSettings: AppSettings,
    currentFocusActive: Boolean,
    initialReplaceTasks: Boolean = false,
    onBack: () -> Unit,
    onImported: () -> Unit,
) {
    var parsed by remember(source) { mutableStateOf<BackupParseResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var replaceTasks by remember(source, initialReplaceTasks) { mutableStateOf(initialReplaceTasks) }
    var restoreSettings by remember { mutableStateOf(true) }
    var restoreTasks by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf<RestoreResult?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(source) {
        parsed = source?.let { backupCoordinator.inspect(it) }
            ?: BackupParseResult.Error("This import has expired.")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import backup") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Cancel import")
                    }
                },
            )
        },
    ) { padding ->
        when (val state = parsed) {
            null -> LoadingImport(Modifier.fillMaxSize().padding(padding))
            is BackupParseResult.Error -> ImportError(
                message = state.message,
                modifier = Modifier.fillMaxSize().padding(padding),
                onClose = onBack,
            )
            is BackupParseResult.Success -> ImportReview(
                envelope = state.envelope,
                replaceTasks = replaceTasks,
                onReplaceTasksChange = { replaceTasks = it },
                restoreSettings = restoreSettings,
                onRestoreSettingsChange = { restoreSettings = it },
                restoreTasks = restoreTasks,
                onRestoreTasksChange = {
                    restoreTasks = it
                    if (!it) replaceTasks = false
                },
                busy = busy,
                currentFocusActive = currentFocusActive,
                modifier = Modifier.fillMaxSize().padding(padding),
                onImport = {
                    if (restoreSettings || restoreTasks) {
                        busy = true
                        result = null
                        scope.launch {
                            result = source?.let {
                                backupCoordinator.import(
                                    source = it,
                                    replaceTasks = replaceTasks,
                                    currentSettings = currentSettings,
                                    currentFocusActive = currentFocusActive,
                                    restoreSettings = restoreSettings,
                                    restoreTasks = restoreTasks,
                                )
                            } ?: RestoreResult.Error("This import has expired.")
                            busy = false
                        }
                    }
                },
                onCancel = onBack,
            )
        }
    }

    when (val outcome = result) {
        is RestoreResult.Error -> AlertDialog(
            onDismissRequest = { result = null },
            icon = { Icon(Icons.Outlined.ErrorOutline, contentDescription = null) },
            title = { Text("Import failed") },
            text = { Text(outcome.message) },
            confirmButton = { Button(onClick = { result = null }) { Text("Close") } },
        )
        is RestoreResult.Success -> AlertDialog(
            onDismissRequest = onImported,
            icon = { Icon(Icons.Outlined.CloudDownload, contentDescription = null) },
            title = { Text("Backup imported") },
            text = {
                Text(
                    "${outcome.summary.tasksImported} task${if (outcome.summary.tasksImported == 1) "" else "s"} added. " +
                        "${outcome.summary.tasksSkipped} skipped." +
                        if (outcome.summary.warnings.isNotEmpty()) {
                            "\n\nWarnings:\n${outcome.summary.warnings.joinToString("\n")}"
                        } else "",
                )
            },
            confirmButton = { Button(onClick = onImported) { Text("Done") } },
        )
        null -> Unit
    }
}

@Composable
private fun LoadingImport(modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator()
        Text("Reading backup…", modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun ImportError(message: String, modifier: Modifier, onClose: () -> Unit) {
    Column(
        modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        Text("Import unavailable", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        OutlinedButton(onClick = onClose, modifier = Modifier.padding(top = 16.dp)) { Text("Close") }
    }
}

@Composable
private fun ImportReview(
    envelope: BackupEnvelope,
    replaceTasks: Boolean,
    onReplaceTasksChange: (Boolean) -> Unit,
    restoreSettings: Boolean,
    onRestoreSettingsChange: (Boolean) -> Unit,
    restoreTasks: Boolean,
    onRestoreTasksChange: (Boolean) -> Unit,
    busy: Boolean,
    currentFocusActive: Boolean,
    modifier: Modifier,
    onImport: () -> Unit,
    onCancel: () -> Unit,
) {
    val taskCount = envelope.raw.optJSONObject("summary")?.optInt("taskCount", envelope.tasks.length())
        ?: envelope.tasks.length()
    val blockedWordCount = envelope.raw.optJSONObject("summary")?.optInt("blockedWordCount")
        ?: envelope.settings.optJSONArray("blockedWords")?.length().orZero()
    val settingsCount = envelope.settings.length()

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("FocusFlow backup", style = MaterialTheme.typography.headlineSmall)
                Text("Review the contents and choose what to bring onto this device. Nothing changes until you tap the import button.")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "Settings and block lists are merged with this device. Tasks can be merged or replaced, depending on the option below.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text("This file contains", style = MaterialTheme.typography.titleMedium)
        Card {
            Column(Modifier.padding(horizontal = 16.dp)) {
                SummaryRow(Icons.Outlined.TaskAlt, "Tasks", taskCount.toString())
                SummaryRow(Icons.Outlined.Settings, "Settings fields", settingsCount.toString())
                SummaryRow(Icons.Outlined.WarningAmber, "Blocked words", blockedWordCount.toString())
            }
        }
        Text("Sections to import", style = MaterialTheme.typography.titleMedium)
        Card {
            Column {
                ImportSectionRow("Portable settings and block lists", restoreSettings, onRestoreSettingsChange)
                ImportSectionRow("Tasks and reminders", restoreTasks, onRestoreTasksChange)
            }
        }
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (replaceTasks) "Replace mode" else "Merge mode",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    if (replaceTasks) {
                        "Existing tasks are removed first, then the backup tasks are restored. Use this only when the backup should become the task list on this device."
                    } else {
                        "Existing tasks stay in place. Backup tasks are added without deleting current tasks, and matching task IDs are skipped."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Card {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(if (replaceTasks) "Replace all existing tasks" else "Merge with existing tasks", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (replaceTasks) "Current tasks will be deleted before the backup tasks are restored. Settings are still merged."
                        else "Existing task IDs are kept. New tasks and portable settings are added without deleting current data.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = replaceTasks, onCheckedChange = onReplaceTasksChange, enabled = restoreTasks)
            }
        }
        if (replaceTasks) {
            Card {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(
                        if (currentFocusActive) "Replace is unavailable while a focus session is active."
                        else "Replace is destructive. Existing tasks will be deleted.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        Button(
            onClick = onImport,
            enabled = !busy && (restoreSettings || restoreTasks) && !(replaceTasks && currentFocusActive),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
            Text(if (replaceTasks) "Replace & Import" else "Merge & Import")
        }
        OutlinedButton(onClick = onCancel, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}

@Composable
private fun ImportSectionRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label)
    }
}

@Composable
private fun SummaryRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(label, Modifier.weight(1f).padding(start = 12.dp))
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun Int?.orZero(): Int = this ?: 0