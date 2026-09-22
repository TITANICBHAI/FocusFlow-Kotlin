# IMPL_1B — Services Layer
## Remaining Entities · DAOs · Repositories · AppModule · Notification · Prune
### Based on actual codebase (app.zip)

Continues from IMPL_1A. All 7 new tables were created in MIGRATION_5_6.
This document provides the entity/DAO files for the 5 tables that IMPL_1A
didn't cover, the 4 repositories, AppModule wiring, day-rating notification,
prune additions to BackgroundFetchWorker, and the BootReceiver addition.

**Entity style:** minimal — one KDoc line, `@ColumnInfo(name = "...")` on every field.
**DAO style:** interface, single-line `/** */` comments, no multiline blocks.
**AppModule:** `lateinit var NAME: TYPE private set` — no Hilt, no injection framework.

---

## File 1 — `DayRatingEntity.kt`

`data/local/entity/DayRatingEntity.kt`

```kotlin
package com.tbtechs.focusflow.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** User's 1–10 end-of-day self-rating for a local calendar date. One row per date. */
@Entity(tableName = "day_ratings")
data class DayRatingEntity(

    /** Local YYYY-MM-DD. Primary key — one rating per day. */
    @PrimaryKey
    @ColumnInfo(name = "date")
    val date: String,

    /** 1–10 inclusive. */
    @ColumnInfo(name = "rating")
    val rating: Int,

    /** One of: 'rest_day' | 'sick' | 'travel' | 'holiday' | 'off_schedule'. Nullable. */
    @ColumnInfo(name = "context_tag")
    val contextTag: String?,

    /** Free text note, max 200 chars. Nullable. */
    @ColumnInfo(name = "note")
    val note: String?,

    /** JSON-encoded List<String> of package names the user tapped. Default '[]'. */
    @ColumnInfo(name = "app_tags", defaultValue = "[]")
    val appTags: String,

    /** JSON-encoded List<String> of word/affect labels. Default '[]'. */
    @ColumnInfo(name = "word_tags", defaultValue = "[]")
    val wordTags: String,

    /** ISO 8601 timestamp — when the rating was first entered. */
    @ColumnInfo(name = "created_at")
    val createdAt: String,

    /** ISO 8601 timestamp — last edit time. */
    @ColumnInfo(name = "updated_at")
    val updatedAt: String,
)
```

---

## File 2 — `FindingEntity.kt`

`data/local/entity/FindingEntity.kt`

```kotlin
package com.tbtechs.focusflow.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A stateful behavioural finding surfaced in the Findings tab.
 *
 * Lifecycle: detected → seen → (intentional | aware) → resolved.
 * See FindingRepository for transition rules and the one-per-week surface limit.
 */
@Entity(
    tableName = "findings",
    indices = [
        Index(value = ["state"],           name = "idx_findings_state"),
        Index(value = ["subject_package"], name = "idx_findings_package"),
    ],
)
data class FindingEntity(

    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    /** e.g. 'VARIABLE_REWARD_LOOP' | 'MORNING_HIJACK' | 'POST_FAILURE_CASCADE'. */
    @ColumnInfo(name = "detection_type")
    val detectionType: String,

    /** Package this finding targets. Null for user-pattern (non-app) findings. */
    @ColumnInfo(name = "subject_package")
    val subjectPackage: String?,

    @ColumnInfo(name = "subject_app_name")
    val subjectAppName: String?,

    /** One of: 'detected' | 'seen' | 'intentional' | 'aware' | 'resolved'. */
    @ColumnInfo(name = "state", defaultValue = "detected")
    val state: String,

    /**
     * SHA-based fingerprint of key evidence parameters. Changes only when
     * evidence shifts materially — used to decide when to resurface an
     * 'intentional' finding.
     */
    @ColumnInfo(name = "evidence_fingerprint")
    val evidenceFingerprint: String,

    /** JSON: sampleSize, dateRange, keyMetrics used to generate the body. */
    @ColumnInfo(name = "evidence_json")
    val evidenceJson: String,

    @ColumnInfo(name = "headline")
    val headline: String,

    @ColumnInfo(name = "body")
    val body: String,

    /** e.g. "Observed across 12 sessions over 3 weeks". */
    @ColumnInfo(name = "evidence_line")
    val evidenceLine: String,

    @ColumnInfo(name = "first_detected_at")
    val firstDetectedAt: String,

    @ColumnInfo(name = "last_updated_at")
    val lastUpdatedAt: String,

    @ColumnInfo(name = "seen_at")
    val seenAt: String?,

    @ColumnInfo(name = "resolved_at")
    val resolvedAt: String?,

    /** ISO 8601. Set when state = 'intentional'. Suppresses re-surfacing until this time. */
    @ColumnInfo(name = "suppressed_until")
    val suppressedUntil: String?,
)
```

