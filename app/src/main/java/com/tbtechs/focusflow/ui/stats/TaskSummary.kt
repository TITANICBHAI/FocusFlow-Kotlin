package com.tbtechs.focusflow.ui.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.analytics.AnalyticsSnapshot

@Composable
fun TaskSummary(snapshot: AnalyticsSnapshot) = StatsCard {
    Column(modifier = androidx.compose.ui.Modifier.padding(12.dp)) {
        Text("TASKS", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Row {
            Metric("Done", snapshot.tasks.completed, MaterialTheme.colorScheme.secondary)
            Metric("Skipped", snapshot.tasks.skipped, MaterialTheme.colorScheme.tertiary)
            Metric("Missed", snapshot.tasks.missed, MaterialTheme.colorScheme.error)
        }
        if (snapshot.tasks.total == 0) Text("No tasks were recorded in this window.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Metric(label: String, value: Int, color: androidx.compose.ui.graphics.Color) = Column {
    Text(value.toString(), fontSize = 18.sp, color = color)
    Text(label, fontSize = 11.sp)
}
