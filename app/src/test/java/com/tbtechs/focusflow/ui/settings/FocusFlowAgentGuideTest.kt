package com.tbtechs.focusflow.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusFlowAgentGuideTest {
    @Test
    fun copiedPromptGeneratesACompleteScheduleBackupWithoutExternalReferences() {
        assertFalse(FOCUSFLOW_SCHEDULE_GENERATOR_PROMPT.contains(".md"))
        assertFalse(FOCUSFLOW_SCHEDULE_GENERATOR_PROMPT.contains("docs/"))
        assertFalse(FOCUSFLOW_SCHEDULE_GENERATOR_PROMPT.contains("https://"))
        assertFalse(FOCUSFLOW_SCHEDULE_GENERATOR_PROMPT.contains("app/src/"))
        assertTrue(FOCUSFLOW_SCHEDULE_GENERATOR_PROMPT.contains("self-contained"))
        assertTrue(FOCUSFLOW_SCHEDULE_GENERATOR_PROMPT.contains("FocusFlowBackupV1"))
        assertTrue(FOCUSFLOW_SCHEDULE_GENERATOR_PROMPT.contains("settings"))
        assertTrue(FOCUSFLOW_SCHEDULE_GENERATOR_PROMPT.contains("tasks"))
        assertTrue(FOCUSFLOW_SCHEDULE_GENERATOR_PROMPT.contains("createdAt"))
        assertTrue(FOCUSFLOW_SCHEDULE_GENERATOR_PROMPT.contains("updatedAt"))
        assertTrue(FOCUSFLOW_SCHEDULE_GENERATOR_PROMPT.contains("focusAllowedPackages"))
        assertTrue(FOCUSFLOW_SCHEDULE_GENERATOR_PROMPT.contains("recurrence rule"))
        assertTrue(FOCUSFLOW_SCHEDULE_GENERATOR_PROMPT.contains("UTC"))
        assertTrue(FOCUSFLOW_SCHEDULE_GENERATOR_PROMPT.contains("always set `settings` to `{}`"))
        assertTrue(FOCUSFLOW_SCHEDULE_GENERATOR_PROMPT.contains("schedule.focusflow"))
        assertTrue(FOCUSFLOW_SCHEDULE_GENERATOR_PROMPT.contains("[Describe the routine"))
        assertTrue(FOCUSFLOW_GENERATION_NOTE.contains(".focusflow"))
        assertFalse(FOCUSFLOW_GENERATION_NOTE.contains(".md"))
    }

    @Test
    fun readerFacingGuideAndCopiedPromptHaveDifferentContent() {
        assertTrue(FOCUSFLOW_FORMAT_GUIDE_INTRO.contains("plain UTF-8 JSON"))
        assertFalse(FOCUSFLOW_FORMAT_GUIDE_INTRO.contains("AI schedule generator"))
        assertTrue(FOCUSFLOW_SCHEDULE_GENERATOR_PROMPT.contains("AI schedule generator"))
    }

    @Test
    fun focusAllowedPackagesGuidanceMatchesImportSemantics() {
        assertTrue(FOCUSFLOW_FOCUS_ALLOWED_PACKAGES_NOTE.contains("Missing or null"))
        assertTrue(FOCUSFLOW_FOCUS_ALLOWED_PACKAGES_NOTE.contains("empty array means"))
        assertTrue(FOCUSFLOW_FOCUS_ALLOWED_PACKAGES_NOTE.contains("all apps are allowed"))
    }
}
