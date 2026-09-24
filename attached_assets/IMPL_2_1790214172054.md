# IMPL_2 — UI Layer
## StatsViewModel additions · DayRatingBar · FindingCardView · FindingsSection · ColdStartSheet · StatsInsightsExperience integration
### Based on actual codebase (app.zip)

**Design language confirmed from code:**
- Cards: `StatsCard { }` — `RoundedCornerShape(16.dp)`, `DarkCard` background, `DarkBorder` border
- Hero cards: `StatsHeroCard { }` — same but `RoundedCornerShape(24.dp)`
- Colors: `DarkBackground`, `DarkCard`, `DarkBorder`, `BrandPrimary`, `DarkSurfaceVariant`, `DarkTextPrimary`, `DarkTextSecondary` from `ui.theme`
- Tab chips: `Box + CircleShape + background(if selected BrandPrimary else DarkSurfaceVariant)`
- Text: `fontSize = 13.sp` body, `fontSize = 15.sp` title, `MaterialTheme.typography.labelLarge` labels
- Padding inside cards: `padding(12.dp)`
- **No Material3 Card anywhere in stats UI** — always `StatsCard`
- `StatsViewModel.Factory` is in companion object — must be updated when constructor changes

---

## Part 1 — `StatsViewModel.kt` additions

### 1.1 — Updated constructor and new state flows

Replace the existing constructor and state flow declarations with:

```kotlin
class StatsViewModel(
    private val analyticsProcessor:          AnalyticsProcessor,
    private val insightEngine:               InsightEngine,
    private val achievementEngine:           AchievementEngine,
    // Phase 1 additions
    private val dayRatingRepository:         DayRatingRepository,
    private val findingRepository:           FindingRepository,
    private val hypothesisRepository:        BehaviouralHypothesisRepository,
    private val clarifyingQuestionRepository: ClarifyingQuestionRepository,
) : ViewModel() {

    // ── Existing state flows — unchanged ──────────────────────────────────────
    private val _analyticsSnapshot = MutableStateFlow<AnalyticsSnapshot?>(null)
    private val _insightCards      = MutableStateFlow<List<InsightCard>>(emptyList())
    private val _weeklyStandout    = MutableStateFlow<InsightCard?>(null)
    private val _achievementState  = MutableStateFlow<AchievementState?>(null)
    private val _lifetimeStats     = MutableStateFlow<LifetimeStats?>(null)
    private val _loadState         = MutableStateFlow<StatsLoadState>(StatsLoadState.Loading)
    private val _activeWindow      = MutableStateFlow<AnalyticsWindow>(ANALYTICS_TODAY)

    val analyticsSnapshot: StateFlow<AnalyticsSnapshot?> = _analyticsSnapshot.asStateFlow()
    val insightCards:      StateFlow<List<InsightCard>>  = _insightCards.asStateFlow()
    val weeklyStandout:    StateFlow<InsightCard?>       = _weeklyStandout.asStateFlow()
    val achievementState:  StateFlow<AchievementState?>  = _achievementState.asStateFlow()
    val lifetimeStats:     StateFlow<LifetimeStats?>     = _lifetimeStats.asStateFlow()
    val loadState:         StateFlow<StatsLoadState>     = _loadState.asStateFlow()
    val activeWindow:      StateFlow<AnalyticsWindow>    = _activeWindow.asStateFlow()

    // ── Phase 1 state flows ───────────────────────────────────────────────────
    private val _selectedRatingDate = MutableStateFlow(todayLocalDate())
    private val _currentRating      = MutableStateFlow<DayRatingEntity?>(null)
    private val _ratableDates       = MutableStateFlow<List<RatableDateEntry>>(emptyList())
    private val _suggestedChips     = MutableStateFlow<List<SuggestedChip>>(emptyList())
    private val _activeFindings     = MutableStateFlow<List<FindingEntity>>(emptyList())
    private val _pendingQuestion    = MutableStateFlow<ClarifyingQuestionEntity?>(null)
    private val _needsColdStart     = MutableStateFlow(false)
    var dataHealthDayCount: Int = 0 ; private set
    var totalRatingCount:   Int = 0 ; private set

    val selectedRatingDate: StateFlow<String>                        = _selectedRatingDate.asStateFlow()
    val currentRating:      StateFlow<DayRatingEntity?>             = _currentRating.asStateFlow()
    val ratableDates:       StateFlow<List<RatableDateEntry>>       = _ratableDates.asStateFlow()
    val suggestedChips:     StateFlow<List<SuggestedChip>>          = _suggestedChips.asStateFlow()
    val activeFindings:     StateFlow<List<FindingEntity>>          = _activeFindings.asStateFlow()
    val pendingQuestion:    StateFlow<ClarifyingQuestionEntity?>     = _pendingQuestion.asStateFlow()
    val needsColdStart:     StateFlow<Boolean>                      = _needsColdStart.asStateFlow()
```

