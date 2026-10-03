package com.tbtechs.focusflow.ui.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportProtectionPolicyTest {
    private val protectedSettings = ProtectedSettingsSnapshot(
        alwaysOnPackages = setOf("social"),
        alwaysOnVpnPackages = setOf("streaming"),
        blockedWords = setOf("term"),
        recurringBlockSchedules = setOf("night"),
        dailyAllowanceEntries = setOf("game"),
        focusMirrorVpnEnabled = true,
    )

    @Test
    fun requiresPinWhenAnImportRemovesAnyProtectedEntry() {
        val weakeningImport = protectedSettings.copy(
            alwaysOnPackages = emptySet(),
        )

        assertTrue(ImportProtectionPolicy.requiresPin(protectedSettings, weakeningImport))
    }

    @Test
    fun requiresPinWhenImportTurnsOffFocusMirror() {
        val weakeningImport = protectedSettings.copy(focusMirrorVpnEnabled = false)

        assertTrue(ImportProtectionPolicy.requiresPin(protectedSettings, weakeningImport))
    }

    @Test
    fun doesNotRequirePinForAdditionsOrUnchangedSettings() {
        val additiveImport = protectedSettings.copy(
            alwaysOnPackages = protectedSettings.alwaysOnPackages + "new-app",
            blockedWords = protectedSettings.blockedWords + "new-term",
        )

        assertFalse(ImportProtectionPolicy.requiresPin(protectedSettings, additiveImport))
    }
}