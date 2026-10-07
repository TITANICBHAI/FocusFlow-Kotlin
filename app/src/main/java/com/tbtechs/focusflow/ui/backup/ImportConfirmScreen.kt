package com.tbtechs.focusflow.ui.backup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.data.backup.BackupEnvelope
import com.tbtechs.focusflow.data.backup.BackupSummary
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.scaledSp

@Composable
fun ImportConfirmScreen(
    importState: ImportState,
    pendingEnvelope: BackupEnvelope?,
    onConfirm: (replaceTasks: Boolean) -> Unit,
    onCancelImport: () -> Unit,
    onResetImport: () -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val isRestoring = importState == ImportState.Restoring
    val canCancel = !isRestoring
    var replaceTasks by remember(pendingEnvelope) { mutableStateOf(false) }
    val shouldShowError = importState is ImportState.Error
    val shouldShowSuccess = importState is ImportState.Success

    fun cancelAndReturn() {
        if (canCancel) {
            onCancelImport()
            onCancel()
        }
    }

    BackHandler(
        enabled = canCancel && !shouldShowError && !shouldShowSuccess,
        onBack = ::cancelAndReturn,
    )

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Restore Backup",
                        color = DarkTextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = ::cancelAndReturn,
                        enabled = canCancel,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Cancel import and return to Settings",
                            tint = if (canCancel) DarkTextPrimary else DarkTextMuted,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            pendingEnvelope?.let { envelope ->
                BackupSummaryCard(envelope = envelope)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder.copy(alpha = 0.55f)),
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = replaceTasks,
                                    enabled = importState is ImportState.PendingConfirm,
                                    role = Role.Switch,
                                    onValueChange = { replaceTasks = it },
                                )
                                .semantics {
                                    stateDescription = if (replaceTasks) "On" else "Off"
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Replace all existing tasks",
                                modifier = Modifier.weight(1f),
                                color = DarkTextPrimary,
                                fontSize = 15.scaledSp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.size(12.dp))
                            Switch(
                                checked = replaceTasks,
                                onCheckedChange = null,
                                enabled = importState is ImportState.PendingConfirm,
                            )
                        }
                        if (replaceTasks) {
                            HorizontalDivider(color = DarkBorder)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics { liveRegion = LiveRegionMode.Polite }
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Warning,
                                    contentDescription = null,
                                    tint = BrandPrimary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = "This will permanently delete all your current tasks.",
                                    color = DarkTextSecondary,
                                    fontSize = 14.scaledSp,
                                    lineHeight = 19.scaledSp,
                                )
                            }
                        }
                    }
                }
            } ?: run {
                Text(
                    text = when (importState) {
                        ImportState.Reading -> "Reading backup…"
                        ImportState.Restoring -> "Restoring backup…"
                        else -> "No backup is ready to restore."
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .padding(vertical = 24.dp),
                    color = DarkTextSecondary,
                    fontSize = 16.scaledSp,
                )
            }

            if (importState == ImportState.Reading || isRestoring) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(22.dp)
                            .semantics { stateDescription = "In progress" },
                        color = BrandPrimary,
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = if (isRestoring) "Restoring backup…" else "Reading backup…",
                        color = DarkTextSecondary,
                        fontSize = 14.scaledSp,
                    )
                }
            }

            if (importState !is ImportState.Success && importState !is ImportState.Error) {
                Button(
                    onClick = { onConfirm(replaceTasks) },
                    enabled = importState is ImportState.PendingConfirm && pendingEnvelope != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    if (isRestoring) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(19.dp)
                                .semantics { stateDescription = "In progress" },
                            color = DarkTextPrimary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.size(10.dp))
                    }
                    Text(
                        text = if (isRestoring) "Restoring…" else "Restore",
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (canCancel) {
                    TextButton(
                        onClick = ::cancelAndReturn,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    ) {
                        Text("Cancel import", color = DarkTextSecondary)
                    }
                }
            }
        }
    }

    (importState as? ImportState.Error)?.let { error ->
        AlertDialog(
            onDismissRequest = onResetImport,
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("Could not restore backup", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = error.message,
                    modifier = Modifier
                        .heightIn(max = 180.dp)
                        .verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = onResetImport) {
                    Text("OK", color = BrandPrimary)
                }
            },
        )
    }

    (importState as? ImportState.Success)?.let { success ->
        AlertDialog(
            onDismissRequest = {
                onResetImport()
                onDone()
            },
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("Backup restored", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${success.tasksImported} tasks imported.")
                    Text("${success.tasksSkipped} tasks skipped.")
                    if (success.warnings.isNotEmpty()) {
                        Text(
                            text = if (success.warnings.size == 1) {
                                "1 note"
                            } else {
                                "${success.warnings.size} notes"
                            },
                            color = DarkTextSecondary,
                            fontSize = 13.scaledSp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            itemsIndexed(
                                items = success.warnings,
                                key = { index, _ -> index },
                            ) { _, warning ->
                                Text(warning, color = DarkTextSecondary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onResetImport()
                        onDone()
                    },
                ) {
                    Text("Done", color = BrandPrimary)
                }
            },
        )
    }
}

@Composable
private fun BackupSummaryCard(envelope: BackupEnvelope) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder.copy(alpha = 0.55f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Backup contents",
                color = DarkTextPrimary,
                fontSize = 17.scaledSp,
                fontWeight = FontWeight.Bold,
            )
            HorizontalDivider(color = DarkBorder)
            BackupSummaryRow("Exported", envelope.exportedAtHuman.ifBlank { "Unknown" })
            BackupSummaryRow("App version", envelope.appVersion?.takeIf(String::isNotBlank) ?: "Unknown")
            HorizontalDivider(color = DarkBorder)
            BackupSummaryRow("Tasks", envelope.summary.taskCount.toString())
            BackupSummaryRow("Blocked words", envelope.summary.blockedWordCount.toString())
            BackupSummaryRow("Schedule windows", envelope.summary.greyoutWindowCount.toString())
            BackupSummaryRow("Daily allowances", envelope.summary.dailyAllowanceCount.toString())
        }
    }
}

@Composable
private fun BackupSummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = DarkTextSecondary, fontSize = 14.scaledSp)
        Text(
            text = value,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            color = DarkTextPrimary,
            fontSize = 14.scaledSp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
