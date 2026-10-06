package com.tbtechs.focusflow.ui.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnImportNoticeTest {
    private fun notice(
        restoreSettings: Boolean = true,
        count: Int = 3,
        networkBlockEnabled: Boolean = false,
        permissionGranted: Boolean = false,
    ) = VpnImportPolicy.notice(restoreSettings, count, networkBlockEnabled, permissionGranted)

    @Test
    fun noNoticeWhenSettingsAreNotBeingRestored() = assertNull(notice(restoreSettings = false))

    @Test
    fun noNoticeWhenTheFileHasNoVpnApps() = assertNull(notice(count = 0))

    @Test
    fun noNoticeWhenNetworkBlockingIsAlreadyOn() = assertNull(notice(networkBlockEnabled = true))

    @Test
    fun noticeCarriesTheCountAndWhetherAndroidWillAsk() {
        assertEquals(VpnImportNotice(3, permissionGranted = false), notice(permissionGranted = false))
        assertEquals(VpnImportNotice(3, permissionGranted = true), notice(permissionGranted = true))
    }

    @Test
    fun noticeAppearsExactlyWhenImportWouldActivateProtection() {
        // The screen text and the real activation must never disagree.
        for (restore in listOf(true, false)) for (count in listOf(0, 1, 5)) for (on in listOf(true, false)) for (granted in listOf(true, false)) {
            val decision = VpnImportPolicy.consentDecision(restore, count, on, granted)
            val shown = VpnImportPolicy.notice(restore, count, on, granted) != null
            assertEquals(
                "restore=$restore count=$count on=$on granted=$granted",
                decision != VpnImportConsentDecision.NOT_REQUIRED,
                shown,
            )
        }
    }

    @Test
    fun noticeTextUsesSingularAndPlural() {
        assertTrue(VpnImportPolicy.noticeText(VpnImportNotice(1, true)).contains("1 VPN-blocked app."))
        assertTrue(VpnImportPolicy.noticeText(VpnImportNotice(4, true)).contains("4 VPN-blocked apps."))
    }

    @Test
    fun noticeTextMentionsTheAndroidPromptOnlyWhenPermissionIsMissing() {
        assertTrue(VpnImportPolicy.noticeText(VpnImportNotice(2, permissionGranted = false)).contains("Android will ask"))
        assertFalse(VpnImportPolicy.noticeText(VpnImportNotice(2, permissionGranted = true)).contains("Android"))
    }

    @Test
    fun inactiveCardTellsTheUserWhatToDoNext() {
        assertEquals("No entries were imported.", VpnImportPolicy.inactiveDetails(0, false, false))
        assertTrue(VpnImportPolicy.inactiveDetails(3, featureEnabled = false, permissionAvailable = true).contains("Turn it on in Defense"))
        assertTrue(VpnImportPolicy.inactiveDetails(3, featureEnabled = false, permissionAvailable = false).contains("Turn it on in Defense"))
        assertTrue(VpnImportPolicy.inactiveDetails(3, featureEnabled = true, permissionAvailable = false).contains("Allow it from Defense"))
        assertTrue(VpnImportPolicy.inactiveDetails(1, featureEnabled = false, permissionAvailable = true).startsWith("1 app was imported"))
        assertNotNull(VpnImportPolicy.inactiveDetails(2, featureEnabled = true, permissionAvailable = true))
    }
}
