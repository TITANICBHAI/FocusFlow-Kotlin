package com.tbtechs.focusflow.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.tbtechs.focusflow.data.model.FocusSession
import com.tbtechs.focusflow.data.repository.FocusSessionRepository
import com.tbtechs.focusflow.data.repository.ForegroundServiceController
import com.tbtechs.focusflow.data.repository.SessionPinRequiredException
import com.tbtechs.focusflow.data.repository.SettingsRepository
import com.tbtechs.focusflow.data.repository.TaskRepository
import com.tbtechs.focusflow.di.AppModule
import com.tbtechs.focusflow.enforcement.AppBlockerAccessibilityService
import com.tbtechs.focusflow.enforcement.ForegroundTaskService
import com.tbtechs.focusflow.ui.common.AppErrorEvents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import java.time.Instant

/**
 * FocusSessionViewModel
 *
 * Manages focus session lifecycle in the UI layer and keeps the UI-visible
 * session state in sync with Room and the enforcement SharedPreferences layer.
 *
 * Backed by:
 *   - [FocusSessionRepository] (Track A) — Room reads/writes
 *   - [TaskRepository]         (Track A) — task lookup for startFocusMode
 *   - [SettingsRepository]     (Replit Stage 2) — enforcement SharedPrefs sync
 *   - [ForegroundServiceController] (Replit Stage 2) — foreground service control
 *
 * ─── FLAGS ────────────────────────────────────────────────────────────────────
 *
 * FLAG-1  [focusSession] is backed by a MutableStateFlow, NOT a reactive Room
 *         Flow. FocusSessionDao has no @Query returning Flow<FocusSessionEntity?>
 *         for the active session — all queries are one-shot suspend functions.
 *         The StateFlow is populated on init and updated on startFocusMode/
 *         stopFocusMode. If the session changes externally (BootReceiver, direct
 *         DAO write), the StateFlow will NOT update until loadActiveSession() is
 *         called explicitly or the ViewModel is recreated.
 *         Fix: add observeActiveSession(): Flow<FocusSessionEntity?> to
 *         FocusSessionDao, expose it via FocusSessionRepository.
 *
 * FLAG-2  [focusViolationApp] has no backing source in any repository.
 *         AppBlockerAccessibilityService detects violations in a separate process.
 *         There is no bridge (SharedPreferences key, broadcast, ContentProvider)
 *         to propagate the violated package name to this ViewModel.
 *         Fix: write violated package to a SharedPreferences key (e.g.
 *         "current_violation_app") from AppBlockerAccessibilityService, then
 *         listen for it here via SharedPreferences.OnSharedPreferenceChangeListener.
 *
 * FLAG-3  [ForegroundServiceController] is not in AppModule. Instantiated here
 *         directly with applicationContext. Once AppModule registers it, replace
 *         the inline instantiation with AppModule.foregroundServiceController.
 *
 * FLAG-4  [startFocusMode] resolves the task via observeAllTasks().first().
 *         No-op if taskId is not found. Requires the tasks Flow to have emitted
 *         at least once (guaranteed after TaskViewModel.init completes).
 *
 * FLAG-5  The "use global allowed_packages" fallback (when task.focusAllowedPackages
 *         is null) reads the raw SharedPreferences key "allowed_packages" through
 *         the repository's compatibility string accessor. A typed list accessor
 *         would still be preferable for future cleanup.
 *
 * GPT Terra: treat the public API here as the stable contract.
 */
