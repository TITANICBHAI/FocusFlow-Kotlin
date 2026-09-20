package com.tbtechs.focusflow.ui.launcher

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import com.tbtechs.focusflow.data.model.AllowedAppPreset
import com.tbtechs.focusflow.data.model.BLOCK_ALL_SENTINEL
import com.tbtechs.focusflow.data.repository.InstalledAppInfo
import com.tbtechs.focusflow.data.repository.InstalledAppsRepository
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkSurfaceVariant
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.LocalFocusFlowDimensions
import com.tbtechs.focusflow.ui.home.ReferencePill
import kotlinx.coroutines.launch

private data class SensitiveApp(
    val reason: String,
    val category: String,
)

private val sensitiveApps = mapOf(
    "com.android.phone" to SensitiveApp("Phone service", "System"),
    "com.google.android.dialer" to SensitiveApp("Phone dialer", "System"),
    "com.samsung.android.dialer" to SensitiveApp("Phone dialer", "System"),
    "com.android.server.telecom" to SensitiveApp("Emergency calls", "System"),
    "com.google.android.apps.messaging" to SensitiveApp("SMS / 2FA verification", "Communication"),
    "com.samsung.android.messaging" to SensitiveApp("SMS / 2FA verification", "Communication"),
    "com.android.mms" to SensitiveApp("SMS / 2FA verification", "Communication"),
    "com.android.settings" to SensitiveApp("System Settings", "System"),
    "com.google.android.calculator" to SensitiveApp("Calculator utility", "Utility"),
    "com.google.android.calendar" to SensitiveApp("Calendar schedule", "Productivity"),
    "com.google.android.deskclock" to SensitiveApp("Clock & Alarms", "Utility"),
    "com.sec.android.app.clockpackage" to SensitiveApp("Clock & Alarms", "Utility"),
)

