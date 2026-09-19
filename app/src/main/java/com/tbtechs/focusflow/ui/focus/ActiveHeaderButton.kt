package com.tbtechs.focusflow.ui.focus

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.data.model.AppSettings
import com.tbtechs.focusflow.data.model.FocusSession
import com.tbtechs.focusflow.data.repository.NetworkBlockStatus
import com.tbtechs.focusflow.data.repository.UsageStatsRepository
import com.tbtechs.focusflow.data.repository.VpnRepository
import kotlinx.coroutines.delay
import org.json.JSONArray

enum class ActiveStatusLevel {
    INACTIVE,
    ACTIVE,
    WARNING,
}

data class ActiveStatusSummary(
    val level: ActiveStatusLevel,
    val description: String,
    val activeLayersCount: Int,
)

/**
 * Evaluates the live protection state across all 7 layers:
 * 1. Focus Session
 * 2. Standalone Block
 * 3. Always-On Apps
 * 4. Daily Allowance
 * 5. Keyword Blocker
 * 6. VPN Blocking
 * 7. Scheduled Blocks
 */
fun evaluateActiveProtection(
    settings: AppSettings,
    focusSession: FocusSession?,
    vpnStatus: NetworkBlockStatus?,
    hasAccessibility: Boolean = true,
    hasUsageStats: Boolean = true,
): ActiveStatusSummary {
    val now = System.currentTimeMillis()
    val isFocusActive = focusSession?.isActive == true
    val isStandaloneActive = settings.standaloneBlockActive &&
        settings.standaloneBlockPackages.isNotEmpty() &&
        settings.standaloneBlockUntilMs > now
    val isAlwaysOnActive = settings.alwaysBlockEnabled && settings.alwaysBlockPackages.isNotEmpty()

    val allowancePackages = runCatching {
        val arr = JSONArray(settings.dailyAllowanceConfigJson ?: "[]")
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("package")?.takeIf(String::isNotBlank) }
    }.getOrDefault(emptyList())
    val hasDailyAllowance = allowancePackages.isNotEmpty()
    val hasKeywords = settings.blockedWords.isNotEmpty()
    val isVpnRunning = vpnStatus?.running == true
    val activeSchedules = settings.recurringBlockSchedules.filter { it.isActiveNow() }
    val hasActiveSchedule = activeSchedules.isNotEmpty()

    val vpnProblem = vpnStatus != null && (
        vpnStatus.failedPackages.isNotEmpty() ||
            vpnStatus.state in setOf(
                "permission_missing",
                "another_vpn_active",
                "package_registration_failed",
                "startup_failed",
            )
        )

    val activeCount = listOf(
        isFocusActive,
        isStandaloneActive,
        isAlwaysOnActive,
        hasDailyAllowance,
        hasKeywords,
        isVpnRunning,
        hasActiveSchedule,
    ).count { it }

    return when {
        vpnProblem -> ActiveStatusSummary(
            level = ActiveStatusLevel.WARNING,
            description = "Protection warning: VPN block requires attention. Tap to view Active status.",
            activeLayersCount = activeCount,
        )
        activeCount > 0 -> ActiveStatusSummary(
            level = ActiveStatusLevel.ACTIVE,
            description = "Protections active ($activeCount layers running). Tap to view Active status.",
            activeLayersCount = activeCount,
        )
        else -> ActiveStatusSummary(
            level = ActiveStatusLevel.INACTIVE,
            description = "No protection active. Tap to view Active status.",
            activeLayersCount = 0,
        )
    }
}

private fun com.tbtechs.focusflow.data.model.RecurringBlockSchedule.isActiveNow(): Boolean {
    if (!enabled) return false
    val now = java.time.ZonedDateTime.now()
    val minute = now.hour * 60 + now.minute
    val start = startHour * 60
    val end = endHour * 60
    val day = now.dayOfWeek.value % 7
    return day in daysOfWeek && if (start <= end) {
        minute in start until end
    } else {
        minute >= start || minute < end
    }
}