### 1.2 — Add to `reload()` after `_loadState.value = StatsLoadState.Ready`

Inside the `try` block in `reload()`, after `_loadState.value` is set:

```kotlin
// Phase 1: load rating and findings data concurrently with analytics
launch { loadRatingData() }
launch { loadFindingData() }
```

### 1.3 — New private suspend functions (add below `hasUsableStats`)

```kotlin
private suspend fun loadRatingData() {
    val db = AppModule.database
    _currentRating.value  = dayRatingRepository.getForDate(_selectedRatingDate.value)
    _ratableDates.value   = dayRatingRepository.getRatableDates(
        dailyAppUsageDao  = db.dailyAppUsageDao(),
        taskDao           = db.taskDao(),
    )
    totalRatingCount      = dayRatingRepository.count()
    dataHealthDayCount    = runCatching {
        db.dailyAppUsageDao().countDistinctDates()
    }.getOrDefault(0)
}

private suspend fun loadFindingData() {
    _activeFindings.value  = findingRepository.getActiveFindings()
    _pendingQuestion.value = clarifyingQuestionRepository.getPending()
    _needsColdStart.value  = hypothesisRepository.needsColdStart()
}

private fun todayLocalDate(): String =
    java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
```

### 1.4 — New public action functions (add to the class body)

```kotlin
// ── Day rating actions ────────────────────────────────────────────────────────

fun selectRatingDate(date: String) {
    _selectedRatingDate.value = date
    viewModelScope.launch {
        _currentRating.value   = dayRatingRepository.getForDate(date)
        _suggestedChips.value  = buildSuggestedChips(date)
    }
}

fun loadChipsForDate(date: String) {
    viewModelScope.launch { _suggestedChips.value = buildSuggestedChips(date) }
}

fun submitRating(
    date:       String,
    rating:     Int,
    contextTag: String?,
    note:       String?,
    appTags:    List<String>,
    wordTags:   List<String>,
) {
    viewModelScope.launch {
        val now    = java.time.Instant.now().toString()
        val entity = dayRatingRepository.getForDate(date)?.copy(
            rating     = rating,
            contextTag = contextTag,
            note       = note,
            appTags    = appTags.toString(),    // simple JSON-ish; replace with json lib if present
            wordTags   = wordTags.toString(),
            updatedAt  = now,
        ) ?: DayRatingEntity(
            date       = date,
            rating     = rating,
            contextTag = contextTag,
            note       = note,
            appTags    = appTags.toString(),
            wordTags   = wordTags.toString(),
            createdAt  = now,
            updatedAt  = now,
        )
        dayRatingRepository.upsert(entity)
        if (date == _selectedRatingDate.value) _currentRating.value = entity
    }
}

// ── Finding actions ───────────────────────────────────────────────────────────

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

// ── Cold start ────────────────────────────────────────────────────────────────

fun saveColdStartAnswers(answers: List<BehaviouralHypothesisEntity>) {
    viewModelScope.launch {
        answers.forEach { hypothesisRepository.save(it) }
        _needsColdStart.value = false
    }
}

// ── Private helpers ───────────────────────────────────────────────────────────

private suspend fun buildSuggestedChips(date: String): List<SuggestedChip> {
    val chips = mutableListOf<SuggestedChip>()
    val db    = AppModule.database
    runCatching {
        db.dailyAppUsageDao()
            .getForDateRange(date, date)
            .filter { it.foregroundMs >= 20 * 60_000L }
            .take(3)
            .forEach { row ->
                chips.add(SuggestedChip(ChipType.APP, row.appName, row.packageName))
            }
    }
    // Affect chips from snapshot if loaded
    val snap = _analyticsSnapshot.value
    if (snap != null) {
        if (snap.blocking.totalAttempts > 10)
            chips.add(SuggestedChip(ChipType.WORD, "distracted"))
        val rate = if (snap.tasks.total > 0)
            snap.tasks.completed.toFloat() / snap.tasks.total else 1f
        if (rate < 0.3f && snap.tasks.total >= 2)
            chips.add(SuggestedChip(ChipType.WORD, "low energy"))
        if (rate > 0.85f && snap.tasks.total >= 3)
            chips.add(SuggestedChip(ChipType.WORD, "on track"))
    }
    return chips
}
```