---

## File 3 — `FindingAcknowledgementEntity.kt`

`data/local/entity/FindingAcknowledgementEntity.kt`

```kotlin
package com.tbtechs.focusflow.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Records a user response to a finding. One row per response event. */
@Entity(
    tableName = "finding_acknowledgements",
    foreignKeys = [
        ForeignKey(
            entity        = FindingEntity::class,
            parentColumns = ["id"],
            childColumns  = ["finding_id"],
            onDelete      = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["finding_id"], name = "idx_finding_ack_finding_id"),
    ],
)
data class FindingAcknowledgementEntity(

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "finding_id")
    val findingId: String,

    /** One of: 'intentional' | 'aware' | 'later'. */
    @ColumnInfo(name = "response")
    val response: String,

    /** Fingerprint of the finding's evidence at response time. */
    @ColumnInfo(name = "evidence_fingerprint")
    val evidenceFingerprint: String,

    @ColumnInfo(name = "created_at")
    val createdAt: String,

    @ColumnInfo(name = "note")
    val note: String?,
)
```

---

## File 4 — `BehaviouralHypothesisEntity.kt`

`data/local/entity/BehaviouralHypothesisEntity.kt`

```kotlin
package com.tbtechs.focusflow.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Stores the user's answer to one of the three cold-start seed questions. */
@Entity(tableName = "behavioural_hypotheses")
data class BehaviouralHypothesisEntity(

    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    /** One of: 'reflex_app' | 'bad_day_meaning' | 'morning_phone'. */
    @ColumnInfo(name = "question_id")
    val questionId: String,

    @ColumnInfo(name = "answer_text")
    val answerText: String,

    /** Resolved package name if the user named an app. Null otherwise. */
    @ColumnInfo(name = "answer_package")
    val answerPackage: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: String,
)
```

---

## File 5 — `ClarifyingQuestionEntity.kt`

`data/local/entity/ClarifyingQuestionEntity.kt`

```kotlin
package com.tbtechs.focusflow.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A system-generated prompt shown when a day rating is inconsistent with
 * observed behavioural patterns. Rate-limited to one per 14 days.
 */
@Entity(
    tableName = "clarifying_questions",
    indices = [Index(value = ["asked_at"], name = "idx_cq_asked_at")],
)
data class ClarifyingQuestionEntity(

    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    /** One of: 'morning_high_rating' | 'productive_high_usage' | 'skip_despite_completion'. */
    @ColumnInfo(name = "question_type")
    val questionType: String,

    /** Local YYYY-MM-DD of the inconsistency that triggered this question. */
    @ColumnInfo(name = "date_of_concern")
    val dateOfConcern: String,

    /** JSON params used to render the question text. */
    @ColumnInfo(name = "context_json")
    val contextJson: String,

    @ColumnInfo(name = "asked_at")
    val askedAt: String,

    /** One of: 'yes_intentional' | 'not_really' | 'skipped'. Null until answered. */
    @ColumnInfo(name = "response")
    val response: String?,

    @ColumnInfo(name = "responded_at")
    val respondedAt: String?,
)
```

---

## File 6 — `DayRatingDao.kt`

`data/local/dao/DayRatingDao.kt`

```kotlin
package com.tbtechs.focusflow.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tbtechs.focusflow.data.local.entity.DayRatingEntity

@Dao
interface DayRatingDao {

    /** Upserts a rating. Second call for the same date overwrites — retroactive edits. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rating: DayRatingEntity)

    @Query("SELECT * FROM day_ratings WHERE date = :date LIMIT 1")
    suspend fun getForDate(date: String): DayRatingEntity?

    /** All rated days in [startDate, endDate] inclusive, ascending. */
    @Query("SELECT * FROM day_ratings WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    suspend fun getForDateRange(startDate: String, endDate: String): List<DayRatingEntity>

    /** Total ratings stored. Used for cold-start gate. */
    @Query("SELECT COUNT(*) FROM day_ratings")
    suspend fun count(): Int

    /** Most-recently-rated dates descending — for the retroactive dropdown. */
    @Query("SELECT date FROM day_ratings ORDER BY date DESC LIMIT :limit")
    suspend fun getRecentRatedDates(limit: Int): List<String>
}
```

