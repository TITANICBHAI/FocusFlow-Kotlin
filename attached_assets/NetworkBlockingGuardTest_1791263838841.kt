package com.tbtechs.focusflow.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkBlockingGuardTest {
    @Test
    fun enablingIsAllowedWhileFocusOrStandaloneIsActive() {
        assertTrue(
            ActiveBlockGuardPolicy.mayChangeNetworkBlocking(
                currentlyEnabled = false,
                requestedEnabled = true,
                blockActive = true,
            ),
        )
    }

    @Test
    fun disablingIsBlockedWhileABlockIsActive() {
        assertFalse(
            ActiveBlockGuardPolicy.mayChangeNetworkBlocking(
                currentlyEnabled = true,
                requestedEnabled = false,
                blockActive = true,
            ),
        )
    }

    @Test
    fun disablingIsAllowedWhenNoBlockIsActive() {
        assertTrue(
            ActiveBlockGuardPolicy.mayChangeNetworkBlocking(
                currentlyEnabled = true,
                requestedEnabled = false,
                blockActive = false,
            ),
        )
    }

    @Test
    fun anUnchangedValueIsNeverRejected() {
        for (value in listOf(true, false)) {
            for (blockActive in listOf(true, false)) {
                assertTrue(
                    ActiveBlockGuardPolicy.mayChangeNetworkBlocking(
                        currentlyEnabled = value,
                        requestedEnabled = value,
                        blockActive = blockActive,
                    ),
                )
            }
        }
    }
}
