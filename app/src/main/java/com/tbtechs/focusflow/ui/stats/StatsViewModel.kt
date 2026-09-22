package com.tbtechs.focusflow.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.tbtechs.focusflow.analytics.AchievementEngine
import com.tbtechs.focusflow.analytics.AchievementState
import com.tbtechs.focusflow.analytics.AnalyticsProcessor
import com.tbtechs.focusflow.analytics.AnalyticsSnapshot
import com.tbtechs.focusflow.analytics.AnalyticsWindow
import com.tbtechs.focusflow.analytics.ANALYTICS_THREE_MONTHS
import com.tbtechs.focusflow.analytics.ANALYTICS_ALL_TIME
import com.tbtechs.focusflow.analytics.ANALYTICS_TODAY
import com.tbtechs.focusflow.analytics.ANALYTICS_WEEK
import com.tbtechs.focusflow.analytics.InsightCard
import com.tbtechs.focusflow.analytics.InsightEngine
import com.tbtechs.focusflow.analytics.LifetimeStats
import com.tbtechs.focusflow.data.local.entity.BehaviouralHypothesisEntity
import com.tbtechs.focusflow.data.local.entity.ClarifyingQuestionEntity
import com.tbtechs.focusflow.data.local.entity.DayRatingEntity
import com.tbtechs.focusflow.data.local.entity.FindingEntity
import com.tbtechs.focusflow.data.repository.BehaviouralHypothesisRepository
import com.tbtechs.focusflow.data.repository.ClarifyingQuestionRepository
import com.tbtechs.focusflow.data.repository.DayRatingRepository
import com.tbtechs.focusflow.data.repository.FindingRepository
import com.tbtechs.focusflow.data.repository.DayRatingRepository.RatableDateEntry
import com.tbtechs.focusflow.di.AppModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class StatsLoadState {
    data object Loading : StatsLoadState()
    data object Ready : StatsLoadState()
    data object PermissionNeeded : StatsLoadState()
    data object Unavailable : StatsLoadState()
    data class Error(val cause: Throwable) : StatsLoadState()
}

