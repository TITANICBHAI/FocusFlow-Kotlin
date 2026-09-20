package com.tbtechs.focusflow.ui.navigation

import android.app.Activity
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.tbtechs.focusflow.data.repository.InstalledAppsRepository
import com.tbtechs.focusflow.data.repository.LauncherController
import com.tbtechs.focusflow.data.repository.VpnRepository
import com.tbtechs.focusflow.di.AppModule
import com.tbtechs.focusflow.domain.FocusPinManager
import com.tbtechs.focusflow.ui.AppBootViewModel
import com.tbtechs.focusflow.ui.FocusSessionViewModel
import com.tbtechs.focusflow.ui.SettingsViewModel
import com.tbtechs.focusflow.ui.TaskViewModel
import com.tbtechs.focusflow.ui.active.ActiveScreen
import com.tbtechs.focusflow.ui.alwayson.AlwaysOnScreen
import com.tbtechs.focusflow.ui.backup.BackupCoordinator
import com.tbtechs.focusflow.ui.backup.ImportConfirmScreen
import com.tbtechs.focusflow.ui.common.ErrorBoundary
import com.tbtechs.focusflow.ui.common.SideMenu
import com.tbtechs.focusflow.ui.defense.DefenseScreen
import com.tbtechs.focusflow.ui.defense.StandaloneBlockSetupScreen
import com.tbtechs.focusflow.ui.focus.ActiveBlockScreen
import com.tbtechs.focusflow.ui.focus.FocusScreen
import com.tbtechs.focusflow.ui.home.HomeScreen
import com.tbtechs.focusflow.ui.keyword.KeywordBlockerScreen
import com.tbtechs.focusflow.ui.launcher.LauncherSetupScreen
import com.tbtechs.focusflow.ui.launcher.VpnBlockListScreen
import com.tbtechs.focusflow.ui.legal.PrivacyPolicyScreen
import com.tbtechs.focusflow.ui.legal.TermsOfServiceScreen
import com.tbtechs.focusflow.ui.onboarding.OnboardingScreen
import com.tbtechs.focusflow.ui.permissions.PermissionsScreen
import com.tbtechs.focusflow.ui.profile.PasswordProtectionScreen
import com.tbtechs.focusflow.ui.profile.UserProfileScreen
import com.tbtechs.focusflow.ui.settings.SettingsScreen
import com.tbtechs.focusflow.ui.stats.ReportScreen
import com.tbtechs.focusflow.ui.stats.ReportsScreen
import com.tbtechs.focusflow.ui.stats.StatsInsightsExperience
import com.tbtechs.focusflow.ui.support.ChangelogScreen
import com.tbtechs.focusflow.ui.support.HowToUseScreen
import com.tbtechs.focusflow.ui.theme.LocalFocusFlowDimensions
import kotlinx.coroutines.launch

