package com.tbtechs.focusflow.ui.launcher

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.data.repository.InstalledAppInfo
import com.tbtechs.focusflow.data.repository.InstalledAppsRepository
import com.tbtechs.focusflow.data.repository.NetworkBlockSettings
import com.tbtechs.focusflow.data.repository.VpnRepository
import com.tbtechs.focusflow.ui.SettingsViewModel
import com.tbtechs.focusflow.ui.alwayson.VpnConsentModal
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

/**
 * VPN Network Block List screen.
 *
 * Implements the dark theme design system with high-contrast app list,
 * persistent 24/7 network cutoff explanation, search, and defense PIN gating.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VpnBlockListScreen(
    settingsViewModel: SettingsViewModel,
    vpnRepository: VpnRepository,
    installedAppsRepository: InstalledAppsRepository,
    isFocusActive: Boolean = false,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val settings by settingsViewModel.settings.collectAsState()
    var networkSettings by remember { mutableStateOf<NetworkBlockSettings?>(null) }
    var apps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var original by remember { mutableStateOf<Set<String>>(emptySet()) }
    var search by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pinDialog by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var clearDialog by remember { mutableStateOf(false) }
    var showVpnConsent by remember { mutableStateOf(false) }

    val systemNeverBlock = remember {
        setOf(
            "com.android.dialer",
            "com.google.android.dialer",
            "com.samsung.android.incallui",
            "com.whatsapp",
        )
    }

    LaunchedEffect(Unit) {
        loading = true
        val loaded = runCatching { vpnRepository.getNetworkBlockSettings() }.getOrNull()
        networkSettings = loaded
        selected = loaded?.packages?.toSet().orEmpty()
        original = selected
        apps = runCatching {
            installedAppsRepository.getInstalledApps()
                .filterNot { it.packageName in systemNeverBlock }
                .sortedBy { it.appName.lowercase() }
        }.getOrDefault(emptyList())
        loading = false
    }

    val filtered = remember(apps, search) {
        val query = search.trim().lowercase()
        if (query.isBlank()) apps else apps.filter {
            it.appName.lowercase().contains(query) || it.packageName.lowercase().contains(query)
        }
    }
    val locked = isFocusActive ||
        (settings.standaloneBlockActive &&
            settings.standaloneBlockUntilMs > System.currentTimeMillis())
    val removing = original.any { it !in selected }

    fun save() {
        if (removing && locked) {
            error = "VPN-blocked apps cannot be removed while Focus Mode or a Standalone Block is active."
            return
        }
        if (removing && settings.pinProtectionEnabled && !pinDialog) {
            pinDialog = true
            return
        }
        scope.launch {
            saving = true
            error = null
            try {
                if (removing && settings.pinProtectionEnabled && !settingsViewModel.verifyPin(pin)) {
                    error = "Incorrect defense password."
                    return@launch
                }
                if (selected.isNotEmpty() && !vpnRepository.isVpnPermissionGranted()) {
                    showVpnConsent = true
                    return@launch
                }
                val current = networkSettings ?: NetworkBlockSettings()
                val hasPackages = selected.isNotEmpty()
                vpnRepository.setNetworkBlockSettings(
                    current.copy(
                        enabled = hasPackages,
                        vpn = hasPackages,
                        packages = selected.toList().sorted(),
                    ),
                    defensePinHash = pin.takeIf {
                        removing && settings.pinProtectionEnabled
                    }?.let(::legacyPinHash),
                )
                vpnRepository.setVpnSelfHealEnabled(hasPackages)
                original = selected
                pinDialog = false
                pin = ""
                onBack()
            } catch (exception: Exception) {
                error = exception.message ?: "Could not save the VPN block list."
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
                            text = "VPN Block List",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkTextPrimary,
                        )
                        Text(
                            text = if (selected.isEmpty()) "Cut internet access — no overlay needed"
                            else "${selected.size} app${if (selected.size == 1) "" else "s"} network-blocked 24/7",
                            fontSize = 12.sp,
                            color = DarkTextSecondary,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = DarkTextPrimary)
                    }
                },
                actions = {
                    if (selected.isNotEmpty()) {
                        IconButton(
                            onClick = { clearDialog = true },
                            enabled = !saving,
                        ) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear all", tint = Color(0xFFEF4444))
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
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Button(
                    onClick = ::save,
                    enabled = !loading && !saving,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    if (saving) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    } else {
                        Text(
                            text = if (selected.isEmpty()) "Save (No apps selected)"
                            else "Save ${selected.size} Blocked App${if (selected.size == 1) "" else "s"}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White,
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
            // Information Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkCard)
                    .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                            Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = BrandPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "24/7 Network Isolation",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkTextPrimary,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Selected apps have all internet access routed to an offline loopback VPN. No external servers or sessions required. Operates in addition to visual overlays.",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = DarkTextSecondary,
                        )
                    }
                }
            }

            if (locked) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF451A03))
                        .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                ) {
                    Text(
                        "Removing blocked apps is locked while Focus Mode or Standalone Block is active.",
                        color = Color(0xFFFBBF24),
                        fontSize = 12.sp,
                    )
                }
            }

            // Search Bar
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null, tint = DarkTextMuted, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (search.isNotBlank()) {
                        IconButton(onClick = { search = "" }) {
                            Icon(Icons.Outlined.Clear, contentDescription = "Clear", tint = DarkTextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                placeholder = { Text("Search installed apps", color = DarkTextMuted, fontSize = 13.sp) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = DarkSurfaceVariant,
                    unfocusedContainerColor = DarkSurfaceVariant,
                    focusedBorderColor = BrandPrimary,
                    unfocusedBorderColor = DarkBorder,
                    focusedTextColor = DarkTextPrimary,
                    unfocusedTextColor = DarkTextPrimary,
                ),
            )

            error?.let {
                Text(it, color = Color(0xFFEF4444), fontSize = 12.sp)
            }

            if (loading) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandPrimary, modifier = Modifier.size(32.dp))
                }
            } else if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(DarkSurfaceVariant)
                        .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (search.isBlank()) "No installed apps found" else "No apps match \"$search\"",
                        color = DarkTextSecondary,
                        fontSize = 14.sp,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        val isChecked = app.packageName in selected
                        val isOverlayBlocked = app.packageName in settings.alwaysBlockPackages
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(DarkCard)
                                .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
                                .clickable {
                                    selected = if (isChecked) selected - app.packageName else selected + app.packageName
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                AppIcon(app.icon)
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = app.appName,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = DarkTextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (isOverlayBlocked) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0xFF374151))
                                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                                            ) {
                                                Text(
                                                    "Overlay",
                                                    fontSize = 9.sp,
                                                    color = Color(0xFFD1D5DB),
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = app.packageName,
                                        fontSize = 11.sp,
                                        color = DarkTextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Switch(
                                    checked = isChecked,
                                    onCheckedChange = {
                                        selected = if (isChecked) selected - app.packageName else selected + app.packageName
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = BrandPrimary,
                                        uncheckedThumbColor = DarkTextMuted,
                                        uncheckedTrackColor = DarkSurfaceVariant,
                                        uncheckedBorderColor = DarkBorder,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (clearDialog) {
        AlertDialog(
            onDismissRequest = { clearDialog = false },
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("Clear VPN Block List?") },
            text = { Text("This will re-enable network connectivity for all apps on this list.", fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        selected = emptySet()
                        clearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                ) {
                    Text("Clear All", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { clearDialog = false }) {
                    Text("Cancel", color = DarkTextSecondary)
                }
            },
        )
    }

    if (pinDialog) {
        AlertDialog(
            onDismissRequest = {
                pinDialog = false
                pin = ""
            },
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("Defense Password Required") },
            text = {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it },
                    placeholder = { Text("Enter Defense Password", color = DarkTextMuted, fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant,
                        focusedBorderColor = BrandPrimary,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = DarkTextPrimary,
                        unfocusedTextColor = DarkTextPrimary,
                    ),
                )
            },
            confirmButton = {
                Button(
                    onClick = ::save,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                ) {
                    Text("Verify & Save", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pinDialog = false
                    pin = ""
                }) {
                    Text("Cancel", color = DarkTextSecondary)
                }
            },
        )
    }

    VpnConsentModal(
        visible = showVpnConsent,
        onCancel = { showVpnConsent = false },
        onConfirm = {
            showVpnConsent = false
            scope.launch {
                try {
                    vpnRepository.requestVpnPermission(context as? Activity)
                    error = "Grant the Android VPN permission, then tap Save again."
                } catch (exception: Exception) {
                    error = exception.message ?: "Could not open VPN consent."
                }
            }
        },
    )
}

private fun legacyPinHash(pin: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(pin.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