### 1.5 — Update `StatsViewModel.Factory` (replace existing Factory)

```kotlin
companion object {
    val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create(modelClass, androidx.lifecycle.viewmodel.MutableCreationExtras())

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            StatsViewModel(
                analyticsProcessor          = AppModule.analyticsProcessor,
                insightEngine               = AppModule.insightEngine,
                achievementEngine           = AppModule.achievementEngine,
                dayRatingRepository         = AppModule.dayRatingRepository,
                findingRepository           = AppModule.findingRepository,
                hypothesisRepository        = AppModule.behaviouralHypothesisRepository,
                clarifyingQuestionRepository = AppModule.clarifyingQuestionRepository,
            ) as T
    }
}
```

### 1.6 — Imports to add to `StatsViewModel.kt`

```kotlin
import com.tbtechs.focusflow.data.local.entity.BehaviouralHypothesisEntity
import com.tbtechs.focusflow.data.local.entity.ClarifyingQuestionEntity
import com.tbtechs.focusflow.data.local.entity.DayRatingEntity
import com.tbtechs.focusflow.data.local.entity.FindingEntity
import com.tbtechs.focusflow.data.repository.BehaviouralHypothesisRepository
import com.tbtechs.focusflow.data.repository.ClarifyingQuestionRepository
import com.tbtechs.focusflow.data.repository.DayRatingRepository
import com.tbtechs.focusflow.data.repository.DayRatingRepository.RatableDateEntry
import com.tbtechs.focusflow.data.repository.FindingRepository
```

---

## Part 2 — `DayRatingBar.kt`

`ui/stats/DayRatingBar.kt`

Matches the StatsCard visual style. Rating chips use the same `Box + CircleShape`
pattern as `AnalyticsWindowTabs`.

