package com.tbtechs.focusflow

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.navigation.compose.rememberNavController
import com.tbtechs.focusflow.data.repository.NetworkBlockSettings
import com.tbtechs.focusflow.data.repository.StartupLogger
import com.tbtechs.focusflow.data.repository.VpnRepository
import com.tbtechs.focusflow.di.AppModule
import com.tbtechs.focusflow.enforcement.AppBlockerAccessibilityService
import com.tbtechs.focusflow.enforcement.receivers.NotificationActionReceiver
import com.tbtechs.focusflow.ui.AppBootViewModel
import com.tbtechs.focusflow.ui.FocusSessionViewModel
import com.tbtechs.focusflow.ui.SettingsViewModel
import com.tbtechs.focusflow.ui.TaskViewModel
import com.tbtechs.focusflow.ui.alwayson.VpnPermissionLostBanner
import com.tbtechs.focusflow.ui.backup.BackupCoordinator
import com.tbtechs.focusflow.ui.common.AchievementCelebrationModal
import com.tbtechs.focusflow.ui.common.AppErrorEvents
import com.tbtechs.focusflow.ui.common.ErrorAlertBanner
import com.tbtechs.focusflow.ui.common.ErrorBoundary
import com.tbtechs.focusflow.ui.navigation.FocusFlowNavGraph
import com.tbtechs.focusflow.ui.navigation.Routes
import com.tbtechs.focusflow.ui.stats.StatsViewModel
import com.tbtechs.focusflow.ui.support.DiagnosticLogEntry
import com.tbtechs.focusflow.ui.support.DiagnosticLogLevel
import com.tbtechs.focusflow.ui.support.DiagnosticsModal
import com.tbtechs.focusflow.ui.splash.FocusFlowSplashOverlay
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Normal app activity host. LauncherActivity remains a separate CATEGORY_HOME
 * activity and is intentionally not part of this NavHost.
 */
class MainActivity : ComponentActivity() {
    private val vpnRepository by lazy { VpnRepository(applicationContext) }
    private var requestedRoute by mutableStateOf(Routes.HOME)
    private var notificationEventNonce by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StartupLogger.info("MainActivity", "Main activity created")
        requestedRoute = routeFromIntent(intent)
        setTheme(R.style.Theme_FocusFlow)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            FocusFlowRoot(
                requestedRoute = requestedRoute,
                notificationEventNonce = notificationEventNonce,
                vpnRepository = vpnRepository,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        StartupLogger.info("MainActivity", "Main activity received a new intent")
        setIntent(intent)
        requestedRoute = routeFromIntent(intent)
        notificationEventNonce++
    }

    private fun routeFromIntent(intent: Intent?): String =
        Routes.fromPath(intent?.data?.path)
}

