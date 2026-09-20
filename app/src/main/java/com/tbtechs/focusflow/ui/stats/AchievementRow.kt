package com.tbtechs.focusflow.ui.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.analytics.AchievementState

@Composable
fun AchievementRow(state: AchievementState) {
    if (state.definitions.isEmpty()) return
    StatsCard {
        Column(modifier = androidx.compose.ui.Modifier.padding(12.dp)) {
            Row {
                Text("ACHIEVEMENTS", style = MaterialTheme.typography.labelLarge)
                if (state.newlyEarnedIds.isNotEmpty()) Text("NEW", color = MaterialTheme.colorScheme.secondary)
            }
            state.definitions.forEach { achievement ->
                Text(achievement.title, style = MaterialTheme.typography.titleSmall)
                Text(achievement.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