```kotlin
package com.tbtechs.focusflow.ui.stats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.data.local.entity.DayRatingEntity
import com.tbtechs.focusflow.data.repository.DayRatingRepository.RatableDateEntry
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkSurfaceVariant
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

enum class ChipType { WORD, APP }

data class SuggestedChip(
    val type:        ChipType,
    val label:       String,
    val packageName: String? = null,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayRatingBar(
    selectedDate:   String,
    currentRating:  DayRatingEntity?,
    ratableDates:   List<RatableDateEntry>,
    suggestedChips: List<SuggestedChip>,
    onSelectDate:   (String) -> Unit,
    onLoadChips:    (String) -> Unit,
    onSubmit:       (date: String, rating: Int, contextTag: String?,
                     note: String?, appTags: List<String>, wordTags: List<String>) -> Unit,
) {
    var dateMenuOpen     by remember { mutableStateOf(false) }
    var tappedRating     by remember(selectedDate) { mutableStateOf(currentRating?.rating) }
    var contextTag       by remember(selectedDate) { mutableStateOf(currentRating?.contextTag) }
    var noteText         by remember(selectedDate) { mutableStateOf(currentRating?.note ?: "") }
    val wordTags         = remember(selectedDate) { mutableStateListOf<String>() }
    val appTags          = remember(selectedDate) { mutableStateListOf<String>() }
    val focusManager     = LocalFocusManager.current

    LaunchedEffect(currentRating) {
        tappedRating = currentRating?.rating
        contextTag   = currentRating?.contextTag
        noteText     = currentRating?.note ?: ""
    }

    LaunchedEffect(selectedDate) { onLoadChips(selectedDate) }

    StatsCard {
        Column(modifier = Modifier.padding(12.dp)) {

            // ── Date selector row ─────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box {
                    TextButton(onClick = { dateMenuOpen = true }) {
                        Text(
                            friendlyDate(selectedDate) + " ▾",
                            fontSize   = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = BrandPrimary,
                        )
                    }
                    DropdownMenu(
                        expanded         = dateMenuOpen,
                        onDismissRequest = { dateMenuOpen = false },
                    ) {
                        ratableDates.forEach { entry ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        friendlyDate(entry.date) +
                                            if (entry.hasRating) "  ●" else "",
                                        fontSize = 13.sp,
                                        color    = if (entry.hasRating) BrandPrimary
                                                   else DarkTextPrimary,
                                    )
                                },
                                onClick = {
                                    onSelectDate(entry.date)
                                    dateMenuOpen = false
                                },
                            )
                        }
                    }
                }

                Text(
                    text     = if (tappedRating != null) "${tappedRating}/10" else "How was it?",
                    fontSize = 13.sp,
                    color    = DarkTextSecondary,
                )
            }

            Spacer(Modifier.height(8.dp))

            // ── 1–10 rating chips ─────────────────────────────────────────────
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                (1..10).forEach { n ->
                    val selected = tappedRating == n
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier         = Modifier
                            .clip(CircleShape)
                            .background(if (selected) BrandPrimary else DarkSurfaceVariant)
                            .clickable {
                                tappedRating = n
                                onSubmit(selectedDate, n, contextTag,
                                    noteText.ifBlank { null }, appTags.toList(), wordTags.toList())
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            "$n",
                            fontSize   = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = if (selected) Color.White else DarkTextSecondary,
                        )
                    }
                }
            }

            // ── Optional extras — visible after a rating is set ───────────────
            AnimatedVisibility(
                visible = tappedRating != null,
                enter   = expandVertically(),
                exit    = shrinkVertically(),
            ) {
                Column {
                    Spacer(Modifier.height(10.dp))

                    // Context chips
                    Text("Any context? (optional)", fontSize = 12.sp, color = DarkTextSecondary)
                    Spacer(Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Rest day" to "rest_day", "Sick" to "sick",
                               "Travel" to "travel", "Holiday" to "holiday",
                               "Off schedule" to "off_schedule").forEach { (label, tag) ->
                            val selected = contextTag == tag
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (selected) BrandPrimary else DarkSurfaceVariant)
                                    .clickable {
                                        contextTag = if (selected) null else tag
                                        onSubmit(selectedDate, tappedRating!!, contextTag,
                                            noteText.ifBlank { null }, appTags.toList(), wordTags.toList())
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            ) {
                                Text(
                                    label,
                                    fontSize = 12.sp,
                                    color    = if (selected) Color.White else DarkTextSecondary,
                                )
                            }
                        }
                    }

                    // Suggested chips from today's data
                    if (suggestedChips.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            suggestedChips.forEach { chip ->
                                val isSelected = when (chip.type) {
                                    ChipType.WORD -> chip.label in wordTags
                                    ChipType.APP  -> chip.packageName in appTags
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (isSelected) BrandPrimary else DarkSurfaceVariant)
                                        .border(
                                            1.dp,
                                            if (isSelected) BrandPrimary else DarkBorder,
                                            RoundedCornerShape(20.dp),
                                        )
                                        .clickable {
                                            when (chip.type) {
                                                ChipType.WORD ->
                                                    if (chip.label in wordTags) wordTags.remove(chip.label)
                                                    else wordTags.add(chip.label)
                                                ChipType.APP  ->
                                                    chip.packageName?.let {
                                                        if (it in appTags) appTags.remove(it)
                                                        else appTags.add(it)
                                                    }
                                            }
                                            onSubmit(selectedDate, tappedRating!!, contextTag,
                                                noteText.ifBlank { null }, appTags.toList(), wordTags.toList())
                                        }
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                ) {
                                    Text(chip.label, fontSize = 12.sp,
                                        color = if (isSelected) Color.White else DarkTextSecondary)
                                }
                            }
                        }
                    }

                    // Free text note
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value         = noteText,
                        onValueChange = { if (it.length <= 200) noteText = it },
                        placeholder   = { Text("Anything else? (optional)", fontSize = 13.sp,
                                            color = DarkTextSecondary) },
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = true,
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = BrandPrimary,
                            unfocusedBorderColor = DarkBorder,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            onSubmit(selectedDate, tappedRating!!, contextTag,
                                noteText.ifBlank { null }, appTags.toList(), wordTags.toList())
                        }),
                    )
                }
            }
        }
    }
}

private fun friendlyDate(dateStr: String): String {
    val today     = LocalDate.now()
    val yesterday = today.minusDays(1)
    val date      = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: return dateStr
    return when (date) {
        today     -> "Today"
        yesterday -> "Yesterday"
        else      -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) +
                     " ${date.dayOfMonth}"
    }
}
```

