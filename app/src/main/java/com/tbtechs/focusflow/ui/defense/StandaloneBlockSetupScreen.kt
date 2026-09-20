package com.tbtechs.focusflow.ui.defense

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tbtechs.focusflow.data.model.DailyAllowanceEntry
import com.tbtechs.focusflow.data.model.StandaloneBlockAndAllowanceConfig
import com.tbtechs.focusflow.domain.FocusPinManager
import com.tbtechs.focusflow.ui.SettingsViewModel
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkSurfaceVariant
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.settings.dailyAllowanceEntriesFromJson
import kotlinx.coroutines.delay
import java.text.DateFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandaloneBlockSetupScreen(
    settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
    initialPackage: String? = null,
    onBack: () -> Unit = {},
) {
    val settings by settingsViewModel.settings.collectAsState()
    val context = LocalContext.current
    val focusPinManager = remember(context) { FocusPinManager(context) }
    var modalVisible by remember { mutableStateOf(false) }
    val initialBlockedPackages = remember(settings.standaloneBlockPackages, initialPackage) {
        if (initialPackage.isNullOrBlank() || initialPackage in settings.standaloneBlockPackages) {
            settings.standaloneBlockPackages
        } else {
            settings.standaloneBlockPackages + initialPackage
        }
    }
    LaunchedEffect(initialPackage) {
        if (!initialPackage.isNullOrBlank()) {
            modalVisible = true
        }
    }
    val active = settings.standaloneBlockActive &&
        settings.standaloneBlockUntilMs > System.currentTimeMillis() &&
        settings.standaloneBlockPackages.isNotEmpty()
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(active, settings.standaloneBlockUntilMs) {
        while (active) {
            nowMs = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val remainingMs = (settings.standaloneBlockUntilMs - nowMs).coerceAtLeast(0L)
    val remainingMinutes = (remainingMs / 60_000L).coerceAtLeast(0L)

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Standalone Block",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkTextPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = DarkTextPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Hero Status Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkCard)
                    .border(
                        1.dp,
                        if (active) Color(0xFFEF4444).copy(alpha = 0.45f) else DarkBorder,
                        RoundedCornerShape(16.dp),
                    )
                    .padding(20.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (active) Color(0xFFEF4444).copy(alpha = 0.15f)
                                    else BrandPrimary.copy(alpha = 0.15f),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (active) Icons.Outlined.Lock else Icons.Outlined.Block,
                                contentDescription = null,
                                tint = if (active) Color(0xFFF87171) else BrandPrimary,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (active) Color(0xFFEF4444).copy(alpha = 0.15f)
                                            else BrandPrimary.copy(alpha = 0.15f),
                                        )
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = if (active) "BLOCK ACTIVE" else "STANDALONE",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (active) Color(0xFFF87171) else BrandPrimary,
                                        letterSpacing = 0.8.sp,
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (active) "Enforcing Restrictions" else "Block Apps Without a Task",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkTextPrimary,
                            )
                        }
                    }

                    if (active) {
                        // Digital countdown timer display
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkSurfaceVariant)
                                .padding(14.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "TIME REMAINING",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkTextMuted,
                                    letterSpacing = 0.8.sp,
                                )
                                Text(
                                    text = "${remainingMinutes / 60}h ${remainingMinutes % 60}m",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF87171),
                                )
                                Text(
                                    text = "${settings.standaloneBlockPackages.size} app${if (settings.standaloneBlockPackages.size == 1) "" else "s"} blocked until ${DateFormat.getTimeInstance(DateFormat.SHORT).format(settings.standaloneBlockUntilMs)}",
                                    fontSize = 13.sp,
                                    color = DarkTextSecondary,
                                )
                            }
                        }

                        // Quick extension chips
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "QUICK EXTEND",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkTextMuted,
                                letterSpacing = 0.8.sp,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                listOf(30L to "+30m", 60L to "+1h", 120L to "+2h").forEach { (mins, label) ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(DarkSurfaceVariant)
                                            .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                                            .clickable {
                                                settingsViewModel.setStandaloneBlockAndAllowance(
                                                    StandaloneBlockAndAllowanceConfig(
                                                        standaloneBlockActive = true,
                                                        standaloneBlockPackages = settings.standaloneBlockPackages,
                                                        standaloneBlockUntilMs = settings.standaloneBlockUntilMs + (mins * 60_000L),
                                                        allowanceEntries = dailyAllowanceEntriesFromJson(settings.dailyAllowanceConfigJson),
                                                        pinHash = null,
                                                    ),
                                                )
                                            }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = BrandPrimary,
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Choose apps, an expiry time, and optional daily allowances. This block runs independently of scheduled focus tasks and prevents distracting app launches.",
                            fontSize = 14.sp,
                            color = DarkTextSecondary,
                            lineHeight = 20.sp,
                        )

                        // Feature Highlights
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkSurfaceVariant.copy(alpha = 0.5f))
                                .padding(12.dp),
                        ) {
                            Text("• Strict Enforcement: Cannot be disabled early once active", fontSize = 12.sp, color = DarkTextSecondary)
                            Text("• Daily Allowances: Configure launch or time limits per app", fontSize = 12.sp, color = DarkTextSecondary)
                            Text("• Network Block: Optional VPN-level connection cut", fontSize = 12.sp, color = DarkTextSecondary)
                        }
                    }
                }
            }

            Button(
                onClick = { modalVisible = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (active) BrandPrimary else BrandPrimary,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Outlined.Block, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (active) "Manage Active Block" else "Choose Apps to Block",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = DarkTextSecondary,
                ),
                shape = RoundedCornerShape(12.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(DarkBorder),
                ),
            ) {
                Text("Return to Defense")
            }
        }
    }

    StandaloneBlockModal(
        visible = modalVisible,
        blockedPackages = initialBlockedPackages,
        blockUntilMs = settings.standaloneBlockUntilMs,
        locked = active,
        dailyAllowanceEntries = dailyAllowanceEntriesFromJson(settings.dailyAllowanceConfigJson),
        presets = settings.launcherPresets.map { BlockPresetUi(it.id, it.name, it.packages) },
        onSave = { packages, untilMs, allowances, _, pin ->
            settingsViewModel.setStandaloneBlockAndAllowance(
                StandaloneBlockAndAllowanceConfig(
                    standaloneBlockActive = packages.isNotEmpty() && untilMs != null,
                    standaloneBlockPackages = packages,
                    standaloneBlockUntilMs = untilMs ?: 0L,
                    allowanceEntries = allowances,
                    pinHash = pin,
                ),
            )
            modalVisible = false
        },
        onClose = { modalVisible = false },
        hintDismissed = settings.standaloneBlockHintDismissed,
        onDismissHint = {
            settingsViewModel.updateSettings(settings.copy(standaloneBlockHintDismissed = true))
        },
        verifyPin = settingsViewModel::verifyFocusPin,
        sessionPinSet = settingsViewModel.isFocusPinSet(),
        hashPin = focusPinManager::hash,
    )
}
