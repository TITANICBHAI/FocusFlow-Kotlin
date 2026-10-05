package com.tbtechs.focusflow.enforcement.health

import com.tbtechs.focusflow.ui.permissions.PermissionDefinition
import com.tbtechs.focusflow.ui.permissions.PermissionId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnforcementHealthTest {
    @Test
    fun requiredPermissionSetComesFromOptionalFlags() {
        val definitions = listOf(
            permission(PermissionId.ACCESSIBILITY, optional = false),
            permission(PermissionId.USAGE, optional = true),
            permission(PermissionId.OVERLAY, optional = false),
        )

        val required = EnforcementHealthReader.requiredPermissionIds(definitions)
        val incomplete = EnforcementHealth.fromRequiredPermissions(
            required = required,
            granted = setOf(PermissionId.ACCESSIBILITY),
        )
        val complete = EnforcementHealth.fromRequiredPermissions(
            required = required,
            granted = setOf(PermissionId.ACCESSIBILITY, PermissionId.OVERLAY),
        )

        assertEquals(
            listOf(PermissionId.ACCESSIBILITY, PermissionId.OVERLAY),
            required,
        )
        assertEquals(listOf(PermissionId.OVERLAY), incomplete.missingRequiredPermissions)
        assertTrue(incomplete.needsAttention)
        assertFalse(complete.needsAttention)
    }

    @Test
    fun unknownHealthRequiresAttentionUntilVerified() {
        assertTrue(EnforcementHealth.UNKNOWN.needsAttention)
    }

    @Test
    fun accessibilityRefreshesOnlyWhenTheObservedValueChanges() {
        val tracker = AccessibilityStateChangeTracker()

        assertFalse(tracker.observe(false))
        assertFalse(tracker.observe(false))
        assertTrue(tracker.observe(true))
        assertFalse(tracker.observe(true))
        assertTrue(tracker.observe(false))
    }

    private fun permission(id: PermissionId, optional: Boolean) = PermissionDefinition(
        id = id,
        title = id.name,
        description = "",
        whyNeeded = "",
        brokenWithout = emptyList(),
        optional = optional,
        actionLabel = "",
    )
}