---

## Part 3 — `FindingCardView.kt`

`ui/stats/FindingCardView.kt`

Matches `InsightCardView` structure exactly — `StatsCard`, same typography.

```kotlin
package com.tbtechs.focusflow.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.data.local.entity.FindingEntity
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary

private val MANIPULATION_TYPES = setOf(
    "VARIABLE_REWARD_LOOP", "INFINITE_SESSION_DESIGN", "MORNING_HIJACK",
    "ESCALATING_CAPTURE", "STREAK_LOCK_IN", "NOTIFICATION_CONDITIONING",
)

@Composable
fun FindingCardView(
    finding:       FindingEntity,
    onMarkSeen:    (String) -> Unit,
    onIntentional: (String, String) -> Unit,
    onAware:       (String, String) -> Unit,
) {
    val isNew   = finding.state == "detected"
    val showReply = finding.state == "seen"
    val isAware   = finding.state == "aware"

    val accentColor = when {
        isNew   -> BrandPrimary
        isAware -> MaterialTheme.colorScheme.onSurfaceVariant
        else    -> MaterialTheme.colorScheme.primary
    }

    StatsCard(
        modifier = Modifier
            .then(if (isNew) Modifier.clickable { onMarkSeen(finding.id) } else Modifier)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            // ── Category label + state badge ──────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    text       = findingCategoryLabel(finding),
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = accentColor,
                )
                when {
                    isNew   -> Text("NEW", fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold, color = BrandPrimary)
                    isAware -> Text("WATCHING", fontSize = 11.sp, color = DarkTextSecondary)
                }
            }

            Spacer(Modifier.height(4.dp))

            // ── Headline ──────────────────────────────────────────────────────
            Text(
                text       = finding.headline,
                fontSize   = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(6.dp))

            // ── Body ──────────────────────────────────────────────────────────
            Text(
                text     = finding.body,
                fontSize = 13.sp,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(6.dp))

            // ── Evidence line ─────────────────────────────────────────────────
            Text(
                text     = finding.evidenceLine,
                fontSize = 11.sp,
                color    = DarkTextSecondary,
            )

            // ── Response buttons — shown only while state == 'seen' ───────────
            if (showReply) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onIntentional(finding.id, finding.evidenceFingerprint) }) {
                        Text("I chose this", fontSize = 13.sp, color = DarkTextSecondary)
                    }
                    TextButton(onClick = { onAware(finding.id, finding.evidenceFingerprint) }) {
                        Text("I didn't know", fontSize = 13.sp, color = BrandPrimary)
                    }
                }
            }
        }
    }
}

private fun findingCategoryLabel(finding: FindingEntity): String =
    if (finding.detectionType in MANIPULATION_TYPES && finding.subjectAppName != null)
        "WHAT ${finding.subjectAppName.uppercase()} IS DOING"
    else "FINDING"
```

