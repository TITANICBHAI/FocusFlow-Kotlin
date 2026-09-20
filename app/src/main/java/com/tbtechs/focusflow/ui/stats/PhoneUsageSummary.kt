package com.tbtechs.focusflow.ui.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.tbtechs.focusflow.analytics.AnalyticsSnapshot
import kotlin.math.roundToInt

@Composable
fun PhoneUsageSummary(
    snapshot: AnalyticsSnapshot,
    onQuickBlock: (String?) -> Unit = {},
) = Card {
    Column(modifier = androidx.compose.ui.Modifier.padding(16.dp)) {
        Text("ANDROID USAGESTATS", style = MaterialTheme.typography.labelLarge)
        Text("ON DEVICE", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelSmall)
        val usage = snapshot.phoneUsage
        if (usage == null) Text("Android returned no phone-use history for this period.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        else {
            val minutes = usage.byHour.values.sum().roundToInt()
            Text("${minutes}m average daily phone use", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                buildString {
                    append(if (usage.peakHour == null) "No hourly peak was recorded." else "Heaviest use is around ${hourLabel(usage.peakHour)}.")
                    usage.heaviestApp?.let { append(" ${it.appName} was the most-used app.") }
                },
            )
            if (usage.apps.isNotEmpty()) {
                Text(
                    "Most-used apps",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = androidx.compose.ui.Modifier.padding(top = 12.dp),
                )
                usage.apps.take(8).forEach { app ->
                    Row(
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Column(modifier = androidx.compose.ui.Modifier.weight(1f)) {
                            Text(app.appName)
                            Text(
                                "${app.minutes.roundToInt()} minutes foreground",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { onQuickBlock(app.packageName) }) {
                            Text("Block")
                        }
                    }
                }
            }
        }
    }
}

/** Plain analytics formatter: 14 becomes "2pm". */
fun hourLabel(hour: Int?): String {
    if (hour == null || hour !in 0..23) return "that hour"
    val normalized = hour % 12
    return "${if (normalized == 0) 12 else normalized}${if (hour < 12) "am" else "pm"}"
}
