package com.tbtechs.focusflow

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.navigation.compose.rememberNavController
import com.tbtechs.focusflow.data.repository.AlarmCapabilitySnapshotRecord
import com.tbtechs.focusflow.data.repository.SetupPersistenceManager
import com.tbtechs.focusflow.data.repository.StartupLogger
import com.tbtechs.focusflow.data.repository.VpnRepository
import com.tbtechs.focusflow.di.AppModule
import com.tbtechs.focusflow.data.restore.RestoreGate
import com.tbtechs.focusflow.data.restore.RestoreUiState
import com.tbtechs.focusflow.enforcement.AppBlockerAccessibilityService
import com.tbtechs.focusflow.enforcement.LauncherActivity
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
import com.tbtechs.focusflow.ui.common.InAppNotice
import com.tbtechs.focusflow.ui.common.InAppNoticeStrip
import com.tbtechs.focusflow.ui.common.InAppNoticeTone
import com.tbtechs.focusflow.ui.navigation.FocusFlowNavGraph
import com.tbtechs.focusflow.ui.navigation.Routes
import com.tbtechs.focusflow.ui.navigation.RouteTextScaleContext
import com.tbtechs.focusflow.ui.stats.StatsViewModel
import com.tbtechs.focusflow.ui.support.DiagnosticLogEntry
import com.tbtechs.focusflow.ui.support.DiagnosticLogLevel
import com.tbtechs.focusflow.ui.support.DiagnosticsModal
import com.tbtechs.focusflow.ui.splash.FocusFlowSplashOverlay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Normal app activity host. LauncherActivity remains a separate CATEGORY_HOME
 * activity and is intentionally not part of this NavHost.
 */
class MainActivity : ComponentActivity() {
    private val vpnRepository by lazy {
        VpnRepository(applicationContext, AppModule.restoreGate)
    }
    private var requestedRoute by mutableStateOf(Routes.HOME)
    private var focusDayRating by mutableStateOf(false)
    private var notificationEventNonce by mutableStateOf(0)
    private var resumeNonce by mutableStateOf(0)
    private var externalBackupUri by mutableStateOf<Uri?>(null)
    private var externalBackupRequestId by mutableStateOf(0)
    private var externalFileUriNoticeId by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StartupLogger.info("MainActivity", "Main activity created")
        externalBackupUri = externalBackupUriFromIntent(intent)
        if (externalBackupUri != null) {
            externalBackupRequestId += 1
            requestedRoute = Routes.HOME
        } else if (isUnsupportedFileUriIntent(intent)) {
            externalFileUriNoticeId += 1
            requestedRoute = Routes.HOME
        } else {
            requestedRoute = routeFromIntent(intent)
        }
        focusDayRating = intent?.action == LauncherActivity.ACTION_OPEN_DAY_RATING
        setTheme(R.style.Theme_FocusFlow)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            FocusFlowRoot(
                requestedRoute = requestedRoute,
                focusDayRating = focusDayRating,
                notificationEventNonce = notificationEventNonce,
                resumeNonce = resumeNonce,
                vpnRepository = vpnRepository,
                externalBackupUri = externalBackupUri,
                externalBackupRequestId = externalBackupRequestId,
                externalFileUriNoticeId = externalFileUriNoticeId,
                onExternalBackupConsumed = ::consumeExternalBackupIntent,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        StartupLogger.info("MainActivity", "Main activity received a new intent")
        setIntent(intent)
        val openedBackupUri = externalBackupUriFromIntent(intent)
        if (openedBackupUri != null) {
            externalBackupUri = openedBackupUri
            externalBackupRequestId += 1
        } else if (isUnsupportedFileUriIntent(intent)) {
            externalFileUriNoticeId += 1
            requestedRoute = Routes.HOME
        } else {
            requestedRoute = routeFromIntent(intent)
        }
        focusDayRating = intent.action == LauncherActivity.ACTION_OPEN_DAY_RATING
        notificationEventNonce++
    }

