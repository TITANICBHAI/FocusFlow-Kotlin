package com.tbtechs.focusflow.ui.support

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.ui.navigation.Routes
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.LocalFocusFlowDimensions
import com.tbtechs.focusflow.ui.theme.scaledSp

private data class SettingsGuideEntry(
    val title: String,
    val body: String,
    val destination: String? = null,
    val destinationLabel: String? = null,
)

private data class SettingsGuideSection(
    val title: String,
    val icon: ImageVector,
    val iconBackground: Color,
    val iconTint: Color,
    val entries: List<SettingsGuideEntry>,
)

private val SETTINGS_GUIDE_SECTIONS = listOf(
    SettingsGuideSection(
        title = "All Modes",
        icon = Icons.Outlined.Layers,
        iconBackground = Color(0xFF1E1B4B),
        iconTint = Color(0xFF818CF8),
        entries = listOf(
            SettingsGuideEntry(
                "Focus Mode",
                "Focus Mode is connected to a task. Start a task from the Focus tab, choose the apps allowed during that session, and start Focus Mode when you are ready to work.",
                Routes.FOCUS,
                "Open Focus",
            ),
            SettingsGuideEntry(
                "Standalone Block",
                "Standalone Block does not need a task. Open it from the Focus tab, choose the apps you want blocked, and set how long the block should last. It is the best choice when you want a timed block right now.",
                Routes.BLOCK_DEFENSE,
                "Set up a Standalone Block",
            ),
            SettingsGuideEntry(
                "Keyword Blocker",
                "Keyword Blocker watches visible text, searches, and URLs for words you add. When a blocked word appears, FocusFlow sends the current app home. It works independently of an app list or focus session.",
                Routes.KEYWORD_BLOCKER,
                "Open Keyword Blocker",
            ),
            SettingsGuideEntry(
                "VPN Network Protection",
                "VPN protection cuts internet access for the apps you select. Use the VPN list for always-on network blocking, or add VPN protection to a standalone block or group schedule when that block is running.",
                Routes.VPN_BLOCK_LIST,
                "Open VPN Block List",
            ),
            SettingsGuideEntry(
                "Group Schedules",
                "Group schedules are recurring block windows. Add several apps to one window, choose the days and times, and let the same group run automatically every week. A schedule can also include VPN protection.",
                Routes.DEFENSE,
                "Open Defense schedules",
            ),
            SettingsGuideEntry(
                "Home Launcher",
                "Home Launcher replaces your home screen with a focused launcher. Choose Classic or Glassy, select the apps shown in the drawer, and use launcher protections to make switching away harder during a standalone block.",
                Routes.HOME_LAUNCHER_SETUP,
                "Open Home Launcher",
            ),
        ),
    ),
    SettingsGuideSection(
        title = "Absolute Blocking",
        icon = Icons.Outlined.Security,
        iconBackground = Color(0xFF450A0A),
        iconTint = Color(0xFFF87171),
        entries = listOf(
            SettingsGuideEntry(
                "Start with Standalone Block",
                "Go to the Focus tab, choose Standalone Block, select every app you want blocked, and set the time. This works without creating a task and stays active until the timer ends.",
                Routes.BLOCK_DEFENSE,
                "Set up a Standalone Block",
            ),
            SettingsGuideEntry(
                "Grant the important permissions first",
                "Open Settings → Permissions and grant Accessibility and Usage Access so FocusFlow can detect and stop blocked apps. Grant Device Admin as an additional layer before starting a serious standalone block; it adds resistance to force-stop and uninstall escape paths, but it is not a magic guarantee by itself.",
                Routes.PERMISSIONS,
                "Open Permissions",
            ),
            SettingsGuideEntry(
                "Turn on Protect system controls",
                "In the Defense tab, enable Protect system controls before the block starts. This protects system screens and navigation paths that could otherwise be used to weaken an active block.",
                Routes.DEFENSE,
                "Open Defense",
            ),
            SettingsGuideEntry(
                "Add the extra layers you need",
                "From the Defense tab, enable Network Protection, launcher protections, aversion deterrents, Shorts/Reels blocking, or other safeguards. These layers work alongside the app block instead of replacing it.",
                Routes.DEFENSE,
                "Open Defense",
            ),
            SettingsGuideEntry(
                "Know what absolute means",
                "FocusFlow blocks the apps and escape routes you configured. Keep emergency, phone, launcher, and other protected system apps available, and do not treat any Android protection as a substitute for emergency access.",
            ),
        ),
    ),
    SettingsGuideSection(
        title = "What Can and Cannot Change",
        icon = Icons.Outlined.Lock,
        iconBackground = Color(0xFF451A03),
        iconTint = Color(0xFFFBBF24),
        entries = listOf(
            SettingsGuideEntry(
                "Always-On and VPN lists",
                "You can add more apps to the Always-On list or VPN list while protection is running. Removing apps from either list is locked during an active Focus Mode or Standalone Block so the block cannot be weakened halfway through.",
                Routes.ALWAYS_ON,
                "Open Always-On",
            ),
            SettingsGuideEntry(
                "Keyword Blocker",
                "You can add keywords without a password. Removing keywords or clearing the list is protected, and an active standalone block can lock those removals completely.",
                Routes.KEYWORD_BLOCKER,
                "Open Keyword Blocker",
            ),
            SettingsGuideEntry(
                "Group schedules",
                "You can add a new group schedule without a PIN. When Defense PIN protection is enabled, editing a schedule, removing apps, disabling a window or its VPN protection, shortening its duration, or deleting it can require the Defense PIN. An active Standalone Block can also prevent deleting a schedule.",
                Routes.DEFENSE,
                "Open Defense schedules",
            ),
            SettingsGuideEntry(
                "Why FocusFlow locks changes",
                "A protection tool is only useful if it cannot be quietly weakened after it starts. FocusFlow allows safer additions, but guards removals, shorter windows, disabled toggles, and other changes that reduce protection.",
            ),
        ),
    ),
    SettingsGuideSection(
        title = "PIN System",
        icon = Icons.Outlined.VpnKey,
        iconBackground = Color(0xFF2E1065),
        iconTint = Color(0xFFC084FC),
        entries = listOf(
            SettingsGuideEntry(
                "Focus Session PIN",
                "When configured, the Focus Session PIN is required to stop an active Focus Mode session early or turn off the full-duration rule. Without a PIN, stopping still requires the normal confirmation.",
                Routes.FOCUS,
                "Open Focus",
            ),
            SettingsGuideEntry(
                "Defense PIN",
                "The Defense PIN guards actions that weaken protection: disabling protected Defense toggles, removing apps from Always-On or VPN lists, removing keywords, and changing protected settings.",
                Routes.PASSWORD_PROTECTION,
                "Open PIN Protection",
            ),
            SettingsGuideEntry(
                "Group schedules are guarded more heavily",
                "You can add a new schedule without a PIN. When Defense PIN protection is enabled, editing, removing apps, disabling protection, shortening, or deleting a schedule can require the Defense PIN. An active Standalone Block can prevent deleting it.",
                Routes.DEFENSE,
                "Open Defense schedules",
            ),
            SettingsGuideEntry(
                "Adding is intentionally easier in three lists",
                "Adding apps to Always-On, adding apps to the VPN list, and adding keywords do not normally require a PIN. The protection is focused on preventing removal or weakening, not on stopping you from adding another safeguard.",
            ),
            SettingsGuideEntry(
                "Set both passwords before a serious block",
                "Open Defense → PIN Protection to configure the Focus Session PIN and Defense PIN. Keep them somewhere safe; forgetting them can leave a protection active until its normal expiry or until the correct recovery path is used.",
                Routes.PASSWORD_PROTECTION,
                "Open PIN Protection",
            ),
        ),
    ),
    SettingsGuideSection(
        title = "Other Toggles",
        icon = Icons.Outlined.Tune,
        iconBackground = Color(0xFF042F2E),
        iconTint = Color(0xFF2DD4BF),
        entries = listOf(
            SettingsGuideEntry(
                "Protect system controls",
                "Blocks or redirects sensitive system-control paths such as power-menu, Settings, and other escape routes. It cannot be turned off while Focus Mode or Standalone Block is active.",
                Routes.DEFENSE,
                "Open Defense",
            ),
            SettingsGuideEntry(
                "Network Protection and self-heal",
                "Network Protection uses the local VPN to cut internet access for selected apps. Self-heal watches the VPN and helps restore it if Android disconnects it. Android VPN permission is required.",
                Routes.VPN_BLOCK_LIST,
                "Open VPN Block List",
            ),
            SettingsGuideEntry(
                "Launcher protections",
                "Home Launcher protections can lock the default launcher choice, protect against uninstall attempts, and keep FocusFlow in control during a standalone block. Configure them from Home Launcher or the Defense tab.",
                Routes.HOME_LAUNCHER_SETUP,
                "Open Home Launcher",
            ),
            SettingsGuideEntry(
                "Aversion deterrents",
                "Vibration, screen dimming, and sound alerts react when a blocked app opens. They are optional deterrents that reinforce the block; they do not replace Accessibility, Usage Access, or the block list.",
                Routes.DEFENSE,
                "Open Defense",
            ),
            SettingsGuideEntry(
                "Content and Focus settings",
                "Shorts/Reels blocking, Auto-enable Focus Mode, and keeping focus active for the full task duration change how enforcement behaves. Enable only the layers that match your routine, then test them before starting a long block.",
                Routes.DEFENSE,
                "Open Defense",
            ),
        ),
    ),
    SettingsGuideSection(
        title = "Focus and active sessions",
        icon = Icons.Outlined.VpnKey,
        iconBackground = Color(0xFF2E1065),
        iconTint = Color(0xFFC084FC),
        entries = listOf(
            SettingsGuideEntry(
                "Why does stopping Focus ask for my Focus Session PIN?",
                "When a Focus Session PIN is set, stopping an active session early requires that PIN before app blocking ends. Without a Focus Session PIN, the app still asks you to confirm the stop.",
                Routes.FOCUS,
                "Open Focus",
            ),
            SettingsGuideEntry(
                "Why is full-duration Focus protected?",
                "Turning off the rule that keeps Focus active until a task ends asks for the Focus Session PIN when one is set. This prevents a running task from becoming easier to interrupt.",
                Routes.DEFENSE,
                "Open Defense",
            ),
            SettingsGuideEntry(
                "Why does clearing saved Standalone Block apps ask for a password?",
                "Clearing the saved app list from Active Blocks asks for the Defense PIN when PIN protection is enabled, then shows a separate confirmation. This is different from stopping an active Focus session, which uses the Focus Session PIN.",
                Routes.ACTIVE,
                "Open Active Blocks",
            ),
            SettingsGuideEntry(
                "Why does Clear All Tasks ask for my Focus Session PIN?",
                "If a Focus session is active and a Focus Session PIN is set, confirming Clear All Tasks asks for that PIN before clearing the tasks and ending the session. Canceling the PIN prompt keeps the active task and clears the other tasks.",
                Routes.SETTINGS,
                "Open Settings",
            ),
            SettingsGuideEntry(
                "Why does deleting a task ask for my Focus Session PIN?",
                "When a Focus Session PIN is configured, deleting a task from its edit screen asks for that PIN before removing the task. Without one, the task is deleted directly.",
                Routes.HOME,
                "Open Schedule",
            ),
        ),
    ),
    SettingsGuideSection(
        title = "Apps and keywords",
        icon = Icons.Outlined.Apps,
        iconBackground = Color(0xFF1E1B4B),
        iconTint = Color(0xFF818CF8),
        entries = listOf(
            SettingsGuideEntry(
                "When can I remove apps from Always-On or VPN lists?",
                "Removing an app is blocked while Focus Mode or Standalone Block is active. When no block is active, removal asks for the Defense PIN if PIN protection is enabled. Adding apps is intentionally allowed without a PIN.",
                Routes.ALWAYS_ON,
                "Open Always-On",
            ),
            SettingsGuideEntry(
                "Can I remove blocked keywords?",
                "Adding keywords does not require a PIN. Removing or clearing keywords asks for the Defense PIN when PIN protection is enabled; an active Standalone Block can lock removals completely.",
                Routes.KEYWORD_BLOCKER,
                "Open Keyword Blocker",
            ),
            SettingsGuideEntry(
                "Why is removing a VPN-blocked app locked?",
                "A Focus session or Standalone Block prevents removing protected VPN apps. Outside an active block, removal asks for the Defense PIN when protection is enabled. Adding another VPN-blocked app remains available.",
                Routes.VPN_BLOCK_LIST,
                "Open VPN Block List",
            ),
        ),
    ),
    SettingsGuideSection(
        title = "Schedules and allowances",
        icon = Icons.Outlined.CalendarMonth,
        iconBackground = Color(0xFF451A03),
        iconTint = Color(0xFFFBBF24),
        entries = listOf(
            SettingsGuideEntry(
                "Why does changing a group schedule ask for my Defense PIN?",
                "New schedules can be added without a PIN. When Defense PIN protection is enabled, editing a schedule, removing apps, disabling its window or VPN protection, shortening its duration, or deleting it can require the Defense PIN. An active Standalone Block also prevents deleting a schedule.",
                Routes.DEFENSE,
                "Open Defense schedules",
            ),
            SettingsGuideEntry(
                "Why can't I remove an existing daily allowance?",
                "While a block is active, existing allowance values are read-only and those entries cannot be removed. You can add and configure allowances for new apps; clearing the list then removes only those new additions. After the block, removing an entry asks for the Defense PIN when protection is enabled.",
                Routes.DEFENSE,
                "Open Defense allowances",
            ),
            SettingsGuideEntry(
                "What can I change during a Standalone Block?",
                "The active block's expiry and existing blocked apps are locked. The setup flow still allows adding apps or extending the block; saving those updates asks for the Focus Session PIN when one is set.",
                Routes.BLOCK_DEFENSE,
                "Open Standalone Block",
            ),
            SettingsGuideEntry(
                "Why does stopping a Standalone Block ask for my Focus Session PIN?",
                "If a Focus Session PIN is set, clearing an active Standalone Block asks for it before the block ends. Without one, FocusFlow still asks you to confirm.",
                Routes.ACTIVE,
                "Open Active Blocks",
            ),
        ),
    ),
    SettingsGuideSection(
        title = "Protection settings and access",
        icon = Icons.Outlined.Security,
        iconBackground = Color(0xFF042F2E),
        iconTint = Color(0xFF2DD4BF),
        entries = listOf(
            SettingsGuideEntry(
                "Why can't I turn off some Defense settings?",
                "Turning off Always-On Enforcement, System Guard, Shorts/Reels blocking, Screen Dimmer, Vibration Harassment, Sound Alert, Network Blocking, or VPN Self-Healing is guarded. An active Focus or Standalone Block can block the change; otherwise a configured Defense PIN is required. Turning protections on is allowed without a PIN, and not every Defense setting uses this rule.",
                Routes.DEFENSE,
                "Open Defense",
            ),
            SettingsGuideEntry(
                "Why are permission settings unavailable during a block?",
                "Permission settings are disabled while Focus or Standalone Block is active, so an enforcement permission cannot be changed mid-block.",
                Routes.PERMISSIONS,
                "Open Permissions",
            ),
            SettingsGuideEntry(
                "Why are launcher settings locked?",
                "Launcher settings cannot be modified while a Standalone Block is active.",
                Routes.HOME_LAUNCHER_SETUP,
                "Open Launcher settings",
            ),
            SettingsGuideEntry(
                "Why must I verify a password before changing it?",
                "Removing or replacing an existing Focus Session or Defense PIN first asks you to verify that current PIN. You can set a new PIN when one is not already protecting the feature.",
                Routes.PASSWORD_PROTECTION,
                "Open PIN Protection",
            ),
        ),
    ),
    SettingsGuideSection(
        title = "Backups and protection changes",
        icon = Icons.Outlined.Lock,
        iconBackground = Color(0xFF450A0A),
        iconTint = Color(0xFFF87171),
        entries = listOf(
            SettingsGuideEntry(
                "Why does restoring some backups ask for my Defense PIN?",
                "If a backup would remove protection entries or turn off Focus Mirror, the import flow requires the Defense PIN before applying those changes. Other import confirmations are separate from this protection check.",
                Routes.SETTINGS,
                "Open Settings backup options",
            ),
            SettingsGuideEntry(
                "Why do some changes stay locked instead of showing a PIN prompt?",
                "An active-block lock is stronger than a PIN prompt for some actions: the change is unavailable until the block ends. Adding a safeguard may still be allowed when removing or weakening it is not.",
            ),
        ),
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsGuideScreen(
    onBack: () -> Unit,
    onOpenRoute: (String) -> Unit,
) {
    val dimensions = LocalFocusFlowDimensions.current
    var expandedSection by rememberSaveable { mutableStateOf<Int?>(0) }

    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "How to Use",
                        fontSize = 20.scaledSp,
                        fontWeight = FontWeight.Bold,
                        color = DarkTextPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back to Settings",
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
                .padding(horizontal = dimensions.screenPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(dimensions.sectionSpacing),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "How to Use FocusFlow",
                    fontSize = 24.scaledSp,
                    fontWeight = FontWeight.Bold,
                    color = DarkTextPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Learn how each mode works, what protection changes are guarded, and when a PIN or active block applies.",
                    fontSize = 14.scaledSp,
                    lineHeight = 20.scaledSp,
                    color = DarkTextSecondary,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            SETTINGS_GUIDE_SECTIONS.forEachIndexed { index, section ->
                val isOpen = expandedSection == index
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkCard)
                        .border(1.dp, DarkBorder, RoundedCornerShape(16.dp)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedSection = if (isOpen) null else index
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(section.iconBackground),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = section.icon,
                                contentDescription = null,
                                tint = section.iconTint,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Text(
                            text = section.title,
                            modifier = Modifier.weight(1f),
                            fontSize = 16.scaledSp,
                            fontWeight = FontWeight.Bold,
                            color = DarkTextPrimary,
                        )
                        Icon(
                            imageVector = if (isOpen) {
                                Icons.Outlined.KeyboardArrowUp
                            } else {
                                Icons.Outlined.KeyboardArrowDown
                            },
                            contentDescription = if (isOpen) "Collapse" else "Expand",
                            tint = DarkTextMuted,
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    if (isOpen) {
                        HorizontalDivider(color = DarkBorder)
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            section.entries.forEachIndexed { entryIndex, entry ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Text(
                                        text = "${entryIndex + 1}.",
                                        fontSize = 14.scaledSp,
                                        fontWeight = FontWeight.Bold,
                                        color = BrandPrimary,
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(3.dp),
                                    ) {
                                        Text(
                                            text = entry.title,
                                            fontSize = 14.scaledSp,
                                            fontWeight = FontWeight.Bold,
                                            color = DarkTextPrimary,
                                        )
                                        Text(
                                            text = entry.body,
                                            fontSize = 13.scaledSp,
                                            lineHeight = 18.scaledSp,
                                            color = DarkTextSecondary,
                                        )
                                        val destination = entry.destination
                                        val destinationLabel = entry.destinationLabel
                                        if (destination != null && destinationLabel != null) {
                                            TextButton(onClick = { onOpenRoute(destination) }) {
                                                Text(
                                                    text = destinationLabel,
                                                    fontSize = 13.scaledSp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = BrandPrimary,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}