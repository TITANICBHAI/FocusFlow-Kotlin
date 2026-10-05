package com.tbtechs.focusflow.ui.alwayson

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnConsentPolicyTest {
    @Test
    fun cancelledConsentDoesNotConfirmPermissionOrEnableProtection() {
        val outcome = VpnConsentPolicy.afterResult(permissionGranted = false)

        assertEquals(VpnConsentOutcome.NOT_GRANTED, outcome)
        assertFalse(outcome.isGranted)
    }

    @Test
    fun confirmedSystemPermissionAllowsProtectionFlowToContinue() {
        val outcome = VpnConsentPolicy.afterResult(permissionGranted = true)

        assertEquals(VpnConsentOutcome.GRANTED, outcome)
        assertTrue(outcome.isGranted)
    }
}
