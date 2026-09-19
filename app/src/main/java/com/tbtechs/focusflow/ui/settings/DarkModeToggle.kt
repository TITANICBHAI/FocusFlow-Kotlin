package com.tbtechs.focusflow.ui.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

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
        thumbContent = {
            Icon(
                imageVector = if (isDark) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                contentDescription = null,
                modifier = Modifier.size(androidx.compose.material3.SwitchDefaults.IconSize),
            )
        },
        modifier = Modifier.semantics {
            contentDescription = if (isDark) "Switch to light mode" else "Switch to dark mode"
            role = Role.Switch
        },
    )
}
