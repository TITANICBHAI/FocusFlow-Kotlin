// Paste this BELOW the FocusFlowActiveIcons object (same file / same snippet) in an online
// Compose playground, then call ActiveIconsPreview() from the template's root composable.
// Uses only foundation + ui, so it needs no Material dependency.

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Reference colours sampled from the screenshot you sent
private val PreviewBackground = Color(0xFF202938)
private val PreviewActive = Color(0xFF6366F1)

@Composable
private fun PreviewIcon(icon: ImageVector, size: Dp) {
    // Same thing Icon(tint = ...) does internally: tint the vector, keep its holes transparent
    Image(
        painter = rememberVectorPainter(icon),
        contentDescription = null,
        colorFilter = ColorFilter.tint(PreviewActive),
        modifier = Modifier.size(size),
    )
}

@Composable
fun ActiveIconsPreview() {
    Column(
        modifier = Modifier.background(PreviewBackground).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // 1) Real tab-bar size, all five active icons
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
            PreviewIcon(FocusFlowActiveIcons.TimerFilled, 22.dp)
            PreviewIcon(FocusFlowActiveIcons.CalendarFilled, 22.dp)
            PreviewIcon(FocusFlowActiveIcons.ShieldCheckmarkFilled, 22.dp)
            PreviewIcon(FocusFlowActiveIcons.BarChartFilled, 22.dp)
            PreviewIcon(FocusFlowActiveIcons.SettingsFilled, 22.dp)
        }
        // 2) Shield blown up so silhouette, chin, check shape and edges are inspectable
        Box { PreviewIcon(FocusFlowActiveIcons.ShieldCheckmarkFilled, 220.dp) }
    }
}
