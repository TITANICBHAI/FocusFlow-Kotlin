package com.tbtechs.focusflow.ui.support

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.scaledSp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FocusFlowFileGuideScreen(onBack: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "FocusFlow File Guide",
                        color = DarkTextPrimary,
                        fontSize = 18.scaledSp,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = DarkTextPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = DarkTextPrimary,
                ),
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FocusFlowFormatGuideHeader()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkCard)
                    .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Text(
                    "AI task-backup prompt",
                    color = DarkTextPrimary,
                    fontSize = 15.scaledSp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Copy this prompt into an AI assistant along with the task details " +
                        "you want to turn into a backup.",
                    color = DarkTextSecondary,
                    fontSize = 13.scaledSp,
                    lineHeight = 19.scaledSp,
                )
                Button(
                    onClick = {
                        clipboard.setText(AnnotatedString(FOCUSFLOW_TASK_BACKUP_PROMPT))
                        copied = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                ) {
                    Text(
                        if (copied) "Prompt copied" else "Copy task-backup prompt",
                        color = Color.White,
                        fontSize = 14.scaledSp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            SelectionContainer { FocusFlowFormatGuideBody() }
        }
    }
}

@Composable
private fun FocusFlowFormatGuideHeader() {
    GuideInfoCard(
        title = "Portable task backups",
        body = "A .focusflow file is a versioned UTF-8 JSON backup. FocusFlow validates " +
            "the file before importing it, so keep the required envelope fields and task " +
            "values intact.",
    )
}

@Composable
private fun FocusFlowFormatGuideBody() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GuideInfoCard(
            title = "Backup envelope",
            body = "Use kind \"FocusFlowBackupV1\" and version 1. The envelope includes " +
                "export timestamps, platform, a settings object, a tasks array, preset " +
                "sections, and a summary.",
        )
        GuideInfoCard(
            title = "Task-only imports",
            body = "Set settings to an empty object ({}). The importer writes only setting " +
                "values present in the file, so an empty settings object leaves the current " +
                "app-wide settings unchanged. Tasks are imported separately.",
        )
        GuideInfoCard(
            title = "Task fields",
            body = "Each task needs id, title, startTime, endTime, durationMinutes, status, " +
                "priority, color, focusMode, createdAt, and updatedAt. Timestamps should be " +
                "ISO-8601 UTC strings. Use scheduled, active, completed, skipped, or overdue " +
                "for status, and low, medium, high, or critical for priority.",
        )
        GuideInfoCard(
            title = "Focus app choices",
            body = "focusAllowedPackages has three meanings: null uses the app-wide allowed " +
                "app list, an empty list allows all apps for that task, and a list of Android " +
                "package names limits the task to those apps. Don’t replace null with an " +
                "empty list unless all apps should be allowed.",
        )
        GuideInfoCard(
            title = "Other task data",
            body = "Use an empty tags or reminders list when none are provided. Keep task IDs " +
                "unique. Make durationMinutes agree with the start and end times, and don’t " +
                "invent dates, package names, or task details that weren’t supplied.",
        )
    }
}

@Composable
private fun GuideInfoCard(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            title,
            color = DarkTextPrimary,
            fontSize = 15.scaledSp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            body,
            color = DarkTextSecondary,
            fontSize = 13.scaledSp,
            lineHeight = 19.scaledSp,
        )
    }
}

private const val FOCUSFLOW_TASK_BACKUP_PROMPT = """
Create a task-only FocusFlow backup file with the .focusflow extension.
Return exactly one valid UTF-8 JSON object. Do not include Markdown fences,
comments, explanations, or trailing commas.

Use the version 1 FocusFlow backup envelope:
- kind: "FocusFlowBackupV1"
- version: 1
- platform: {"os":"android"}
- settings: {}
- tasks: an array containing the task records
- presetSections: []
- summary: include taskCount and set blockedWordCount, greyoutWindowCount,
  and dailyAllowanceCount to 0
- exportedAt: the current time in ISO-8601 UTC format
- exportedAtHuman: the same time in a readable format
- appVersion: null

The empty settings object is intentional: do not include app-wide settings,
permissions, blocking lists, or device state. This backup is for tasks only.

Each task must include id, title, startTime, endTime, durationMinutes, status,
priority, color, focusMode, createdAt, and updatedAt. Use unique IDs and
ISO-8601 UTC timestamps. durationMinutes must match the start and end times.
Use status scheduled, active, completed, skipped, or overdue, and priority low,
medium, high, or critical. Include description, tags, and reminders when supplied;
otherwise use null or empty lists as appropriate.

For focusAllowedPackages, use null to inherit FocusFlow's app-wide allowed-app
list. Use an empty list only when all apps should be allowed for that task.
Use a list only when specific Android package names were provided. Do not guess
package names. If required task details such as date, time, or timezone are
missing, ask for them instead of inventing values.
"""
