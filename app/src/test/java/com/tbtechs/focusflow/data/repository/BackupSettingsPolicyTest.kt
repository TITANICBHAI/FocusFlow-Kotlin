package com.tbtechs.focusflow.data.repository

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BackupSettingsPolicyTest {
    @Test
    fun oldExportLiveStateKeysAreNeverImported() {
        val fixture = requireNotNull(
            javaClass.classLoader?.getResource("backup/old-export-1.0.6.json"),
        ).readText()
        val settings = Json.parseToJsonElement(fixture)
            .jsonObject.getValue("settings")
            .jsonObject
        val importable = BackupSettingsPolicy.importableKeys(settings.keys)

        assertEquals(21, BackupSettingsPolicy.neverApplyImportKeys.size)
        assertFalse(importable.any { it in BackupSettingsPolicy.neverApplyImportKeys })
        assertEquals(setOf("allowedInFocus"), importable)
    }
}