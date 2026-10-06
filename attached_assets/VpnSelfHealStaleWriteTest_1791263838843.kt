package com.tbtechs.focusflow.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class VpnSelfHealStaleWriteTest {
    @Test
    fun anUnchangedCachedValueNeverOverwritesTheStoredFlag() {
        // The VPN list screen turned self-heal on in the store; the view model still caches false.
        assertEquals(
            true,
            VpnSelfHealPolicy.valueToPersist(loadedValue = false, requestedValue = false, storedValue = true),
        )
    }

    @Test
    fun anExplicitUserToggleWins() {
        assertEquals(
            false,
            VpnSelfHealPolicy.valueToPersist(loadedValue = true, requestedValue = false, storedValue = true),
        )
        assertEquals(
            true,
            VpnSelfHealPolicy.valueToPersist(loadedValue = false, requestedValue = true, storedValue = false),
        )
    }

    @Test
    fun consistentStateIsLeftAlone() {
        for (value in listOf(true, false)) {
            assertEquals(
                value,
                VpnSelfHealPolicy.valueToPersist(loadedValue = value, requestedValue = value, storedValue = value),
            )
        }
    }
}