    private fun consumeExternalBackupIntent(requestId: Int) {
        if (requestId != externalBackupRequestId) return
        val consumedUri = externalBackupUri
        externalBackupUri = null
        if (consumedUri != null && intent?.data == consumedUri) {
            setIntent(Intent(this, MainActivity::class.java))
        }
    }

    private fun externalBackupUriFromIntent(intent: Intent?): Uri? =
        intent
            ?.takeIf { it.action == Intent.ACTION_VIEW }
            ?.data
            ?.takeIf { it.scheme == "content" }

    private fun isUnsupportedFileUriIntent(intent: Intent?): Boolean =
        intent
            ?.takeIf { it.action == Intent.ACTION_VIEW }
            ?.data
            ?.scheme == "file"

    override fun onStart() {
        super.onStart()
        val setupPersistence = SetupPersistenceManager(applicationContext)
        if (setupPersistence.isUserConsentedBackgroundService() &&
            setupPersistence.isOnboardingComplete()
        ) {
            AppModule.foregroundServiceController.ensureRunning()
        }
        AppModule.requestTaskAlarmReconciliation("activity_start")
    }

    override fun onResume() {
        super.onResume()
        resumeNonce++
        // Also re-check after returning from exact-alarm or notification
        // settings; Android does not broadcast exact-alarm revocation.
        AppModule.requestTaskAlarmReconciliation("activity_resume")
    }

    private fun routeFromIntent(intent: Intent?): String =
        if (intent?.action == LauncherActivity.ACTION_OPEN_DAY_RATING) {
            Routes.STATS
        } else {
            Routes.fromPath(intent?.data?.path)
        }
}

