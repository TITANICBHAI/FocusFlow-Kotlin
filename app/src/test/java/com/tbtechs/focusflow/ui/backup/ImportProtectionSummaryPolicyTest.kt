package com.tbtechs.focusflow.ui.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportProtectionSummaryPolicyTest {
    @Test
    fun onlyImportedCategoriesAppearAndPermissionIsRequiredForActiveStatus() {
        val categories = ImportProtectionSummaryPolicy.build(
            listOf(
                ImportProtectionCategoryFact(
                    id = "vpn",
                    title = "VPN list",
                    wasImported = true,
                    itemCount = 2,
                    featureEnabled = true,
                    requiredPermissionAvailable = false,
                    activeDetails = "active",
                    inactiveDetails = "stored but permission is unavailable",
                ),
                ImportProtectionCategoryFact(
                    id = "keywords",
                    title = "Keywords",
                    wasImported = true,
                    itemCount = 1,
                    featureEnabled = true,
                    requiredPermissionAvailable = true,
                    activeDetails = "active",
                    inactiveDetails = "inactive",
                ),
                ImportProtectionCategoryFact(
                    id = "allowance",
                    title = "Daily allowance",
                    wasImported = false,
                    itemCount = 4,
                    featureEnabled = true,
                    requiredPermissionAvailable = true,
                    activeDetails = "active",
                    inactiveDetails = "inactive",
                ),
            ),
        )

        assertEquals(listOf("vpn", "keywords"), categories.map { it.id })
        assertFalse(categories[0].active)
        assertEquals("stored but permission is unavailable", categories[0].details)
        assertTrue(categories[1].active)
    }

    @Test
    fun emptyOrDisabledProtectionIsReportedInactive() {
        val category = ImportProtectionSummaryPolicy.build(
            listOf(
                ImportProtectionCategoryFact(
                    id = "always-on",
                    title = "Always-On",
                    wasImported = true,
                    itemCount = 0,
                    featureEnabled = true,
                    requiredPermissionAvailable = true,
                    activeDetails = "active",
                    inactiveDetails = "no packages",
                ),
            ),
        ).single()

        assertFalse(category.active)
        assertEquals("no packages", category.details)
    }
}
