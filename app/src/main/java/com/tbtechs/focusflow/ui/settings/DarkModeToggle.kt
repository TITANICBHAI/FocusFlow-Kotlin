package com.tbtechs.focusflow.ui.settings

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Theme control from the settings Appearance section.
 *
 * Theme ownership deliberately stays with the activity-level theme host. The
 * settings ViewModel has no theme field, so callers supply the current value
 * and persistence action rather than creating an unrelated ViewModel here.
 */
@Composable
fun DarkModeToggle(
    isDark: Boolean,
    onToggle: () -> Unit,
) {
    Switch(
        checked = isDark,
        onCheckedChange = { onToggle() },
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = Color(0xFF6366F1),
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = Color(0xFF475569),
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}