@Composable
fun FocusFlowNavGraph(
    navController: NavHostController,
    taskViewModel: TaskViewModel,
    settingsViewModel: SettingsViewModel,
    focusSessionViewModel: FocusSessionViewModel,
    appBootViewModel: AppBootViewModel,
    statsViewModel: com.tbtechs.focusflow.ui.stats.StatsViewModel,
    vpnRepository: VpnRepository,
    backupCoordinator: BackupCoordinator? = null,
    onExportBackup: () -> Unit = {},
    onImportBackup: (Boolean) -> Unit = {},
    pendingImportUri: android.net.Uri? = null,
    initialReplaceTasks: Boolean = false,
    onImportFinished: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val installedAppsRepository = remember { InstalledAppsRepository(context) }
    val launcherController = remember { LauncherController(context) }
    val focusPinManager = remember { FocusPinManager(context) }
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val drawerState = androidx.compose.material3.rememberDrawerState(
        androidx.compose.material3.DrawerValue.Closed,
    )
    var pendingQuickBlockPackage by rememberSaveable { mutableStateOf<String?>(null) }

    fun navigate(route: String) {
        if (route == currentRoute) return
        if (route in Routes.tabRoutes) {
            navController.navigate(route) {
                popUpTo(Routes.HOME) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        } else {
            navController.navigate(route) { launchSingleTop = true }
        }
    }

    fun back() {
        if (!navController.popBackStack()) navigate(Routes.HOME)
    }

    androidx.compose.material3.ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SideMenu(
                currentRoute = currentRoute,
                onNavigate = ::navigate,
                onClose = { scope.launch { drawerState.close() } },
            )
        },
    ) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(Routes.HOME) {
                MainScaffold(currentRoute, ::navigate) {
                    ScreenBoundary(Routes.HOME) {
                        HomeScreen(
                            taskViewModel = taskViewModel,
                            settingsViewModel = settingsViewModel,
                            focusSessionViewModel = focusSessionViewModel,
                            appBootViewModel = appBootViewModel,
                            onOpenActiveBlocks = { navigate(Routes.ACTIVE) },
                        )
                    }
                }
            }
            composable(Routes.FOCUS) {
                MainScaffold(currentRoute, ::navigate) {
                    ScreenBoundary(Routes.FOCUS) {
                        FocusScreen(
                            taskViewModel = taskViewModel,
                            settingsViewModel = settingsViewModel,
                            focusSessionViewModel = focusSessionViewModel,
                            onOpenActiveBlocks = { navigate(Routes.ACTIVE) },
                            onOpenSchedule = { navigate(Routes.HOME) },
                            onOpenPermissions = { navigate(Routes.PERMISSIONS) },
                        )
                    }
                }
            }
            composable(Routes.STATS) {
                MainScaffold(currentRoute, ::navigate) {
                    ScreenBoundary(Routes.STATS) {
                        StatsInsightsExperience(
                            statsViewModel = statsViewModel,
                            onOpenUsageAccessSettings = {
                                scope.launch {
                                    AppModule.usageStatsRepository.openUsageAccessSettings()
                                }
                            },
                            onOpenActiveBlocks = { navigate(Routes.ACTIVE) },
                            onOpenQuickBlock = { packageName ->
                                pendingQuickBlockPackage = packageName
                                navigate(Routes.BLOCK_DEFENSE)
                            },
                        )
                    }
                }
            }
            composable(Routes.SETTINGS) {
                MainScaffold(currentRoute, ::navigate) {
                    ScreenBoundary(Routes.SETTINGS) {
                        SettingsScreen(
                            settingsViewModel = settingsViewModel,
                            taskViewModel = taskViewModel,
                            focusSessionViewModel = focusSessionViewModel,
                            appBootViewModel = appBootViewModel,
                            onOpenActiveBlocks = { navigate(Routes.ACTIVE) },
                            onExportBackup = backupCoordinator?.let { onExportBackup },
                            onImportBackup = backupCoordinator?.let { onImportBackup },
                            onOpenProfile = { navigate(Routes.USER_PROFILE) },
                            onOpenPermissions = { navigate(Routes.PERMISSIONS) },
                            onOpenStats = { navigate(Routes.STATS) },
                            onOpenChangelog = { navigate(Routes.CHANGELOG) },
                            onOpenPrivacyTerms = { navigate(Routes.PRIVACY_POLICY) },
                        )
                    }
                }
            }
            composable(Routes.DEFENSE) {
                MainScaffold(currentRoute, ::navigate) {
                    ScreenBoundary(Routes.DEFENSE) {
                        DefenseScreen(
                            settingsViewModel = settingsViewModel,
                            isFocusActive = focusSessionViewModel.focusSession.value?.isActive == true,
                            vpnRepository = vpnRepository,
                            onOpenAlwaysOn = { navigate(Routes.ALWAYS_ON) },
                            onOpenKeywordBlocker = { navigate(Routes.KEYWORD_BLOCKER) },
                            onOpenVpnBlockList = { navigate(Routes.VPN_BLOCK_LIST) },
                            onOpenPasswordProtection = { navigate(Routes.PASSWORD_PROTECTION) },
                            onOpenPermissions = { navigate(Routes.PERMISSIONS) },
                            onOpenHowToUse = { navigate(Routes.HOW_TO_USE) },
                            onOpenLauncher = { navigate(Routes.HOME_LAUNCHER_SETUP) },
                            onOpenActiveBlocks = { navigate(Routes.ACTIVE) },
                        )
                    }
                }
            }
            composable(Routes.ACTIVE) {
                ScreenBoundary(Routes.ACTIVE) {
                    ActiveScreen(
                        taskViewModel = taskViewModel,
                        settingsViewModel = settingsViewModel,
                        focusSessionViewModel = focusSessionViewModel,
                        vpnRepository = vpnRepository,
                        onBack = ::back,
                        onOpenFocus = { navigate(Routes.FOCUS) },
                        onOpenAlwaysOn = { navigate(Routes.ALWAYS_ON) },
                        onOpenDefense = { navigate(Routes.DEFENSE) },
                        onOpenKeywordBlocker = { navigate(Routes.KEYWORD_BLOCKER) },
                        onOpenVpnBlockList = { navigate(Routes.VPN_BLOCK_LIST) },
                    )
                }
            }
            composable(Routes.ALWAYS_ON) {
                ScreenBoundary(Routes.ALWAYS_ON) {
                    AlwaysOnScreen(
                        settingsViewModel = settingsViewModel,
                        focusSessionViewModel = focusSessionViewModel,
                        settingsRepository = AppModule.settingsRepository,
                        vpnRepository = vpnRepository,
                        installedAppsRepository = installedAppsRepository,
                        onBack = ::back,
                    )
                }
            }
            composable(Routes.BLOCK_DEFENSE) {
                ScreenBoundary(Routes.BLOCK_DEFENSE) {
                    StandaloneBlockSetupScreen(
                        settingsViewModel = settingsViewModel,
                        initialPackage = pendingQuickBlockPackage,
                        onBack = {
                            pendingQuickBlockPackage = null
                            back()
                        },
                    )
                }
            }
            composable(Routes.CHANGELOG) {
                ScreenBoundary(Routes.CHANGELOG) { ChangelogScreen(onBack = ::back) }
            }
            composable(Routes.HOME_LAUNCHER_SETUP) {
                ScreenBoundary(Routes.HOME_LAUNCHER_SETUP) {
                    LauncherSetupScreen(
                        settingsViewModel = settingsViewModel,
                        settingsRepository = AppModule.settingsRepository,
                        installedAppsRepository = installedAppsRepository,
                        launcherController = launcherController,
                        onBack = ::back,
                    )
                }
            }
            composable(Routes.IMPORT_CONFIRM) {
                ScreenBoundary(Routes.IMPORT_CONFIRM) {
                    ImportConfirmScreen(
                        source = pendingImportUri,
                        backupCoordinator = backupCoordinator
                            ?: error("Backup coordinator is required for import confirmation."),
                        currentSettings = settingsViewModel.settings.value,
                        currentFocusActive = focusSessionViewModel.focusSession.value?.isActive == true,
                        initialReplaceTasks = initialReplaceTasks,
                        onBack = onImportFinished,
                        onImported = onImportFinished,
                    )
                }
            }
            composable(Routes.HOW_TO_USE) {
                ScreenBoundary(Routes.HOW_TO_USE) {
                    HowToUseScreen(
                        isOnboarding = false,
                        onBack = ::back,
                        onGetStarted = { navigate(Routes.FOCUS) },
                    )
                }
            }
            composable(
                route = "${Routes.HOW_TO_USE}?onboarding={onboarding}",
                arguments = listOf(
                    navArgument("onboarding") {
                        type = NavType.StringType
                        defaultValue = "true"
                    },
                ),
            ) { backStackEntry ->
                val onboardingParam = backStackEntry.arguments?.getString("onboarding")
                val isOnboarding = onboardingParam == "true" || onboardingParam == "1"
                ScreenBoundary(Routes.HOW_TO_USE) {
                    HowToUseScreen(
                        isOnboarding = isOnboarding,
                        onBack = {
                            if (isOnboarding) {
                                navController.navigate(Routes.FOCUS) {
                                    popUpTo(Routes.HOME) { inclusive = false }
                                }
                            } else {
                                back()
                            }
                        },
                        onGetStarted = {
                            navController.navigate(Routes.FOCUS) {
                                popUpTo(Routes.HOME) { inclusive = false }
                            }
                        },
                    )
                }
            }
            composable(Routes.KEYWORD_BLOCKER) {
                ScreenBoundary(Routes.KEYWORD_BLOCKER) {
                    KeywordBlockerScreen(
                        settingsViewModel = settingsViewModel,
                        onBack = ::back,
                    )
                }
            }
            composable(Routes.ONBOARDING) {
                ScreenBoundary(Routes.ONBOARDING) {
                    OnboardingScreen(
                        settingsViewModel = settingsViewModel,
                        onFinished = {
                            navController.navigate("${Routes.HOW_TO_USE}?onboarding=true") {
                                popUpTo(Routes.ONBOARDING) { inclusive = true }
                            }
                        },
                    )
                }
            }
            composable(Routes.PASSWORD_PROTECTION) {
                ScreenBoundary(Routes.PASSWORD_PROTECTION) {
                    PasswordProtectionScreen(
                        settingsViewModel = settingsViewModel,
                        focusPinManager = focusPinManager,
                        onBack = { navigate(Routes.DEFENSE) },
                    )
                }
            }
            composable(Routes.PERMISSIONS) {
                ScreenBoundary(Routes.PERMISSIONS) {
                    PermissionsScreen(
                        settingsViewModel = settingsViewModel,
                        isFocusActive = focusSessionViewModel.focusSession.value?.isActive == true,
                        onBack = ::back,
                        onConfigureLauncher = { navigate(Routes.HOME_LAUNCHER_SETUP) },
                    )
                }
            }
            composable(Routes.PRIVACY_POLICY) {
                val privacyAccepted by settingsViewModel.privacyAccepted.collectAsState()
                ScreenBoundary(Routes.PRIVACY_POLICY) {
                    PrivacyPolicyScreen(
                        settingsRepository = AppModule.settingsRepository,
                        isRevisit = privacyAccepted,
                        onBack = ::back,
                        onAccepted = {
                            navController.navigate(Routes.ONBOARDING) {
                                popUpTo(Routes.PRIVACY_POLICY) { inclusive = true }
                            }
                        },
                        onDeclineExit = { (context as? Activity)?.finishAndRemoveTask() },
                    )
                }
            }
            composable(
                route = "${Routes.PRIVACY_POLICY}?revisit={revisit}",
                arguments = listOf(
                    navArgument("revisit") {
                        type = NavType.StringType
                        defaultValue = "false"
                    },
                ),
            ) { backStackEntry ->
                val revisitParam = backStackEntry.arguments?.getString("revisit")
                val isRevisit = revisitParam == "true" || revisitParam == "1"
                ScreenBoundary(Routes.PRIVACY_POLICY) {
                    PrivacyPolicyScreen(
                        settingsRepository = AppModule.settingsRepository,
                        isRevisit = isRevisit,
                        onBack = ::back,
                        onAccepted = {
                            navController.navigate(Routes.ONBOARDING) {
                                popUpTo(Routes.PRIVACY_POLICY) { inclusive = true }
                            }
                        },
                        onDeclineExit = { (context as? Activity)?.finishAndRemoveTask() },
                    )
                }
            }
            composable(Routes.REPORTS) {
                ScreenBoundary(Routes.REPORTS) {
                    ReportsScreen(
                        taskViewModel = taskViewModel,
                        settingsRepository = AppModule.settingsRepository,
                        onBack = ::back,
                    )
                }
            }
            composable(Routes.REPORT) {
                ScreenBoundary(Routes.REPORT) {
                    ReportScreen(
                        taskViewModel = taskViewModel,
                        settingsRepository = AppModule.settingsRepository,
                        onBack = ::back,
                    )
                }
            }
            composable(Routes.TERMS_OF_SERVICE) {
                ScreenBoundary(Routes.TERMS_OF_SERVICE) {
                    TermsOfServiceScreen(onBack = ::back)
                }
            }
            composable(Routes.USER_PROFILE) {
                ScreenBoundary(Routes.USER_PROFILE) {
                    UserProfileScreen(
                        settingsRepository = AppModule.settingsRepository,
                        isEditMode = true,
                        onBack = ::back,
                        onFinished = ::back,
                        onImportBackup = backupCoordinator?.let {
                            { onImportBackup(false) }
                        },
                        focusSessionRepository = AppModule.focusSessionRepository,
                        settingsViewModel = settingsViewModel,
                    )
                }
            }
            composable(Routes.VPN_BLOCK_LIST) {
                ScreenBoundary(Routes.VPN_BLOCK_LIST) {
                    VpnBlockListScreen(
                        settingsViewModel = settingsViewModel,
                        vpnRepository = vpnRepository,
                        installedAppsRepository = installedAppsRepository,
                        isFocusActive = focusSessionViewModel.focusSession.value?.isActive == true,
                        onBack = ::back,
                    )
                }
            }
            composable(Routes.NOT_FOUND) {
                ScreenBoundary(Routes.NOT_FOUND) {
                    NotFoundScreen(onBack = ::back)
                }
            }
        }
    }
}

