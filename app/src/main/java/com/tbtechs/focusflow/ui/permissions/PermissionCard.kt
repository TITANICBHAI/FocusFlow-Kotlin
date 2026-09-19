package com.tbtechs.focusflow.ui.permissions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.StatusMissing
import com.tbtechs.focusflow.ui.theme.StatusMissingBg
import com.tbtechs.focusflow.ui.theme.StatusMissingText
import com.tbtechs.focusflow.ui.theme.StatusNotSetUpBg
import com.tbtechs.focusflow.ui.theme.StatusNotSetUpText
import com.tbtechs.focusflow.ui.theme.StatusOptionalBg
import com.tbtechs.focusflow.ui.theme.StatusOptionalText
import com.tbtechs.focusflow.ui.theme.StatusReady
import com.tbtechs.focusflow.ui.theme.StatusReadyBg
import com.tbtechs.focusflow.ui.theme.StatusReadyText

@Composable
fun PermissionCard(
    permission: PermissionDefinition,
    status: PermissionStatus,
    expanded: Boolean,
    busy: Boolean,
    showTroubleshoot: Boolean = false,
    showOpenWhenGranted: Boolean = false,
    onToggle: () -> Unit,
    onGrant: () -> Unit,
    onTroubleshoot: () -> Unit = {},
) {
    val (icon, iconBg, iconTint) = when (permission.id) {
        PermissionId.NOTIFICATIONS -> Triple(
            Icons.Outlined.Notifications,
            if (status == PermissionStatus.GRANTED) StatusReadyBg else Color(0xFF0F2E28),
            if (status == PermissionStatus.GRANTED) StatusReadyText else Color(0xFF34D399),
        )
        PermissionId.BATTERY -> Triple(
            Icons.Outlined.BatteryChargingFull,
            if (status == PermissionStatus.GRANTED) StatusReadyBg else StatusNotSetUpBg,
            if (status == PermissionStatus.GRANTED) StatusReadyText else StatusNotSetUpText,
        )
        PermissionId.OVERLAY -> Triple(
            Icons.Outlined.Layers,
            if (status == PermissionStatus.GRANTED) StatusReadyBg else StatusNotSetUpBg,
            if (status == PermissionStatus.GRANTED) StatusReadyText else StatusNotSetUpText,
        )
        PermissionId.USAGE -> Triple(
            Icons.Outlined.ShowChart,
            if (status == PermissionStatus.GRANTED) StatusReadyBg else StatusMissingBg,
            if (status == PermissionStatus.GRANTED) StatusReadyText else StatusMissingText,
        )
        PermissionId.ACCESSIBILITY -> Triple(
            Icons.Outlined.Visibility,
            if (status == PermissionStatus.GRANTED) StatusReadyBg else StatusMissingBg,
            if (status == PermissionStatus.GRANTED) StatusReadyText else StatusMissingText,
        )
        PermissionId.MEDIA -> Triple(
            Icons.Outlined.Image,
            if (status == PermissionStatus.GRANTED) StatusReadyBg else StatusNotSetUpBg,
            if (status == PermissionStatus.GRANTED) StatusReadyText else StatusNotSetUpText,
        )
        PermissionId.VPN -> Triple(
            Icons.Outlined.Shield,
            if (status == PermissionStatus.GRANTED) StatusReadyBg else StatusNotSetUpBg,
            if (status == PermissionStatus.GRANTED) StatusReadyText else StatusNotSetUpText,
        )
        PermissionId.DEVICE_ADMIN -> Triple(
            Icons.Outlined.AdminPanelSettings,
            if (status == PermissionStatus.GRANTED) StatusReadyBg else StatusNotSetUpBg,
            if (status == PermissionStatus.GRANTED) StatusReadyText else StatusNotSetUpText,
        )
        PermissionId.EXACT_ALARMS -> Triple(
            Icons.Outlined.Alarm,
            if (status == PermissionStatus.GRANTED) StatusReadyBg else StatusNotSetUpBg,
            if (status == PermissionStatus.GRANTED) StatusReadyText else StatusNotSetUpText,
        )
        PermissionId.LAUNCHER -> Triple(
            Icons.Outlined.Home,
            if (status == PermissionStatus.GRANTED) StatusReadyBg else StatusNotSetUpBg,
            if (status == PermissionStatus.GRANTED) StatusReadyText else StatusNotSetUpText,
        )
        else -> Triple(
            Icons.Outlined.Shield,
            if (status == PermissionStatus.GRANTED) StatusReadyBg else StatusNotSetUpBg,
            if (status == PermissionStatus.GRANTED) StatusReadyText else StatusNotSetUpText,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkCard)
            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Main Top Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                // Colored Icon Badge
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp),
                    )
                }

                // Title, Optional pill, Status pill, and Description
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = permission.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkTextPrimary,
                        )

                        if (permission.optional) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(100.dp))
                                    .background(StatusOptionalBg)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = "Optional",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusOptionalText,
                                )
                            }
                        }

                        // Status Badge
                        val (statusText, statusBg, statusColor) = when (status) {
                            PermissionStatus.GRANTED -> Triple("Ready", StatusReadyBg, StatusReadyText)
                            PermissionStatus.DENIED -> if (permission.optional) {
                                Triple("Not set up", StatusNotSetUpBg, StatusNotSetUpText)
                            } else {
                                Triple("Missing", StatusMissingBg, StatusMissingText)
                            }
                            PermissionStatus.UNKNOWN -> Triple("Unknown", Color(0xFF1E283E), DarkTextSecondary)
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(statusBg)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = statusText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusColor,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = permission.description,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = DarkTextSecondary,
                    )
                }

                // Chevron icon
                Icon(
                    imageVector = if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = DarkTextMuted,
                    modifier = Modifier.size(22.dp),
                )
            }

            // Action Button when not granted and not expanded (matches 1a.jpg / 1b.jpg)
            if (status != PermissionStatus.GRANTED && !expanded) {
                Button(
                    onClick = onGrant,
                    enabled = !busy,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandPrimary,
                        disabledContainerColor = Color(0xFF1E283E),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                ) {
                    if (busy) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = permission.actionLabel,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                    }
                }
            }

            // Expanded Details Section
            if (expanded) {
                HorizontalDivider(color = DarkBorder)

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Why this is needed",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkTextPrimary,
                    )
                    Text(
                        text = permission.whyNeeded,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = DarkTextSecondary,
                    )

                    if (status != PermissionStatus.GRANTED) {
                        Text(
                            text = "Without this permission:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = StatusMissing,
                        )
                        permission.brokenWithout.forEach { item ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = null,
                                    tint = StatusMissing,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = item,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    color = DarkTextSecondary,
                                )
                            }
                        }
                    }

                    if (status != PermissionStatus.GRANTED || showOpenWhenGranted) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onGrant,
                                enabled = !busy,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                                modifier = Modifier.weight(1f).height(44.dp),
                            ) {
                                Text(
                                    text = if (busy) "Opening…" else permission.actionLabel,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            if (showTroubleshoot && status != PermissionStatus.GRANTED) {
                                OutlinedButton(
                                    onClick = onTroubleshoot,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.height(44.dp),
                                ) {
                                    Icon(Icons.Outlined.HelpOutline, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Troubleshoot")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