class StatsViewModel(
    private val analyticsProcessor: AnalyticsProcessor,
    private val insightEngine: InsightEngine,
    private val achievementEngine: AchievementEngine,
    private val dayRatingRepository: DayRatingRepository,
    private val findingRepository: FindingRepository,
    private val hypothesisRepository: BehaviouralHypothesisRepository,
    private val clarifyingQuestionRepository: ClarifyingQuestionRepository,
) : ViewModel() {
    private val _analyticsSnapshot = MutableStateFlow<AnalyticsSnapshot?>(null)
    private val _insightCards = MutableStateFlow<List<InsightCard>>(emptyList())
    private val _weeklyStandout = MutableStateFlow<InsightCard?>(null)
    private val _achievementState = MutableStateFlow<AchievementState?>(null)
    private val _lifetimeStats = MutableStateFlow<LifetimeStats?>(null)
    private val _loadState = MutableStateFlow<StatsLoadState>(StatsLoadState.Loading)
    // The archived RN stats screen opens on Today. Keep that same first view
    // so a fresh install does not land on a denser historical report.
    private val _activeWindow = MutableStateFlow<AnalyticsWindow>(ANALYTICS_TODAY)

    val analyticsSnapshot: StateFlow<AnalyticsSnapshot?> = _analyticsSnapshot.asStateFlow()
    val insightCards: StateFlow<List<InsightCard>> = _insightCards.asStateFlow()
    val weeklyStandout: StateFlow<InsightCard?> = _weeklyStandout.asStateFlow()
    val achievementState: StateFlow<AchievementState?> = _achievementState.asStateFlow()
    val lifetimeStats: StateFlow<LifetimeStats?> = _lifetimeStats.asStateFlow()
    val loadState: StateFlow<StatsLoadState> = _loadState.asStateFlow()
    val activeWindow: StateFlow<AnalyticsWindow> = _activeWindow.asStateFlow()

    private val _selectedRatingDate = MutableStateFlow(todayLocalDate())
    private val _currentRating = MutableStateFlow<DayRatingEntity?>(null)
    private val _ratableDates = MutableStateFlow<List<RatableDateEntry>>(emptyList())
    private val _suggestedChips = MutableStateFlow<List<SuggestedChip>>(emptyList())
    private val _activeFindings = MutableStateFlow<List<FindingEntity>>(emptyList())
    private val _pendingQuestion = MutableStateFlow<ClarifyingQuestionEntity?>(null)
    private val _needsColdStart = MutableStateFlow(false)
    var dataHealthDayCount: Int = 0
        private set
    var totalRatingCount: Int = 0
        private set

    val selectedRatingDate: StateFlow<String> = _selectedRatingDate.asStateFlow()
    val currentRating: StateFlow<DayRatingEntity?> = _currentRating.asStateFlow()
    val ratableDates: StateFlow<List<RatableDateEntry>> = _ratableDates.asStateFlow()
    val suggestedChips: StateFlow<List<SuggestedChip>> = _suggestedChips.asStateFlow()
    val activeFindings: StateFlow<List<FindingEntity>> = _activeFindings.asStateFlow()
    val pendingQuestion: StateFlow<ClarifyingQuestionEntity?> = _pendingQuestion.asStateFlow()
    val needsColdStart: StateFlow<Boolean> = _needsColdStart.asStateFlow()

    private var loadJob: Job? = null

    init {
        reload()
    }

    fun setWindow(window: AnalyticsWindow) {
        if (window !in setOf("yesterday", ANALYTICS_TODAY, ANALYTICS_WEEK, ANALYTICS_THREE_MONTHS, ANALYTICS_ALL_TIME)) return
        _activeWindow.value = window
        reload()
    }

    fun reload() {
        loadJob?.cancel()
        val window = _activeWindow.value
        loadJob = viewModelScope.launch {
            _loadState.value = StatsLoadState.Loading
            _weeklyStandout.value = null
            try {
                val usagePermission = if (window == ANALYTICS_THREE_MONTHS) {
                    analyticsProcessor.hasUsageStatsPermission()
                } else {
                    null
                }
                if (window == ANALYTICS_THREE_MONTHS && usagePermission != true) {
                    _analyticsSnapshot.value = null
                    _insightCards.value = emptyList()
                    _achievementState.value = null
                    _loadState.value = StatsLoadState.PermissionNeeded
                    launch { loadRatingData() }
                    launch { loadFindingData() }
                    return@launch
                }

                val snapshot = analyticsProcessor.buildAnalyticsSnapshot(
                    window = window,
                    options = com.tbtechs.focusflow.analytics.AnalyticsBuildOptions(
                        usageStatsPermission = usagePermission,
                    ),
                )
                _analyticsSnapshot.value = snapshot
                _insightCards.value = insightEngine.buildInsights(snapshot)
                // Keep achievement persistence independent from weekly
                // standout recording. A transient weekly-ledger failure
                // should not prevent newly earned achievements from syncing.
                _achievementState.value = achievementEngine.syncAchievements(snapshot)
                _lifetimeStats.value = analyticsProcessor.getLifetimeStats()
                if (window == ANALYTICS_WEEK) {
                    _weeklyStandout.value = insightEngine.syncWeeklyStandout(snapshot)
                }
                _loadState.value = if (hasUsableStats(snapshot)) {
                    StatsLoadState.Ready
                } else {
                    StatsLoadState.Unavailable
                }
                launch {
                    loadRatingData()
                }
                launch {
                    loadFindingData()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _loadState.value = StatsLoadState.Error(error)
            }
        }
    }

    private fun hasUsableStats(snapshot: AnalyticsSnapshot): Boolean {
        val hasRecords = snapshot.tasks.total > 0 ||
            snapshot.sessions.total > 0 ||
            snapshot.blocking.totalAttempts > 0 ||
            (snapshot.trends?.weeksWithData ?: 0) > 0 ||
            snapshot.phoneUsage?.byHour?.values?.any { it > 0.0 } == true
        return hasRecords
    }

    fun selectRatingDate(date: String) {
        _selectedRatingDate.value = date
        viewModelScope.launch {
            _currentRating.value = dayRatingRepository.getForDate(date)
            _suggestedChips.value = buildSuggestedChips(date)
        }
    }

    fun loadChipsForDate(date: String) {
        viewModelScope.launch {
            _suggestedChips.value = buildSuggestedChips(date)
        }
    }

    fun submitRating(
        date: String,
        rating: Int,
        contextTag: String?,
        note: String?,
        appTags: List<String>,
        wordTags: List<String>,
    ) {
        val safeRating = rating.coerceIn(1, 10)
        viewModelScope.launch {
            val now = java.time.Instant.now().toString()
            val existing = dayRatingRepository.getForDate(date)
            val entity = existing?.copy(
                rating = safeRating,
                contextTag = contextTag,
                note = note?.take(200),
                appTags = appTags.toStorageValue(),
                wordTags = wordTags.toStorageValue(),
                updatedAt = now,
            ) ?: DayRatingEntity(
                date = date,
                rating = safeRating,
                contextTag = contextTag,
                note = note?.take(200),
                appTags = appTags.toStorageValue(),
                wordTags = wordTags.toStorageValue(),
                createdAt = now,
                updatedAt = now,
            )
            dayRatingRepository.upsert(entity)
            if (date == _selectedRatingDate.value) {
                _currentRating.value = entity
            }
            totalRatingCount = dayRatingRepository.count()
        }
    }

    fun markFindingSeen(id: String) {
        viewModelScope.launch {
            findingRepository.markSeen(id)
            _activeFindings.value = findingRepository.getActiveFindings()
        }
    }

    fun acknowledgeFindingIntentional(id: String, fingerprint: String) {
        viewModelScope.launch {
            findingRepository.acknowledgeIntentional(id, fingerprint, null)
            _activeFindings.value = findingRepository.getActiveFindings()
        }
    }

    fun acknowledgeFindingAware(id: String, fingerprint: String) {
        viewModelScope.launch {
            findingRepository.acknowledgeAware(id, fingerprint)
            _activeFindings.value = findingRepository.getActiveFindings()
        }
    }

    fun answerClarifyingQuestion(id: String, response: String) {
        viewModelScope.launch {
            clarifyingQuestionRepository.answer(id, response)
            _pendingQuestion.value = clarifyingQuestionRepository.getPending()
        }
    }

    fun saveColdStartAnswers(answers: List<BehaviouralHypothesisEntity>) {
        viewModelScope.launch {
            answers.forEach { hypothesisRepository.save(it) }
            _needsColdStart.value = false
        }
    }

    private suspend fun loadRatingData() {
        val db = AppModule.database
        _currentRating.value = dayRatingRepository.getForDate(_selectedRatingDate.value)
        _ratableDates.value = dayRatingRepository.getRatableDates(
            dailyAppUsageDao = db.dailyAppUsageDao(),
            taskDao = db.taskDao(),
        )
        totalRatingCount = dayRatingRepository.count()
        dataHealthDayCount = runCatching {
            db.dailyAppUsageDao().countDistinctDates()
        }.getOrDefault(0)
    }

    private suspend fun loadFindingData() {
        _activeFindings.value = findingRepository.getActiveFindings()
        _pendingQuestion.value = clarifyingQuestionRepository.getPending()
        _needsColdStart.value = hypothesisRepository.needsColdStart()
    }

    private suspend fun buildSuggestedChips(date: String): List<SuggestedChip> {
        val chips = mutableListOf<SuggestedChip>()
        runCatching {
            AppModule.database.dailyAppUsageDao()
                .getForDateRange(date, date)
                .filter { it.foregroundMs >= 20 * 60_000L }
                .sortedByDescending { it.foregroundMs }
                .take(3)
                .forEach { row ->
                    chips += SuggestedChip(ChipType.APP, row.appName, row.packageName)
                }
        }
        _analyticsSnapshot.value?.let { snapshot ->
            if (snapshot.blocking.totalAttempts > 10) {
                chips += SuggestedChip(ChipType.WORD, "distracted")
            }
            val completionRate = if (snapshot.tasks.total > 0) {
                snapshot.tasks.completed.toFloat() / snapshot.tasks.total
            } else {
                1f
            }
            if (completionRate < 0.3f && snapshot.tasks.total >= 2) {
                chips += SuggestedChip(ChipType.WORD, "low energy")
            }
            if (completionRate > 0.85f && snapshot.tasks.total >= 3) {
                chips += SuggestedChip(ChipType.WORD, "on track")
            }
        }
        return chips
    }

    private fun todayLocalDate(): String =
        java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return StatsViewModel(
                    analyticsProcessor = AppModule.analyticsProcessor,
                    insightEngine = AppModule.insightEngine,
                    achievementEngine = AppModule.achievementEngine,
                    dayRatingRepository = AppModule.dayRatingRepository,
                    findingRepository = AppModule.findingRepository,
                    hypothesisRepository = AppModule.behaviouralHypothesisRepository,
                    clarifyingQuestionRepository = AppModule.clarifyingQuestionRepository,
                ) as T
            }

            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return StatsViewModel(
                    analyticsProcessor = AppModule.analyticsProcessor,
                    insightEngine = AppModule.insightEngine,
                    achievementEngine = AppModule.achievementEngine,
                    dayRatingRepository = AppModule.dayRatingRepository,
                    findingRepository = AppModule.findingRepository,
                    hypothesisRepository = AppModule.behaviouralHypothesisRepository,
                    clarifyingQuestionRepository = AppModule.clarifyingQuestionRepository,
                ) as T
            }
        }
    }
}

private fun List<String>.toStorageValue(): String =
    kotlinx.serialization.json.Json.encodeToString(this)