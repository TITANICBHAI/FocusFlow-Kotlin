package com.tbtechs.focusflow.ui.backup

import com.tbtechs.focusflow.data.repository.ImportedProtectionCategory

internal data class ImportProtectionCategoryFact(
    val id: String,
    val title: String,
    val wasImported: Boolean,
    val itemCount: Int,
    val featureEnabled: Boolean,
    val requiredPermissionAvailable: Boolean,
    val activeDetails: String,
    val inactiveDetails: String,
)

internal object ImportProtectionSummaryPolicy {
    fun build(facts: List<ImportProtectionCategoryFact>): List<ImportedProtectionCategory> =
        facts.filter(ImportProtectionCategoryFact::wasImported).map { fact ->
            val active = fact.itemCount > 0 &&
                fact.featureEnabled &&
                fact.requiredPermissionAvailable
            ImportedProtectionCategory(
                id = fact.id,
                title = fact.title,
                active = active,
                details = if (active) fact.activeDetails else fact.inactiveDetails,
            )
        }
}
