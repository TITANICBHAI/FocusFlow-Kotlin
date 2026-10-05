package com.tbtechs.focusflow.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * FocusFlow timer icon used by the bottom navigation.
 *
 * The drawable is intentionally kept inside a fixed 22.dp slot so its
 * perceived size matches the other navbar icons. The actual artwork is
 * smaller than the slot, just like the Material icons beside it.
 *
 * selected = false -> outline only, no glow
 * selected = true  -> filled active artwork, no glow
 */
@Composable
internal fun FocusFlowTimerIcon(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    Canvas(modifier = modifier) {
        // Work in a 48 x 48 normalized viewport, then scale uniformly.
        val scale = minOf(size.width, size.height) / 48f
        val offsetX = (size.width - 48f * scale) / 2f
        val offsetY = (size.height - 48f * scale) / 2f

        fun x(v: Float) = offsetX + v * scale
        fun y(v: Float) = offsetY + v * scale

        val gradient = Brush.linearGradient(
            colors = listOf(
                Color(0xFF647AF1),
                Color(0xFF675DEB),
                Color(0xFF8555E8),
            ),
            start = androidx.compose.ui.geometry.Offset(x(13f), y(6f)),
            end = androidx.compose.ui.geometry.Offset(x(39f), y(42f)),
        )

        // Neutral inactive treatment. No blur/filter/glow is used.
        val inactive = Color(0xFF7F899A)

        // The approved artwork occupies roughly 19dp of the 22dp slot.
        val cx = 24f
        val cy = 25f
        val outerR = 17.0f
        val ringW = 4.15f

        val outerBrush = if (selected) gradient else Brush.linearGradient(
            colors = listOf(inactive, inactive),
            start = androidx.compose.ui.geometry.Offset(x(24f), y(8f)),
            end = androidx.compose.ui.geometry.Offset(x(24f), y(40f)),
        )

        if (!selected) {
            // Unselected: clean outline, no fill, no glow.
            drawArc(
                brush = outerBrush,
                startAngle = -81.5f,
                sweepAngle = 326.0f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(x(cx - outerR), y(cy - outerR)),
                size = Size(2f * outerR * scale, 2f * outerR * scale),
                style = Stroke(
                    width = ringW * scale,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )

            // Vertical top segment.
            drawLine(
                brush = outerBrush,
                start = androidx.compose.ui.geometry.Offset(x(cx), y(7.6f)),
                end = androidx.compose.ui.geometry.Offset(x(cx), y(13.0f)),
                strokeWidth = ringW * scale,
                cap = StrokeCap.Round,
            )

            // Pointer/hand, matching the approved silhouette.
            val pointer = Path().apply {
                moveTo(x(15.0f), y(14.8f))
                cubicTo(x(16.1f), y(15.7f), x(17.6f), y(17.0f), x(19.0f), y(18.3f))
                lineTo(x(23.9f), y(22.3f))
                cubicTo(x(25.2f), y(23.4f), x(25.3f), y(24.7f), x(24.4f), y(25.5f))
                cubicTo(x(23.5f), y(26.4f), x(22.2f), y(26.3f), x(21.1f), y(25.3f))
                lineTo(x(17.4f), y(21.3f))
                lineTo(x(14.6f), y(17.0f))
                cubicTo(x(14.0f), y(16.0f), x(14.3f), y(15.0f), x(15.0f), y(14.8f))
                close()
            }
            drawPath(pointer, brush = outerBrush)

            // Center pin.
            drawCircle(
                brush = outerBrush,
                radius = 2.4f * scale,
                center = androidx.compose.ui.geometry.Offset(x(24.0f), y(25.0f)),
            )
        } else {
            // Selected: filled inner disc while retaining the open/power-symbol silhouette.
            drawArc(
                brush = gradient,
                startAngle = -81.5f,
                sweepAngle = 326.0f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(x(cx - outerR), y(cy - outerR)),
                size = Size(2f * outerR * scale, 2f * outerR * scale),
                style = Stroke(
                    width = ringW * scale,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )

            // Fill the interior only; keep the upper opening and top stem dark/transparent.
            drawCircle(
                brush = gradient,
                radius = 14.75f * scale,
                center = androidx.compose.ui.geometry.Offset(x(cx), y(25.0f)),
            )

            drawLine(
                brush = gradient,
                start = androidx.compose.ui.geometry.Offset(x(cx), y(7.6f)),
                end = androidx.compose.ui.geometry.Offset(x(cx), y(13.0f)),
                strokeWidth = ringW * scale,
                cap = StrokeCap.Round,
            )

            // Dark cut-out for the clock hand, matching the navbar screenshot.
            val dark = Color(0xFF202938)
            drawLine(
                color = dark,
                start = androidx.compose.ui.geometry.Offset(x(24.0f), y(24.8f)),
                end = androidx.compose.ui.geometry.Offset(x(15.0f), y(14.8f)),
                strokeWidth = 3.45f * scale,
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = dark,
                radius = 3.1f * scale,
                center = androidx.compose.ui.geometry.Offset(x(24.0f), y(24.8f)),
            )
        }
    }
}