---

## File 7 — `FindingDao.kt`

`data/local/dao/FindingDao.kt`

```kotlin
package com.tbtechs.focusflow.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tbtechs.focusflow.data.local.entity.FindingEntity

@Dao
interface FindingDao {

    /** INSERT OR IGNORE — UUID collision (impossible in practice) is silently skipped. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(finding: FindingEntity)

    @Query("SELECT * FROM findings WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): FindingEntity?

    /**
     * Active findings for the Findings tab: detected, seen, aware — ordered so
     * 'detected' appears first.
     */
    @Query("""
        SELECT * FROM findings
        WHERE state NOT IN ('intentional', 'resolved')
        ORDER BY
            CASE state WHEN 'detected' THEN 0 WHEN 'seen' THEN 1 ELSE 2 END ASC,
            first_detected_at DESC
    """)
    suspend fun getActiveFindings(): List<FindingEntity>

    /** Most-recent detected finding — used to enforce the 7-day surface cooldown. */
    @Query("SELECT * FROM findings WHERE state = 'detected' ORDER BY first_detected_at DESC LIMIT 1")
    suspend fun getMostRecentDetected(): FindingEntity?

    /** Existing finding for (type, package) — for update-vs-insert decision. */
    @Query("""
        SELECT * FROM findings
        WHERE detection_type = :detectionType
          AND (subject_package = :subjectPackage
               OR (:subjectPackage IS NULL AND subject_package IS NULL))
        LIMIT 1
    """)
    suspend fun getExisting(detectionType: String, subjectPackage: String?): FindingEntity?

    @Query("UPDATE findings SET state = 'seen', seen_at = :now, last_updated_at = :now WHERE id = :id AND state = 'detected'")
    suspend fun markSeen(id: String, now: String)

    @Query("UPDATE findings SET state = 'intentional', suppressed_until = :suppressedUntil, last_updated_at = :now WHERE id = :id")
    suspend fun setIntentional(id: String, suppressedUntil: String, now: String)

    @Query("UPDATE findings SET state = 'aware', last_updated_at = :now WHERE id = :id")
    suspend fun setAware(id: String, now: String)

    @Query("UPDATE findings SET state = 'resolved', resolved_at = :now, last_updated_at = :now WHERE id = :id")
    suspend fun markResolved(id: String, now: String)

    @Query("""
        UPDATE findings
        SET state = 'detected', suppressed_until = NULL,
            evidence_fingerprint = :fingerprint, evidence_json = :evidenceJson,
            headline = :headline, body = :body, evidence_line = :evidenceLine,
            last_updated_at = :now
        WHERE id = :id
    """)
    suspend fun resurface(
        id: String, fingerprint: String, evidenceJson: String,
        headline: String, body: String, evidenceLine: String, now: String,
    )

    @Query("DELETE FROM findings WHERE state = 'resolved' AND resolved_at < :cutoffIso")
    suspend fun deleteOldResolved(cutoffIso: String)
}
```

---

## File 8 — `FindingAcknowledgementDao.kt`

`data/local/dao/FindingAcknowledgementDao.kt`

```kotlin
package com.tbtechs.focusflow.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tbtechs.focusflow.data.local.entity.FindingAcknowledgementEntity

@Dao
interface FindingAcknowledgementDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(ack: FindingAcknowledgementEntity)

    @Query("SELECT * FROM finding_acknowledgements WHERE finding_id = :findingId ORDER BY created_at DESC")
    suspend fun getForFinding(findingId: String): List<FindingAcknowledgementEntity>
}
```

---

## File 9 — `BehaviouralHypothesisDao.kt`

`data/local/dao/BehaviouralHypothesisDao.kt`

```kotlin
package com.tbtechs.focusflow.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tbtechs.focusflow.data.local.entity.BehaviouralHypothesisEntity

@Dao
interface BehaviouralHypothesisDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(hypothesis: BehaviouralHypothesisEntity)

    @Query("SELECT * FROM behavioural_hypotheses ORDER BY created_at ASC")
    suspend fun getAll(): List<BehaviouralHypothesisEntity>

    /** The reflex-app package from seed questions, or null if not answered / no app named. */
    @Query("SELECT answer_package FROM behavioural_hypotheses WHERE question_id = 'reflex_app' AND answer_package IS NOT NULL LIMIT 1")
    suspend fun getReflexAppPackage(): String?
}
```