@Composable
private fun FocusFlowRoot(
    requestedRoute: String,
    focusDayRating: Boolean,
    notificationEventNonce: Int,
    resumeNonce: Int,
    vpnRepository: VpnRepository,
    externalBackupUri: Uri?,
    externalBackupRequestId: Int,
    externalFileUriNoticeId: Int,
    onExternalBackupConsumed: (Int) -> Unit,
) {
    val context = LocalContext.current
    val uiScope = rememberCoroutineScope()
    val navController = rememberNavController()
    val settingsViewModel = remember {
        SettingsViewModel(
            AppModule.settingsRepository,
            AppModule.pinManager,
            context,
            AppModule.restoreGate,
        )
    }
    val focusSessionViewModel = remember {
        FocusSessionViewModel(
            focusSessionRepository = AppModule.focusSessionRepository,
            taskRepository = AppModule.taskRepository,
            settingsRepository = AppModule.settingsRepository,
            context = context,
            foregroundServiceController = AppModule.foregroundServiceController,
            taskAlarmReconciler = AppModule.taskAlarmReconciler,
            restoreGate = AppModule.restoreGate,
        )
    }
    val taskViewModel = remember {
        TaskViewModel(
            taskRepository = AppModule.taskRepository,
            alarmRepository = AppModule.alarmRepository,
            taskAlarmReconciler = AppModule.taskAlarmReconciler,
            focusSessionRepository = AppModule.focusSessionRepository,
            foregroundServiceController = AppModule.foregroundServiceController,
            settingsRepository = AppModule.settingsRepository,
            schedulerEngine = AppModule.schedulerEngine,
            beforeTaskDelete = { taskId, pinHash ->
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
    val restoreState by AppModule.restoreCoordinator.state.collectAsState()
    val restoreGateState by AppModule.restoreGate.state.collectAsState()
    val statsViewModel = remember {
        StatsViewModel(
            analyticsProcessor = AppModule.analyticsProcessor,
            insightEngine = AppModule.insightEngine,
            achievementEngine = AppModule.achievementEngine,
            dayRatingRepository = AppModule.dayRatingRepository,
            findingRepository = AppModule.findingRepository,
            hypothesisRepository = AppModule.behaviouralHypothesisRepository,
            clarifyingQuestionRepository = AppModule.clarifyingQuestionRepository,
            settingsRepository = AppModule.settingsRepository,
        )
    }
    val backupCoordinator = remember {
        BackupCoordinator(
            context = context,
            taskRepository = AppModule.taskRepository,
            focusSessionRepository = AppModule.focusSessionRepository,
            settingsRepository = AppModule.settingsRepository,
            settingsViewModel = settingsViewModel,
            restoreCoordinator = AppModule.restoreCoordinator,
            restoreGate = AppModule.restoreGate,
        )
    }
    val pendingImportAvailable = backupCoordinator.restorePendingAvailable()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var replaceTasksOnImport by remember { mutableStateOf(false) }
    var pendingImportGeneration by remember { mutableStateOf(0) }
    var noticeId by remember { mutableStateOf(0) }
    var inAppNotice by remember { mutableStateOf<InAppNotice?>(null) }
    var importProgressNoticeId by remember { mutableStateOf<Int?>(null) }

    fun showInAppNotice(
        message: String,
        tone: InAppNoticeTone = InAppNoticeTone.INFO,
        dismissAfterMillis: Long? = 4_500,
    ) {
        noticeId += 1
        inAppNotice = InAppNotice(
            id = noticeId,
            message = message,
            tone = tone,
            dismissAfterMillis = dismissAfterMillis,
        )
    }

    fun showImportProgress(message: String) {
        showInAppNotice(
            message = message,
            tone = InAppNoticeTone.LOADING,
            dismissAfterMillis = null,
        )
        importProgressNoticeId = noticeId
    }

    fun clearImportProgress() {
        if (inAppNotice?.id == importProgressNoticeId) {
            inAppNotice = null
        }
        importProgressNoticeId = null
    }

    fun stageImportFromUri(source: Uri, externalRequestId: Int? = null) {
        showImportProgress("Reading backup…")
        scope.launch {
            val staged = try {
                withContext(Dispatchers.IO) {
                    backupCoordinator.stageImport(source)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Result.failure(error)
            }

            externalRequestId?.let(onExternalBackupConsumed)
            clearImportProgress()
            if (staged.isSuccess) {
                pendingImportGeneration += 1
            } else {
                showInAppNotice(
                    "Import failed: " +
                        (staged.exceptionOrNull()?.message ?: "The selected backup could not be read."),
                    InAppNoticeTone.WARNING,
                )
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val destination = result.data?.data
        if (destination == null) {
            showInAppNotice("Backup export cancelled.")
        } else {
            showInAppNotice(
                message = "Saving backup…",
                tone = InAppNoticeTone.LOADING,
                dismissAfterMillis = null,
            )
            scope.launch {
                val outcome = try {
                    withContext(Dispatchers.IO) {
                        backupCoordinator.export(settings, destination)
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    null
                }
                if (outcome?.ok == true) {
                    showInAppNotice("Backup exported successfully.", InAppNoticeTone.SUCCESS)
                } else {
                    showInAppNotice(
                        outcome?.error?.let { "Backup export failed: $it" }
                            ?: "Backup export failed. Please try again.",
                        InAppNoticeTone.WARNING,
                    )
                }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val source = result.data?.data
        if (source == null) {
            showInAppNotice("Backup import cancelled.")
        } else {
            stageImportFromUri(source)
        }
    }

    var handledExternalBackupRequestId by remember { mutableStateOf(0) }
    LaunchedEffect(externalBackupUri, externalBackupRequestId) {
        val source = externalBackupUri ?: return@LaunchedEffect
        if (externalBackupRequestId <= 0 ||
            externalBackupRequestId == handledExternalBackupRequestId
        ) {
            return@LaunchedEffect
        }
        handledExternalBackupRequestId = externalBackupRequestId
        stageImportFromUri(source, externalBackupRequestId)
    }

    var handledExternalFileUriNoticeId by remember { mutableStateOf(0) }
    LaunchedEffect(externalFileUriNoticeId) {
        if (
            externalFileUriNoticeId <= 0 ||
            externalFileUriNoticeId == handledExternalFileUriNoticeId
        ) {
            return@LaunchedEffect
        }
        handledExternalFileUriNoticeId = externalFileUriNoticeId
        showInAppNotice(
            "Unsupported file location. Use Import in Settings.",
            InAppNoticeTone.WARNING,
        )
    }

    var diagnosticsVisible by remember { mutableStateOf(false) }
    LaunchedEffect(
        isDbReady,
        pendingImportGeneration,
        privacyAccepted,
        onboardingComplete,
        pendingImportAvailable,
        diagnosticsVisible,
    ) {
        if (
            isDbReady &&
            privacyAccepted &&
            onboardingComplete &&
            pendingImportAvailable &&
            RouteTextScaleContext.routeBase(navController.currentDestination?.route) !=
            Routes.IMPORT_CONFIRM
        ) {
            val sourceTab = if (pendingImportGeneration > 0) {
                navController.currentBackStackEntry?.let { entry ->
                    RouteTextScaleContext.sourceTabForDestination(
                        currentRoute = entry.destination.route,
                        currentSourceTab = entry.arguments
                            ?.getString(RouteTextScaleContext.SOURCE_TAB_ARGUMENT),
                        destinationRoute = Routes.IMPORT_CONFIRM,
                    )
                }
            } else {
                null
            }
            navController.navigate(
                RouteTextScaleContext.routeWithSourceTab(
                    Routes.IMPORT_CONFIRM,
                    sourceTab,
                ),
            ) {
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(restoreState) {
        if (restoreState is RestoreUiState.Completed) {
            settingsViewModel.refreshFromStore()
        }
    }
    var diagnosticEvents by remember { mutableStateOf(startupDiagnosticEntries()) }
    var alarmCapabilitySnapshots by remember {
        mutableStateOf(AppModule.alarmRepository.capabilitySnapshots())
    }
    var showFullScreenIntentPrompt by remember { mutableStateOf(false) }
    var dismissedAchievementId by remember { mutableStateOf<String?>(null) }
    var showDiscardRestorePrompt by remember { mutableStateOf(false) }
    val achievementState by statsViewModel.achievementState.collectAsState()
    val newlyEarned = achievementState?.newlyEarnedIds.orEmpty()
        .firstOrNull()
        ?.let { id -> achievementState?.definitions?.firstOrNull { it.id == id } }

    // Set this synchronously after both VMs exist. AppBootViewModel starts its
    // coroutine from init, so assigning it later in LaunchedEffect could miss
    // an active-session recovery on a fast database.
    appBootViewModel.onSessionRecovered = focusSessionViewModel::loadActiveSession

    LaunchedEffect(
        resumeNonce,
        isDbReady,
        privacyAccepted,
        onboardingComplete,
        pendingImportAvailable,
    ) {
        if (
            resumeNonce > 0 &&
            isDbReady &&
            privacyAccepted &&
            onboardingComplete &&
            !pendingImportAvailable &&
            !diagnosticsVisible &&
            Build.VERSION.SDK_INT >= 34 &&
            !AppModule.alarmRepository.canUseFullScreenIntent() &&
            AppModule.alarmRepository.markFullScreenIntentPromptShownOnce()
        ) {
            showFullScreenIntentPrompt = true
        }
    }

    LaunchedEffect(diagnosticsVisible) {
        if (diagnosticsVisible) {
            diagnosticEvents = startupDiagnosticEntries()
            alarmCapabilitySnapshots = AppModule.alarmRepository.capabilitySnapshots()
        }
    }

    LaunchedEffect(Unit) {
        AppErrorEvents.events.collect {
            diagnosticEvents = startupDiagnosticEntries()
        }
    }

    LaunchedEffect(
        requestedRoute,
        isDbReady,
        privacyAccepted,
        onboardingComplete,
        pendingImportAvailable,
    ) {
        if (!isDbReady) return@LaunchedEffect
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        val isCurrentlyInHowToUse =
            RouteTextScaleContext.routeBase(currentRoute) == Routes.HOW_TO_USE

        val guardedRoute = when {
            !privacyAccepted && requestedRoute != Routes.PRIVACY_POLICY -> Routes.PRIVACY_POLICY
            privacyAccepted && !onboardingComplete &&
                requestedRoute != Routes.PRIVACY_POLICY &&
                requestedRoute != Routes.ONBOARDING -> Routes.ONBOARDING
            pendingImportAvailable -> Routes.IMPORT_CONFIRM
            else -> requestedRoute
        }

        // When user just completed onboarding and is viewing the How-To-Use onboarding tour,
        // do not yank them away to HOME.
        if (isCurrentlyInHowToUse && onboardingComplete) {
            return@LaunchedEffect
        }

        if (
            RouteTextScaleContext.routeBase(guardedRoute) != Routes.HOME &&
            RouteTextScaleContext.routeBase(currentRoute) !=
            RouteTextScaleContext.routeBase(guardedRoute)
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
        if (action.isNullOrBlank() || taskId.isNullOrBlank()) {
            return@LaunchedEffect
        }
        val expired = timestamp <= 0L ||
            System.currentTimeMillis() - timestamp > 5 * 60 * 1_000L
        if (expired) {
            prefs.edit()
                .remove(NotificationActionReceiver.PREF_PENDING_ACTION)
                .remove(NotificationActionReceiver.PREF_PENDING_TASK_ID)
                .remove(NotificationActionReceiver.PREF_PENDING_MINUTES)
                .remove(NotificationActionReceiver.PREF_PENDING_TIME_MS)
                .apply()
            Toast.makeText(
                context,
                "That action expired — open the task to try again.",
                Toast.LENGTH_LONG,
            ).show()
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

    val view = LocalView.current
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window
        if (window != null) {
            // The app draws edge-to-edge. Transparent system bars let the
            // active Material theme continue behind the clock/status strip
            // and behind both gesture and 3-button navigation.
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            // Keep the navigation bar's own surface in sync with the theme.
            // Transparent bars do not reliably refresh on 3-button navigation
            // when the user changes Dark Mode while the activity is alive.
            window.navigationBarColor = if (settings.darkModeEnabled) {
                android.graphics.Color.rgb(13, 19, 34)
            } else {
                android.graphics.Color.rgb(240, 242, 255)
            }
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
        generalTextScale = settings.generalTextScale,
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
                    pendingImportGeneration = pendingImportGeneration,
                    initialReplaceTasks = replaceTasksOnImport,
                    onImportFinished = {
                        clearImportProgress()
                        showInAppNotice(
                            "Backup imported successfully.",
                            InAppNoticeTone.SUCCESS,
                        )
                        settingsViewModel.refreshFromStore()
                        replaceTasksOnImport = false
                        navController.popBackStack()
                    },
                    onImportCancelled = {
                        clearImportProgress()
                        showInAppNotice("Backup import cancelled.")
                        replaceTasksOnImport = false
                        navController.popBackStack()
                    },
                    onImportProgressChanged = { importing ->
                        if (importing) {
                            showImportProgress("Importing backup…")
                        } else {
                            clearImportProgress()
                        }
                    },
                    onImportFailed = { message ->
                        showInAppNotice("Import failed: $message", InAppNoticeTone.WARNING)
                    },
                    onOnboardingTourFinished = {
                        // A normal cold launch starts on Schedule, but the
                        // first post-onboarding handoff intentionally lands on
                        // Defense. FocusFlowNavGraph performs that navigation
                        // immediately after this callback.
                    },
                    focusDayRating = focusDayRating,
                )
            }
            FocusFlowSplashOverlay(
                visible = isLoading || !isDbReady,
                modifier = Modifier.fillMaxSize(),
            )

            when {
                restoreState is RestoreUiState.Blocked -> {
                    val blocked = restoreState as RestoreUiState.Blocked
                    AlertDialog(
                        onDismissRequest = {},
                        title = { Text("Restore could not be completed") },
                        text = {
                            Column {
                                Text(blocked.message)
                                if (blocked.unreadableJournal) {
                                    Text(
                                        "If retrying does not help, choose Discard to keep your current data.",
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                scope.launch { AppModule.restoreCoordinator.retryRecovery() }
                            }) { Text("Retry") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDiscardRestorePrompt = true }) {
                                Text("Discard")
                            }
                        },
                    )
                }
                restoreGateState != RestoreGate.State.OPEN -> {
                    Dialog(
                        onDismissRequest = {},
                        properties = DialogProperties(
                            dismissOnBackPress = false,
                            dismissOnClickOutside = false,
                        ),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(28.dp),
                        ) {
                            CircularProgressIndicator()
                            Text(
                                "Finishing restore…",
                                modifier = Modifier.padding(top = 16.dp),
                            )
                        }
                    }
                }
            }

            if (showDiscardRestorePrompt) {
                AlertDialog(
                    onDismissRequest = { showDiscardRestorePrompt = false },
                    title = { Text("Discard this restore?") },
                    text = {
                        Text(
                            "Some tasks or settings may already have changed. Discarding keeps the current data, repairs derived state where possible, and reopens the app.",
                        )
                    },
                    confirmButton = {
                        Button(onClick = {
                            showDiscardRestorePrompt = false
                            scope.launch {
                                val result = AppModule.restoreCoordinator.discardRecovery()
                                if (result.isFailure) {
                                    val reopened =
                                        AppModule.restoreGate.state.value == RestoreGate.State.OPEN
                                    Toast.makeText(
                                        context,
                                        if (reopened) {
                                            "The app reopened, but some derived state could not be refreshed."
                                        } else {
                                            "The restore could not be discarded. Please try again."
                                        },
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            }
                        }) { Text("Discard and reopen") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDiscardRestorePrompt = false }) {
                            Text("Keep trying")
                        }
                    },
                )
            }

            VpnPermissionLostBanner(vpnRepository)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.BottomCenter),
                contentAlignment = Alignment.BottomCenter,
            ) {
                ErrorAlertBanner(onViewLogs = { diagnosticsVisible = true })
            }

            InAppNoticeStrip(
                notice = inAppNotice,
                onDismiss = { inAppNotice = null },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 92.dp),
            )

            AchievementCelebrationModal(
                visible = newlyEarned != null && newlyEarned.id != dismissedAchievementId,
                achievement = newlyEarned,
                onDismiss = { dismissedAchievementId = newlyEarned?.id },
            )
        }

        DiagnosticsModal(
            visible = diagnosticsVisible,
            logs = diagnosticEvents,
            alarmCapabilitySnapshots = alarmCapabilitySnapshots,
            onRefresh = {
                diagnosticEvents = startupDiagnosticEntries()
                alarmCapabilitySnapshots = AppModule.alarmRepository.capabilitySnapshots()
            },
            onClearLogs = {
                StartupLogger.clear()
                diagnosticEvents = emptyList()
            },
            onClose = { diagnosticsVisible = false },
        )

        if (showFullScreenIntentPrompt) {
            AlertDialog(
                onDismissRequest = { showFullScreenIntentPrompt = false },
                title = { Text("Allow full-screen task alarms?") },
                text = {
                    Text(
                        "On Android 14 and later, task-end notifications may appear without " +
                            "opening the alarm screen unless full-screen alarm access is allowed.",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showFullScreenIntentPrompt = false
                            uiScope.launch {
                                if (!AppModule.alarmRepository.requestFullScreenIntentPermission()) {
                                    AppErrorEvents.report(
                                        tag = "Task alarms",
                                        message = "Could not open full-screen alarm settings. Open Permissions to review this access.",
                                    )
                                }
                            }
                        },
                    ) {
                        Text("Open settings")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showFullScreenIntentPrompt = false }) {
                        Text("Not now")
                    }
                },
            )
        }
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
