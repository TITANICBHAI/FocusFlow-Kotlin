package com.tbtechs.focusflow.ui.backup

import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.data.repository.BackupEnvelope
import com.tbtechs.focusflow.data.repository.BackupParseResult
import com.tbtechs.focusflow.data.repository.RestoreResult
import com.tbtechs.focusflow.data.restore.RestorePreview
import kotlinx.coroutines.launch

@Composable
fun ImportConfirmScreen(
    pendingGeneration: Int,
    backupCoordinator: BackupCoordinator,
    currentFocusActive: Boolean,
    initialReplaceTasks: Boolean = false,
    onBack: () -> Unit,
    onImported: () -> Unit,
) {
    var parsed by remember(pendingGeneration) { mutableStateOf<BackupParseResult?>(null) }
    var busy by remember(pendingGeneration) { mutableStateOf(false) }
    var replaceTasks by remember(pendingGeneration, initialReplaceTasks) { mutableStateOf(initialReplaceTasks) }
    var restoreSettings by remember(pendingGeneration) { mutableStateOf(true) }
    var restoreTasks by remember(pendingGeneration) { mutableStateOf(true) }
    var result by remember(pendingGeneration) { mutableStateOf<RestoreResult?>(null) }
    var preview by remember(pendingGeneration) { mutableStateOf<RestorePreview?>(null) }
    var requiresDefensePin by remember(pendingGeneration) { mutableStateOf(false) }
    var showPinPrompt by remember(pendingGeneration) { mutableStateOf(false) }
    var defensePin by remember(pendingGeneration) { mutableStateOf("") }
    var pinError by remember(pendingGeneration) { mutableStateOf<String?>(null) }
    var pendingConsentPin by remember(pendingGeneration) { mutableStateOf<String?>(null) }
    var awaitingVpnConsent by remember(pendingGeneration) { mutableStateOf(false) }
    var expandedProtectionCategories by remember(pendingGeneration, result) {
        mutableStateOf(emptySet<String>())
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    suspend fun performImport(pin: String?, activateImportedVpn: Boolean) {
        val outcome = backupCoordinator.importPending(
            replaceTasks = replaceTasks,
            currentFocusActive = currentFocusActive,
            restoreSettings = restoreSettings,
            restoreTasks = restoreTasks,
            defensePin = pin,
            activateImportedVpnAfterGrant = activateImportedVpn,
        )
        busy = false
        if (outcome is RestoreResult.Error && outcome.requiresPin) {
            showPinPrompt = true
            pinError = outcome.message
        } else {
            showPinPrompt = false
            defensePin = ""
            pinError = null
            result = outcome
        }
    }

    val vpnConsentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (awaitingVpnConsent) {
            awaitingVpnConsent = false
            val pin = pendingConsentPin
            pendingConsentPin = null
            val permissionGranted = runCatching {
                VpnService.prepare(context) == null
            }.getOrDefault(false)
            scope.launch {
                performImport(
                    pin,
                    VpnImportPolicy.shouldActivateAfterConsent(
                        VpnImportConsentDecision.REQUEST_CONSENT,
                        permissionGranted,
                    ),
                )
            }
        }
    }

    LaunchedEffect(pendingGeneration) {
        parsed = backupCoordinator.inspectPending()
    }

    LaunchedEffect(pendingGeneration, parsed, restoreSettings) {
        requiresDefensePin = parsed is BackupParseResult.Success &&
            backupCoordinator.requiresDefensePin(restoreSettings)
    }

    LaunchedEffect(pendingGeneration, replaceTasks, restoreSettings, restoreTasks) {
        preview = backupCoordinator.preview(
            replaceTasks = replaceTasks,
            restoreSettings = restoreSettings,
            restoreTasks = restoreTasks,
        ).getOrNull()
    }

    fun importBackup(pin: String? = null) {
        if (!restoreSettings && !restoreTasks) return
        busy = true
        result = null
        scope.launch {
            val decision = runCatching {
                backupCoordinator.vpnImportConsentDecision(restoreSettings)
            }.getOrDefault(VpnImportConsentDecision.NOT_REQUIRED)
            when (decision) {
                VpnImportConsentDecision.NOT_REQUIRED -> performImport(pin, activateImportedVpn = false)
                VpnImportConsentDecision.PERMISSION_ALREADY_GRANTED ->
                    performImport(pin, activateImportedVpn = true)
                VpnImportConsentDecision.REQUEST_CONSENT -> {
                    pendingConsentPin = pin
                    awaitingVpnConsent = true
                    val intent = runCatching {
                        backupCoordinator.vpnConsentIntentOrNull()
                    }.getOrNull()
                    if (intent == null) {
                        awaitingVpnConsent = false
                        pendingConsentPin = null
                        val permissionGranted = runCatching {
                            VpnService.prepare(context) == null
                        }.getOrDefault(false)
                        performImport(
                            pin,
                            VpnImportPolicy.shouldActivateAfterConsent(decision, permissionGranted),
                        )
                    } else {
                        runCatching {
                            vpnConsentLauncher.launch(intent)
                        }.onFailure {
                            awaitingVpnConsent = false
                            pendingConsentPin = null
                            scope.launch { performImport(pin, activateImportedVpn = false) }
                        }
                    }
                }
            }
        }
    }

    fun cancelImport() {
        if (busy) return
        scope.launch {
            backupCoordinator.cancelPendingImport()
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import backup") },
                navigationIcon = {
                    IconButton(onClick = ::cancelImport) {
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
                onClose = ::cancelImport,
            )
            is BackupParseResult.Success -> ImportReview(
                envelope = state.envelope,
                preview = preview,
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
                    if (requiresDefensePin) {
                        showPinPrompt = true
                        pinError = null
                    } else {
                        importBackup()
                    }
                },
                onCancel = ::cancelImport,
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
                Column(
                    Modifier.fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "${outcome.summary.tasksImported} task${if (outcome.summary.tasksImported == 1) "" else "s"} added. " +
                            "${outcome.summary.tasksSkipped} skipped.",
                    )
                    if (outcome.summary.warnings.isNotEmpty()) {
                        Text(
                            "Warnings:\n${outcome.summary.warnings.joinToString("\n")}",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (outcome.summary.protectionCategories.isNotEmpty()) {
                        Text("Protection settings", style = MaterialTheme.typography.titleMedium)
                        outcome.summary.protectionCategories.forEach { category ->
                            val expanded = category.id in expandedProtectionCategories
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.Outlined.Info,
                                            contentDescription = null,
                                            tint = if (category.active) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.error
                                            },
                                        )
                                        Column(
                                            Modifier.weight(1f).padding(start = 10.dp),
                                        ) {
                                            Text(category.title, style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                if (category.active) "Active" else "Inactive",
                                                color = if (category.active) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.error
                                                },
                                                style = MaterialTheme.typography.labelMedium,
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                expandedProtectionCategories =
                                                    if (expanded) {
                                                        expandedProtectionCategories - category.id
                                                    } else {
                                                        expandedProtectionCategories + category.id
                                                    }
                                            },
                                        ) {
                                            Icon(
                                                if (expanded) Icons.Outlined.ExpandLess
                                                else Icons.Outlined.ExpandMore,
                                                contentDescription = if (expanded) {
                                                    "Collapse ${category.title}"
                                                } else {
                                                    "Expand ${category.title}"
                                                },
                                            )
                                        }
                                    }
                                    if (expanded) {
                                        Text(
                                            category.details,
                                            Modifier.padding(top = 8.dp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = onImported) { Text("Done") } },
        )
        null -> Unit
    }

    if (showPinPrompt) {
        AlertDialog(
            onDismissRequest = {
                if (!busy) {
                    showPinPrompt = false
                    defensePin = ""
                    pinError = null
                }
            },
            icon = { Icon(Icons.Outlined.WarningAmber, contentDescription = null) },
            title = { Text("Confirm protection changes") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "This backup removes one or more protection entries or turns off Focus Mirror. " +
                            "Enter your Defense PIN to continue.",
                    )
                    OutlinedTextField(
                        value = defensePin,
                        onValueChange = {
                            defensePin = it
                            pinError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Defense PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = pinError != null,
                    )
                    pinError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Button(
                    enabled = !busy && defensePin.isNotBlank(),
                    onClick = {
                        val enteredPin = defensePin
                        defensePin = ""
                        importBackup(enteredPin)
                    },
                ) {
                    Text(if (busy) "Checking…" else "Verify & Import")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        showPinPrompt = false
                        defensePin = ""
                        pinError = null
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
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
    preview: RestorePreview?,
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
    val taskCount = preview?.tasksInFile ?: envelope.tasks.length()
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
                        "Portable settings overwrite matching fields; fields omitted from the backup stay local. Tasks can be merged or replaced.",
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
                preview?.let {
                    SummaryRow(Icons.Outlined.TaskAlt, "New tasks", it.newTasks.toString())
                    SummaryRow(Icons.Outlined.TaskAlt, "Matching IDs", it.identicalDuplicates.toString())
                    SummaryRow(Icons.Outlined.WarningAmber, "Invalid tasks", it.invalidTasks.toString())
                    SummaryRow(Icons.Outlined.WarningAmber, "Past tasks to skip", it.pastScheduledToSkipped.toString())
                    if (it.externalResourcesUnresolved > 0) {
                        SummaryRow(
                            Icons.Outlined.WarningAmber,
                            "Local-only resources not restored",
                            it.externalResourcesUnresolved.toString(),
                        )
                    }
                }
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
                    Text(if (replaceTasks) "Replace tasks" else "Merge tasks", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (replaceTasks) "All current task rows will be replaced by the backup tasks. Portable settings are still applied separately."
                        else "Existing task IDs are kept. New tasks and portable settings are added without deleting current data.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = replaceTasks, onCheckedChange = onReplaceTasksChange, enabled = restoreTasks)
            }
        }
        if (!replaceTasks && preview?.hasConflicts == true) {
            Card {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "${preview.totalConflicts} tasks already exist with different content. Use Replace to overwrite.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    preview.conflictingTasks.forEach { conflict ->
                        Text(
                            "${conflict.importedTitle} — ${conflict.reason} (ID ${conflict.id})",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (replaceTasks) {
            Card {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(
                        if (currentFocusActive) "Replace is unavailable while a focus session is active."
                        else "Replace tasks is destructive. Existing task rows will be deleted.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        Button(
            onClick = onImport,
            enabled = !busy &&
                (restoreSettings || restoreTasks) &&
                !(replaceTasks && currentFocusActive) &&
                !(restoreTasks && !replaceTasks && preview?.hasConflicts == true),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
            Text(if (replaceTasks) "Replace tasks" else "Merge & Import")
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