---

## Part 4 — `FindingsSection.kt`

`ui/stats/FindingsSection.kt`

```kotlin
package com.tbtechs.focusflow.ui.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.data.local.entity.ClarifyingQuestionEntity
import com.tbtechs.focusflow.data.local.entity.FindingEntity
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun FindingsSection(
    activeFindings:   List<FindingEntity>,
    pendingQuestion:  ClarifyingQuestionEntity?,
    dayCount:         Int,
    ratingCount:      Int,
    onMarkSeen:       (String) -> Unit,
    onIntentional:    (String, String) -> Unit,
    onAware:          (String, String) -> Unit,
    onAnswerQuestion: (id: String, response: String) -> Unit,
) {
    // ── Section header ────────────────────────────────────────────────────────
    Text(
        text       = "FINDINGS",
        style      = MaterialTheme.typography.labelLarge,
        color      = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier   = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )

    // ── Cold start — < 14 days of data ───────────────────────────────────────
    if (dayCount < 14) {
        StatsCard {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Building your baseline",
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text("Behavioural findings appear after 14 days of usage data.",
                    fontSize = 13.sp, color = DarkTextSecondary)
                Spacer(Modifier.height(8.dp))
                BaselineRow("Days of data", dayCount, 14)
                BaselineRow("Days rated", ratingCount, 7)
            }
        }
        return
    }

    // ── Clarifying question ───────────────────────────────────────────────────
    pendingQuestion?.let {
        ClarifyingQuestionCard(
            question    = it,
            onYes       = { onAnswerQuestion(it.id, "yes_intentional") },
            onNotReally = { onAnswerQuestion(it.id, "not_really") },
            onSkip      = { onAnswerQuestion(it.id, "skipped") },
        )
        Spacer(Modifier.height(4.dp))
    }

    // ── Active findings: detected + seen ─────────────────────────────────────
    val primary  = activeFindings.filter { it.state in setOf("detected", "seen") }
    val watching = activeFindings.filter { it.state == "aware" }

    primary.forEach { f ->
        FindingCardView(f, onMarkSeen, onIntentional, onAware)
        Spacer(Modifier.height(4.dp))
    }

    // ── Watching section ──────────────────────────────────────────────────────
    if (watching.isNotEmpty()) {
        Text("WATCHING", style = MaterialTheme.typography.labelLarge,
            color = DarkTextSecondary, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
        watching.forEach { f ->
            FindingCardView(f, onMarkSeen, onIntentional, onAware)
            Spacer(Modifier.height(4.dp))
        }
    }

    // ── Clean state ───────────────────────────────────────────────────────────
    if (activeFindings.isEmpty() && pendingQuestion == null) {
        StatsCard {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Nothing to flag", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "No unusual patterns detected. The system has nothing to report.",
                    fontSize = 13.sp, color = DarkTextSecondary,
                )
            }
        }
    }

    Spacer(Modifier.height(12.dp))
}

@Composable
private fun BaselineRow(label: String, current: Int, target: Int) {
    val done  = current >= target
    Text(
        text     = if (done) "✓ $label" else "$label: $current / $target",
        fontSize = 12.sp,
        color    = if (done) MaterialTheme.colorScheme.secondary
                   else DarkTextSecondary,
    )
}

// ── Clarifying question card ──────────────────────────────────────────────────

@Composable
private fun ClarifyingQuestionCard(
    question:    ClarifyingQuestionEntity,
    onYes:       () -> Unit,
    onNotReally: () -> Unit,
    onSkip:      () -> Unit,
) {
    StatsCard {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("A question", fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(4.dp))
            Text(renderQuestion(question), fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
            ) {
                TextButton(onClick = onYes)       { Text("Yes, I chose to", fontSize = 12.sp) }
                TextButton(onClick = onNotReally) { Text("Not really", fontSize = 12.sp) }
                TextButton(onClick = onSkip)      { Text("Skip", fontSize = 12.sp, color = DarkTextSecondary) }
            }
        }
    }
}

private fun renderQuestion(q: ClarifyingQuestionEntity): String {
    val ctx     = runCatching { Json.parseToJsonElement(q.contextJson) as? JsonObject }.getOrNull()
    val appName = ctx?.get("appName")?.jsonPrimitive?.content ?: "that app"
    val date    = ctx?.get("date")?.jsonPrimitive?.content ?: "that day"
    val rating  = ctx?.get("rating")?.jsonPrimitive?.content ?: ""
    return when (q.questionType) {
        "morning_high_rating"     -> "You rated $date a $rating, but $appName was the first thing you opened that morning. Was that intentional?"
        "productive_high_usage"   -> "On your best-rated days, $appName use was above your average. Does using it fit into days that go well?"
        "skip_despite_completion" -> "You completed most tasks on $date but rated it $rating. What made it feel off?"
        else -> q.contextJson
    }
}
```

