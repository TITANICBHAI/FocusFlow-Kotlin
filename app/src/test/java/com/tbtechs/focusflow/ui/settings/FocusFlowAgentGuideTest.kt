package com.tbtechs.focusflow.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusFlowAgentGuideTest {
    @Test
    fun copiedPromptIsSelfContainedAndIncludesTheFormatEssentials() {
        assertFalse(FOCUSFLOW_CODING_AGENT_PROMPT.contains(".md"))
        assertFalse(FOCUSFLOW_CODING_AGENT_PROMPT.contains("docs/"))
        assertFalse(FOCUSFLOW_CODING_AGENT_PROMPT.contains("https://"))
        assertTrue(FOCUSFLOW_CODING_AGENT_PROMPT.contains("self-contained"))
        assertTrue(FOCUSFLOW_CODING_AGENT_PROMPT.contains("FocusFlowBackupV1"))
        assertTrue(FOCUSFLOW_CODING_AGENT_PROMPT.contains("settings"))
        assertTrue(FOCUSFLOW_CODING_AGENT_PROMPT.contains("tasks"))
        assertTrue(FOCUSFLOW_CODING_AGENT_PROMPT.contains("focusAllowedPackages"))
        assertTrue(FOCUSFLOW_CODING_AGENT_PROMPT.contains("Merge"))
        assertTrue(FOCUSFLOW_CODING_AGENT_PROMPT.contains("Replace"))
        assertTrue(FOCUSFLOW_CODING_AGENT_PROMPT.contains("[Add the specific change"))
    }

    @Test
    fun readerFacingGuideAndCopiedPromptHaveDifferentContent() {
        assertTrue(FOCUSFLOW_FORMAT_GUIDE_INTRO.contains("plain UTF-8 JSON"))
        assertFalse(FOCUSFLOW_FORMAT_GUIDE_INTRO.contains("AI coding agent"))
        assertTrue(FOCUSFLOW_CODING_AGENT_PROMPT.contains("AI coding agent"))
    }

    @Test
    fun focusAllowedPackagesMatchesTheV14Contract() {
        assertTrue(FOCUSFLOW_FOCUS_ALLOWED_PACKAGES_NOTE.contains("Missing or null"))
        assertTrue(FOCUSFLOW_FOCUS_ALLOWED_PACKAGES_NOTE.contains("empty array means"))
        assertTrue(FOCUSFLOW_FOCUS_ALLOWED_PACKAGES_NOTE.contains("all apps are allowed"))
    }
}
