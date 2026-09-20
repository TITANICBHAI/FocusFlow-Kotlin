package com.tbtechs.focusflow.ui.launcher

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.data.model.AppSettings
import com.tbtechs.focusflow.data.repository.SettingsRepository
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkSurfaceVariant
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickBlockSheet(
    visible: Boolean,
    packageName: String,
    appName: String,
    settings: AppSettings,
    settingsRepository: SettingsRepository,
    onClose: () -> Unit,
    onOpenActive: () -> Unit,
    onOpenAlwaysOn: () -> Unit,
) {
    if (!visible) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var customExpiry by remember { mutableStateOf<Long?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var protectedWarning by remember { mutableStateOf(false) }

    fun applyTemporary(durationMs: Long) {
        if (isProtectedSystemApp(context, packageName)) {
            protectedWarning = true
            return
        }
        scope.launch {
            loading = true
            error = null
            try {
                val until = System.currentTimeMillis() + durationMs.coerceAtLeast(1L)
                settingsRepository.setStandaloneBlock(
                    active = true,
                    packages = (settings.standaloneBlockPackages + packageName).distinct(),
                    untilMs = until,
                )
                onClose()
            } catch (exception: Exception) {
                error = exception.message ?: "Could not start the temporary block."
            } finally {
                loading = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = DarkBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Quick Block",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkTextPrimary,
                    )
                    Text(
                        appName,
                        fontSize = 11.sp,
                        color = DarkTextSecondary,
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close", tint = DarkTextMuted)
                }
            }

            Button(
                onClick = { applyTemporary(60 * 60 * 1000L) },
                enabled = !loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(Icons.Outlined.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Block for one hour", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
                onClick = {
                    val now = LocalDateTime.now()
                    val tonight = LocalDateTime.of(now.toLocalDate(), LocalTime.of(23, 59))
                    applyTemporary(Duration.between(now, tonight).toMillis())
                },
                enabled = !loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = DarkCard,
                    contentColor = DarkTextPrimary,
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(DarkBorder),
                ),
            ) {
                Text("Until tonight (11:59 PM)", fontSize = 13.sp)
            }

            OutlinedButton(
                onClick = {
                    val tomorrow = LocalDate.now().plusDays(1)
                    val wakeUp = LocalDateTime.of(tomorrow, LocalTime.of(7, 0))
                    applyTemporary(Duration.between(LocalDateTime.now(), wakeUp).toMillis())
                },
                enabled = !loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = DarkCard,
                    contentColor = DarkTextPrimary,
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(DarkBorder),
                ),
            ) {
                Text("Until tomorrow morning (7:00 AM)", fontSize = 13.sp)
            }

            OutlinedButton(
                onClick = {
                    showCustomDateTimePicker(context) { until ->
                        if (until <= System.currentTimeMillis()) {
                            error = "Choose a time in the future."
                        } else {
                            customExpiry = until
                            applyTemporary(until - System.currentTimeMillis())
                        }
                    }
                },
                enabled = !loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = DarkCard,
                    contentColor = DarkTextPrimary,
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(DarkBorder),
                ),
            ) {
                Text(
                    if (customExpiry == null) "Choose custom expiry..." else "Custom expiry selected",
                    fontSize = 13.sp,
                )
            }

            // Always-On Option
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkCard)
                    .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                    .padding(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = BrandPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Always-On",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DarkTextPrimary,
                        )
                        Text(
                            "Keep this app blocked indefinitely",
                            fontSize = 11.sp,
                            color = DarkTextSecondary,
                        )
                    }
                    TextButton(
                        onClick = {
                            if (isProtectedSystemApp(context, packageName)) {
                                protectedWarning = true
                            } else {
                                scope.launch {
                                    settingsRepository.setAlwaysBlockActive(
                                        active = true,
                                        packages = (settings.alwaysBlockPackages + packageName).distinct(),
                                    )
                                    onClose()
                                    onOpenAlwaysOn()
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                    ) {
                        Text("Enable", color = BrandPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (settings.standaloneBlockActive) {
                Text(
                    "A standalone block is already active.",
                    color = Color(0xFFFBBF24),
                    fontSize = 13.sp,
                )
                TextButton(
                    onClick = onOpenActive,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                ) {
                    Text("Open Active Dashboard", color = BrandPrimary)
                }
            }

            error?.let {
                Text(it, color = Color(0xFFF87171), fontSize = 13.sp)
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    if (protectedWarning) {
        AlertDialog(
            onDismissRequest = { protectedWarning = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("Protected system app", fontWeight = FontWeight.Bold) },
            text = { Text("This system app cannot be blocked because doing so could make the device unusable.") },
            confirmButton = {
                Button(
                    onClick = { protectedWarning = false },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                ) {
                    Text("OK")
                }
            },
        )
    }
}

private fun isProtectedSystemApp(context: Context, packageName: String): Boolean =
    runCatching {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        info.flags and ApplicationInfo.FLAG_SYSTEM != 0
    }.getOrDefault(false)

private fun showCustomDateTimePicker(context: Context, onSelected: (Long) -> Unit) {
    val now = LocalDateTime.now()
    DatePickerDialog(
        context,
        { _, year, month, day ->
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    val selected = LocalDateTime.of(
                        LocalDate.of(year, month + 1, day),
                        LocalTime.of(hour, minute),
                    )
                    onSelected(selected.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                },
                now.hour,
                now.minute,
                false,
            ).show()
        },
        now.year,
        now.monthValue - 1,
        now.dayOfMonth,
    ).show()
}