/**
 * Shared persistent Active status indicator appearing across all main tabs.
 */
@Composable
fun ActiveStatusIndicator(
    focusSession: FocusSession? = null,
    settings: AppSettings? = null,
    vpnStatus: NetworkBlockStatus? = null,
    onOpenActiveBlocks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // The host screen owns the configured ViewModels and passes their current
    // values here. Do not call the default Compose viewModel() factory: the
    // native ViewModels require constructor dependencies and have no
    // no-argument constructor.
    val resolvedSettings = settings ?: AppSettings()
    val resolvedSession = focusSession

    val vpnRepo = remember { VpnRepository(context) }
    val usageRepo = remember { UsageStatsRepository(context) }

    val resolvedVpnStatus by produceState(initialValue = vpnStatus) {
        if (vpnStatus != null) {
            value = vpnStatus
            return@produceState
        }
        while (true) {
            value = runCatching { vpnRepo.getNetworkBlockStatus() }.getOrNull()
            delay(5_000)
        }
    }

    val permissions by produceState(initialValue = true to true) {
        while (true) {
            val acc = runCatching { usageRepo.hasAccessibilityPermission() }.getOrDefault(true)
            val use = runCatching { usageRepo.hasPermission() }.getOrDefault(true)
            value = acc to use
            delay(10_000)
        }
    }

    val summary = remember(resolvedSettings, resolvedSession, resolvedVpnStatus, permissions) {
        evaluateActiveProtection(
            settings = resolvedSettings,
            focusSession = resolvedSession,
            vpnStatus = resolvedVpnStatus,
            hasAccessibility = permissions.first,
            hasUsageStats = permissions.second,
        )
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                onClick = onOpenActiveBlocks,
                onClickLabel = "Open active protections",
            )
            .semantics {
                contentDescription = summary.description
                role = Role.Button
            }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        EcgHeartPulseMonitor(
            level = summary.level,
            activeCount = summary.activeLayersCount,
        )
    }
}

/**
 * Realistic ECG heart pulse monitor that renders a medical electrocardiogram
 * waveform with a sweeping glowing pulse line and cardiac beat, replacing any static dot.
 */