@Composable
private fun FocusFlowRoot(
    requestedRoute: String,
    notificationEventNonce: Int,
    vpnRepository: VpnRepository,
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val settingsViewModel = remember {
        SettingsViewModel(AppModule.settingsRepository, AppModule.pinManager, context)
    }
    val focusSessionViewModel = remember {
        FocusSessionViewModel(
            focusSessionRepository = AppModule.focusSessionRepository,
            taskRepository = AppModule.taskRepository,
            settingsRepository = AppModule.settingsRepository,
            context = context,
        )
    }
    val taskViewModel = remember {
        TaskViewModel(
            taskRepository = AppModule.taskRepository,
            alarmRepository = AppModule.alarmRepository,
            beforeTaskDelete = { taskId, pinHash ->
                AppModule.alarmRepository.cancelAlarm(taskId)
                AppModule.alarmRepository.dismissAlarm(taskId)
                focusSessionViewModel.stopFocusModeForTaskAwait(taskId, pinHash)
            },
            beforeClearTasks = { pinHash ->
                focusSessionViewModel.stopFocusModeAwait(pinHash)
            },
        )
    }
    val appBootViewModel = remember {
        AppBootViewModel(
            settingsRepository = AppModule.settingsRepository,
            taskRepository = AppModule.taskRepository,
            focusSessionRepository = AppModule.focusSessionRepository,
        )
    }
    val settings by settingsViewModel.settings.collectAsState()
    val privacyAccepted by settingsViewModel.privacyAccepted.collectAsState()
    val onboardingComplete by settingsViewModel.onboardingComplete.collectAsState()
    val isLoading by appBootViewModel.isLoading.collectAsState()
    val isDbReady by appBootViewModel.isDbReady.collectAsState()
    val statsViewModel = remember {
        StatsViewModel(
            analyticsProcessor = AppModule.analyticsProcessor,
            insightEngine = AppModule.insightEngine,
            achievementEngine = AppModule.achievementEngine,
        )
    }
    val backupCoordinator = remember {
        BackupCoordinator(
            context = context,
            taskRepository = AppModule.taskRepository,
            focusSessionRepository = AppModule.focusSessionRepository,
            settingsRepository = AppModule.settingsRepository,
            settingsViewModel = settingsViewModel,
        )
    }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var replaceTasksOnImport by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        result.data?.data?.let { destination ->
            scope.launch {
                val outcome = backupCoordinator.export(settings, destination)
                Toast.makeText(
                    context,
                    if (outcome.ok) "Backup exported." else "Backup export failed: ${outcome.error}",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        result.data?.data?.let { source ->
            pendingImportUri = source
            navController.navigate(Routes.IMPORT_CONFIRM)
        }
    }
    var networkSettings by remember { mutableStateOf<NetworkBlockSettings?>(null) }
    var diagnosticEvents by remember { mutableStateOf(startupDiagnosticEntries()) }
    var diagnosticsVisible by remember { mutableStateOf(false) }
    var dismissedAchievementId by remember { mutableStateOf<String?>(null) }
    val achievementState by statsViewModel.achievementState.collectAsState()
    val newlyEarned = achievementState?.newlyEarnedIds.orEmpty()
        .firstOrNull()
        ?.let { id -> achievementState?.definitions?.firstOrNull { it.id == id } }

    // Set this synchronously after both VMs exist. AppBootViewModel starts its
    // coroutine from init, so assigning it later in LaunchedEffect could miss
    // an active-session recovery on a fast database.
    appBootViewModel.onSessionRecovered = focusSessionViewModel::loadActiveSession

    LaunchedEffect(Unit) {
        AppErrorEvents.events.collect {
            diagnosticEvents = startupDiagnosticEntries()
        }
    }

    LaunchedEffect(requestedRoute, isDbReady, privacyAccepted, onboardingComplete) {
        if (!isDbReady) return@LaunchedEffect
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        val isCurrentlyInHowToUse = currentRoute?.contains(Routes.HOW_TO_USE) == true

        val guardedRoute = when {
            !privacyAccepted && requestedRoute != Routes.PRIVACY_POLICY -> Routes.PRIVACY_POLICY
            privacyAccepted && !onboardingComplete &&
                requestedRoute != Routes.PRIVACY_POLICY &&
                requestedRoute != Routes.ONBOARDING -> Routes.ONBOARDING
            else -> requestedRoute
        }

        // When user just completed onboarding and is viewing the How-To-Use onboarding tour,
        // do not yank them away to HOME.
        if (isCurrentlyInHowToUse && onboardingComplete) {
            return@LaunchedEffect
        }

        if (guardedRoute != Routes.HOME &&
            currentRoute != guardedRoute
        ) {
            navController.navigate(guardedRoute) {
                launchSingleTop = true
            }
        }
    }

    // NotificationActionReceiver persists actions before launching this
    // activity, so notification actions survive a cold process start.
    LaunchedEffect(isDbReady, notificationEventNonce) {
        if (!isDbReady) return@LaunchedEffect
        val prefs = context.getSharedPreferences(
            AppBlockerAccessibilityService.PREFS_NAME,
            android.content.Context.MODE_PRIVATE,
        )
        val action = prefs.getString(NotificationActionReceiver.PREF_PENDING_ACTION, null)
        val taskId = prefs.getString(NotificationActionReceiver.PREF_PENDING_TASK_ID, null)
        val minutes = prefs.getInt(NotificationActionReceiver.PREF_PENDING_MINUTES, 15)
        val timestamp = prefs.getLong(NotificationActionReceiver.PREF_PENDING_TIME_MS, 0L)
        if (action.isNullOrBlank() || taskId.isNullOrBlank() ||
            System.currentTimeMillis() - timestamp > 5 * 60 * 1_000L
        ) {
            return@LaunchedEffect
        }
        prefs.edit()
            .remove(NotificationActionReceiver.PREF_PENDING_ACTION)
            .remove(NotificationActionReceiver.PREF_PENDING_TASK_ID)
            .remove(NotificationActionReceiver.PREF_PENDING_MINUTES)
            .remove(NotificationActionReceiver.PREF_PENDING_TIME_MS)
            .apply()
        when (action) {
            NotificationActionReceiver.ACTION_COMPLETE -> taskViewModel.completeTask(taskId)
            NotificationActionReceiver.ACTION_EXTEND -> taskViewModel.extendTaskTime(taskId, minutes)
            NotificationActionReceiver.ACTION_SKIP -> taskViewModel.skipTask(taskId)
        }
    }

    LaunchedEffect(vpnRepository) {
        while (true) {
            networkSettings = runCatching {
                vpnRepository.getNetworkBlockSettings()
            }.getOrNull()
            delay(1_500)
        }
    }

    val view = LocalView.current
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window
        if (window != null) {
            // The app draws edge-to-edge. Transparent system bars let the
            // active Material theme continue behind the clock/status strip
            // and behind both gesture and 3-button navigation.
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !settings.darkModeEnabled
                isAppearanceLightNavigationBars = !settings.darkModeEnabled
            }
        }
    }

    com.tbtechs.focusflow.ui.theme.FocusFlowTheme(
        darkTheme = settings.darkModeEnabled,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            ErrorBoundary(screenName = "root") {
                FocusFlowNavGraph(
                    navController = navController,
                    taskViewModel = taskViewModel,
                    settingsViewModel = settingsViewModel,
                    focusSessionViewModel = focusSessionViewModel,
                    appBootViewModel = appBootViewModel,
                    statsViewModel = statsViewModel,
                    vpnRepository = vpnRepository,
                    backupCoordinator = backupCoordinator,
                    onExportBackup = {
                        exportLauncher.launch(backupCoordinator.createExportIntent())
                    },
                    onImportBackup = { replace ->
                        replaceTasksOnImport = replace
                        importLauncher.launch(backupCoordinator.createImportIntent())
                    },
                    pendingImportUri = pendingImportUri,
                    initialReplaceTasks = replaceTasksOnImport,
                    onImportFinished = {
                        pendingImportUri = null
                        replaceTasksOnImport = false
                        navController.popBackStack()
                    },
                )
            }
            FocusFlowSplashOverlay(
                visible = isLoading || !isDbReady,
                modifier = Modifier.fillMaxSize(),
            )

            networkSettings?.let { policy ->
                VpnPermissionLostBanner(
                    vpnBlockEnabled = policy.enabled && policy.vpn,
                    vpnPackages = (policy.packages + policy.standalonePackages).distinct(),
                    vpnRepository = vpnRepository,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.BottomCenter),
                contentAlignment = Alignment.BottomCenter,
            ) {
                ErrorAlertBanner(onViewLogs = { diagnosticsVisible = true })
            }

            AchievementCelebrationModal(
                visible = newlyEarned != null && newlyEarned.id != dismissedAchievementId,
                achievement = newlyEarned,
                onDismiss = { dismissedAchievementId = newlyEarned?.id },
            )
        }

        DiagnosticsModal(
            visible = diagnosticsVisible,
            logs = diagnosticEvents,
            onRefresh = { diagnosticEvents = startupDiagnosticEntries() },
            onClearLogs = {
                StartupLogger.clear()
                diagnosticEvents = emptyList()
            },
            onClose = { diagnosticsVisible = false },
        )
    }
}

private fun startupDiagnosticEntries(): List<DiagnosticLogEntry> =
    StartupLogger.recent(200).map { entry ->
        DiagnosticLogEntry(
            timestamp = entry.timestamp,
            level = DiagnosticLogLevel.valueOf(entry.level.name),
            tag = entry.tag,
            message = entry.message,
        )
    }