---

## File 10 — `ClarifyingQuestionDao.kt`

`data/local/dao/ClarifyingQuestionDao.kt`

```kotlin
package com.tbtechs.focusflow.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tbtechs.focusflow.data.local.entity.ClarifyingQuestionEntity

@Dao
interface ClarifyingQuestionDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(question: ClarifyingQuestionEntity)

    /** Most-recent unanswered question. Only one is shown at a time. */
    @Query("SELECT * FROM clarifying_questions WHERE response IS NULL ORDER BY asked_at DESC LIMIT 1")
    suspend fun getUnanswered(): ClarifyingQuestionEntity?

    @Query("UPDATE clarifying_questions SET response = :response, responded_at = :now WHERE id = :id")
    suspend fun answer(id: String, response: String, now: String)

    /** Count asked since [sinceIso] — enforces the 14-day rate limit. */
    @Query("SELECT COUNT(*) FROM clarifying_questions WHERE asked_at >= :sinceIso")
    suspend fun countAskedSince(sinceIso: String): Int

    @Query("DELETE FROM clarifying_questions WHERE response IS NOT NULL AND responded_at < :cutoffIso")
    suspend fun deleteAnsweredBefore(cutoffIso: String)
}
```

---

## File 11 — `DayRatingRepository.kt`

`data/repository/DayRatingRepository.kt`

```kotlin
package com.tbtechs.focusflow.data.repository

import android.util.Log
import com.tbtechs.focusflow.data.local.dao.DailyAppUsageDao
import com.tbtechs.focusflow.data.local.dao.DayRatingDao
import com.tbtechs.focusflow.data.local.dao.TaskDao
import com.tbtechs.focusflow.data.local.entity.DayRatingEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class DayRatingRepository(
    private val dao: DayRatingDao,
) {

    private companion object {
        private const val TAG          = "DayRatingRepository"
        private val DATE_FMT           = DateTimeFormatter.ISO_LOCAL_DATE
        private const val RETRO_DAYS   = 14L
    }

    suspend fun upsert(rating: DayRatingEntity) {
        runCatching { dao.upsert(rating) }
            .onFailure { Log.e(TAG, "upsert(${rating.date}) failed: ${it.message}") }
    }

    suspend fun getForDate(date: String): DayRatingEntity? =
        runCatching { dao.getForDate(date) }.getOrNull()

    suspend fun getForDateRange(startDate: String, endDate: String): List<DayRatingEntity> =
        runCatching { dao.getForDateRange(startDate, endDate) }.getOrDefault(emptyList())

    suspend fun count(): Int =
        runCatching { dao.count() }.getOrDefault(0)

    /**
     * Returns the last [RETRO_DAYS] calendar dates that have any data, annotated
     * with whether a rating already exists. Used to populate the retroactive
     * dropdown in [DayRatingBar].
     */
    suspend fun getRatableDates(
        dailyAppUsageDao: DailyAppUsageDao,
        taskDao: TaskDao,
    ): List<RatableDateEntry> {
        val today    = LocalDate.now()
        val cutoff   = today.minusDays(RETRO_DAYS).format(DATE_FMT)
        val todayStr = today.format(DATE_FMT)

        val usageDates = runCatching {
            dailyAppUsageDao.getForDateRange(cutoff, todayStr).map { it.date }.toSet()
        }.getOrDefault(emptySet())

        val taskDates = runCatching {
            taskDao.getTaskDatesInRange(cutoff, todayStr).toSet()
        }.getOrDefault(emptySet())

        val allDates = (usageDates + taskDates).sortedDescending()
        if (allDates.isEmpty()) return emptyList()

        val rated = runCatching {
            dao.getRecentRatedDates(RETRO_DAYS.toInt()).toSet()
        }.getOrDefault(emptySet())

        return allDates.map { date ->
            RatableDateEntry(date = date, hasRating = date in rated)
        }
    }

    data class RatableDateEntry(val date: String, val hasRating: Boolean)
}
```

**Note — `TaskDao.getTaskDatesInRange`:** Add this query to `TaskDao.kt` if not present:

