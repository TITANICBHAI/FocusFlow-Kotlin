package com.tbtechs.focusflow.enforcement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplicitVpnPolicyTest {
    @Test
    fun t1ReproFocusMirrorTargetsDoNotBecomePersistentAfterFocusEnds() {
        val focusTargets = listOf("com.example.appB", "com.example.appC")
        val explicitPackages: List<String>? = null
        val derivedSnapshot = focusTargets
        val migrationComplete = true
        val policyGeneration = 1L

        val explicitCandidates = ExplicitVpnPolicy.selectExplicitCandidates(
            explicitKeyExists = false,
            explicitPackages = explicitPackages,
            migrationComplete = migrationComplete,
            derivedSnapshot = derivedSnapshot,
            policyGeneration = policyGeneration,
        )
        val duringFocus = ExplicitVpnPolicy.effectiveTargets(
            explicitCandidates = explicitCandidates,
            focusTargets = focusTargets,
        )
        assertEquals(focusTargets, duringFocus)
        assertFalse(ExplicitVpnPolicy.hasPersistentExplicitTargets(explicitCandidates))

        val afterFocus = ExplicitVpnPolicy.effectiveTargets(
            explicitCandidates = explicitCandidates,
            focusTargets = emptyList(),
        )
        assertEquals(emptyList<String>(), afterFocus)
        assertFalse(ExplicitVpnPolicy.hasPersistentExplicitTargets(explicitCandidates))
    }

    @Test
    fun t1MigrateCopiesLegacySnapshotOnceAtGenerationZero() {
        val migration = ExplicitVpnPolicy.migrationPlan(
            alreadyMigrated = false,
            explicitKeyExists = false,
            derivedSnapshot = listOf("com.example.legacy"),
            policyGeneration = 0L,
        )

        assertEquals(listOf("com.example.legacy"), migration?.explicitPackagesToWrite)
        assertTrue(migration?.markMigrated == true)
        assertEquals(
            listOf("com.example.legacy"),
            ExplicitVpnPolicy.selectExplicitCandidates(
                explicitKeyExists = false,
                explicitPackages = null,
                migrationComplete = false,
                derivedSnapshot = listOf("com.example.legacy"),
                policyGeneration = 0L,
            ),
        )
        assertEquals(
            null,
            ExplicitVpnPolicy.migrationPlan(
                alreadyMigrated = true,
                explicitKeyExists = false,
                derivedSnapshot = listOf("com.example.changed"),
                policyGeneration = 0L,
            ),
        )
    }

    @Test
    fun t1Migrate2IgnoresDerivedSnapshotAfterPositiveGeneration() {
        val migration = ExplicitVpnPolicy.migrationPlan(
            alreadyMigrated = false,
            explicitKeyExists = false,
            derivedSnapshot = listOf("com.example.derivedB", "com.example.derivedC"),
            policyGeneration = 1L,
        )

        assertEquals(emptyList<String>(), migration?.explicitPackagesToWrite)
        assertTrue(migration?.markMigrated == true)
        assertEquals(
            emptyList<String>(),
            ExplicitVpnPolicy.selectExplicitCandidates(
                explicitKeyExists = false,
                explicitPackages = null,
                migrationComplete = false,
                derivedSnapshot = listOf("com.example.derivedB", "com.example.derivedC"),
                policyGeneration = 1L,
            ),
        )
    }

    @Test
    fun migrationPreservesAnExistingExplicitList() {
        val explicitPackages = listOf("com.example.explicit")
        val migration = ExplicitVpnPolicy.migrationPlan(
            alreadyMigrated = false,
            explicitKeyExists = true,
            derivedSnapshot = listOf("com.example.derived"),
            policyGeneration = 1L,
        )

        assertEquals(null, migration?.explicitPackagesToWrite)
        assertTrue(migration?.markMigrated == true)
        assertEquals(
            explicitPackages,
            ExplicitVpnPolicy.selectExplicitCandidates(
                explicitKeyExists = true,
                explicitPackages = explicitPackages,
                migrationComplete = false,
                derivedSnapshot = listOf("com.example.derived"),
                policyGeneration = 0L,
            ),
        )
    }
}
