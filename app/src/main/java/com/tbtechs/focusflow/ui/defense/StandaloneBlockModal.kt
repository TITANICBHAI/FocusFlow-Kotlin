package com.tbtechs.focusflow.ui.defense

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.tbtechs.focusflow.data.model.DailyAllowanceEntry
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Calendar

data class BlockPresetUi(
    val id: String,
    val name: String,
    val packages: List<String>,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StandaloneBlockModal(
    visible: Boolean,
    blockedPackages: List<String>,
    blockUntilMs: Long,
    locked: Boolean,
    dailyAllowanceEntries: List<DailyAllowanceEntry> = emptyList(),
    vpnPackages: List<String> = emptyList(),
    presets: List<BlockPresetUi> = emptyList(),
    onSave: (List<String>, Long?, List<DailyAllowanceEntry>, List<String>, String?) -> Unit,
    onSavePreset: ((BlockPresetUi) -> Unit)? = null,
    onDeletePreset: ((String) -> Unit)? = null,
    onClose: () -> Unit,
    verifyPin: ((String) -> Boolean)? = null,
    sessionPinSet: Boolean = false,
    hashPin: ((String) -> String)? = null,
) {
    if (!visible) return
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var apps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
    var selected by remember(visible, blockedPackages) {
        mutableStateOf(
            blockedPackages
                .filterNot { isNeverBlockPackage(it, context.packageName) }
                .toSet(),
        )
    }
    var allowances by remember(visible, dailyAllowanceEntries) {
        mutableStateOf(dailyAllowanceEntries.associateBy { it.packageName })
    }
    var vpn by remember(visible, vpnPackages) { mutableStateOf(vpnPackages.toSet()) }
    var search by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf("") }
    var advanced by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }
    var showPresetForm by remember { mutableStateOf(false) }
    var until by remember(visible, blockUntilMs) {
        mutableLongStateOf(blockUntilMs.takeIf { it > System.currentTimeMillis() } ?: defaultExpiry())
    }
    var confirmClear by remember { mutableStateOf(false) }
    var pinPrompt by remember { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf(false) }
    var clearPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(visible) {
        apps = withContext(Dispatchers.IO) {
            runCatching {
                InstalledAppsRepository(context).getInstalledApps()
                    .filterNot { isNeverBlockPackage(it.packageName, context.packageName) }
                    .sortedBy { it.appName.lowercase() }
            }.getOrDefault(emptyList())
        }
    }

    val results = apps.filter {
        search.isBlank() ||
            it.appName.contains(search, true) ||
            it.packageName.contains(search, true)
    }
    val installedPackages = apps.mapTo(mutableSetOf()) { it.packageName }
    val manualPackages = selected.filter { it !in installedPackages }.toList()

    fun chooseDate() {
        val calendar = Calendar.getInstance().apply { timeInMillis = until }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val next = Calendar.getInstance().apply {
                    timeInMillis = until
                    set(year, month, day)
                }
                until = next.timeInMillis
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    fun chooseTime() {
        val calendar = Calendar.getInstance().apply { timeInMillis = until }
        TimePickerDialog(
            context,
            { _, hour, minute ->
                val next = Calendar.getInstance().apply {
                    timeInMillis = until
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                }
                until = next.timeInMillis
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            false,
        ).show()
    }

    fun commitSave(pinHash: String?) {
        onSave(
            selected.toList(),
            until,
            allowances.values.toList(),
            vpn.toList(),
            pinHash,
        )
        onClose()
    }

    fun save() {
        when {
            selected.isEmpty() -> errorMessage = "Select at least one app to block."
            until <= System.currentTimeMillis() -> errorMessage = "Choose an expiry time in the future."
            locked && sessionPinSet && verifyPin != null -> {
                pendingSave = true
                clearPin = ""
                pinPrompt = true
            }
            else -> commitSave(null)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        containerColor = DarkBackground,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onClose) {
                    Text("Cancel", color = DarkTextSecondary, fontSize = 15.sp)
                }
                Text(
                    text = if (locked) "🔒 Block Active" else "Block Schedule",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkTextPrimary,
                )
                Button(
                    onClick = ::save,
                    enabled = selected.isNotEmpty() && until > System.currentTimeMillis(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandPrimary,
                        contentColor = Color.White,
                        disabledContainerColor = DarkSurfaceVariant,
                        disabledContentColor = DarkTextMuted,
                    ),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Save", fontWeight = FontWeight.SemiBold)
                }
            }

            HorizontalDivider(color = DarkBorder)

            // Locked Warning Banner
            if (locked) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF59E0B).copy(alpha = 0.12f))
                        .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = "The expiry and existing blocked apps are locked. You can still add apps and extend the block.",
                            fontSize = 13.sp,
                            color = Color(0xFFFDE68A),
                            lineHeight = 18.sp,
                        )
                    }
                }
            }

            errorMessage?.let { msg ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFEF4444).copy(alpha = 0.15f))
                        .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                ) {
                    Text(msg, color = Color(0xFFFCA5A5), fontSize = 13.sp)
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Expiry & Quick Extension Controls
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = if (locked) "BLOCK EXPIRES AT" else "BLOCK EXPIRY",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkTextMuted,
                            letterSpacing = 0.8.sp,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedButton(
                                onClick = ::chooseDate,
                                enabled = !locked,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = DarkCard,
                                    contentColor = DarkTextPrimary,
                                    disabledContainerColor = DarkCard.copy(alpha = 0.5f),
                                    disabledContentColor = DarkTextSecondary,
                                ),
                                border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder)),
                            ) {
                                Icon(
                                    Icons.Outlined.CalendarToday,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (!locked) BrandPrimary else DarkTextMuted,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(until),
                                    fontSize = 14.sp,
                                )
                            }

                            OutlinedButton(
                                onClick = ::chooseTime,
                                enabled = !locked,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = DarkCard,
                                    contentColor = DarkTextPrimary,
                                    disabledContainerColor = DarkCard.copy(alpha = 0.5f),
                                    disabledContentColor = DarkTextSecondary,
                                ),
                                border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder)),
                            ) {
                                Icon(
                                    Icons.Outlined.Timer,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (!locked) BrandPrimary else DarkTextMuted,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    DateFormat.getTimeInstance(DateFormat.SHORT).format(until),
                                    fontSize = 14.sp,
                                )
                            }
                        }

                        if (locked) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    "Add time:",
                                    fontSize = 13.sp,
                                    color = DarkTextSecondary,
                                    fontWeight = FontWeight.Medium,
                                )
                                listOf(30L, 60L, 120L, 240L).forEach { minutes ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(DarkSurfaceVariant)
                                            .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                                            .clickable { until += minutes * 60_000L }
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                    ) {
                                        Text(
                                            "+${if (minutes >= 60) "${minutes / 60}h" else "${minutes}m"}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = BrandPrimary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Presets Section
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "PRESETS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkTextMuted,
                            letterSpacing = 0.8.sp,
                        )

                        if (presets.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                presets.forEach { preset ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(DarkCard)
                                            .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                                            .clickable {
                                                val safePackages = preset.packages.filterNot {
                                                    isNeverBlockPackage(it, context.packageName)
                                                }
                                                selected = if (locked) selected + safePackages else safePackages.toSet()
                                            }
                                            .padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                "${preset.name} (${preset.packages.size})",
                                                fontSize = 13.sp,
                                                color = DarkTextPrimary,
                                            )
                                            IconButton(
                                                onClick = { onDeletePreset?.invoke(preset.id) },
                                                modifier = Modifier.size(28.dp),
                                            ) {
                                                Icon(
                                                    Icons.Outlined.Delete,
                                                    contentDescription = "Delete preset",
                                                    tint = DarkTextMuted,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (selected.isNotEmpty() && !showPresetForm) {
                            TextButton(
                                onClick = { showPresetForm = true },
                                modifier = Modifier.padding(top = 2.dp),
                            ) {
                                Icon(Icons.Outlined.Add, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("+ Save current selection as preset", color = BrandPrimary, fontSize = 14.sp)
                            }
                        }

                        if (showPresetForm) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(DarkCard)
                                    .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OutlinedTextField(
                                    value = presetName,
                                    onValueChange = { presetName = it },
                                    placeholder = { Text("Preset name", color = DarkTextMuted) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = DarkTextPrimary,
                                        unfocusedTextColor = DarkTextPrimary,
                                        focusedBorderColor = BrandPrimary,
                                        unfocusedBorderColor = DarkBorder,
                                    ),
                                )
                                Button(
                                    onClick = {
                                        if (presetName.isNotBlank()) {
                                            onSavePreset?.invoke(
                                                BlockPresetUi(
                                                    System.currentTimeMillis().toString(),
                                                    presetName.trim(),
                                                    selected.toList(),
                                                ),
                                            )
                                            presetName = ""
                                            showPresetForm = false
                                        }
                                    },
                                    enabled = presetName.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Text("Save")
                                }
                                IconButton(onClick = { showPresetForm = false }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Cancel", tint = DarkTextMuted)
                                }
                            }
                        }
                    }
                }

                // Advanced Package Input
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { advanced = !advanced },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = DarkCard,
                                contentColor = DarkTextPrimary,
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(DarkBorder)),
                        ) {
                            Icon(Icons.Outlined.Settings, contentDescription = null, tint = DarkTextSecondary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (advanced) "Hide Advanced" else "Advanced — Add by Package Name",
                                fontSize = 14.sp,
                            )
                        }

                        if (advanced) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OutlinedTextField(
                                    value = manual,
                                    onValueChange = { manual = it },
                                    placeholder = { Text("com.example.app", color = DarkTextMuted) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = DarkTextPrimary,
                                        unfocusedTextColor = DarkTextPrimary,
                                        focusedBorderColor = BrandPrimary,
                                        unfocusedBorderColor = DarkBorder,
                                    ),
                                )
                                Button(
                                    onClick = {
                                        val pkg = manual.trim().lowercase()
                                        if (pkg.contains('.') && isNeverBlockPackage(pkg, context.packageName)) {
                                            errorMessage = "This system app is protected and cannot be blocked."
                                        } else if (pkg.contains('.')) {
                                            selected = selected + pkg
                                            manual = ""
                                            errorMessage = null
                                        }
                                    },
                                    enabled = manual.contains('.'),
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Text("Add")
                                }
                            }
                        }
                    }
                }

                // Search & Counter
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        OutlinedTextField(
                            value = search,
                            onValueChange = { search = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search installed apps", color = DarkTextMuted) },
                            leadingIcon = {
                                Icon(Icons.Outlined.Search, contentDescription = null, tint = DarkTextSecondary)
                            },
                            trailingIcon = {
                                if (search.isNotBlank()) {
                                    IconButton(onClick = { search = "" }) {
                                        Icon(Icons.Outlined.Close, contentDescription = "Clear search", tint = DarkTextMuted)
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DarkCard,
                                unfocusedContainerColor = DarkCard,
                                focusedTextColor = DarkTextPrimary,
                                unfocusedTextColor = DarkTextPrimary,
                                focusedBorderColor = BrandPrimary,
                                unfocusedBorderColor = DarkBorder,
                            ),
                        )
                        Text(
                            text = "${selected.size} app${if (selected.size == 1) "" else "s"} will be blocked",
                            fontSize = 13.sp,
                            color = DarkTextSecondary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                // Manual packages if present
                if (manualPackages.isNotEmpty()) {
                    items(manualPackages) { pkg ->
                        AppSelectionRow(
                            packageName = pkg,
                            name = "Manual Entry",
                            selected = pkg in selected,
                            locked = locked,
                            onClick = { selected = selected.toggle(pkg, locked) },
                        )
                    }
                }

                // Installed Apps List
                items(results, key = { it.packageName }) { app ->
                    if (app.packageName !in manualPackages) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkCard)
                                .border(1.dp, if (app.packageName in selected) BrandPrimary.copy(alpha = 0.4f) else DarkBorder, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                        ) {
                            AppSelectionRow(
                                packageName = app.packageName,
                                name = app.appName,
                                selected = app.packageName in selected,
                                locked = locked,
                                onClick = { selected = selected.toggle(app.packageName, locked) },
                            )
                            if (app.packageName in selected) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    color = DarkBorder,
                                )
                                AllowanceRow(
                                    app = app,
                                    entry = allowances[app.packageName],
                                    locked = locked,
                                    onToggle = {
                                        allowances = if (app.packageName in allowances) {
                                            allowances - app.packageName
                                        } else {
                                            allowances + (app.packageName to DailyAllowanceEntry(app.packageName, 30L * 60_000L))
                                        }
                                    },
                                    onEntry = { entry ->
                                        allowances = allowances + (app.packageName to entry)
                                    },
                                    vpnEnabled = app.packageName in vpn,
                                    onVpnToggle = { vpn = vpn.toggle(app.packageName, locked) },
                                )
                            }
                        }
                    }
                }

                // Clear Block Option (when not locked)
                item {
                    if (selected.isNotEmpty() && !locked) {
                        TextButton(
                            onClick = { confirmClear = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = null, tint = Color(0xFFEF4444))
                            Spacer(Modifier.width(8.dp))
                            Text("Clear Block", color = Color(0xFFEF4444), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("Clear Block", fontWeight = FontWeight.Bold) },
            text = { Text("This disables the standalone block and allows all apps again.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmClear = false
                        if (verifyPin != null && sessionPinSet) {
                            pinPrompt = true
                        } else {
                            onSave(emptyList(), null, allowances.values.toList(), emptyList(), null)
                            onClose()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text("Cancel", color = DarkTextSecondary)
                }
            },
        )
    }

    if (pinPrompt) {
        AlertDialog(
            onDismissRequest = {
                pinPrompt = false
                clearPin = ""
            },
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("Session Password Required", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = clearPin,
                    onValueChange = { clearPin = it },
                    label = { Text("Password", color = DarkTextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DarkTextPrimary,
                        unfocusedTextColor = DarkTextPrimary,
                        focusedBorderColor = BrandPrimary,
                        unfocusedBorderColor = DarkBorder,
                    ),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (verifyPin?.invoke(clearPin) == true) {
                            val hash = hashPin?.invoke(clearPin)
                            pinPrompt = false
                            if (pendingSave) {
                                pendingSave = false
                                commitSave(hash)
                            } else {
                                onSave(emptyList(), null, allowances.values.toList(), emptyList(), hash)
                                onClose()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { pinPrompt = false }) {
                    Text("Cancel", color = DarkTextSecondary)
                }
            },
        )
    }
}

@Composable
private fun AppSelectionRow(
    packageName: String,
    name: String,
    selected: Boolean,
    locked: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) Color(0xFFEF4444).copy(alpha = 0.15f) else DarkSurfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = name.firstOrNull()?.uppercase() ?: "A",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) Color(0xFFF87171) else DarkTextSecondary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkTextPrimary,
                    maxLines = 1,
                )
                Text(
                    text = packageName,
                    fontSize = 12.sp,
                    color = DarkTextMuted,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (selected) Color(0xFFEF4444).copy(alpha = 0.2f)
                    else DarkSurfaceVariant,
                )
                .border(
                    1.dp,
                    if (selected) Color(0xFFEF4444) else DarkBorder,
                    RoundedCornerShape(8.dp),
                )
                .clickable(enabled = !(locked && selected), onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text(
                text = if (selected) "Blocked" else "Block",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) Color(0xFFF87171) else DarkTextSecondary,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AllowanceRow(
    app: InstalledAppInfo,
    entry: DailyAllowanceEntry?,
    locked: Boolean,
    onToggle: () -> Unit,
    onEntry: (DailyAllowanceEntry) -> Unit,
    vpnEnabled: Boolean,
    onVpnToggle: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurfaceVariant.copy(alpha = 0.5f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Daily Allowance",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = DarkTextPrimary,
                )
                Text(
                    text = if (entry == null) {
                        "None (full block)"
                    } else {
                        when (entry.mode) {
                            "count" -> "${entry.countPerDay} launches/day"
                            "interval" -> "Every ${entry.intervalMinutes}m"
                            else -> "${entry.budgetMinutes} min/day"
                        }
                    },
                    fontSize = 12.sp,
                    color = DarkTextSecondary,
                )
            }
            Switch(
                checked = entry != null,
                onCheckedChange = { onToggle() },
                enabled = !locked,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = BrandPrimary,
                    uncheckedThumbColor = DarkTextMuted,
                    uncheckedTrackColor = DarkCard,
                    uncheckedBorderColor = DarkBorder,
                ),
            )
        }

        if (entry != null && !locked) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("count" to "Count", "time_budget" to "Time", "interval" to "Interval")
                    .forEach { (mode, label) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (entry.mode == mode) BrandPrimary else DarkCard)
                                .border(1.dp, if (entry.mode == mode) BrandPrimary else DarkBorder, RoundedCornerShape(6.dp))
                                .clickable { onEntry(entry.copy(mode = mode)) }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (entry.mode == mode) Color.White else DarkTextSecondary,
                            )
                        }
                    }

                when (entry.mode) {
                    "count" -> listOf(1, 3, 5, 10).forEach { count ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (entry.countPerDay == count) BrandPrimary else DarkCard)
                                .border(1.dp, if (entry.countPerDay == count) BrandPrimary else DarkBorder, RoundedCornerShape(6.dp))
                                .clickable {
                                    onEntry(
                                        entry.copy(
                                            mode = "count",
                                            countPerDay = count,
                                            dailyAllowanceMs = 0L,
                                        ),
                                    )
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                "$count/day",
                                fontSize = 11.sp,
                                color = if (entry.countPerDay == count) Color.White else DarkTextSecondary,
                            )
                        }
                    }
                    "interval" -> listOf(5, 15, 30, 60).forEach { minutes ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (entry.intervalMinutes == minutes) BrandPrimary else DarkCard)
                                .border(1.dp, if (entry.intervalMinutes == minutes) BrandPrimary else DarkBorder, RoundedCornerShape(6.dp))
                                .clickable {
                                    onEntry(
                                        entry.copy(
                                            mode = "interval",
                                            intervalMinutes = minutes,
                                            dailyAllowanceMs = minutes * 60_000L,
                                        ),
                                    )
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                "${minutes}m",
                                fontSize = 11.sp,
                                color = if (entry.intervalMinutes == minutes) Color.White else DarkTextSecondary,
                            )
                        }
                    }
                    else -> listOf(5L, 15L, 30L, 60L).forEach { minutes ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (entry.budgetMinutes == minutes.toInt()) BrandPrimary else DarkCard)
                                .border(1.dp, if (entry.budgetMinutes == minutes.toInt()) BrandPrimary else DarkBorder, RoundedCornerShape(6.dp))
                                .clickable {
                                    onEntry(
                                        entry.copy(
                                            mode = "time_budget",
                                            budgetMinutes = minutes.toInt(),
                                            dailyAllowanceMs = minutes * 60_000L,
                                        ),
                                    )
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                "${minutes}m",
                                fontSize = 11.sp,
                                color = if (entry.budgetMinutes == minutes.toInt()) Color.White else DarkTextSecondary,
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = if (vpnEnabled) BrandPrimary else DarkTextMuted,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (vpnEnabled) "Network block: on" else "Add network block (VPN)",
                    fontSize = 13.sp,
                    color = DarkTextSecondary,
                )
            }
            Switch(
                checked = vpnEnabled,
                onCheckedChange = { onVpnToggle() },
                enabled = !locked,
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

private fun Set<String>.toggle(value: String, locked: Boolean): Set<String> {
    if (locked && value in this) return this
    return if (value in this) this - value else this + value
}

private fun defaultExpiry(): Long =
    Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis

private fun isNeverBlockPackage(packageName: String, ownPackageName: String): Boolean =
    packageName.equals(ownPackageName, ignoreCase = true) ||
        packageName.equals("android", ignoreCase = true) ||
        packageName.equals("com.android.systemui", ignoreCase = true) ||
        packageName.equals("com.android.phone", ignoreCase = true) ||
        packageName.equals("com.android.server.telecom", ignoreCase = true) ||
        packageName.equals("com.google.android.dialer", ignoreCase = true) ||
        packageName.equals("com.google.android.gms", ignoreCase = true) ||
        packageName.equals("com.google.android.gsf", ignoreCase = true) ||
        packageName.contains("packageinstaller", ignoreCase = true) ||
        packageName.contains("permissioncontroller", ignoreCase = true) ||
        packageName.contains("launcher", ignoreCase = true)