---

## Part 5 — `ColdStartSheet.kt`

`ui/stats/ColdStartSheet.kt`

```kotlin
package com.tbtechs.focusflow.ui.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.data.local.entity.BehaviouralHypothesisEntity
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

private val COLD_START_QUESTIONS = listOf(
    Triple("reflex_app",
        "Which app do you find yourself opening without deciding to?",
        "Name an app, or skip if nothing comes to mind."),
    Triple("bad_day_meaning",
        "What usually makes a day feel like it went badly?",
        "A few words is enough."),
    Triple("morning_phone",
        "Do you check your phone before doing anything else in the morning?",
        "No wrong answer."),
)

private val MORNING_OPTIONS = listOf("Almost always", "Sometimes", "Rarely", "No")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColdStartSheet(onComplete: (List<BehaviouralHypothesisEntity>) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope      = rememberCoroutineScope()
    var step       by remember { mutableIntStateOf(0) }
    var answer     by remember { mutableStateOf("") }
    val answers    = remember { mutableListOf<BehaviouralHypothesisEntity>() }

    fun advance() {
        if (answer.isNotBlank()) {
            answers.add(BehaviouralHypothesisEntity(
                id            = UUID.randomUUID().toString(),
                questionId    = COLD_START_QUESTIONS[step].first,
                answerText    = answer.trim(),
                answerPackage = null,
                createdAt     = Instant.now().toString(),
            ))
        }
        answer = ""
        if (step < COLD_START_QUESTIONS.lastIndex) { step++ }
        else scope.launch { sheetState.hide(); onComplete(answers) }
    }

    ModalBottomSheet(
        onDismissRequest = { onComplete(answers) },
        sheetState       = sheetState,
        containerColor   = DarkBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .navigationBarsPadding()
        ) {
            LinearProgressIndicator(
                progress = { (step + 1).toFloat() / COLD_START_QUESTIONS.size },
                modifier = Modifier.fillMaxWidth(),
                color    = BrandPrimary,
            )
            Spacer(Modifier.height(16.dp))

            val (_, question, hint) = COLD_START_QUESTIONS[step]

            Text(question, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(hint, fontSize = 13.sp, color = DarkTextSecondary)
            Spacer(Modifier.height(16.dp))

            if (step == 2) {
                // Morning phone: button options
                MORNING_OPTIONS.forEach { option ->
                    TextButton(
                        onClick  = { answer = option; advance() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(option, fontSize = 14.sp,
                            color = if (answer == option) BrandPrimary
                                    else MaterialTheme.colorScheme.onSurface)
                    }
                }
            } else {
                OutlinedTextField(
                    value         = answer,
                    onValueChange = { answer = it },
                    placeholder   = {
                        Text(if (step == 0) "e.g. Instagram, TikTok…" else "Your answer",
                            fontSize = 13.sp, color = DarkTextSecondary)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = BrandPrimary,
                        unfocusedBorderColor = com.tbtechs.focusflow.ui.theme.DarkBorder,
                    ),
                )
            }

            Spacer(Modifier.height(20.dp))
            Row {
                TextButton(onClick = { answer = ""; advance() }) {
                    Text("Skip", color = DarkTextSecondary)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = ::advance) {
                    Text(
                        if (step < COLD_START_QUESTIONS.lastIndex) "Next" else "Done",
                        color = BrandPrimary,
                    )
                }
            }
        }
    }
}
```