@Composable
private fun ScreenBoundary(
    route: String,
    content: @Composable () -> Unit,
) {
    ErrorBoundary(screenName = route, content = content)
}

@Composable
fun MainScaffold(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    val dimensions = LocalFocusFlowDimensions.current
    val tabs = listOf(
        Triple(Routes.FOCUS, "Focus", Icons.Outlined.Timer),
        Triple(Routes.HOME, "Schedule", Icons.Outlined.CalendarMonth),
        Triple(Routes.DEFENSE, "Defense", Icons.Outlined.Shield),
        Triple(Routes.STATS, "Stats", Icons.Outlined.Analytics),
        Triple(Routes.SETTINGS, "Settings", Icons.Outlined.Settings),
    )
    Scaffold(
        containerColor = com.tbtechs.focusflow.ui.theme.DarkBackground,
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            NavigationBar(
                containerColor = com.tbtechs.focusflow.ui.theme.DarkBackground,
                windowInsets = WindowInsets.navigationBars,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .border(
                        width = 1.dp,
                        color = com.tbtechs.focusflow.ui.theme.DarkBorder.copy(alpha = 0.65f),
                        shape = MaterialTheme.shapes.large,
                    )
                    .clip(MaterialTheme.shapes.large),
            ) {
                tabs.forEach { (route, label, icon) ->
                    val isSelected = currentRoute == route
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { onNavigate(route) },
                        icon = {
                            Icon(
                                icon,
                                contentDescription = label,
                                tint = if (isSelected) com.tbtechs.focusflow.ui.theme.BrandPrimary else com.tbtechs.focusflow.ui.theme.DarkTextMuted,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        label = {
                            Text(
                                text = label,
                                fontSize = if (dimensions.screenPadding < 16.dp) 10.sp else 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) com.tbtechs.focusflow.ui.theme.BrandPrimary else com.tbtechs.focusflow.ui.theme.DarkTextSecondary,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = com.tbtechs.focusflow.ui.theme.BrandPrimary,
                            selectedTextColor = com.tbtechs.focusflow.ui.theme.BrandPrimary,
                            unselectedIconColor = com.tbtechs.focusflow.ui.theme.DarkTextMuted,
                            unselectedTextColor = com.tbtechs.focusflow.ui.theme.DarkTextSecondary,
                            indicatorColor = com.tbtechs.focusflow.ui.theme.BrandPrimary.copy(alpha = 0.14f),
                        ),
                    )
                }
            }
        },
    ) { padding ->
        var dragDistance = 0f
        val swipeModifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .pointerInput(currentRoute) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, amount ->
                        dragDistance += amount
                        change.consume()
                    },
                    onDragEnd = {
                        val index = tabs.indexOfFirst { it.first == currentRoute }
                        when {
                            index >= 0 && dragDistance <= -60f && index < tabs.lastIndex ->
                                onNavigate(tabs[index + 1].first)
                            index > 0 && dragDistance >= 60f ->
                                onNavigate(tabs[index - 1].first)
                        }
                        dragDistance = 0f
                    },
                    onDragCancel = { dragDistance = 0f },
                )
            }
        Box(modifier = swipeModifier) {
            content()
        }
    }
}

@Composable
private fun NotFoundScreen(onBack: () -> Unit) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text("Screen not found", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
        androidx.compose.material3.Text("This FocusFlow destination is not available.")
        androidx.compose.material3.Button(onClick = onBack) { Text("Go back") }
    }
}