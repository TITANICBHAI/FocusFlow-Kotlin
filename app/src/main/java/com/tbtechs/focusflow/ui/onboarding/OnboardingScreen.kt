package com.tbtechs.focusflow.ui.onboarding

import android.Manifest
import android.content.Intent
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tbtechs.focusflow.data.model.AppSettings
import com.tbtechs.focusflow.enforcement.AppBlockerAccessibilityService
import com.tbtechs.focusflow.ui.SettingsViewModel
import com.tbtechs.focusflow.ui.permissions.AccessibilityRestrictedRecovery
import com.tbtechs.focusflow.ui.permissions.PermissionCard
import com.tbtechs.focusflow.ui.permissions.PermissionDefinition
import com.tbtechs.focusflow.ui.permissions.PermissionId
import com.tbtechs.focusflow.ui.permissions.PermissionStatus
import com.tbtechs.focusflow.ui.permissions.checkPermission
import com.tbtechs.focusflow.ui.permissions.openPermissionSettings
import com.tbtechs.focusflow.ui.permissions.permissionDefinitions
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkSurfaceVariant
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.LavenderBg
import com.tbtechs.focusflow.ui.theme.LavenderBorder
import com.tbtechs.focusflow.ui.theme.LavenderText
import com.tbtechs.focusflow.ui.theme.StatusOptionalBg
import com.tbtechs.focusflow.ui.theme.StatusOptionalText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class OnboardingStep { CORE, OPTIONAL }