@Composable
private fun EcgHeartPulseMonitor(
    level: ActiveStatusLevel,
    activeCount: Int,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ecgSweep")

    // ECG sweep cycle duration depends on status:
    // Active = brisk cardiac rhythm (1100ms)
    // Warning = rapid alert rhythm (850ms)
    // Inactive = slow calm resting rhythm (1600ms)
    val sweepDuration = when (level) {
        ActiveStatusLevel.ACTIVE -> 1100
        ActiveStatusLevel.WARNING -> 850
        ActiveStatusLevel.INACTIVE -> 1600
    }

    val sweepProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = sweepDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sweepProgress",
    )

    // Pulse peak bounce for cardiac contraction
    val pulseBounce by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = sweepDuration / 2, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseBounce",
    )

    val (traceColor, glowColor, screenBg, borderColor) = when (level) {
        ActiveStatusLevel.ACTIVE -> Quadruple(
            Color(0xFF10B981), // Emerald green
            Color(0xFF34D399),
            Color(0xFF021B14),
            Color(0xFF059669).copy(alpha = 0.45f),
        )
        ActiveStatusLevel.WARNING -> Quadruple(
            Color(0xFFF59E0B), // Electric amber
            Color(0xFFFBBF24),
            Color(0xFF1F1203),
            Color(0xFFD97706).copy(alpha = 0.5f),
        )
        ActiveStatusLevel.INACTIVE -> Quadruple(
            Color(0xFF64748B), // Slate resting trace
            Color(0xFF94A3B8),
            Color(0xFF0B111A),
            Color(0xFF334155).copy(alpha = 0.4f),
        )
    }

    Box(
        modifier = modifier
            .height(28.dp)
            .width(52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(screenBg)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val midY = height * 0.52f

            // Baseline & ECG Heart Pulse Waveform definition:
            // P-wave, PR-segment, Q-dip, R-tall peak, S-dip, ST-segment, T-wave
            val rHeight = when (level) {
                ActiveStatusLevel.ACTIVE -> height * 0.44f * pulseBounce
                ActiveStatusLevel.WARNING -> height * 0.40f * pulseBounce
                ActiveStatusLevel.INACTIVE -> height * 0.30f
            }
            val pHeight = height * 0.12f
            val qDip = height * 0.10f
            val sDip = height * 0.20f
            val tHeight = height * 0.16f

            // Sample points along the ECG normalized X [0f .. 1f]
            val points = listOf(
                0.00f to midY,
                0.15f to midY,
                0.22f to (midY - pHeight), // P-wave peak
                0.30f to midY,             // PR segment
                0.38f to midY,
                0.42f to (midY + qDip),    // Q-dip
                0.48f to (midY - rHeight), // R-spike (sharp tall peak)
                0.54f to (midY + sDip),    // S-dip
                0.60f to midY,             // ST segment
                0.70f to (midY - tHeight), // T-wave peak
                0.80f to midY,             // Return to isoelectric line
                1.00f to midY,             // Trailing baseline
            )

            // Build complete ECG path
            val ecgPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(points[0].first * width, points[0].second)
                for (i in 1 until points.size) {
                    val prev = points[i - 1]
                    val curr = points[i]
                    // Smooth curve into peaks, sharp linear into Q-R-S
                    if (i in 5..7) {
                        lineTo(curr.first * width, curr.second)
                    } else {
                        val midX = (prev.first + curr.first) * 0.5f * width
                        val midYVal = (prev.second + curr.second) * 0.5f
                        quadraticBezierTo(prev.first * width, prev.second, midX, midYVal)
                        lineTo(curr.first * width, curr.second)
                    }
                }
            }

            // 1. Draw ambient background trace (resting ECG trace line)
            drawPath(
                path = ecgPath,
                color = traceColor.copy(alpha = if (level == ActiveStatusLevel.INACTIVE) 0.35f else 0.25f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 1.5.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round,
                ),
            )

            // 2. Calculate the sweeping pulse coordinate (X, Y)
            val sweepX = sweepProgress * width
            // Interpolate Y from points
            var sweepY = midY
            for (i in 0 until points.size - 1) {
                val p1 = points[i]
                val p2 = points[i + 1]
                val x1 = p1.first * width
                val x2 = p2.first * width
                if (sweepX in x1..x2 && x2 > x1) {
                    val t = (sweepX - x1) / (x2 - x1)
                    sweepY = p1.second + t * (p2.second - p1.second)
                    break
                }
            }

            // 3. Draw active lit portion trailing the sweep beam
            val trailLength = width * 0.45f
            val trailStart = (sweepX - trailLength).coerceAtLeast(0f)

            // Draw glowing pulse dot at the leading head of the ECG wave
            if (level != ActiveStatusLevel.INACTIVE || sweepProgress < 0.95f) {
                // Outer phosphor glow aura
                drawCircle(
                    color = glowColor.copy(alpha = 0.40f),
                    radius = 3.5.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(sweepX, sweepY),
                )
                // Bright intense core
                drawCircle(
                    color = Color.White,
                    radius = 1.5.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(sweepX, sweepY),
                )
            }

            // Draw a subtle vertical CRT scan sweep bar
            drawLine(
                color = glowColor.copy(alpha = 0.20f),
                start = androidx.compose.ui.geometry.Offset(sweepX, 0f),
                end = androidx.compose.ui.geometry.Offset(sweepX, height),
                strokeWidth = 1.dp.toPx(),
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

/** Backward-compatible alias for existing call sites. */
@Composable
fun ActiveHeaderButton(
    focusSession: FocusSession?,
    settings: AppSettings,
    onOpenActiveBlocks: () -> Unit,
) {
    ActiveStatusIndicator(
        focusSession = focusSession,
        settings = settings,
        onOpenActiveBlocks = onOpenActiveBlocks,
    )
}
