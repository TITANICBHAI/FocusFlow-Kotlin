package com.tbtechs.focusflow.data.backup

import com.tbtechs.focusflow.data.model.AppSettings
import com.tbtechs.focusflow.data.model.BlockPreset
import com.tbtechs.focusflow.data.repository.BackupSettingsPolicy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TsSettingsAdapterTest {
    @Test
    fun importsOnlyPortableTsKeysAndUsesBackupBooleanValue() {
        val current = AppSettings(
            defaultDurationMinutes = 60,
            focusMirrorVpnEnabled = true,
        )
        val input = jsonObject(
            """{"defaultDuration":45,"focusMirrorVpnEnabled":false,"alwaysOnEnforcementEnabled":true,"standaloneBlockActive":true}""",
        )

        val applied = TsSettingsAdapter.applyToSettings(current, input)

        assertEquals(45, applied.settings.defaultDurationMinutes)
        assertFalse(applied.settings.focusMirrorVpnEnabled)
        assertFalse(applied.settings.alwaysBlockEnabled)
        assertTrue(applied.warnings.isEmpty())
    }

    @Test
    fun absentProtectionSettingPreservesTheLocalValue() {
        val current = AppSettings(focusMirrorVpnEnabled = true)

        val applied = TsSettingsAdapter.applyToSettings(current, jsonObject("""{"darkMode":false}"""))

        assertFalse(applied.settings.darkModeEnabled)
        assertTrue(applied.settings.focusMirrorVpnEnabled)
    }

    @Test
    fun settingsApplyKeepsUserAuthoredWindowsSeparateFromRecurringSchedules() {
        val input = jsonObject(
            """
            {
              "recurringBlockSchedules":[{
                "id":"weekly","name":"Weekly","packages":["com.example.social"],
                "days":[1,7],"startHour":21,"startMin":15,"endHour":6,"endMin":30
              }],
              "greyoutSchedule":[{
                "pkg":"com.example.video","days":[2],"startHour":9,"startMin":0,
                "endHour":10,"endMin":0
              }]
            }
            """.trimIndent(),
        )

        val applied = TsSettingsAdapter.applyToSettings(AppSettings(), input)

        assertEquals(listOf(0, 6), applied.settings.recurringBlockSchedules.single().daysOfWeek)
        assertEquals(15, applied.settings.recurringBlockSchedules.single().startMinute)
        assertTrue(applied.settings.userGreyoutWindowsJson.contains("com.example.video"))
        assertFalse(applied.settings.userGreyoutWindowsJson.contains("scheduleId"))
    }

    @Test
    fun exportUsesTypescriptNamesAndKeepsNewSettingsInTheirOwnFields() {
        val settings = AppSettings(
            defaultDurationMinutes = 75,
            pomodoroWorkMinutes = 35,
            alwaysOnVpnPackages = listOf("com.example.vpn"),
            blockPresets = listOf(BlockPreset("block-1", "Block", listOf("com.example.social"))),
            overlayQuotes = listOf("Stay focused"),
            launcherClockStyle = "digital",
        )

        val wire = TsSettingsAdapter.toWireSettings(settings)

        assertEquals(JsonPrimitive(75), wire["defaultDuration"])
        assertEquals(JsonPrimitive(35), wire["pomodoroDuration"])
        assertNotNull(wire["alwaysOnVpnPackages"])
        assertNotNull(wire["blockPresets"])
        assertNotNull(wire["overlayQuotes"])
        assertNotNull(wire["launcherClockStyle"])
        assertFalse(wire.containsKey("defaultDurationMinutes"))
        assertFalse(wire.keys.any { it in BackupSettingsPolicy.neverApplyImportKeys })
    }

    @Test
    fun legacyMigrationMapsValidatedValuesToCorrectPreferenceTypesAndKeys() {
        val legacy = TsSettingsAdapter.parseLegacySettingsJson(
            """
            {
              "darkMode": false,
              "defaultDuration": 45,
              "allowedInFocus": ["com.example.focus"],
              "alwaysOnVpnPackages": ["com.example.vpn"],
              "blockPresets": [{"id":"b1","name":"Block","packages":["com.example.social"]}],
              "overlayQuotes": ["Quote"],
              "recurringBlockSchedules": [{
                "id":"s1","name":"Schedule","packages":["com.example.social"],
                "days":[1,7],"startHour":21,"startMin":15,"endHour":6,"endMin":30
              }],
              "alwaysOnEnforcementEnabled": true
            }
            """.trimIndent(),
        )

        val mapped = TsSettingsAdapter.normalizeForLegacyMigration(legacy)

        assertEquals(
            LegacyPreferenceValue.BooleanValue(false),
            mapped["dark_mode_enabled"],
        )
        assertEquals(LegacyPreferenceValue.IntValue(45), mapped["default_duration_minutes"])
        assertEquals(
            LegacyPreferenceValue.StringValue("""["com.example.focus"]"""),
            mapped["allowed_focus_packages"],
        )
        assertEquals(
            LegacyPreferenceValue.StringValue("""["com.example.vpn"]"""),
            mapped["always_on_vpn_packages"],
        )
        assertTrue(mapped.containsKey("block_presets"))
        assertTrue(mapped.containsKey("block_overlay_quotes"))
        assertTrue(mapped.containsKey("recurring_block_schedules"))
        assertFalse(mapped.containsKey("always_block_enabled"))
    }

    @Test
    fun badPortableSettingIsIgnoredWithWarningRatherThanApplied() {
        val normalized = TsSettingsAdapter.normalizeForImport(
            jsonObject("""{"defaultDuration":9999}"""),
        )

        assertTrue(normalized.settings.isEmpty())
        assertTrue(normalized.warnings.any { it.contains("defaultDuration") })
        assertEquals(JsonObject(emptyMap()), normalized.settings)
    }

    private fun jsonObject(text: String): JsonObject =
        Json.parseToJsonElement(text).jsonObject
}
