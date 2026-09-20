package com.tbtechs.focusflow.ui.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.analytics.InsightCard

@Composable
fun InsightCardView(insight: InsightCard) {
    val accent = when (insight.sentiment) {
        "positive" -> MaterialTheme.colorScheme.secondary
        "warning" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    StatsCard {
        Column(modifier = androidx.compose.ui.Modifier.padding(12.dp)) {
            Row { Text(if (insight.category == "nothing_to_report") "OBSERVATION" else insight.category.uppercase(), color = accent, style = MaterialTheme.typography.labelLarge) }
            Text(insight.headline, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            Text(insight.body, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
