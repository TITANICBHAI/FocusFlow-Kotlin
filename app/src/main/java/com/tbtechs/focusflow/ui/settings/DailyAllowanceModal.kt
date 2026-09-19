package com.tbtechs.focusflow.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.data.model.BLOCK_ALL_SENTINEL
import com.tbtechs.focusflow.data.model.DailyAllowanceEntry
import com.tbtechs.focusflow.data.repository.AllowanceUsage
import com.tbtechs.focusflow.data.repository.InstalledAppInfo
import com.tbtechs.focusflow.data.repository.InstalledAppsRepository
import com.tbtechs.focusflow.ui.launcher.AppIcon
import com.tbtechs.focusflow.ui.launcher.AppPickerSheet
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkSurfaceVariant
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import org.json.JSONArray
import java.time.LocalDate

private val SunAmber = Color(0xFFF59E0B)
private val SunAmberBg = Color(0xFF451A03)

/**
 * Per-app daily allowance editor.
 *
 * Implements screenshots 3e_4 and 3e_5 with full dark theme styling,
 * expandable mode configurations, and custom steppers.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DailyAllowanceModal(
    visible: Boolean,
    selectedEntries: List<DailyAllowanceEntry>,
    locked: Boolean = false,
    requireDefensePin: Boolean = false,
    onSave: (List<DailyAllowanceEntry>) -> Unit,
    onVerifyDefensePin: (String) -> Boolean,
    onClose: () -> Unit,
    usageByPackage: Map<String, AllowanceUsage> = emptyMap(),
) {
    if (!visible) return

    val initialDrafts = remember(selectedEntries) {
        selectedEntries.map { entry ->
            DailyAllowanceDraft(
                packageName = entry.packageName,
                mode = when (entry.mode) {
                    "count" -> AllowanceMode.Count
                    "interval" -> AllowanceMode.Interval
                    "time_budget" -> AllowanceMode.TimeBudget
                    else -> AllowanceMode.Count
                },
                countPerDay = entry.countPerDay,
                budgetMinutes = entry.budgetMinutes.coerceAtLeast(1),
                intervalMinutes = entry.intervalMinutes.coerceAtLeast(1),
                intervalHours = entry.intervalHours.coerceAtLeast(1),
            )
        }
    }
    var drafts by remember(selectedEntries) { mutableStateOf(initialDrafts) }
    val originalPackages = remember(selectedEntries) { selectedEntries.mapTo(mutableSetOf()) { it.packageName } }
    var expandedPackage by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var packageDraft by remember { mutableStateOf("") }
    var pickerVisible by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<AllowanceMessage?>(null) }
    var pendingRemoval by remember { mutableStateOf<PendingAllowanceRemoval?>(null) }
    var pin by remember { mutableStateOf("") }
    var installedApps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
    var appsLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        appsLoading = true
        installedApps = runCatching {
            InstalledAppsRepository(context).getInstalledApps()
                .sortedBy { it.appName.lowercase() }
        }.getOrDefault(emptyList())
        appsLoading = false
    }

    fun updateDraft(packageName: String, transform: (DailyAllowanceDraft) -> DailyAllowanceDraft) {
        drafts = drafts.map { draft -> if (draft.packageName == packageName) transform(draft) else draft }
    }

    fun remove(packageName: String) {
        drafts = drafts.filterNot { it.packageName == packageName }
        if (expandedPackage == packageName) expandedPackage = null
    }

    fun requestRemoval(packageName: String) {
        if (locked && packageName in originalPackages) {
            message = AllowanceMessage("Allowances locked", "A block is active, so allowances cannot be removed until it expires.")
        } else if (requireDefensePin) {
            pendingRemoval = PendingAllowanceRemoval.Package(packageName)
            pin = ""
        } else {
            remove(packageName)
        }
    }

    fun requestClear() {
        if (locked) {
            drafts = drafts.filter { it.packageName in originalPackages }
            expandedPackage = null
        } else if (requireDefensePin) {
            pendingRemoval = PendingAllowanceRemoval.All
            pin = ""
        } else {
            drafts = emptyList()
            expandedPackage = null
        }
    }

    fun addPackage() {
        val packageName = packageDraft.trim()
        when {
            packageName.isEmpty() -> Unit
            !PACKAGE_NAME.matches(packageName) -> {
                message = AllowanceMessage("Enter a package name", "Use an Android package name such as com.example.app.")
            }
            drafts.any { it.packageName == packageName } -> {
                expandedPackage = packageName
                packageDraft = ""
            }
            else -> {
                drafts = drafts + DailyAllowanceDraft(packageName = packageName)
                expandedPackage = packageName
                packageDraft = ""
            }
        }
    }

    fun save() {
        onSave(
            drafts.map {
                DailyAllowanceEntry(
                    packageName = it.packageName,
                    dailyAllowanceMs = when (it.mode) {
                        AllowanceMode.TimeBudget -> it.budgetMinutes.coerceAtLeast(1).toLong() * MINUTE_MS
                        AllowanceMode.Interval -> it.intervalMinutes.coerceAtLeast(1).toLong() * MINUTE_MS
                        AllowanceMode.Count -> 0L
                    },
                    mode = when (it.mode) {
                        AllowanceMode.Count -> "count"
                        AllowanceMode.Interval -> "interval"
                        AllowanceMode.TimeBudget -> "time_budget"
                    },
                    countPerDay = it.countPerDay.coerceAtLeast(1),
                    budgetMinutes = it.budgetMinutes.coerceAtLeast(1),
                    intervalMinutes = it.intervalMinutes.coerceAtLeast(1),
                    intervalHours = it.intervalHours.coerceAtLeast(1),
                )
            },
        )
        onClose()
    }

    val shownApps = remember(installedApps, drafts, search) {
        val query = search.trim().lowercase()
        val manualApps = drafts
            .filter { draft -> installedApps.none { it.packageName == draft.packageName } }
            .map {
                InstalledAppInfo(
                    packageName = it.packageName,
                    appName = it.packageName,
                    isIme = false,
                    icon = null,
                )
            }
        (installedApps + manualApps)
            .distinctBy { it.packageName }
            .filter {
                query.isEmpty() ||
                    it.appName.lowercase().contains(query) ||
                    it.packageName.lowercase().contains(query)
            }
    }

    fun entryIsLocked(packageName: String): Boolean =
        locked && packageName in originalPackages

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Outlined.WbSunny,
                            contentDescription = null,
                            tint = SunAmber,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = "Daily Allowance",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkTextPrimary,
                        )
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onClose) {
                        Text("Cancel", color = DarkTextSecondary, fontSize = 14.sp)
                    }
                },
                actions = {
                    TextButton(onClick = ::save) {
                        Text(
                            text = "Save",
                            color = BrandPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Spacer(modifier = Modifier.height(2.dp))
            }

            if (locked) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(SunAmberBg)
                            .border(1.dp, SunAmber.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                            .padding(14.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(Icons.Outlined.Lock, contentDescription = null, tint = SunAmber, modifier = Modifier.size(18.dp))
                            Text(
                                "Block is active — existing allowances are locked. You can add apps, but cannot remove allowances until the block expires.",
                                color = SunAmber,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                            )
                        }
                    }
                }
            }

            // Explanatory Info Card matching 3e_4
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(DarkCard)
                        .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(SunAmber.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.WbSunny, contentDescription = null, tint = SunAmber, modifier = Modifier.size(20.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Allowances give you controlled access to select apps every day without turning off entire blocks.",
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = DarkTextPrimary,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Tap an app to enable its allowance. Tap again to configure mode and limits.",
                                fontSize = 12.sp,
                                color = DarkTextSecondary,
                            )
                        }
                    }
                }
            }

            // Search Box
            item {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Search installed apps…", color = DarkTextMuted, fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(Icons.Outlined.Search, contentDescription = null, tint = DarkTextSecondary, modifier = Modifier.size(20.dp))
                    },
                    trailingIcon = {
                        if (search.isNotBlank()) {
                            IconButton(onClick = { search = "" }) {
                                Icon(Icons.Outlined.Clear, contentDescription = "Clear", tint = DarkTextSecondary, modifier = Modifier.size(18.dp))
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
            }

            // Add manual package row & choose apps button
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = packageDraft,
                            onValueChange = { packageDraft = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Add by package (e.g. com.app)", color = DarkTextMuted, fontSize = 13.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DarkCard,
                                unfocusedContainerColor = DarkCard,
                                focusedBorderColor = BrandPrimary,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = DarkTextPrimary,
                                unfocusedTextColor = DarkTextPrimary,
                            ),
                        )
                        Button(
                            onClick = ::addPackage,
                            enabled = packageDraft.trim().isNotEmpty(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                        ) {
                            Text("Add", color = Color.White)
                        }
                    }

                    OutlinedButton(
                        onClick = { pickerVisible = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DarkTextPrimary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    ) {
                        Icon(Icons.Outlined.Apps, contentDescription = null, modifier = Modifier.size(18.dp), tint = BrandPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Choose from all installed apps", fontSize = 13.sp)
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (drafts.isEmpty()) "No allowances configured"
                        else "${drafts.size} app${if (drafts.size == 1) "" else "s"} with daily allowance",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = DarkTextSecondary,
                    )
                }
            }

            if (appsLoading) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(color = BrandPrimary, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Loading apps…", color = DarkTextSecondary, fontSize = 13.sp)
                    }
                }
            }

            if (!appsLoading && shownApps.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            if (search.isBlank()) "No apps found" else "No apps match \"$search\"",
                            color = DarkTextSecondary,
                            fontSize = 14.sp,
                        )
                    }
                }
            }

            items(shownApps, key = { app -> app.packageName }) { app ->
                val draft = drafts.firstOrNull { it.packageName == app.packageName }
                val isActive = draft != null
                val isExpanded = expandedPackage == app.packageName
                val isEntryLocked = locked && app.packageName in originalPackages

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(DarkCard)
                        .border(
                            1.dp,
                            if (isActive) SunAmber.copy(alpha = 0.4f) else DarkBorder,
                            RoundedCornerShape(14.dp),
                        ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (draft == null) {
                                        drafts = drafts + DailyAllowanceDraft(
                                            packageName = app.packageName,
                                            mode = AllowanceMode.Count,
                                        )
                                        expandedPackage = app.packageName
                                    } else {
                                        expandedPackage = if (isExpanded) null else app.packageName
                                    }
                                },
                                onLongClick = { if (isActive) requestRemoval(app.packageName) },
                            )
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
                            if (draft != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.WbSunny,
                                        contentDescription = null,
                                        tint = SunAmber,
                                        modifier = Modifier.size(13.dp),
                                    )
                                    Text(
                                        text = draft.summary(),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = SunAmber,
                                    )
                                }
                            }
                        }

                        if (isActive) {
                            IconButton(onClick = { expandedPackage = if (isExpanded) null else app.packageName }) {
                                Icon(
                                    if (isExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                                    tint = DarkTextSecondary,
                                )
                            }
                            if (!isEntryLocked) {
                                IconButton(onClick = { requestRemoval(app.packageName) }) {
                                    Icon(
                                        Icons.Outlined.Clear,
                                        contentDescription = "Remove",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        } else {
                            Button(
                                onClick = {
                                    drafts = drafts + DailyAllowanceDraft(
                                        packageName = app.packageName,
                                        mode = AllowanceMode.Count,
                                    )
                                    expandedPackage = app.packageName
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add", fontSize = 12.sp, color = Color.White)
                            }
                        }
                    }

                    // Expanded Settings Area matching screenshot 3e_5
                    if (isExpanded && draft != null) {
                        HorizontalDivider(color = DarkBorder)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkSurfaceVariant.copy(alpha = 0.5f))
                                .padding(16.dp),
                        ) {
                            AllowanceConfiguration(
                                draft = draft,
                                locked = isEntryLocked,
                                usage = usageByPackage[draft.packageName],
                                onUpdate = { updated -> updateDraft(draft.packageName) { updated } },
                            )
                        }
                    }
                }
            }

            if (drafts.any { !entryIsLocked(it.packageName) }) {
                item {
                    OutlinedButton(
                        onClick = ::requestClear,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                    ) {
                        Text(
                            if (locked) "Clear new allowances" else "Clear all daily allowances",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    pendingRemoval?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null; pin = "" },
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text("Defense password required") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (pending is PendingAllowanceRemoval.All) {
                            "Enter your defense password to remove all apps from the daily allowance list."
                        } else {
                            "Enter your defense password to remove this app from the daily allowance list."
                        },
                        fontSize = 13.sp,
                    )
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it },
                        label = { Text("Defense password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
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
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!onVerifyDefensePin(pin)) {
                            message = AllowanceMessage("Incorrect password", "The defense password did not match.")
                        } else {
                            when (pending) {
                                is PendingAllowanceRemoval.Package -> remove(pending.packageName)
                                PendingAllowanceRemoval.All -> {
                                    drafts = emptyList()
                                    expandedPackage = null
                                }
                            }
                            pendingRemoval = null
                            pin = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                ) {
                    Text("Remove", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null; pin = "" }) {
                    Text("Cancel", color = DarkTextSecondary)
                }
            },
        )
    }

    message?.let { notice ->
        AlertDialog(
            onDismissRequest = { message = null },
            containerColor = DarkCard,
            titleContentColor = DarkTextPrimary,
            textContentColor = DarkTextSecondary,
            title = { Text(notice.title) },
            text = { Text(notice.body, fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = { message = null },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                ) {
                    Text("OK", color = Color.White)
                }
            },
        )
    }

    if (pickerVisible) {
        val repository = remember { InstalledAppsRepository(context) }
        AppPickerSheet(
            visible = true,
            title = "Choose allowance apps",
            initialSelected = drafts.map { it.packageName },
            noneWhenEmpty = true,
            presets = emptyList(),
            installedAppsRepository = repository,
            onSave = { selected ->
                val selectedPackages = selected
                    .filter { it.isNotBlank() && it != BLOCK_ALL_SENTINEL }
                    .toSet()
                drafts = drafts + selectedPackages
                    .filterNot { packageName -> drafts.any { it.packageName == packageName } }
                    .map { packageName -> DailyAllowanceDraft(packageName = packageName) }
                pickerVisible = false
            },
            onSavePreset = {},
            onDeletePreset = {},
            onClose = { pickerVisible = false },
        )
    }
}

@Composable
private fun AllowanceConfiguration(
    draft: DailyAllowanceDraft,
    locked: Boolean,
    usage: AllowanceUsage?,
    onUpdate: (DailyAllowanceDraft) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (locked) {
            Text(
                "Values locked while block is active",
                fontSize = 12.sp,
                color = SunAmber,
            )
        }
        Text(
            allowanceUsageLabel(draft, usage),
            fontSize = 12.sp,
            color = DarkTextSecondary,
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Allowance mode",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = DarkTextPrimary,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkCard)
                    .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AllowanceMode.values().forEach { mode ->
                    val isSelected = draft.mode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isSelected) BrandPrimary else Color.Transparent)
                            .combinedClickable(
                                enabled = !locked,
                                onClick = { onUpdate(draft.copy(mode = mode)) },
                            )
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = mode.label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.White else DarkTextSecondary,
                        )
                    }
                }
            }
        }

        when (draft.mode) {
            AllowanceMode.Count -> {
                StepperRow(
                    label = "Opens per day",
                    value = draft.countPerDay.toString(),
                    locked = locked,
                    onDecrease = { onUpdate(draft.copy(countPerDay = (draft.countPerDay - 1).coerceAtLeast(1))) },
                    onIncrease = { onUpdate(draft.copy(countPerDay = (draft.countPerDay + 1).coerceAtMost(20))) },
                )
            }
            AllowanceMode.TimeBudget -> {
                StepperRow(
                    label = "Total minutes per day",
                    value = "${draft.budgetMinutes} min",
                    locked = locked,
                    onDecrease = { onUpdate(draft.copy(budgetMinutes = (draft.budgetMinutes - 5).coerceAtLeast(1))) },
                    onIncrease = { onUpdate(draft.copy(budgetMinutes = (draft.budgetMinutes + 5).coerceAtMost(480))) },
                )
            }
            AllowanceMode.Interval -> {
                StepperRow(
                    label = "Minutes allowed per window",
                    value = "${draft.intervalMinutes} min",
                    locked = locked,
                    onDecrease = { onUpdate(draft.copy(intervalMinutes = (draft.intervalMinutes - 1).coerceAtLeast(1))) },
                    onIncrease = { onUpdate(draft.copy(intervalMinutes = (draft.intervalMinutes + 1).coerceAtMost(120))) },
                )
                StepperRow(
                    label = "Window size (hours)",
                    value = "${draft.intervalHours} hr",
                    locked = locked,
                    onDecrease = { onUpdate(draft.copy(intervalHours = (draft.intervalHours - 1).coerceAtLeast(1))) },
                    onIncrease = { onUpdate(draft.copy(intervalHours = (draft.intervalHours + 1).coerceAtMost(24))) },
                )
                Text(
                    "App is allowed for ${draft.intervalMinutes} min every ${draft.intervalHours} hour${if (draft.intervalHours == 1) "" else "s"}.",
                    fontSize = 12.sp,
                    color = DarkTextSecondary,
                )
            }
        }
    }
}

private fun allowanceUsageLabel(
    draft: DailyAllowanceDraft,
    usage: AllowanceUsage?,
): String {
    if (usage == null) return "No usage recorded in the current allowance window."

    val today = LocalDate.now().toString()
    return when (draft.mode) {
        AllowanceMode.Count -> {
            val count = if (usage.date == today) usage.count else 0
            "Used today: $count of ${draft.countPerDay} opens"
        }
        AllowanceMode.TimeBudget -> {
            val usedMs = if (usage.date == today) usage.usedMs else 0L
            "Used today: ${formatAllowanceMinutes(usedMs)} of ${draft.budgetMinutes} min"
        }
        AllowanceMode.Interval -> {
            val windowEnd = usage.windowStartMs +
                draft.intervalHours.coerceAtLeast(1).toLong() * 60L * MINUTE_MS
            val usedMs = if (usage.windowStartMs > 0L && System.currentTimeMillis() < windowEnd) {
                usage.usedMs
            } else {
                0L
            }
            "Used in current window: ${formatAllowanceMinutes(usedMs)} of ${draft.intervalMinutes} min"
        }
    }
}

private fun formatAllowanceMinutes(usedMs: Long): String {
    val minutes = (usedMs / MINUTE_MS).toInt()
    return if (minutes == 0 && usedMs > 0L) "<1 min" else "$minutes min"
}

@Composable
private fun StepperRow(
    label: String,
    value: String,
    locked: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkCard)
            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            color = DarkTextPrimary,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurfaceVariant)
                    .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                    .combinedClickable(enabled = !locked, onClick = onDecrease),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Remove, contentDescription = "Decrease", tint = if (locked) DarkTextMuted else DarkTextPrimary, modifier = Modifier.size(16.dp))
            }
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = DarkTextPrimary,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurfaceVariant)
                    .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                    .combinedClickable(enabled = !locked, onClick = onIncrease),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Increase", tint = if (locked) DarkTextMuted else DarkTextPrimary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

private enum class AllowanceMode(val label: String) {
    Count("Count"),
    TimeBudget("Time Budget"),
    Interval("Interval"),
}

private data class DailyAllowanceDraft(
    val packageName: String,
    val mode: AllowanceMode = AllowanceMode.Count,
    val countPerDay: Int = 1,
    val budgetMinutes: Int = 30,
    val intervalMinutes: Int = 5,
    val intervalHours: Int = 1,
) {
    fun summary(): String = when (mode) {
        AllowanceMode.Count -> "$countPerDay open${if (countPerDay == 1) "" else "s"}/day"
        AllowanceMode.TimeBudget -> "$budgetMinutes min/day"
        AllowanceMode.Interval -> "$intervalMinutes min every ${intervalHours}hr"
    }
}

private sealed interface PendingAllowanceRemoval {
    data class Package(val packageName: String) : PendingAllowanceRemoval
    object All : PendingAllowanceRemoval
}

private data class AllowanceMessage(val title: String, val body: String)

private const val MINUTE_MS = 60_000L
private val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")

internal fun dailyAllowanceEntriesFromJson(raw: String?): List<DailyAllowanceEntry> = runCatching {
    val values = JSONArray(raw ?: "[]")
    List(values.length()) { index ->
        val value = values.getJSONObject(index)
        DailyAllowanceEntry(
            packageName = value.getString("package"),
            dailyAllowanceMs = value.optLong("dailyAllowanceMs", 0L),
            mode = value.optString("mode", "count"),
            countPerDay = value.optInt("countPerDay", 1).coerceAtLeast(1),
            budgetMinutes = value.optInt("budgetMinutes", 30).coerceAtLeast(1),
            intervalMinutes = value.optInt("intervalMinutes", 5).coerceAtLeast(1),
            intervalHours = value.optInt("intervalHours", 1).coerceAtLeast(1),
        )
    }
}.getOrDefault(emptyList())