class FocusSessionViewModel(
    private val focusSessionRepository: FocusSessionRepository,
    private val taskRepository: TaskRepository,
    private val settingsRepository: SettingsRepository,
    context: Context,
) : ViewModel() {

    private val appContext = context.applicationContext
    // FLAG-3: not in AppModule — instantiated with applicationContext directly.
    private val foregroundServiceController = ForegroundServiceController(appContext)
    private val prefs = appContext.getSharedPreferences(
        AppBlockerAccessibilityService.PREFS_NAME,
        Context.MODE_PRIVATE,
    )
    private var taskEndedReceiverRegistered = false
    private val focusOperationMutex = Mutex()

    // ─── State ────────────────────────────────────────────────────────────────

    /**
     * Currently active focus session, or null if none is running.
     * See FLAG-1 — not backed by a reactive Room Flow.
     *
     * Initial value: loaded from Room on [init]. Updated synchronously on
     * [startFocusMode] and [stopFocusMode].
     */
    private val _focusSession = MutableStateFlow<FocusSession?>(null)
    val focusSession: StateFlow<FocusSession?> = _focusSession.asStateFlow()

    data class FocusBreakState(
        val active: Boolean,
        val untilMs: Long,
    )

    val focusBreak: StateFlow<FocusBreakState> = flow {
        while (true) {
            val untilMs = settingsRepository.getFocusBreakUntilMs()
            val active = untilMs > System.currentTimeMillis()
            if (!active && untilMs > 0L) {
                settingsRepository.setFocusBreak(active = false, untilMs = 0L)
            }
            emit(FocusBreakState(active = active, untilMs = if (active) untilMs else 0L))
            delay(1_000)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FocusBreakState(active = false, untilMs = 0L),
    )

    val todayFocusMinutes: StateFlow<Int> = flow {
        while (true) {
            emit(runCatching { focusSessionRepository.getTodayFocusMinutes() }.getOrDefault(0))
            delay(15_000)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0,
    )

    val todayOverrideCount: StateFlow<Int> = flow {
        while (true) {
            emit(runCatching { focusSessionRepository.getTodayOverrideCount() }.getOrDefault(0))
            delay(15_000)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0,
    )

    /**
     * Package name of the app that most recently triggered a focus violation,
     * or null if no violation has been detected this session.
     * See FLAG-2 — updated only via [onViolationDetected]; no enforcement bridge yet.
     */
    private val _focusViolationApp = MutableStateFlow<String?>(null)
    val focusViolationApp: StateFlow<String?> = _focusViolationApp.asStateFlow()

    private val violationPreferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == AppBlockerAccessibilityService.PREF_CURRENT_VIOLATION_APP) {
                _focusViolationApp.value = sharedPreferences.getString(key, null)
            }
        }

    private val taskEndedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ForegroundTaskService.ACTION_TASK_ENDED) return
            val taskId = intent
                .getStringExtra(ForegroundTaskService.EXTRA_TASK_ID)
                ?.takeIf { it.isNotBlank() }
                ?: return

            viewModelScope.launch {
                val activeSession = focusSessionRepository.getActiveFocusSession()
                if (activeSession?.taskId != taskId) return@launch

                focusSessionRepository.endFocusSession(taskId)
                settingsRepository.clearActiveTask()
                _focusSession.value = null
                _focusViolationApp.value = null
            }
        }
    }

    // ─── Init ─────────────────────────────────────────────────────────────────

    init {
        prefs.registerOnSharedPreferenceChangeListener(violationPreferenceListener)
        _focusViolationApp.value = prefs.getString(
            AppBlockerAccessibilityService.PREF_CURRENT_VIOLATION_APP,
            null,
        )
        val taskEndedFilter = IntentFilter(ForegroundTaskService.ACTION_TASK_ENDED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(
                taskEndedReceiver,
                taskEndedFilter,
                Context.RECEIVER_NOT_EXPORTED,
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            appContext.registerReceiver(taskEndedReceiver, taskEndedFilter)
        }
        taskEndedReceiverRegistered = true
        viewModelScope.launch {
            focusSessionRepository.observeActiveFocusSession().collect { session ->
                _focusSession.value = session
            }
        }
    }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(violationPreferenceListener)
        if (taskEndedReceiverRegistered) {
            runCatching { appContext.unregisterReceiver(taskEndedReceiver) }
            taskEndedReceiverRegistered = false
        }
        super.onCleared()
    }

    // ─── Session lifecycle ────────────────────────────────────────────────────

    /**
     * Starts focus mode for the task with [taskId].
     *
     * Sequence (fulfils the Track C TODO comments in FocusSessionRepository and
     * Risk 9 in ARCHITECTURE.md):
     *   1. Look up the task from TaskRepository (FLAG-4).
     *   2. Insert a new FocusSession row in Room.
     *   3. Mirror enforcement state to SharedPreferences (focus_active, task_id,
     *      task_end_ms, allowed_packages) so AppBlockerAccessibilityService picks
     *      up the change synchronously without a restart.
     *   4. Start the ForegroundTaskService.
     *   5. Update [focusSession] StateFlow.
     *
     * Backing calls:
     *   [TaskRepository.observeAllTasks] (.first() — one-shot)
     *   [FocusSessionRepository.startFocusSession]
     *   [SettingsRepository.setFocusActive]
     *   [SettingsRepository.setActiveTask]
     *   [SettingsRepository.setAllowedPackages]
     *   [ForegroundServiceController.startService]
     */
    fun startFocusMode(taskId: String) {
        viewModelScope.launch {
            focusOperationMutex.withLock {
                // Step 1 — resolve task (FLAG-4)
                val task = taskRepository.observeAllTasks().first()
                    .firstOrNull { it.id == taskId } ?: return@withLock
                if (task.status == "completed" || task.status == "skipped") {
                    return@withLock
                }
                if (focusSessionRepository.getActiveFocusSession() != null) {
                    return@withLock
                }

                // Resolve allowed packages:
                //   task.focusAllowedPackages != null → use task-specific list
                //   null                              → use global "allowed_packages" key (FLAG-5)
                val allowedPackages: List<String> = task.focusAllowedPackages
                    ?: run {
                        val configured = settingsRepository.readAppSettings().allowedFocusPackages
                        if (configured.isNotEmpty()) return@run configured
                        val raw = settingsRepository.getString("allowed_packages")
                        if (raw.isNullOrBlank()) emptyList()
                        else runCatching {
                            JSONArray(raw).let { arr -> (0 until arr.length()).map { arr.getString(it) } }
                        }.getOrDefault(emptyList())
                    }

                val scheduledStartMs = Instant.parse(task.startTime).toEpochMilli()
                val scheduledEndMs = Instant.parse(task.endTime).toEpochMilli()
                val now = Instant.now()
                val nowMs = now.toEpochMilli()
                val shouldStartNow = scheduledStartMs > nowMs || scheduledEndMs <= nowMs
                val startMs = if (shouldStartNow) nowMs else scheduledStartMs
                val endMs = if (shouldStartNow) {
                    nowMs + (scheduledEndMs - scheduledStartMs).coerceAtLeast(60_000L)
                } else {
                    scheduledEndMs
                }

                if (shouldStartNow) {
                    taskRepository.updateTask(
                        task.copy(
                            startTime = Instant.ofEpochMilli(startMs).toString(),
                            endTime = Instant.ofEpochMilli(endMs).toString(),
                            status = "active",
                            updatedAt = now.toString(),
                        ),
                    )
                }

                val session = FocusSession(
                    taskId          = task.id,
                    startedAt       = Instant.now().toString(),
                    isActive        = true,
                    allowedPackages = allowedPackages,
                )

                // Step 2 — Room write
                focusSessionRepository.startFocusSession(session)

                // Step 3 — mirror to enforcement SharedPreferences
                // setFocusActive(true) is not PIN-gated when starting.
                settingsRepository.setFocusActive(active = true)
                settingsRepository.setActiveTask(
                    taskId = task.id,
                    name = task.title,
                    endMs = endMs,
                    nextName = null,
                )
                settingsRepository.setActiveTaskColor(task.color)
                settingsRepository.setActiveTaskStartMs(task.id, startMs)
                settingsRepository.setAllowedPackages(allowedPackages)

                // Step 4 — start the foreground service
                foregroundServiceController.startService(
                    taskId      = task.id,
                    taskName    = task.title,
                    startTimeMs = startMs,
                    endTimeMs   = endMs,
                    nextName    = null,
                )

                // Step 5 — update UI state
                _focusSession.value = session
            }
        }
    }

    /**
     * Stops the currently active focus session.
     *
     * Sequence:
     *   1. Validate and clear enforcement SharedPreferences (focus_active=false).
     *   2. End the session row in Room and clear its active-task snapshot.
     *   3. Stop ForegroundTaskService via the internal lifecycle path.
     *   4. Clear [focusSession] and [focusViolationApp] StateFlows.
     *
     * [pinHash] is the SHA-256 hex digest of the verified focus-session PIN.
     * The repository enforces it before clearing the active session, so a
     * missing hash fails closed when a focus PIN is configured.
     *
     * Backing calls:
     *   [FocusSessionRepository.endFocusSession]
     *   [SettingsRepository.setFocusActive]
     *   [SettingsRepository.clearActiveTask]
     *   [ForegroundServiceController.stopServiceInternal]
     */
    fun stopFocusMode(pinHash: String? = null) {
        viewModelScope.launch {
            try {
                stopFocusModeAwait(pinHash)
            } catch (error: SessionPinRequiredException) {
                AppErrorEvents.report(
                    tag = "FocusSession",
                    message = "A focus session password is required to stop Focus Mode.",
                    throwable = error,
                )
            }
        }
    }

    /**
     * Synchronous suspend form used by serialized task deletion/clear flows.
     * It reloads Room when the in-memory StateFlow is empty so a task cannot be
     * deleted while a recovered native session still owns enforcement.
     */
    suspend fun stopFocusModeAwait(pinHash: String? = null) {
            val current = _focusSession.value
                ?: focusSessionRepository.getActiveFocusSession()
                ?: return

            // Step 1 — validate the PIN before any Room mutation, then clear
            // the enforcement flag. The repository fails closed here.
            settingsRepository.setFocusActive(active = false, pinHash = pinHash)

            // Step 2 — Room write and active-task cleanup
            focusSessionRepository.endFocusSession(current.taskId)
            settingsRepository.clearActiveTask()

            // Step 3 — stop foreground service (internal / non-PIN-gated path)
            foregroundServiceController.stopServiceInternal()

            // Auto-copy to always-on if the user has that preference enabled
            val snap = settingsRepository.readAppSettings()
            if (snap.autoCopyToAlwaysOn && snap.standaloneBlockPackages.isNotEmpty()) {
                val merged = (snap.alwaysBlockPackages + snap.standaloneBlockPackages).distinct().sorted()
                settingsRepository.setAlwaysBlockActive(active = merged.isNotEmpty(), packages = merged)
            }

            // Step 4 — clear UI state
            _focusSession.value      = null
            _focusViolationApp.value = null
    }

    suspend fun stopFocusModeForTaskAwait(taskId: String, pinHash: String? = null) {
        val current = _focusSession.value
            ?: focusSessionRepository.getActiveFocusSession()
            ?: return
        if (current.taskId == taskId) stopFocusModeAwait(pinHash)
    }

    // ─── External update hooks ────────────────────────────────────────────────

    /**
     * Updates [focusViolationApp] when the enforcement layer detects a blocked-app
     * access during a focus session.
     *
     * See FLAG-2 — wire this to a BroadcastReceiver or SharedPreferences listener
     * once the enforcement bridge is implemented in AppBlockerAccessibilityService.
     */
    fun onViolationDetected(packageName: String) {
        _focusViolationApp.value = packageName
    }

    /** Records an explicit emergency/temptation override before the session ends. */
    fun recordOverride(taskId: String, reason: String) {
        viewModelScope.launch {
            focusSessionRepository.logFocusOverride(
                taskId = taskId,
                appName = "focus_session",
                reason = reason,
            )
        }
    }

    /**
     * Reloads the active session from Room.
     * Call after a process restart, BootReceiver recovery, or any out-of-band
     * change to the focus_sessions table.
     *
     * Backing call: [FocusSessionRepository.getActiveFocusSession]
     */
    fun loadActiveSession() {
        viewModelScope.launch {
            _focusSession.value = focusSessionRepository.getActiveFocusSession()
        }
    }

    fun startPomodoroBreak(minutes: Int) {
        val untilMs = System.currentTimeMillis() + minutes.coerceAtLeast(1) * 60_000L
        viewModelScope.launch {
            // Write SharedPrefs first so ForegroundTaskService can restore the break
            // on process restart, then send the live ACTION_SET_BREAK intent so the
            // in-memory breakUntilMs is updated and the break notification appears.
            settingsRepository.setFocusBreak(active = true, untilMs = untilMs)
            foregroundServiceController.setBreak(untilMs)
        }
    }

    fun endPomodoroBreak() {
        viewModelScope.launch {
            settingsRepository.setFocusBreak(active = false, untilMs = 0L)
            foregroundServiceController.clearBreak()
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    ?: runCatching { AppModule.applicationContext }.getOrNull()
                val ctx = app?.applicationContext
                    ?: throw IllegalStateException("Application context not available to instantiate FocusSessionViewModel")
                return FocusSessionViewModel(
                    focusSessionRepository = AppModule.focusSessionRepository,
                    taskRepository = AppModule.taskRepository,
                    settingsRepository = AppModule.settingsRepository,
                    context = ctx,
                ) as T
            }

            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val ctx = runCatching { AppModule.applicationContext }.getOrNull()
                    ?: throw IllegalStateException("AppModule not initialized to instantiate FocusSessionViewModel")
                return FocusSessionViewModel(
                    focusSessionRepository = AppModule.focusSessionRepository,
                    taskRepository = AppModule.taskRepository,
                    settingsRepository = AppModule.settingsRepository,
                    context = ctx,
                ) as T
            }
        }
    }
}
