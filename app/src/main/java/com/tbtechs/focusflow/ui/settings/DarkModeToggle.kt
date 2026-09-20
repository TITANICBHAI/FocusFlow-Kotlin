package com.tbtechs.focusflow.ui.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val thumbOffset by animateDpAsState(
        targetValue = if (isDark) 18.dp else 0.dp,
        label = "darkModeThumbOffset",
    )
    val activeIcon = if (isDark) Icons.Outlined.DarkMode else Icons.Outlined.WbSunny
    val inactiveIcon = if (isDark) Icons.Outlined.WbSunny else Icons.Outlined.DarkMode

    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (isDark) Color(0xFF6366F1) else Color(0xFFCBD5E1))
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.CenterStart,
    ) {
        Icon(
            imageVector = inactiveIcon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.58f),
            modifier = Modifier
                .align(if (isDark) Alignment.CenterStart else Alignment.CenterEnd)
                .padding(horizontal = 6.dp)
                .size(11.dp),
        )
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .padding(3.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = activeIcon,
                contentDescription = if (isDark) "Dark mode enabled" else "Light mode enabled",
                tint = if (isDark) Color(0xFF6366F1) else Color(0xFF64748B),
                modifier = Modifier.size(12.dp),
            )
        }
    }
}
