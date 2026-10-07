package com.tbtechs.focusflow.data.restore

import com.tbtechs.focusflow.data.model.Task
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for the journal wire format (assumes JUnit 4; adjust imports if you use kotlin.test). */
class RestoreJournalCodecTest {

    private val journal = RestoreJournal.fromPlan(
        sessionId = "session-1",
        plan = RestorePlan(
            mode = RestoreMode.REPLACE,
            planNowMillis = 1_700_000_000_000,
            deleteAllExisting = true,
            preDeleteTaskIds = listOf("old-1"),
            tasksToInsert = listOf(
                Task(
                    id = "t1",
                    title = "Write",
                    startTime = "2026-10-08T09:00:00.000Z",
                    endTime = "2026-10-08T10:00:00.000Z",
                    durationMinutes = 60,
                    status = "scheduled",
                    priority = "medium",
                    color = "#6366F1",
                    focusMode = false,
                    createdAt = "2026-10-01T00:00:00.000Z",
                    updatedAt = "2026-10-01T00:00:00.000Z",
                ),
            ),
            settingsPlan = JsonObject(emptyMap()),
            userGreyoutWindows = JsonArray(emptyList()),
            recurringBlockSchedules = JsonArray(emptyList()),
            warningCodes = emptyList(),
            counts = RestoreCounts(tasksInFile = 1, tasksInserted = 1),
        ),
    )

    @Test
    fun writtenJournalCarriesExplicitVersionAndRoundTrips() {
        val text = RestoreStoreJson.encodeToString(journal)
        assertTrue(text.contains("\"journalVersion\":1"))
        assertEquals(RestoreJournalRead.Value(journal), parseJournalText(text))
    }

    @Test
    fun journalWrittenWithoutDefaultsStillReads() {
        // Reproduces files written before encodeDefaults was enabled.
        val legacy = Json { encodeDefaults = false }.encodeToString(journal)
        assertFalse(legacy.contains("journalVersion"))
        assertEquals(RestoreJournalRead.Value(journal), parseJournalText(legacy))
    }

    @Test
    fun differentVersionIsRejected() {
        val text = RestoreStoreJson.encodeToString(journal).replace("\"journalVersion\":1", "\"journalVersion\":2")
        assertEquals(RestoreJournalRead.UnknownVersion(2), parseJournalText(text))
    }

    @Test
    fun garbageIsCorrupt() {
        assertTrue(parseJournalText("not json") is RestoreJournalRead.Corrupt)
        assertTrue(parseJournalText("[]") is RestoreJournalRead.Corrupt)
    }
}
