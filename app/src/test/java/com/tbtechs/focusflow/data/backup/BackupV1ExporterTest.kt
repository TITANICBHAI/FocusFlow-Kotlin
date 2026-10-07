package com.tbtechs.focusflow.data.backup

import com.tbtechs.focusflow.data.model.AllowedAppPreset
import com.tbtechs.focusflow.data.model.AppSettings
import com.tbtechs.focusflow.data.model.BlockPreset
import com.tbtechs.focusflow.data.model.RecurringBlockSchedule
import com.tbtechs.focusflow.data.repository.BackupSettingsPolicy
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupV1ExporterTest {
    private val now = Instant.parse("2026-10-03T12:34:56.789Z")
    private val exportedAtHuman = "10/3/26, 12:34:56 PM"
    private val appVersion = "1.1.4"

    @Test
    fun exportMatchesTypescriptV1GoldenWithPortableSettingsAndCanonicalTasks() {
        val wireSettings = TsSettingsAdapter.toWireSettings(
            settings = sampleSettings(),
            userProfileJson = """{"name":"Morgan","focusGoals":["plan"]}""",
            protectionMode = "iron",
        )
        val settingsWithDeviceState = JsonObject(
            wireSettings.toMutableMap().apply {
                BackupSettingsPolicy.neverApplyImportKeys.forEach {
                    put(it, JsonPrimitive(true))
                }
                put("onboardingComplete", JsonPrimitive(true))
                put("privacyAccepted", JsonPrimitive(true))
                put("launcherWallpaperUri", JsonPrimitive("content://device-local/wallpaper"))
                put("overlayWallpaper", JsonPrimitive("content://device-local/overlay"))
            },
        )

        val actual = BackupV1Exporter.buildBackupJson(
            settings = settingsWithDeviceState,
            tasks = listOf(sampleTask()),
            exportedAt = now,
            exportedAtHuman = exportedAtHuman,
            appVersion = appVersion,
        )

        assertEquals(goldenFixture(), Json.parseToJsonElement(actual).jsonObject)
    }

    @Test
    fun typescriptGoldenFixtureImportsAndExportsThroughKotlinWithoutSemanticDrift() {
        val expected = goldenFixture()
        val parsed = BackupV1Parser.parse(expected.toString(), now)
        assertTrue(parsed is BackupV1ParseResult.Success)
        val importedEnvelope = (parsed as BackupV1ParseResult.Success).backup.envelope
        assertEquals(listOf("planning"), importedEnvelope.tasks.single().tags)

        val applied = TsSettingsAdapter.applyToSettings(
            current = AppSettings(),
            input = importedEnvelope.settings,
        )
        val reExportedSettings = TsSettingsAdapter.toWireSettings(
            settings = applied.settings,
            userProfileJson = applied.userProfileJson,
            protectionMode = applied.protectionMode,
        )
        val reExported = BackupV1Exporter.buildBackupJson(
            settings = reExportedSettings,
            tasks = importedEnvelope.tasks.map { it.wire },
            exportedAt = now,
            exportedAtHuman = exportedAtHuman,
            appVersion = appVersion,
        )
        val actual = Json.parseToJsonElement(reExported).jsonObject

        assertEquals(expected, actual)
        assertEquals(
            JsonArray(listOf(JsonPrimitive("planning"))),
            actual["tasks"]!!.jsonArray.single().jsonObject["tags"]!!.jsonArray,
        )
        assertEquals(
            expected["tasks"]!!.jsonArray.single().jsonObject["reminders"],
            actual["tasks"]!!.jsonArray.single().jsonObject["reminders"],
        )
        val settings = actual["settings"]!!.jsonObject
        assertFalse(settings.containsKey("defaultDurationMinutes"))
        assertFalse(settings.containsKey("pomodoroWorkMinutes"))
        assertFalse(settings.containsKey("startMinute"))
        assertEquals(JsonPrimitive(false), settings["focusMirrorVpnEnabled"])
        assertEquals(
            JsonArray(listOf(JsonPrimitive(1), JsonPrimitive(7))),
            settings["recurringBlockSchedules"]!!.jsonArray.single().jsonObject["days"],
        )
    }

    @Test
    fun exportRejectsInvalidTaskTimestampsInsteadOfWritingAnIncompleteBackup() {
        val invalid = Json.parseToJsonElement(
            sampleTask().toMutableMap().apply {
                put("updatedAt", JsonPrimitive("not-a-timestamp"))
            }.let { JsonObject(it).toString() },
        ).jsonObject

        val error = runCatching {
            BackupV1Exporter.buildBackupJson(
                settings = TsSettingsAdapter.toWireSettings(sampleSettings()),
                tasks = listOf(invalid),
                exportedAt = now,
                exportedAtHuman = exportedAtHuman,
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("updatedAt"))
    }

    private fun sampleSettings() = AppSettings(
        darkModeEnabled = false,
        alwaysBlockPackages = listOf("com.example.blocked"),
        launcherPresets = listOf(
            AllowedAppPreset("allowed-1", "Focus", listOf("com.example.focus")),
        ),
        blockPresets = listOf(
            BlockPreset("blocked-1", "Block", listOf("com.example.blocked")),
        ),
        alwaysOnVpnPackages = emptyList(),
        dailyAllowanceConfigJson =
            """[{"packageName":"com.example.timer","mode":"count","countPerDay":3,"dailyAllowanceMs":0}]""",
        recurringBlockSchedules = listOf(
            RecurringBlockSchedule(
                id = "weekly",
                name = "Weekly",
                packages = listOf("com.example.social"),
                startHour = 21,
                startMinute = 15,
                endHour = 6,
                endMinute = 30,
                daysOfWeek = listOf(0, 6),
            ),
        ),
        userGreyoutWindowsJson =
            """[{"pkg":"com.example.video","startHour":9,"startMin":0,"endHour":10,"endMin":0,"days":[2]}]""",
        focusMirrorVpnEnabled = false,
        defaultDurationMinutes = 45,
        pomodoroWorkMinutes = 30,
        pomodoroBreakMinutes = 10,
        allowedFocusPackages = listOf("com.example.focus"),
        launcherTheme = "glassy",
        launcherClockStyle = "digital",
        overlayQuotes = listOf("Stay focused"),
    )

    private fun sampleTask(): JsonObject = Json.parseToJsonElement(
        """
        {
          "id":"task-1",
          "title":"Planning",
          "description":"Plan the week",
          "startTime":"2026-10-03T13:04:05.006789+01:00",
          "endTime":"2026-10-03T13:34:05.009+01:00",
          "durationMinutes":30,
          "status":"scheduled",
          "priority":"high",
          "tags":["planning"],
          "reminders":[{
            "id":"reminder-1",
            "taskId":"task-1",
            "offsetMinutes":-5,
            "type":"pre-start",
            "notifId":"notify-1"
          }],
          "color":"#22c55e",
          "focusMode":true,
          "focusAllowedPackages":[],
          "createdAt":"2026-10-03T12:00:00Z",
          "updatedAt":"2026-10-03T12:01:02.1Z"
        }
        """.trimIndent(),
    ).jsonObject

    private fun goldenFixture(): JsonObject =
        requireNotNull(javaClass.classLoader?.getResource("backup/v1-export-golden.json"))
            .readText()
            .let { Json.parseToJsonElement(it).jsonObject }
}