@Composable
fun OnboardingScreen(
    settingsViewModel: SettingsViewModel = viewModel(),
    onFinished: () -> Unit = {},
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val settings by settingsViewModel.settings.collectAsState()
    var step by remember { mutableStateOf(OnboardingStep.CORE) }
    var statuses by remember { mutableStateOf<Map<PermissionId, PermissionStatus>>(emptyMap()) }
    var expanded by remember { mutableStateOf<PermissionId?>(null) }
    var loading by remember { mutableStateOf<PermissionId?>(null) }
    var accessibilityAttempted by remember { mutableStateOf(false) }
    var pinChoice by remember { mutableStateOf(false) }
    var defensePinSet by remember { mutableStateOf(false) }
    var pinDialog by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var pinConfirm by remember { mutableStateOf("") }

    val onboardingPermissions = remember {
        val definitionsById = permissionDefinitions.associateBy { it.id }
        listOf(
            PermissionId.NOTIFICATIONS,
            PermissionId.BATTERY,
            PermissionId.OVERLAY,
            PermissionId.USAGE,
            PermissionId.ACCESSIBILITY,
            PermissionId.MEDIA,
            PermissionId.VPN,
            PermissionId.DEVICE_ADMIN,
        ).mapNotNull { definitionsById[it] }
    }

    fun refresh() {
        scope.launch {
            val updated = withContext(Dispatchers.IO) {
                onboardingPermissions.associate { it.id to checkPermission(context, it.id) }
            }
            statuses = updated
        }
    }

    LaunchedEffect(Unit) { refresh() }

    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }

    fun grant(permission: PermissionDefinition) {
        if (statuses[permission.id] == PermissionStatus.GRANTED) return
        loading = permission.id
        when (permission.id) {
            PermissionId.MEDIA -> {
                val manifestPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_IMAGES
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }
                permissionLauncher.launch(manifestPermission)
                loading = null
            }
            PermissionId.VPN -> {
                val intent = VpnService.prepare(context)
                if (intent == null) refresh() else vpnLauncher.launch(intent)
                loading = null
            }
            PermissionId.NOTIFICATIONS -> {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    scope.launch { openPermissionSettings(context, permission.id) }
                }
                loading = null
            }
            PermissionId.ACCESSIBILITY -> {
                accessibilityAttempted = true
                scope.launch { openPermissionSettings(context, permission.id); loading = null }
            }
            else -> scope.launch { openPermissionSettings(context, permission.id); loading = null }
        }
    }

    val core = onboardingPermissions.filterNot { it.optional }
    val optional = onboardingPermissions.filter { it.optional }
    val requiredReady = core.filter {
        it.id == PermissionId.ACCESSIBILITY || it.id == PermissionId.USAGE || it.id == PermissionId.NOTIFICATIONS
    }.count { statuses[it.id] == PermissionStatus.GRANTED }
    val requiredTotal = 3
    val optionalReady = optional.count { statuses[it.id] == PermissionStatus.GRANTED }

    Scaffold(
        containerColor = DarkBackground,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                // Title and Subtitle Header matching 1a.jpg / 1c.jpg
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (step == OnboardingStep.CORE) "Set up core access" else "Optional protection",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkTextPrimary,
                    )
                    Text(
                        text = if (step == OnboardingStep.CORE) "These permissions help FocusFlow block reliably."
                        else "Add extra protection now or come back later.",
                        fontSize = 14.sp,
                        color = DarkTextSecondary,
                    )
                }
            }

            if (step == OnboardingStep.CORE) {
                // "Why these permissions?" Info Callout Box matching 1a.jpg
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(LavenderBg)
                            .border(1.dp, LavenderBorder, RoundedCornerShape(16.dp))
                            .padding(16.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Shield,
                                    contentDescription = null,
                                    tint = BrandPrimary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = "Why these permissions?",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = LavenderText,
                                )
                            }
                            Text(
                                text = "FocusFlow enforces focus at the system level — not just reminders. To actually block apps and keep your session running, Android requires special access that regular apps don't need.",
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                color = Color(0xFF3730A3),
                            )
                        }
                    }
                }

                // Progress Tracker matching 1a.jpg
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Required access ready",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = DarkTextSecondary,
                            )
                            Text(
                                text = "$requiredReady / $requiredTotal",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrandPrimary,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { requiredReady.toFloat() / requiredTotal.toFloat() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = BrandPrimary,
                            trackColor = DarkSurfaceVariant,
                        )
                    }
                }

                // Section Label
                item {
                    Text(
                        text = "CORE ACCESS — TAP A CARD TO GIVE ACCESS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = DarkTextMuted,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                // Core Cards List
                items(core, key = { it.id }) { permission ->
                    PermissionCard(
                        permission = permission,
                        status = statuses[permission.id] ?: PermissionStatus.UNKNOWN,
                        expanded = expanded == permission.id,
                        busy = loading == permission.id,
                        onToggle = {
                            if (statuses[permission.id] != PermissionStatus.GRANTED) grant(permission)
                            else expanded = if (expanded == permission.id) null else permission.id
                        },
                        onGrant = { grant(permission) },
                    )
                }

                item {
                    AccessibilityRestrictedRecovery(accessibilityAttempted = accessibilityAttempted)
                }

                // Bottom Lavender Info Note matching 1b.jpg
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(LavenderBg)
                            .border(1.dp, LavenderBorder, RoundedCornerShape(14.dp))
                            .padding(14.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = null,
                                tint = BrandPrimary,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = "Usage Access, Accessibility Service, and Notifications can be fixed anytime in Settings → Permissions.",
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = LavenderText,
                            )
                        }
                    }
                }
            } else {
                // Step 2: Optional Protection matching 1c.jpg / 1d.jpg
                item {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { step = OnboardingStep.CORE }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = null,
                            tint = BrandPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Back to core setup",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandPrimary,
                        )
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "OPTIONAL SETUP — TAP A CARD TO GIVE ACCESS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = DarkTextMuted,
                        )
                        Text(
                            text = "These features are not required to use FocusFlow and can be configured later.",
                            fontSize = 13.sp,
                            color = DarkTextSecondary,
                        )
                    }
                }

                // Optional Cards List
                items(optional, key = { it.id }) { permission ->
                    PermissionCard(
                        permission = permission,
                        status = statuses[permission.id] ?: PermissionStatus.UNKNOWN,
                        expanded = expanded == permission.id,
                        busy = loading == permission.id,
                        onToggle = {
                            if (statuses[permission.id] != PermissionStatus.GRANTED) grant(permission)
                            else expanded = if (expanded == permission.id) null else permission.id
                        },
                        onGrant = { grant(permission) },
                    )
                }

                item {
                    Text(
                        text = "SECURITY PREFERENCE",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = DarkTextMuted,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                // PIN Protection Card matching 1d.jpg
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(DarkCard)
                            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                            .padding(16.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(StatusOptionalBg),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Outlined.Lock,
                                        contentDescription = null,
                                        tint = StatusOptionalText,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "PIN Protection",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DarkTextPrimary,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Require a password to disable block enforcement toggles. Prevents impulsive self-sabotage mid-session.",
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp,
                                        color = DarkTextSecondary,
                                    )
                                }

                                Switch(
                                    checked = pinChoice,
                                    onCheckedChange = { pinChoice = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = BrandPrimary,
                                        uncheckedThumbColor = DarkTextMuted,
                                        uncheckedTrackColor = DarkSurfaceVariant,
                                    ),
                                )
                            }

                            if (pinChoice && !defensePinSet) {
                                Button(
                                    onClick = { pinDialog = true },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                                    modifier = Modifier.fillMaxWidth().height(42.dp),
                                ) {
                                    Text("Set Password Now", fontWeight = FontWeight.Bold)
                                }
                            }

                            // Inner callout box matching 1d.jpg
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DarkSurfaceVariant)
                                    .padding(10.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.Info,
                                        contentDescription = null,
                                        tint = DarkTextSecondary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = if (pinChoice && defensePinSet) "Defense Password set — your protections are locked."
                                        else "You can enable this anytime in Settings → PIN Protection or Block Enforcement.",
                                        fontSize = 12.sp,
                                        color = DarkTextSecondary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom CTA Button matching 1b.jpg / 1d.jpg
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = {
                        if (step == OnboardingStep.CORE) {
                            step = OnboardingStep.OPTIONAL
                        } else {
                            settingsViewModel.updateSettings(settings.copy(pinProtectionEnabled = pinChoice))
                            context.getSharedPreferences(
                                AppBlockerAccessibilityService.PREFS_NAME,
                                0,
                            ).edit()
                                .putString("user_consented_background_service", "true")
                                .putString("onboarding_complete", "true")
                                .apply()
                            onFinished()
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (step == OnboardingStep.CORE && requiredReady < requiredTotal) LavenderBg else BrandPrimary,
                        contentColor = if (step == OnboardingStep.CORE && requiredReady < requiredTotal) LavenderText else Color.White,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text(
                        text = if (step == OnboardingStep.CORE) {
                            "Continue to optional setup →"
                        } else {
                            when {
                                optionalReady == 0 -> "Skip optional setup — let's start"
                                optionalReady == optional.size -> "All optional access ready — let's start"
                                optionalReady == 1 -> "1 optional permission enabled — let's start"
                                else -> "$optionalReady optional permissions enabled — let's start"
                            }
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Footer Subtitle Caption
            item {
                Text(
                    text = if (step == OnboardingStep.CORE) "You can manage permissions in Settings at any time."
                    else "Optional features can be enabled later from Settings.",
                    fontSize = 12.sp,
                    color = DarkTextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                )
            }
        }
    }

    if (pinDialog) {
        AlertDialog(
            onDismissRequest = { pinDialog = false },
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("Set Defense Password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it },
                        label = { Text("Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = pinConfirm,
                        onValueChange = { pinConfirm = it },
                        label = { Text("Confirm password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (pin.isNotEmpty() && pin != pinConfirm) {
                        Text("Passwords do not match", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pin.length >= 4 && pin == pinConfirm) {
                            settingsViewModel.setPin(pin)
                            defensePinSet = true
                            pinChoice = true
                            pinDialog = false
                            pin = ""
                            pinConfirm = ""
                        }
                    },
                    enabled = pin.length >= 4 && pin == pinConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                ) { Text("Set Password", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { pinDialog = false }) { Text("Set later", color = DarkTextSecondary) }
            },
        )
    }
}