```kotlin
/** Returns distinct local YYYY-MM-DD dates on which tasks are scheduled. */
@Query("SELECT DISTINCT date(scheduled_time / 1000, 'unixepoch', 'localtime') FROM tasks WHERE date(scheduled_time / 1000, 'unixepoch', 'localtime') BETWEEN :startDate AND :endDate")
suspend fun getTaskDatesInRange(startDate: String, endDate: String): List<String>
```

---

## File 12 — `FindingRepository.kt`

`data/repository/FindingRepository.kt`

```kotlin
package com.tbtechs.focusflow.data.repository

import android.util.Log
import com.tbtechs.focusflow.data.local.dao.FindingAcknowledgementDao
import com.tbtechs.focusflow.data.local.dao.FindingDao
import com.tbtechs.focusflow.data.local.entity.FindingAcknowledgementEntity
import com.tbtechs.focusflow.data.local.entity.FindingEntity
import java.time.Instant
import java.time.temporal.ChronoUnit

class FindingRepository(
    private val findingDao: FindingDao,
    private val ackDao: FindingAcknowledgementDao,
) {

    private companion object {
        private const val TAG                  = "FindingRepository"
        private const val SURFACE_COOLDOWN_DAYS = 7L
        private const val INTENTIONAL_DAYS      = 60L
    }

    suspend fun getActiveFindings(): List<FindingEntity> =
        runCatching { findingDao.getActiveFindings() }.getOrDefault(emptyList())

    /**
     * Submits a detected finding. Enforces the one-per-week surface rule and
     * handles all state transitions for existing findings.
     * Returns true if a new 'detected' card will appear in the UI.
     */
    suspend fun submit(finding: FindingEntity): Boolean =
        runCatching {
            val existing = findingDao.getExisting(finding.detectionType, finding.subjectPackage)

            when {
                existing == null -> insertIfCooldownElapsed(finding)

                existing.state == "intentional" -> {
                    if (existing.evidenceFingerprint == finding.evidenceFingerprint) false
                    else { resurface(existing.id, finding); true }
                }

                existing.state == "resolved" -> insertIfCooldownElapsed(finding)

                else -> {
                    // detected / seen / aware — update evidence in place if it changed
                    if (existing.evidenceFingerprint != finding.evidenceFingerprint) {
                        resurface(existing.id, finding)
                    }
                    false
                }
            }
        }.onFailure { Log.e(TAG, "submit failed: ${it.message}") }.getOrDefault(false)

    suspend fun markSeen(id: String) {
        runCatching { findingDao.markSeen(id, now()) }
            .onFailure { Log.e(TAG, "markSeen($id) failed: ${it.message}") }
    }

    suspend fun acknowledgeIntentional(id: String, evidenceFingerprint: String, note: String?) {
        runCatching {
            val until = Instant.now().plus(INTENTIONAL_DAYS, ChronoUnit.DAYS).toString()
            findingDao.setIntentional(id, until, now())
            ackDao.insert(FindingAcknowledgementEntity(
                findingId           = id,
                response            = "intentional",
                evidenceFingerprint = evidenceFingerprint,
                createdAt           = now(),
                note                = note,
            ))
        }.onFailure { Log.e(TAG, "acknowledgeIntentional($id) failed: ${it.message}") }
    }

    suspend fun acknowledgeAware(id: String, evidenceFingerprint: String) {
        runCatching {
            findingDao.setAware(id, now())
            ackDao.insert(FindingAcknowledgementEntity(
                findingId           = id,
                response            = "aware",
                evidenceFingerprint = evidenceFingerprint,
                createdAt           = now(),
                note                = null,
            ))
        }.onFailure { Log.e(TAG, "acknowledgeAware($id) failed: ${it.message}") }
    }

    suspend fun markResolved(id: String) {
        runCatching { findingDao.markResolved(id, now()) }
            .onFailure { Log.e(TAG, "markResolved($id) failed: ${it.message}") }
    }

    suspend fun pruneOldResolved(days: Long = 90L) {
        val cutoff = Instant.now().minus(days, ChronoUnit.DAYS).toString()
        runCatching { findingDao.deleteOldResolved(cutoff) }
            .onFailure { Log.e(TAG, "pruneOldResolved failed: ${it.message}") }
    }

    private suspend fun insertIfCooldownElapsed(finding: FindingEntity): Boolean {
        val recent = findingDao.getMostRecentDetected()
        if (recent != null) {
            val age = ChronoUnit.DAYS.between(
                Instant.parse(recent.firstDetectedAt), Instant.now()
            )
            if (age < SURFACE_COOLDOWN_DAYS) {
                Log.d(TAG, "Cooldown active (${age}d) — queuing ${finding.detectionType}")
                return false
            }
        }
        findingDao.insert(finding)
        return true
    }

    private suspend fun resurface(id: String, f: FindingEntity) =
        findingDao.resurface(id, f.evidenceFingerprint, f.evidenceJson,
            f.headline, f.body, f.evidenceLine, now())

    private fun now() = Instant.now().toString()
}
```