---

## Part 6 — `StatsInsightsExperience.kt` changes

Three surgical additions. Do not restructure the existing file.

### 6.1 — Add new state collection after the existing `val window` line

```kotlin
// Phase 1 state
val selectedDate    by statsViewModel.selectedRatingDate.collectAsState()
val currentRating   by statsViewModel.currentRating.collectAsState()
val ratableDates    by statsViewModel.ratableDates.collectAsState()
val suggestedChips  by statsViewModel.suggestedChips.collectAsState()
val activeFindings  by statsViewModel.activeFindings.collectAsState()
val pendingQuestion by statsViewModel.pendingQuestion.collectAsState()
val needsColdStart  by statsViewModel.needsColdStart.collectAsState()
```

### 6.2 — Add ColdStartSheet trigger before the Column

```kotlin
// Show cold-start seed questions on first Stats visit
if (needsColdStart) {
    ColdStartSheet(onComplete = statsViewModel::saveColdStartAnswers)
}
```

### 6.3 — Add DayRatingBar after `AnalyticsWindowTabs`

```kotlin
AnalyticsWindowTabs(activeWindow = window, onSelect = statsViewModel::setWindow)

// Phase 1: day rating bar — always visible, all windows
DayRatingBar(
    selectedDate   = selectedDate,
    currentRating  = currentRating,
    ratableDates   = ratableDates,
    suggestedChips = suggestedChips,
    onSelectDate   = statsViewModel::selectRatingDate,
    onLoadChips    = statsViewModel::loadChipsForDate,
    onSubmit       = statsViewModel::submitRating,
)
```

### 6.4 — Add FindingsSection inside LazyColumn

Inside the `item { }` block in the `LazyColumn`, after the existing
`achievements?.let { AchievementRow(it) }` call:

```kotlin
// Phase 1: Findings section — appears in all windows
FindingsSection(
    activeFindings   = activeFindings,
    pendingQuestion  = pendingQuestion,
    dayCount         = statsViewModel.dataHealthDayCount,
    ratingCount      = statsViewModel.totalRatingCount,
    onMarkSeen       = statsViewModel::markFindingSeen,
    onIntentional    = statsViewModel::acknowledgeFindingIntentional,
    onAware          = statsViewModel::acknowledgeFindingAware,
    onAnswerQuestion = statsViewModel::answerClarifyingQuestion,
)
```

### 6.5 — New imports to add to `StatsInsightsExperience.kt`

```kotlin
import com.tbtechs.focusflow.data.local.entity.ClarifyingQuestionEntity
import com.tbtechs.focusflow.data.local.entity.FindingEntity
```

---

## What IMPL_2 delivers

After these changes, every stats window shows:

1. **`DayRatingBar`** — always visible above content. Tap a number to rate.
   Retroactive dropdown lets the user rate any of the last 14 data-bearing days.
   Context chips, suggested chips from that day's app data, and optional note
   appear after a number is tapped.

2. **`ColdStartSheet`** — appears as a bottom sheet on the user's first visit to
   Stats. Three questions, one at a time, skip available on all. Dismissed answers
   are stored to `behavioural_hypotheses`. Once dismissed, never shown again.

3. **`FindingsSection`** — at the bottom of every window's content. Shows a
   building-baseline progress card for the first 14 days. After 14 days: pending
   clarifying question, active findings (detected/seen), watching findings (aware),
   and a "nothing to flag" clean-state card.

Next: **IMPL_3** — the detection engine that generates the first `FindingEntity`
rows from data the database now holds.
