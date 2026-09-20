package com.tbtechs.focusflow.ui.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.ui.theme.BrandPrimary

/**
 * Branded first screen shown while the app boot sequence opens the database.
 *
 * The platform splash only provides the solid-color handoff. This Compose
 * overlay owns the actual sequence: shield first, then the product name and
 * tagline. It also keeps the final frame visible until the real boot state is
 * ready, so fast and slow launches both get the same intentional transition.
 */
@Composable
fun FocusFlowSplashOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    var logoVisible by rememberSaveable { mutableStateOf(false) }
    var textVisible by rememberSaveable { mutableStateOf(false) }
    var sequenceComplete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!logoVisible) {
            logoVisible = true
            kotlinx.coroutines.delay(LOGO_DURATION_MS.toLong())
        }
        if (!textVisible) {
            textVisible = true
            kotlinx.coroutines.delay(TEXT_DURATION_MS.toLong())
        }
        if (!sequenceComplete) {
            kotlinx.coroutines.delay(HOLD_AFTER_TEXT_MS)
            sequenceComplete = true
        }
    }

    AnimatedVisibility(
        visible = visible || !sequenceComplete,
        enter = fadeIn(animationSpec = tween(160)),
        exit = fadeOut(animationSpec = tween(220)),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BrandPrimary),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedVisibility(
                    visible = logoVisible,
                    enter = fadeIn(tween(LOGO_DURATION_MS)) + scaleIn(
                        initialScale = 0.72f,
                        animationSpec = tween(LOGO_DURATION_MS),
                    ),
                ) {
                    FocusFlowSplashLogo()
                }

                Spacer(modifier = Modifier.height(24.dp))

                AnimatedVisibility(
                    visible = textVisible,
                    enter = fadeIn(tween(TEXT_DURATION_MS)) + slideInVertically(
                        initialOffsetY = { 22 },
                        animationSpec = tween(TEXT_DURATION_MS),
                    ),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "FocusFlow",
                            color = Color.White,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Your discipline operating system",
                            color = Color.White.copy(alpha = 0.68f),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Custom splash mark keeps the shield silhouette and rounded check independent
 * from the unrelated Material icon shapes.
 */
@Composable
private fun FocusFlowSplashLogo(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(132.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize(),
        ) {
            val scale = size.minDimension / 132f

            drawCircle(
                color = Color.White.copy(alpha = 0.14f),
                radius = size.minDimension / 2f,
                center = center,
            )

            val left = (size.width - 100f * scale) / 2f
            val top = (size.height - 100f * scale) / 2f

            fun point(x: Float, y: Float) =
                androidx.compose.ui.geometry.Offset(
                    left + x * scale,
                    top + y * scale,
                )

            val shield = Path().apply {
                moveTo(point(50f, 22f))
                cubicTo(
                    point(42f, 25f),
                    point(31f, 28f),
                    point(23f, 30f),
                )
                lineTo(point(23f, 50f))
                cubicTo(
                    point(23f, 67f),
                    point(34f, 82f),
                    point(50f, 89f),
                )
                cubicTo(
                    point(66f, 82f),
                    point(77f, 67f),
                    point(77f, 50f),
                )
                lineTo(point(77f, 30f))
                cubicTo(
                    point(69f, 28f),
                    point(58f, 25f),
                    point(50f, 22f),
                )
                close()
            }

            drawPath(
                path = shield,
                color = Color.White,
            )

            val check = Path().apply {
                moveTo(point(36f, 50f))
                lineTo(point(46f, 60f))
                lineTo(point(66f, 38f))
            }

            drawPath(
                path = check,
                color = BrandPrimary,
                style = Stroke(
                    width = 5.5f * scale,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
    }
}

private fun Path.moveTo(point: Offset) {
    moveTo(point.x, point.y)
}

private fun Path.lineTo(point: Offset) {
    lineTo(point.x, point.y)
}

private fun Path.cubicTo(first: Offset, second: Offset, third: Offset) {
    cubicTo(first.x, first.y, second.x, second.y, third.x, third.y)
}

private const val LOGO_DURATION_MS = 400
private const val TEXT_DURATION_MS = 350
private const val HOLD_AFTER_TEXT_MS = 500L