---

## File 13 — `BehaviouralHypothesisRepository.kt`

`data/repository/BehaviouralHypothesisRepository.kt`

```kotlin
package com.tbtechs.focusflow.data.repository

import com.tbtechs.focusflow.data.local.dao.BehaviouralHypothesisDao
import com.tbtechs.focusflow.data.local.entity.BehaviouralHypothesisEntity

class BehaviouralHypothesisRepository(private val dao: BehaviouralHypothesisDao) {

    /** True when no seed answers have been stored yet — triggers cold-start UI. */
    suspend fun needsColdStart(): Boolean =
        runCatching { dao.getAll().isEmpty() }.getOrDefault(true)

    suspend fun save(hypothesis: BehaviouralHypothesisEntity) {
        runCatching { dao.insert(hypothesis) }
    }

    suspend fun getAll(): List<BehaviouralHypothesisEntity> =
        runCatching { dao.getAll() }.getOrDefault(emptyList())

    suspend fun getReflexAppPackage(): String? =
        runCatching { dao.getReflexAppPackage() }.getOrNull()
}
```

---

## File 14 — `ClarifyingQuestionRepository.kt`

`data/repository/ClarifyingQuestionRepository.kt`

```kotlin
package com.tbtechs.focusflow.data.repository

import android.util.Log
import com.tbtechs.focusflow.data.local.dao.ClarifyingQuestionDao
import com.tbtechs.focusflow.data.local.entity.ClarifyingQuestionEntity
import java.time.Instant
import java.time.temporal.ChronoUnit

class ClarifyingQuestionRepository(private val dao: ClarifyingQuestionDao) {

    private companion object {
        private const val TAG              = "ClarifyingQuestionRepo"
        private const val RATE_LIMIT_DAYS  = 14L
    }

    suspend fun getPending(): ClarifyingQuestionEntity? =
        runCatching { dao.getUnanswered() }.getOrNull()

    /** Inserts a question only if the 14-day rate limit allows. Returns true on success. */
    suspend fun submitIfAllowed(question: ClarifyingQuestionEntity): Boolean =
        runCatching {
            val since = Instant.now().minus(RATE_LIMIT_DAYS, ChronoUnit.DAYS).toString()
            if (dao.countAskedSince(since) > 0) {
                Log.d(TAG, "Rate limit active — skipping question")
                return@runCatching false
            }
            dao.insert(question)
            true
        }.onFailure { Log.e(TAG, "submitIfAllowed failed: ${it.message}") }
            .getOrDefault(false)

    suspend fun answer(id: String, response: String) {
        runCatching { dao.answer(id, response, Instant.now().toString()) }
            .onFailure { Log.e(TAG, "answer($id) failed: ${it.message}") }
    }

    suspend fun pruneOldAnswered() {
        val cutoff = Instant.now().minus(90, ChronoUnit.DAYS).toString()
        runCatching { dao.deleteAnsweredBefore(cutoff) }
            .onFailure { Log.e(TAG, "pruneOldAnswered failed: ${it.message}") }
    }
}
```

---

## File 15 — `AppModule.kt` additions

In `di/AppModule.kt`, add inside the `object AppModule` block, after the existing
`lateinit var` declarations:

```kotlin
// Phase 1 — behavioural intelligence repositories
lateinit var dayRatingRepository:           DayRatingRepository            private set
lateinit var findingRepository:             FindingRepository              private set
lateinit var behaviouralHypothesisRepository: BehaviouralHypothesisRepository private set
lateinit var clarifyingQuestionRepository:  ClarifyingQuestionRepository   private set
```

Inside `fun init(app: Application)`, after the last existing repository
initialisation (after `achievementEngine = ...`):

