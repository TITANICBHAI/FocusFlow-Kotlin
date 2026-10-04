package com.tbtechs.focusflow.ui.settings

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
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.sp
import androidx.compose.material3.IconButton
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

private data class AdjustmentQuestion(
    val heading: String,
    val answer: String,
    val destination: String? = null,
    val destinationLabel: String? = null,
)

private data class AdjustmentSection(
    val title: String,
    val icon: ImageVector,
    val iconBackground: Color,
    val iconTint: Color,
    val questions: List<AdjustmentQuestion>,
)

private val ADJUSTMENT_SECTIONS = listOf(
    AdjustmentSection(
        title = "Focus and active sessions",
        icon = Icons.Outlined.VpnKey,
        iconBackground = Color(0xFF2E1065),
        iconTint = Color(0xFFC084FC),
        questions = listOf(
            AdjustmentQuestion(
                heading = "Why does stopping Focus ask for my Focus Session PIN?",
                answer = "When a Focus Session PIN is set, stopping an active session early requires that PIN before app blocking ends. Without a Focus Session PIN, the app still asks you to confirm the stop.",
                destination = Routes.FOCUS,
                destinationLabel = "Open Focus",
            ),
            AdjustmentQuestion(
                heading = "Why is full-duration Focus protected?",
                answer = "Turning off the rule that keeps Focus active until a task ends asks for the Focus Session PIN when one is set. This prevents a running task from becoming easier to interrupt.",
                destination = Routes.DEFENSE,
                destinationLabel = "Open Defense",
            ),
            AdjustmentQuestion(
                heading = "Why does clearing saved Standalone Block apps ask for a password?",
                answer = "Clearing the saved app list from Active Blocks asks for the Defense PIN when PIN protection is enabled, then shows a separate confirmation. This is different from stopping an active Focus session, which uses the Focus Session PIN.",
                destination = Routes.ACTIVE,
                destinationLabel = "Open Active Blocks",
            ),
            AdjustmentQuestion(
                heading = "Why does Clear All Tasks ask for my Focus Session PIN?",
                answer = "If a Focus session is active and a Focus Session PIN is set, confirming Clear All Tasks asks for that PIN before clearing the tasks and ending the session. Canceling the PIN prompt keeps the active task and clears the other tasks.",
                destination = Routes.SETTINGS,
                destinationLabel = "Open Settings",
            ),
            AdjustmentQuestion(
                heading = "Why does deleting a task ask for my Focus Session PIN?",
                answer = "When a Focus Session PIN is configured, deleting a task from its edit screen asks for that PIN before removing the task. Without one, the task is deleted directly.",
                destination = Routes.HOME,
                destinationLabel = "Open Schedule",
            ),
        ),
    ),
    AdjustmentSection(
        title = "Apps and keywords",
        icon = Icons.Outlined.Apps,
        iconBackground = Color(0xFF1E1B4B),
        iconTint = Color(0xFF818CF8),
        questions = listOf(
            AdjustmentQuestion(
                heading = "When can I remove apps from Always-On or VPN lists?",
                answer = "Removing an app is blocked while Focus Mode or Standalone Block is active. When no block is active, removal asks for the Defense PIN if PIN protection is enabled. Adding apps is intentionally allowed without a PIN.",
                destination = Routes.ALWAYS_ON,
                destinationLabel = "Open Always-On",
            ),
            AdjustmentQuestion(
                heading = "Can I remove blocked keywords?",
                answer = "Adding keywords does not require a PIN. Removing or clearing keywords asks for the Defense PIN when PIN protection is enabled; an active Standalone Block can lock removals completely.",
                destination = Routes.KEYWORD_BLOCKER,
                destinationLabel = "Open Keyword Blocker",
            ),
            AdjustmentQuestion(
                heading = "Why is removing a VPN-blocked app locked?",
                answer = "A Focus session or Standalone Block prevents removing protected VPN apps. Outside an active block, removal asks for the Defense PIN when protection is enabled. Adding another VPN-blocked app remains available.",
                destination = Routes.VPN_BLOCK_LIST,
                destinationLabel = "Open VPN Block List",
            ),
        ),
    ),
    AdjustmentSection(
        title = "Schedules and allowances",
        icon = Icons.Outlined.CalendarMonth,
        iconBackground = Color(0xFF451A03),
        iconTint = Color(0xFFFBBF24),
        questions = listOf(
            AdjustmentQuestion(
                heading = "Why does changing a group schedule ask for my Defense PIN?",
                answer = "New schedules can be added without a PIN. When Defense PIN protection is enabled, editing a schedule, removing apps, disabling its window or VPN protection, shortening its duration, or deleting it can require the Defense PIN. An active Standalone Block also prevents deleting a schedule.",
                destination = Routes.DEFENSE,
                destinationLabel = "Open Defense schedules",
            ),
            AdjustmentQuestion(
                heading = "Why can't I remove an existing daily allowance?",
                answer = "While a block is active, existing allowance values are read-only and those entries cannot be removed. You can add and configure allowances for new apps; clearing the list then removes only those new additions. After the block, removing an entry asks for the Defense PIN when protection is enabled.",
                destination = Routes.DEFENSE,
                destinationLabel = "Open Defense allowances",
            ),
            AdjustmentQuestion(
                heading = "What can I change during a Standalone Block?",
                answer = "The active block's expiry and existing blocked apps are locked. The setup flow still allows adding apps or extending the block; saving those updates asks for the Focus Session PIN when one is set.",
                destination = Routes.BLOCK_DEFENSE,
                destinationLabel = "Open Standalone Block",
            ),
            AdjustmentQuestion(
                heading = "Why does stopping a Standalone Block ask for my Focus Session PIN?",
                answer = "If a Focus Session PIN is set, clearing an active Standalone Block asks for it before the block ends. Without one, FocusFlow still asks you to confirm.",
                destination = Routes.ACTIVE,
                destinationLabel = "Open Active Blocks",
            ),
        ),
    ),
    AdjustmentSection(
        title = "Protection settings and access",
        icon = Icons.Outlined.Security,
        iconBackground = Color(0xFF042F2E),
        iconTint = Color(0xFF2DD4BF),
        questions = listOf(
            AdjustmentQuestion(
                heading = "Why can't I turn off some Defense settings?",
                answer = "Turning off Always-On Enforcement, System Guard, Shorts/Reels blocking, Screen Dimmer, Vibration Harassment, Sound Alert, Network Blocking, or VPN Self-Healing is guarded. An active Focus or Standalone Block can block the change; otherwise a configured Defense PIN is required. Turning protections on is allowed without a PIN, and not every Defense setting uses this rule.",
                destination = Routes.DEFENSE,
                destinationLabel = "Open Defense",
            ),
            AdjustmentQuestion(
                heading = "Why are permission settings unavailable during a block?",
                answer = "Permission settings are disabled while Focus or Standalone Block is active, so an enforcement permission cannot be changed mid-block.",
                destination = Routes.PERMISSIONS,
                destinationLabel = "Open Permissions",
            ),
            AdjustmentQuestion(
                heading = "Why are launcher settings locked?",
                answer = "Launcher settings cannot be modified while a Standalone Block is active.",
                destination = Routes.HOME_LAUNCHER_SETUP,
                destinationLabel = "Open Launcher settings",
            ),
            AdjustmentQuestion(
                heading = "Why must I verify a password before changing it?",
                answer = "Removing or replacing an existing Focus Session or Defense PIN first asks you to verify that current PIN. You can set a new PIN when one is not already protecting the feature.",
                destination = Routes.PASSWORD_PROTECTION,
                destinationLabel = "Open PIN Protection",
            ),
        ),
    ),
    AdjustmentSection(
        title = "Backups and protection changes",
        icon = Icons.Outlined.Lock,
        iconBackground = Color(0xFF450A0A),
        iconTint = Color(0xFFF87171),
        questions = listOf(
            AdjustmentQuestion(
                heading = "Why does restoring some backups ask for my Defense PIN?",
                answer = "If a backup would remove protection entries or turn off Focus Mirror, the import flow requires the Defense PIN before applying those changes. Other import confirmations are separate from this protection check.",
                destination = Routes.SETTINGS,
                destinationLabel = "Open Settings backup options",
            ),
            AdjustmentQuestion(
                heading = "Why do some changes stay locked instead of showing a PIN prompt?",
                answer = "An active-block lock is stronger than a PIN prompt for some actions: the change is unavailable until the block ends. Adding a safeguard may still be allowed when removing or weakening it is not.",
            ),
        ),
    ),
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ProtectedAdjustmentsScreen(
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
                        text = "Guarded Adjustments",
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
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Why is this change protected?",
                    fontSize = 23.scaledSp,
                    fontWeight = FontWeight.Bold,
                    color = DarkTextPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Find out when a PIN is needed, what stays locked during a block, and where to manage each setting.",
                    fontSize = 14.scaledSp,
                    lineHeight = 20.scaledSp,
                    color = DarkTextSecondary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "PINs protect changes that weaken a block; not every Settings value requires one.",
                    fontSize = 12.scaledSp,
                    lineHeight = 17.scaledSp,
                    color = DarkTextMuted,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            ADJUSTMENT_SECTIONS.forEachIndexed { index, section ->
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
                            section.questions.forEachIndexed { questionIndex, question ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Text(
                                        text = "${questionIndex + 1}.",
                                        fontSize = 14.scaledSp,
                                        fontWeight = FontWeight.Bold,
                                        color = BrandPrimary,
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(3.dp),
                                    ) {
                                        Text(
                                            text = question.heading,
                                            fontSize = 14.scaledSp,
                                            fontWeight = FontWeight.Bold,
                                            color = DarkTextPrimary,
                                        )
                                        Text(
                                            text = question.answer,
                                            fontSize = 13.scaledSp,
                                            lineHeight = 18.scaledSp,
                                            color = DarkTextSecondary,
                                        )
                                        val destination = question.destination
                                        val destinationLabel = question.destinationLabel
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