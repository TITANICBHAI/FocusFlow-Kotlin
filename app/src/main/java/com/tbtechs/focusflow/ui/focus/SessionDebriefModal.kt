package com.tbtechs.focusflow.ui.focus

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.data.local.dao.RecentSessionSummaryRow
import java.time.Duration
import java.time.Instant

/** Shown after a completed focus session; caller controls visibility and retrieval. */
@Composable
fun SessionDebriefModal(
    session: RecentSessionSummaryRow?,
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    if (!visible || session == null) return

    val actualMinutes = runCatching {
        Duration.between(
            Instant.parse(session.startedAt),
            Instant.parse(session.endedAt),
        ).toMinutes().coerceAtLeast(0)
    }.getOrDefault(0)
    val plannedMinutes = session.plannedMinutes
    val delta = (plannedMinutes ?: actualMinutes.toInt()) - actualMinutes
    val timing = when {
        plannedMinutes == null -> ""
        delta >= 2 -> " You finished $delta minutes ahead of schedule."
        delta <= -2 -> " You ran ${-delta} minutes over the plan."
        else -> ""
    }
    val clean = session.overrideCount == 0
    val title = if (clean) "Clean session" else "Tough session"
    val insight = if (clean) {
        "Zero blocked-app attempts.$timing"
    } else {
        "${session.overrideCount} blocked-app attempt${
            if (session.overrideCount == 1) "" else "s"
        } during this session.$timing"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (clean) Icons.Outlined.Security else Icons.Outlined.Warning,
                contentDescription = null,
                tint = if (clean) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.tertiary
                },
            )
        },
        title = {
            Row {
                Text(title, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "Dismiss session debrief")
                }
            }
        },
        text = {
            Column {
                Text("SESSION DEBRIEF", style = MaterialTheme.typography.labelLarge)
                Text(
                    session.taskTitle ?: "Focus session",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    insight,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Done") }
        },
    )
}