```kotlin
// Phase 1 — behavioural intelligence
dayRatingRepository = DayRatingRepository(
    dao = database.dayRatingDao(),
)
findingRepository = FindingRepository(
    findingDao = database.findingDao(),
    ackDao     = database.findingAcknowledgementDao(),
)
behaviouralHypothesisRepository = BehaviouralHypothesisRepository(
    dao = database.behaviouralHypothesisDao(),
)
clarifyingQuestionRepository = ClarifyingQuestionRepository(
    dao = database.clarifyingQuestionDao(),
)
```

---

## File 16 — `NotificationChannels.kt` — add DAY_RATING channel

Add the channel constant alongside the existing ones:

```kotlin
const val DAY_RATING = "day-rating"
```

Add to `createAll(context: Context)`:

```kotlin
if (nm.getNotificationChannel(DAY_RATING) == null) {
    nm.createNotificationChannel(
        NotificationChannel(
            DAY_RATING,
            "Day rating",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description     = "Daily prompt to rate your day"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
    )
}
```

---

## File 17 — `DayRatingReminderReceiver.kt`

`enforcement/receivers/DayRatingReminderReceiver.kt`

```kotlin
package com.tbtechs.focusflow.enforcement.receivers

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.tbtechs.focusflow.LauncherActivity
import com.tbtechs.focusflow.R
import com.tbtechs.focusflow.notifications.NotificationChannels

/**
 * Fires at sleep_time − 30 min (or 22:00 fallback) to prompt the user to
 * rate their day. Reschedules itself for the next day via
 * [DayRatingNotificationScheduler.scheduleNext].
 */
class DayRatingReminderReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION = "com.tbtechs.focusflow.alarm.DAY_RATING_REMIND"
        private const val NOTIFICATION_ID = 8812
        private const val WAKELOCK_MS     = 8_000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return

        val wl = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FocusFlow:DayRatingReminder")
            ?.also { it.setReferenceCounted(false); it.acquire(WAKELOCK_MS) }

        try {
            postNotification(context)
            // Self-reschedule keeps the alarm alive without exact-alarm permission
            DayRatingNotificationScheduler.scheduleNext(context)
        } finally {
            if (wl?.isHeld == true) runCatching { wl.release() }
        }
    }

    private fun postNotification(context: Context) {
        val tapIntent = Intent(context, LauncherActivity::class.java).apply {
            action = LauncherActivity.ACTION_OPEN_DAY_RATING
            flags  = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, NotificationChannels.DAY_RATING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("How was today?")
            .setContentText("Rate your day — one tap.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, notification)
    }
}
```

---

## File 18 — `DayRatingNotificationScheduler.kt`

`enforcement/DayRatingNotificationScheduler.kt`

```kotlin
package com.tbtechs.focusflow.enforcement

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tbtechs.focusflow.enforcement.receivers.DayRatingReminderReceiver
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Schedules the daily day-rating reminder using [AlarmManager.setInexactRepeating].
 * No SCHEDULE_EXACT_ALARM permission required.
 *
 * Target time = user's sleep_time − 30 min. Fallback: 22:00.
 * SharedPrefs keys (file "focusday_prefs"):
 *   sleep_time_hour   Int  (0–23)
 *   sleep_time_minute Int  (0–59)
 *   day_rating_alarm_set Boolean
 */
object DayRatingNotificationScheduler {

    private const val TAG     = "DayRatingScheduler"
    private const val PREFS   = "focusday_prefs"
    private const val REQUEST = 8813

    /** Schedule for tomorrow (or today if the window hasn't passed). */
    fun scheduleNext(context: Context) {
        val prefs  = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val hour   = prefs.getInt("sleep_time_hour",   22)
        val minute = prefs.getInt("sleep_time_minute",  0)
        val zone   = ZoneId.systemDefault()

        var targetMs = LocalDate.now()
            .atTime(LocalTime.of(hour, minute))
            .minusMinutes(30)
            .atZone(zone).toInstant().toEpochMilli()

        if (targetMs <= System.currentTimeMillis()) {
            targetMs = LocalDate.now().plusDays(1)
                .atTime(LocalTime.of(hour, minute))
                .minusMinutes(30)
                .atZone(zone).toInstant().toEpochMilli()
        }

        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
            .setInexactRepeating(
                AlarmManager.RTC,
                targetMs,
                AlarmManager.INTERVAL_DAY,
                buildPi(context),
            )

        prefs.edit().putBoolean("day_rating_alarm_set", true).apply()
        Log.d(TAG, "Scheduled for $targetMs ms")
    }

    /** Call on first launch or when the user changes their sleep time. */
    fun ensureScheduled(context: Context) {
        val set = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("day_rating_alarm_set", false)
        if (!set) scheduleNext(context)
    }

    fun cancel(context: Context) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
            .cancel(buildPi(context))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("day_rating_alarm_set", false).apply()
    }

    private fun buildPi(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, REQUEST,
            Intent(context, DayRatingReminderReceiver::class.java).apply {
                action = DayRatingReminderReceiver.ACTION
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
```

