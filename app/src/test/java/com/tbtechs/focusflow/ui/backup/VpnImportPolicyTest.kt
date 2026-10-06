package com.tbtechs.focusflow.ui.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnImportPolicyTest {
    @Test
    fun consentIsSkippedWhenSettingsOrVpnListAreNotRestored() {
        assertEquals(
            VpnImportConsentDecision.NOT_REQUIRED,
            VpnImportPolicy.consentDecision(
                restoreSettings = false,
                importedVpnPackageCount = 3,
                networkBlockEnabled = false,
                vpnPermissionGranted = false,
            ),
        )
        assertEquals(
            VpnImportConsentDecision.NOT_REQUIRED,
            VpnImportPolicy.consentDecision(
                restoreSettings = true,
                importedVpnPackageCount = 0,
                networkBlockEnabled = false,
                vpnPermissionGranted = false,
            ),
        )
    }

    @Test
    fun consentIsNeededOnlyWhenNetworkBlockingIsOffAndPermissionIsMissing() {
        assertEquals(
            VpnImportConsentDecision.REQUEST_CONSENT,
            VpnImportPolicy.consentDecision(
                restoreSettings = true,
                importedVpnPackageCount = 2,
                networkBlockEnabled = false,
                vpnPermissionGranted = false,
            ),
        )
        assertEquals(
            VpnImportConsentDecision.NOT_REQUIRED,
            VpnImportPolicy.consentDecision(
                restoreSettings = true,
                importedVpnPackageCount = 2,
                networkBlockEnabled = true,
                vpnPermissionGranted = false,
            ),
        )
    }

    @Test
    fun preGrantedPermissionActivatesWithoutPromptAndCancellationStaysDormant() {
        val preGranted = VpnImportPolicy.consentDecision(
            restoreSettings = true,
            importedVpnPackageCount = 1,
            networkBlockEnabled = false,
            vpnPermissionGranted = true,
        )
        assertEquals(VpnImportConsentDecision.PERMISSION_ALREADY_GRANTED, preGranted)
        assertTrue(VpnImportPolicy.shouldActivateAfterConsent(preGranted, vpnPermissionGranted = false))

        assertFalse(
            VpnImportPolicy.shouldActivateAfterConsent(
                VpnImportConsentDecision.REQUEST_CONSENT,
                vpnPermissionGranted = false,
            ),
        )
        assertTrue(
            VpnImportPolicy.shouldActivateAfterConsent(
                VpnImportConsentDecision.REQUEST_CONSENT,
                vpnPermissionGranted = true,
            ),
        )
        assertFalse(
            VpnImportPolicy.shouldActivateAfterConsent(
                VpnImportConsentDecision.NOT_REQUIRED,
                vpnPermissionGranted = true,
            ),
        )
    }

    @Test
    fun importActivatesVpnProtectionOnlyForASettingsRestoreWithConsent() {
        assertTrue(
            VpnImportPolicy.shouldActivateImportedVpnBlock(
                restoreSettings = true,
                decision = VpnImportConsentDecision.PERMISSION_ALREADY_GRANTED,
                vpnPermissionGranted = false,
            ),
        )
        assertTrue(
            VpnImportPolicy.shouldActivateImportedVpnBlock(
                restoreSettings = true,
                decision = VpnImportConsentDecision.REQUEST_CONSENT,
                vpnPermissionGranted = true,
            ),
        )
        assertFalse(
            VpnImportPolicy.shouldActivateImportedVpnBlock(
                restoreSettings = true,
                decision = VpnImportConsentDecision.REQUEST_CONSENT,
                vpnPermissionGranted = false,
            ),
        )
        assertFalse(
            VpnImportPolicy.shouldActivateImportedVpnBlock(
                restoreSettings = false,
                decision = VpnImportConsentDecision.PERMISSION_ALREADY_GRANTED,
                vpnPermissionGranted = true,
            ),
        )
        assertFalse(
            VpnImportPolicy.shouldActivateImportedVpnBlock(
                restoreSettings = true,
                decision = VpnImportConsentDecision.NOT_REQUIRED,
                vpnPermissionGranted = true,
            ),
        )
    }
}
