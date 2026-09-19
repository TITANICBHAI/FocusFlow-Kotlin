package com.tbtechs.focusflow.ui.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Covers the first frame while the app boot sequence opens the database.
 *
 * The activity already uses the same indigo drawable as its native launch
 * theme, so keeping this overlay visually consistent avoids a flash between
 * the platform splash and Compose content.
 */
@Composable
fun FocusFlowSplashOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = Color.White)
    }
}