/**
 * Redesigned Allowed During Focus App Picker matching Screenshot 10.
 * Reusable across Settings -> Manage Allowed Apps and Task Focus Mode setup.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerSheet(
    visible: Boolean,
    title: String = "Allowed During Focus",
    initialSelected: List<String>,
    noneWhenEmpty: Boolean = false,
    presets: List<AllowedAppPreset> = emptyList(),
    installedAppsRepository: InstalledAppsRepository,
    onSave: (List<String>) -> Unit,
    onSavePreset: (AllowedAppPreset) -> Unit = {},
    onDeletePreset: (String) -> Unit = {},
    onClose: () -> Unit,
) {
    if (!visible) return

    val dimensions = LocalFocusFlowDimensions.current
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var categoryFilter by remember { mutableStateOf<String?>(null) }
    var warning by remember { mutableStateOf<Pair<String, SensitiveApp>?>(null) }
    var deletePreset by remember { mutableStateOf<AllowedAppPreset?>(null) }
    var showPresetInput by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }

    val initialWasBlockAll = remember(initialSelected) {
        initialSelected.contains(BLOCK_ALL_SENTINEL)
    }

    var selected by remember(initialSelected) {
        mutableStateOf(
            if (initialWasBlockAll) emptySet()
            else initialSelected.filter { it != BLOCK_ALL_SENTINEL }.toSet(),
        )
    }

    LaunchedEffect(Unit) {
        loading = true
        loadError = null
        try {
            val loaded = installedAppsRepository.getInstalledApps()
            apps = loaded
            if (!initialWasBlockAll && !noneWhenEmpty && initialSelected.isEmpty()) {
                selected = loaded.mapTo(mutableSetOf()) { it.packageName }
            }
        } catch (_: Exception) {
            loadError = "Installed apps could not be loaded. Close and try again."
        } finally {
            loading = false
        }
    }

    val filteredApps = remember(apps, search, categoryFilter) {
        val query = search.trim().lowercase()
        apps.filter { app ->
            val matchesSearch = query.isEmpty() ||
                app.appName.lowercase().contains(query) ||
                app.packageName.lowercase().contains(query)
            val matchesCategory = categoryFilter == null ||
                sensitiveApps[app.packageName]?.category == categoryFilter
            matchesSearch && matchesCategory
        }
    }

    val selectedPackages = {
        when {
            (initialWasBlockAll || noneWhenEmpty) && selected.isEmpty() ->
                listOf(BLOCK_ALL_SENTINEL)
            !noneWhenEmpty && apps.isNotEmpty() && selected.size == apps.size -> emptyList()
            else -> selected.toList().sorted()
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.94f),
            shape = RoundedCornerShape(24.dp),
            color = DarkBackground,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(dimensions.sectionSpacing),
            ) {
                // Header (Screenshot 10)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkTextPrimary,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (selected.isEmpty()) {
                                "All apps will be blocked during focus"
                            } else {
                                "${selected.size} app${if (selected.size == 1) "" else "s"} allowed · ${apps.size - selected.size} blocked"
                            },
                            fontSize = 12.sp,
                            color = if (selected.isEmpty()) Color(0xFFFBBF24) else BrandPrimary,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Close",
                            tint = DarkTextSecondary,
                        )
                    }
                }

                // Explanatory note matching Screenshot 10
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            tint = BrandPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            "Allowed apps stay open when Focus Mode runs. Everything else is blocked.",
                            fontSize = 12.sp,
                            color = DarkTextSecondary,
                        )
                    }
                }

                // Search Field
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = null,
                            tint = DarkTextMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    placeholder = { Text("Search installed apps...", color = DarkTextMuted, fontSize = 13.sp) },
                    trailingIcon = {
                        if (search.isNotBlank()) {
                            IconButton(onClick = { search = "" }) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = "Clear search",
                                    tint = DarkTextSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant,
                        focusedBorderColor = BrandPrimary,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = DarkTextPrimary,
                        unfocusedTextColor = DarkTextPrimary,
                    ),
                )

                // Category Filter Chips
                val categoryOptions = remember(apps) {
                    sensitiveApps.values.map { it.category }.distinct().sorted()
                }
                if (categoryOptions.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        item {
                            ReferencePill(
                                text = "All (${apps.size})",
                                selected = categoryFilter == null,
                                fontSize = 11.sp,
                                onClick = { categoryFilter = null },
                            )
                        }
                        items(categoryOptions) { category ->
                            val count = apps.count { sensitiveApps[it.packageName]?.category == category }
                            ReferencePill(
                                text = "$category ($count)",
                                selected = categoryFilter == category,
                                fontSize = 11.sp,
                                onClick = { categoryFilter = category },
                            )
                        }
                    }
                }

                // Action Buttons Row (Select All, Deselect All, Save)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { selected = apps.mapTo(mutableSetOf()) { it.packageName } },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 36.dp),
                    ) {
                        Text("Select All", fontSize = 12.sp, color = DarkTextPrimary)
                    }

                    Button(
                        onClick = {
                            selected = apps
                                .map { it.packageName }
                                .filter { it in sensitiveApps }
                                .toSet()
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 36.dp),
                    ) {
                        Text("Deselect All", fontSize = 12.sp, color = DarkTextPrimary)
                    }

                    Button(
                        onClick = {
                            onSave(selectedPackages())
                            onClose()
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 44.dp),
                    ) {
                        Text("Save", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                // Presets row
                if (presets.isNotEmpty() || showPresetInput) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "SAVED PRESETS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = BrandPrimary,
                        )
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(presets, key = { it.id }) { preset ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DarkSurfaceVariant)
                                    .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                                    .combinedClickable(
                                        onClick = {
                                            selected = when {
                                                preset.packages.contains(BLOCK_ALL_SENTINEL) ->
                                                    apps.map { it.packageName }.filter { it in sensitiveApps }.toSet()
                                                preset.packages.isEmpty() ->
                                                    apps.mapTo(mutableSetOf()) { it.packageName }
                                                else ->
                                                    (preset.packages + apps.map { it.packageName }.filter { it in sensitiveApps })
                                                        .filter { packageName -> apps.any { it.packageName == packageName } }
                                                        .toSet()
                                            }
                                        },
                                        onLongClick = { deletePreset = preset },
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(preset.name, fontSize = 12.sp, color = DarkTextPrimary)
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = "Delete preset",
                                        tint = DarkTextMuted,
                                        modifier = Modifier
                                            .size(13.dp)
                                            .clickable { deletePreset = preset },
                                    )
                                }
                            }
                        }
                    }
                }

                // Preset save action
                if (showPresetInput) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = presetName,
                            onValueChange = { presetName = it.take(32) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("Preset name (e.g. Deep Coding)", color = DarkTextMuted, fontSize = 12.sp) },
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DarkSurfaceVariant,
                                unfocusedContainerColor = DarkSurfaceVariant,
                                focusedBorderColor = BrandPrimary,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = DarkTextPrimary,
                                unfocusedTextColor = DarkTextPrimary,
                            ),
                        )
                        Button(
                            onClick = {
                                val trimmed = presetName.trim()
                                if (trimmed.isNotEmpty()) {
                                    onSavePreset(
                                        AllowedAppPreset(
                                            id = "${System.currentTimeMillis()}-$trimmed",
                                            name = trimmed,
                                            packages = selectedPackages(),
                                        ),
                                    )
                                    presetName = ""
                                    showPresetInput = false
                                }
                            },
                            enabled = presetName.isNotBlank(),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                        ) {
                            Text("Save", fontSize = 12.sp, color = Color.White)
                        }
                        TextButton(
                            onClick = {
                                presetName = ""
                                showPresetInput = false
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                        ) {
                            Text("Cancel", fontSize = 12.sp, color = DarkTextSecondary)
                        }
                    }
                } else {
                    TextButton(
                        onClick = { showPresetInput = true },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .align(Alignment.Start)
                            .defaultMinSize(minHeight = 44.dp),
                    ) {
                        Icon(
                            Icons.Outlined.BookmarkAdd,
                            contentDescription = null,
                            tint = BrandPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save current selection as preset", fontSize = 12.sp, color = BrandPrimary)
                    }
                }

                // App List Section. Bound it to the remaining dialog height so
                // the picker controls stay visible and only the app list scrolls.
                when {
                    loading -> Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = BrandPrimary, modifier = Modifier.size(30.dp))
                    }
                    loadError != null -> Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(loadError.orEmpty(), fontSize = 12.sp, color = DarkTextSecondary)
                    }
                    filteredApps.isEmpty() -> Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Outlined.Apps, contentDescription = null, tint = DarkTextMuted, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No matching apps found", fontSize = 14.sp, color = DarkTextSecondary)
                    }
                    else -> LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            AppPickerRow(
                                app = app,
                                checked = selected.contains(app.packageName),
                                onToggle = { clickedApp ->
                                    val sensitive = sensitiveApps[clickedApp.packageName]
                                    if (selected.contains(clickedApp.packageName) && sensitive != null) {
                                        warning = clickedApp.packageName to sensitive
                                    } else {
                                        selected = selected.toggle(clickedApp.packageName)
                                    }
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
            }
        }
    }

    warning?.let { (packageName, sensitive) ->
        AlertDialog(
            onDismissRequest = { warning = null },
            shape = RoundedCornerShape(24.dp),
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("Block ${sensitive.reason}?") },
            text = {
                Text(
                    "Blocking this system app can prevent incoming phone calls, verification messages, or alarms. Are you sure you want to block it?",
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        selected = selected - packageName
                        warning = null
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                ) {
                    Text("Block anyway", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { warning = null },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                ) {
                    Text("Keep allowed", color = DarkTextSecondary)
                }
            },
        )
    }

    deletePreset?.let { preset ->
        AlertDialog(
            onDismissRequest = { deletePreset = null },
            shape = RoundedCornerShape(24.dp),
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("Delete preset?") },
            text = { Text("Delete preset “${preset.name}”?", fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeletePreset(preset.id)
                        deletePreset = null
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deletePreset = null },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                ) {
                    Text("Cancel", color = DarkTextSecondary)
                }
            },
        )
    }
}

@Composable
private fun AppPickerRow(
    app: InstalledAppInfo,
    checked: Boolean,
    onToggle: (InstalledAppInfo) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DarkCard)
            .border(
                1.dp,
                if (checked) BrandPrimary.copy(alpha = 0.6f) else DarkBorder,
                RoundedCornerShape(10.dp),
            )
            .clickable { onToggle(app) }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppIcon(app.icon, size = 32.dp)

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        app.appName,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DarkTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    sensitiveApps[app.packageName]?.let {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF451A03))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        ) {
                            Text(
                                "System",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFBBF24),
                            )
                        }
                    }
                }
                Text(
                    app.packageName,
                    fontSize = 10.5.sp,
                    color = DarkTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (checked) BrandPrimary else Color.Transparent)
                    .border(1.5.dp, if (checked) BrandPrimary else DarkBorder, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = "Allowed",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun AppIcon(drawable: Drawable?, size: Dp = 36.dp) {
    val bitmap = remember(drawable) {
        runCatching { drawable?.toBitmap()?.asImageBitmap() }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.size(size),
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Apps,
                contentDescription = null,
                tint = DarkTextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun Set<String>.toggle(value: String): Set<String> =
    if (value in this) this - value else this + value
