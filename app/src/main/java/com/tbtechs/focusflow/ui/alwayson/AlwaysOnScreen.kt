package com.tbtechs.focusflow.ui.alwayson

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AllInclusive
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.data.repository.InstalledAppInfo
import com.tbtechs.focusflow.data.repository.InstalledAppsRepository
import com.tbtechs.focusflow.data.repository.NetworkBlockSettings
import com.tbtechs.focusflow.data.repository.SettingsRepository
import com.tbtechs.focusflow.data.repository.VpnRepository
import com.tbtechs.focusflow.ui.FocusSessionViewModel
import com.tbtechs.focusflow.ui.SettingsViewModel
import com.tbtechs.focusflow.ui.launcher.AppIcon
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkSurfaceVariant
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import kotlinx.coroutines.launch
import java.security.MessageDigest

private val systemNeverBlock = setOf(
    "com.android.launcher",
    "com.android.launcher2",
    "com.android.launcher3",
    "com.sec.android.app.launcher",
    "com.google.android.apps.nexuslauncher",
    "com.miui.launcher",
    "com.huawei.android.launcher",
    "com.coloros.launcher",
    "com.oneplus.launcher",
    "com.oppo.launcher",
    "com.motorola.launcher3",
    "com.nothing.launcher",
    "com.realme.launcher",
    "com.iqoo.launcher",
    "com.vivo.launcher",
    "com.asus.launcher",
    "com.ZenUI.launcher",
    "com.lge.launcher3",
    "com.htc.launcher",
    "com.sonyericsson.home",
    "com.tcl.launcher",
    "com.nokia.launcher",
    "com.infinix.launcher",
    "com.transsion.launcher",
    "com.hihonor.launcher",
    "com.android.systemui",
    "com.android.phone",
    "com.android.server.telecom",
    "com.android.dialer",
    "com.samsung.android.incallui",
    "com.google.android.dialer",
    "com.google.android.apps.googledialer",
    "com.whatsapp",
    "com.google.android.gms",
    "com.android.packageinstaller",
    "com.google.android.packageinstaller",
    "com.samsung.android.packageinstaller",
    "com.samsung.android.wallet",
    "com.samsung.android.samsungpay",
    "com.google.android.apps.walletnfcrel",
    "com.tbtechs.focusflow",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlwaysOnScreen(
    settingsViewModel: SettingsViewModel,
    focusSessionViewModel: FocusSessionViewModel,
    settingsRepository: SettingsRepository,
    vpnRepository: VpnRepository,
    installedAppsRepository: InstalledAppsRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val settings by settingsViewModel.settings.collectAsState()
    val focusSession by focusSessionViewModel.focusSession.collectAsState()

    var apps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
    var networkSettings by remember { mutableStateOf<NetworkBlockSettings?>(null) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var vpnSelected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var originalSelected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var originalVpnSelected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var search by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showPin by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var showConsent by remember { mutableStateOf(false) }
    var clearConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        loading = true
        val storedSettings = runCatching { settingsRepository.readAppSettings() }.getOrNull()
        val storedNetwork = runCatching { vpnRepository.getNetworkBlockSettings() }.getOrNull()
        apps = runCatching {
            installedAppsRepository.getInstalledApps()
                .filterNot { it.packageName in systemNeverBlock }
                .sortedBy { it.appName.lowercase() }
        }.getOrDefault(emptyList())
        networkSettings = storedNetwork
        selected = storedSettings?.alwaysBlockPackages?.toSet().orEmpty()
        vpnSelected = storedNetwork?.packages?.toSet().orEmpty()
        originalSelected = selected
        originalVpnSelected = vpnSelected
        loading = false
    }

    val filteredApps = remember(apps, search) {
        val query = search.trim().lowercase()
        if (query.isBlank()) apps
        else apps.filter {
            it.appName.lowercase().contains(query) || it.packageName.lowercase().contains(query)
        }
    }
    val blockProtectionActive = focusSession?.isActive == true ||
        (settings.standaloneBlockActive &&
            settings.standaloneBlockUntilMs > System.currentTimeMillis())
    val isRemoving = originalSelected.any { it !in selected } ||
        originalVpnSelected.any { it !in vpnSelected }

    fun save(defensePin: String? = null) {
        if (isRemoving && blockProtectionActive) {
            error = "Always-On apps and VPN-blocked apps cannot be removed while Focus Mode or Standalone Block is active."
            return
        }
        if (isRemoving && settings.pinProtectionEnabled && defensePin == null) {
            showPin = true
            pinError = null
            return
        }

        scope.launch {
            saving = true
            error = null
            try {
                val currentNetwork = networkSettings ?: NetworkBlockSettings()
                val hasVpnPackages = vpnSelected.isNotEmpty() ||
                    currentNetwork.standalonePackages.isNotEmpty()

                if (vpnSelected.isNotEmpty() && !vpnRepository.isVpnPermissionGranted()) {
                    showConsent = true
                    return@launch
                }

                vpnRepository.setNetworkBlockSettings(
                    currentNetwork.copy(
                        enabled = hasVpnPackages,
                        vpn = hasVpnPackages,
                        packages = vpnSelected.toList().sorted(),
                    ),
                    defensePinHash = defensePin?.let(::legacyPinHash),
                )
                vpnRepository.setVpnSelfHealEnabled(hasVpnPackages)
                settingsRepository.setAlwaysBlockActive(
                    active = selected.isNotEmpty(),
                    packages = selected.toList().sorted(),
                )

                settingsViewModel.updateSettings(
                    settings.copy(
                        alwaysBlockEnabled = selected.isNotEmpty(),
                        alwaysBlockPackages = selected.toList().sorted(),
                        networkBlockEnabled = hasVpnPackages,
                    ),
                )
                onBack()
            } catch (exception: Exception) {
                error = exception.message ?: "Could not save the Always-On list."
            } finally {
                saving = false
            }
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Always-On Block List",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkTextPrimary,
                        )
                        Text(
                            text = if (selected.isNotEmpty()) {
                                "${selected.size} app${if (selected.size == 1) "" else "s"} blocked 24/7"
                            } else {
                                "Tick apps to block them permanently — no timer"
                            },
                            fontSize = 12.sp,
                            color = DarkTextSecondary,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = DarkTextPrimary,
                        )
                    }
                },
                actions = {
                    if (selected.isNotEmpty()) {
                        IconButton(
                            onClick = { clearConfirmation = true },
                            enabled = !saving,
                        ) {
                            Icon(
                                Icons.Outlined.ClearAll,
                                contentDescription = "Clear all",
                                tint = DarkTextSecondary,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground),
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBackground)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Button(
                    onClick = { save() },
                    enabled = !loading && !saving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandPrimary,
                        contentColor = Color.White,
                        disabledContainerColor = DarkBorder,
                        disabledContentColor = DarkTextMuted,
                    ),
                ) {
                    if (saving) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp,
                        )
                    } else {
                        Text(
                            text = if (selected.isEmpty()) "Save (no apps selected)"
                            else "Save ${selected.size} app${if (selected.size == 1) "" else "s"}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Explanatory Info Card matching 3e_(3)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkCard)
                    .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(BrandPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.AllInclusive,
                            contentDescription = null,
                            tint = BrandPrimary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "These apps are blocked continuously — no session or timer needed. They stay blocked until you untick them here.",
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = DarkTextPrimary,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Removing apps requires your defense password (if set). Tap a blocked app to also enable network blocking (VPN).",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = DarkTextSecondary,
                        )
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = {
                    Text("Search apps…", color = DarkTextMuted, fontSize = 14.sp)
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = null,
                        tint = DarkTextSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                },
                trailingIcon = {
                    if (search.isNotBlank()) {
                        IconButton(onClick = { search = "" }) {
                            Icon(
                                Icons.Outlined.Clear,
                                contentDescription = "Clear search",
                                tint = DarkTextSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = DarkCard,
                    unfocusedContainerColor = DarkCard,
                    focusedBorderColor = BrandPrimary,
                    unfocusedBorderColor = DarkBorder,
                    focusedTextColor = DarkTextPrimary,
                    unfocusedTextColor = DarkTextPrimary,
                ),
            )

            error?.let { err ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF450A0A))
                        .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                ) {
                    Text(err, color = Color(0xFFF87171), fontSize = 13.sp)
                }
            }

            when {
                loading -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(color = BrandPrimary, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Loading apps…", color = DarkTextSecondary, fontSize = 14.sp)
                }
                filteredApps.isEmpty() -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        if (search.isBlank()) "No apps found" else "No apps match \"$search\"",
                        color = DarkTextSecondary,
                        fontSize = 14.sp,
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        AlwaysOnAppRow(
                            app = app,
                            checked = app.packageName in selected,
                            vpnEnabled = app.packageName in vpnSelected,
                            onToggle = {
                                if (app.packageName in selected) {
                                    selected = selected - app.packageName
                                    vpnSelected = vpnSelected - app.packageName
                                } else {
                                    selected = selected + app.packageName
                                }
                            },
                            onToggleVpn = {
                                vpnSelected = if (app.packageName in vpnSelected) {
                                    vpnSelected - app.packageName
                                } else {
                                    vpnSelected + app.packageName
                                }
                            },
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }
        }
    }

    VpnConsentModal(
        visible = showConsent,
        onCancel = { showConsent = false },
        onConfirm = {
            showConsent = false
            scope.launch {
                try {
                    vpnRepository.requestVpnPermission(activity)
                    error = "Grant VPN permission, then tap Save again."
                } catch (exception: Exception) {
                    error = exception.message ?: "Could not open VPN consent."
                }
            }
        },
    )

    if (clearConfirmation) {
        AlertDialog(
            onDismissRequest = { clearConfirmation = false },
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("Clear all?") },
            text = { Text("This removes all apps from the Always-On enforcement list. They will no longer be blocked continuously.") },
            confirmButton = {
                Button(
                    onClick = {
                        selected = emptySet()
                        vpnSelected = emptySet()
                        clearConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                ) {
                    Text("Clear all", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { clearConfirmation = false }) {
                    Text("Cancel", color = DarkTextSecondary)
                }
            },
        )
    }

    if (showPin) {
        AlertDialog(
            onDismissRequest = {
                showPin = false
                pin = ""
                pinError = null
            },
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("Defense password required") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "You are removing apps from the Always-On block list. Enter your defense password to confirm.",
                        fontSize = 13.sp,
                    )
                    OutlinedTextField(
                        value = pin,
                        onValueChange = {
                            pin = it
                            pinError = null
                        },
                        label = { Text("Defense password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = pinError != null,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedContainerColor = DarkSurfaceVariant,
                            focusedBorderColor = BrandPrimary,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = DarkTextPrimary,
                            unfocusedTextColor = DarkTextPrimary,
                        ),
                    )
                    pinError?.let {
                        Text(it, color = Color(0xFFF87171), fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (settingsViewModel.verifyPin(pin)) {
                            showPin = false
                            val verifiedPin = pin
                            pin = ""
                            save(verifiedPin)
                        } else {
                            pinError = "Incorrect defense password."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                ) {
                    Text("Verify and save", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPin = false
                    pin = ""
                    pinError = null
                }) {
                    Text("Cancel", color = DarkTextSecondary)
                }
            },
        )
    }
}

@Composable
private fun AlwaysOnAppRow(
    app: InstalledAppInfo,
    checked: Boolean,
    vpnEnabled: Boolean,
    onToggle: () -> Unit,
    onToggleVpn: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkCard)
            .border(
                1.dp,
                if (checked) BrandPrimary.copy(alpha = 0.35f) else DarkBorder,
                RoundedCornerShape(14.dp),
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppIcon(app.icon)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = app.packageName,
                    fontSize = 12.sp,
                    color = DarkTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Styled rounded checkbox matching 3e_(3)
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (checked) BrandPrimary else Color.Transparent)
                    .border(
                        1.5.dp,
                        if (checked) BrandPrimary else DarkBorder,
                        RoundedCornerShape(6.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        if (checked) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurfaceVariant.copy(alpha = 0.5f))
                    .clickable(onClick = onToggleVpn)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = if (vpnEnabled) BrandPrimary else DarkTextMuted,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = if (vpnEnabled) "Network block (VPN): on" else "Add network block (VPN)",
                        fontSize = 13.sp,
                        fontWeight = if (vpnEnabled) FontWeight.Medium else FontWeight.Normal,
                        color = if (vpnEnabled) BrandPrimary else DarkTextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = vpnEnabled,
                        onCheckedChange = { onToggleVpn() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = BrandPrimary,
                            uncheckedThumbColor = DarkTextMuted,
                            uncheckedTrackColor = DarkCard,
                            uncheckedBorderColor = DarkBorder,
                        ),
                    )
                }
            }
        }
    }
}

private fun legacyPinHash(pin: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(pin.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
