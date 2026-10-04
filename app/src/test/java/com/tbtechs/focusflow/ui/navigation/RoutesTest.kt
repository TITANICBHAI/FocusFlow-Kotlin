package com.tbtechs.focusflow.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RoutesTest {
    @Test
    fun internalImportAndStatefulRoutesAreRejectedFromExternalPaths() {
        assertEquals(Routes.NOT_FOUND, Routes.fromPath("import_confirm"))
        assertEquals(Routes.NOT_FOUND, Routes.fromPath("import-confirm"))
        assertEquals(Routes.NOT_FOUND, Routes.fromPath("onboarding"))
        assertEquals(Routes.NOT_FOUND, Routes.fromPath("active"))
        assertEquals(Routes.NOT_FOUND, Routes.fromPath("guarded_adjustments"))
        assertEquals(Routes.NOT_FOUND, Routes.fromPath(Routes.TEXT_SIZE_SETTINGS))
        assertEquals(Routes.NOT_FOUND, Routes.fromPath(Routes.SETTINGS_GUIDE))
        assertFalse(Routes.IMPORT_CONFIRM in Routes.externalLinkableRoutes)
        assertFalse(Routes.TEXT_SIZE_SETTINGS in Routes.externalLinkableRoutes)
        assertFalse(Routes.SETTINGS_GUIDE in Routes.externalLinkableRoutes)
    }

    @Test
    fun knownPublicDeepLinksStillResolve() {
        assertEquals(Routes.PRIVACY_POLICY, Routes.fromPath("/privacy-policy?source=test"))
        assertEquals(Routes.ALWAYS_ON, Routes.fromPath("always-on?package=com.example.app"))
        assertEquals(Routes.ALWAYS_ON, Routes.fromPath("always-on?sourceTab=stats"))
        assertEquals(Routes.FOCUS, Routes.fromPath("focus"))
    }
}