---

## File 19 — `BootReceiver.kt` addition

Inside the existing `onReceive`, after all existing alarm rescheduling:

```kotlin
// Reschedule day rating reminder (lost on reboot)
DayRatingNotificationScheduler.ensureScheduled(context)
```

---

## File 20 — `FocusFlowApp.kt` addition

Inside `onCreate()`, after `NotificationChannels.createAll(this)` (step 4):

```kotlin
// 5. Ensure day-rating daily alarm is scheduled.
DayRatingNotificationScheduler.ensureScheduled(this)
```

---

## File 21 — `BackgroundFetchWorker.kt` — daily prune addition

In `doWork()`, after the existing work completes and before `Result.success()`,
add a daily-gated prune block. Uses `AppModule.database` directly — no gateway
change needed since prune is a DB maintenance concern, not a task/notification
operation.

```kotlin
// ── Daily prune — runs once per calendar day ──────────────────────────────────
val prunePrefs  = applicationContext.getSharedPreferences("focusday_prefs", Context.MODE_PRIVATE)
val todayStr    = java.time.LocalDate.now().toString()
val lastPruned  = prunePrefs.getString("last_prune_date", "") ?: ""
if (lastPruned != todayStr) {
    runCatching { prunePhase1Tables() }
        .onSuccess { prunePrefs.edit().putString("last_prune_date", todayStr).apply() }
        .onFailure { Log.w("BackgroundFetch", "Phase 1 prune failed: ${it.message}") }
}
```

Add private suspend function inside `BackgroundFetchWorker`:

```kotlin
private suspend fun prunePhase1Tables() {
    val db      = com.tbtechs.focusflow.di.AppModule.database
    val cutDate = java.time.LocalDate.now().minusDays(90).toString()
    val cutIso  = java.time.Instant.now()
        .minus(90, java.time.temporal.ChronoUnit.DAYS).toString()

    db.dailyAppUsageDao().deleteOlderThan(cutDate)
    db.appSessionDao().deleteOlderThan(cutDate)
    db.findingDao().deleteOldResolved(cutIso)
    db.clarifyingQuestionDao().deleteAnsweredBefore(cutIso)
}
```

---

## File 22 — `AndroidManifest.xml` additions

```xml
<!-- Day rating reminder receiver -->
<receiver
    android:name=".enforcement.receivers.DayRatingReminderReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="com.tbtechs.focusflow.alarm.DAY_RATING_REMIND" />
    </intent-filter>
</receiver>

<!-- Required for API 34+ foreground service type (see API_COMPAT.md) -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
```

---

## `LauncherActivity.kt` — add the deep-link action constant

```kotlin
companion object {
    // existing constants ...
    const val ACTION_OPEN_DAY_RATING = "com.tbtechs.focusflow.action.OPEN_DAY_RATING"
}
```

Handle `ACTION_OPEN_DAY_RATING` in `onCreate` / `onNewIntent` to navigate
to the Stats tab with the day rating bar focused. Implementation is a UI
concern covered in IMPL_2.

---

## IMPL_1B complete — what's now in place

| Component | Status |
|---|---|
| All 7 new entity files | ✓ |
| All 7 new DAO files | ✓ |
| 4 new repositories | ✓ |
| `AppModule` wired | ✓ |
| `NotificationChannels.DAY_RATING` | ✓ |
| Daily rating alarm (receiver + scheduler) | ✓ |
| Alarm rescheduled on boot | ✓ |
| Alarm ensured on app start | ✓ |
| Daily prune of Phase 1 tables | ✓ |
| `LauncherActivity.ACTION_OPEN_DAY_RATING` stub | ✓ |

Next: **IMPL_2** — the UI composables (DayRatingBar, FindingCardView, FindingsSection, ColdStartSheet, StatsInsightsExperience integration) built on the actual `StatsViewModel` and `AnalyticsWindow` values from the real codebase.
