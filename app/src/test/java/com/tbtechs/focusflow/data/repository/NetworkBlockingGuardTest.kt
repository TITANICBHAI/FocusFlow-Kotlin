package com.tbtechs.focusflow.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkBlockingGuardTest {
    @Test
    fun enablingNetworkBlockingOrVpnIsAllowedWhileABlockIsActive() {
        assertTrue(
            ActiveBlockGuardPolicy.mayChangeNetworkBlocking(
                currentlyEnabled = false,
                requestedEnabled = true,
                blockActive = true,
            ),
        )
        assertTrue(
            ActiveBlockGuardPolicy.mayChangeNetworkBlocking(
                currentlyEnabled = false,
                requestedEnabled = true,
                blockActive = true,
            ),
        )
    }

    @Test
    fun disablingEitherProtectionSwitchIsRejectedWhileABlockIsActive() {
        assertFalse(
            ActiveBlockGuardPolicy.mayChangeNetworkBlocking(
                currentlyEnabled = true,
                requestedEnabled = false,
                blockActive = true,
            ),
        )
    }

    @Test
    fun noOpAndInactiveBlockChangesAreAllowed() {
        for (value in listOf(true, false)) {
            assertTrue(
                ActiveBlockGuardPolicy.mayChangeNetworkBlocking(
                    currentlyEnabled = value,
                    requestedEnabled = value,
                    blockActive = true,
                ),
            )
        }
        assertTrue(
            ActiveBlockGuardPolicy.mayChangeNetworkBlocking(
                currentlyEnabled = true,
                requestedEnabled = false,
                blockActive = false,
            ),
        )
    }
}
