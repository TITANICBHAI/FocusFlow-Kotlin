package com.tbtechs.focusflow.ui.stats

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.tbtechs.focusflow.analytics.AnalyticsSnapshot

@Composable
fun PresenceStrip(snapshot: AnalyticsSnapshot, weekStartDay: Int = 0) = Card {
    androidx.compose.foundation.layout.Column {
        Text("PRESENCE", style = MaterialTheme.typography.labelLarge)
        Row(modifier = Modifier.fillMaxWidth()) {
            repeat(7) { index ->
                val day = (weekStartDay + index) % 7
                val attended = (snapshot.tasks.byDayOfWeek[day]?.total ?: 0) > 0
                Text(
                    text = if (attended) "✓ ${dayName(day)}" else dayName(day),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = if (attended) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

private fun dayName(day: Int) = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")[day]
