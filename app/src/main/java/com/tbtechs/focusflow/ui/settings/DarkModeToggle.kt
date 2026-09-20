package com.tbtechs.focusflow.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

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
    val trackColor = if (isDark) Color(0xFF37308F) else Color(0xFF8CC7F4)

    Box(
        modifier = Modifier
            .size(width = 76.dp, height = 40.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(trackColor)
            .clickable(onClick = onToggle)
            .semantics {
                contentDescription = if (isDark) "Switch to light mode" else "Switch to dark mode"
                role = Role.Switch
            },
        contentAlignment = if (isDark) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        if (isDark) {
            Icon(
                imageVector = Icons.Outlined.Star,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.62f),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(10.dp)
                    .offset(x = 12.dp, y = (-5).dp),
            )
            Icon(
                imageVector = Icons.Outlined.Star,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.48f),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(7.dp)
                    .offset(x = 18.dp, y = 7.dp),
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Cloud,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.38f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(25.dp)
                    .offset(x = (-7).dp),
            )
        }

        Box(
            modifier = Modifier
                .align(if (isDark) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(horizontal = 4.dp)
                .size(32.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isDark) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                contentDescription = null,
                tint = if (isDark) Color(0xFF6158E8) else Color(0xFFF2B90B),
                modifier = Modifier.size(21.dp),
            )
        }
    }
}
