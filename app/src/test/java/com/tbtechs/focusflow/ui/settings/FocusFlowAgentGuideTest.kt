package com.tbtechs.focusflow.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusFlowAgentGuideTest {
    @Test
    fun copiedPromptIsSelfContainedAndAccuratelyScopesTaskBackup() {
        assertFalse(FOCUSFLOW_TASK_BACKUP_PROMPT.contains(".md"))
        assertFalse(FOCUSFLOW_TASK_BACKUP_PROMPT.contains("docs/"))
        assertFalse(FOCUSFLOW_TASK_BACKUP_PROMPT.contains("https://"))
        assertFalse(FOCUSFLOW_TASK_BACKUP_PROMPT.contains("app/src/"))
        assertTrue(FOCUSFLOW_TASK_BACKUP_PROMPT.contains("self-contained"))
        assertTrue(FOCUSFLOW_TASK_BACKUP_PROMPT.contains("FocusFlowBackupV1"))
        assertTrue(FOCUSFLOW_TASK_BACKUP_PROMPT.contains("settings"))
        assertTrue(FOCUSFLOW_TASK_BACKUP_PROMPT.contains("tasks"))
        assertTrue(FOCUSFLOW_TASK_BACKUP_PROMPT.contains("createdAt"))
        assertTrue(FOCUSFLOW_TASK_BACKUP_PROMPT.contains("updatedAt"))
        assertTrue(FOCUSFLOW_TASK_BACKUP_PROMPT.contains("focusAllowedPackages"))
        assertTrue(FOCUSFLOW_TASK_BACKUP_PROMPT.contains("recurrence rule"))
        assertTrue(FOCUSFLOW_TASK_BACKUP_PROMPT.contains("UTC"))
        assertTrue(FOCUSFLOW_TASK_BACKUP_PROMPT.contains("settings` to `{}`"))
        assertTrue(FOCUSFLOW_TASK_BACKUP_PROMPT.contains("schedule.focusflow"))
        assertTrue(FOCUSFLOW_TASK_BACKUP_PROMPT.contains("[Describe the tasks"))
        assertTrue(FOCUSFLOW_TASK_BACKUP_PROMPT.contains("native Android focus and"))
        assertTrue(FOCUSFLOW_TASK_BACKUP_PROMPT.contains("app and network blocking"))
        assertTrue(FOCUSFLOW_TASK_BACKUP_PROMPT.contains("daily allowances"))
        assertTrue(FOCUSFLOW_TASK_BACKUP_PROMPT.contains("usage and progress reports"))
        assertTrue(FOCUSFLOW_TASK_BACKUP_PROMPT.contains("broader than a calendar"))
        assertTrue(FOCUSFLOW_TASK_BACKUP_PROMPT.contains("reminders` field to `[]`"))
        assertTrue(FOCUSFLOW_TASK_BACKUP_PROMPT.contains("time-zone identifier"))
        assertTrue(FOCUSFLOW_TASK_BACKUP_PROMPT.contains("daylight-saving transition"))
        assertTrue(FOCUSFLOW_TASK_BACKUP_PROMPT.contains("end time has passed by import time"))
        assertTrue(FOCUSFLOW_GENERATION_NOTE.contains(".focusflow"))
        assertTrue(FOCUSFLOW_GENERATION_NOTE.contains("task records"))
        assertFalse(FOCUSFLOW_GENERATION_NOTE.contains(".md"))
    }

    @Test
    fun readerFacingGuideAndCopiedPromptHaveDifferentContent() {
        assertTrue(FOCUSFLOW_FORMAT_GUIDE_INTRO.contains("plain UTF-8 JSON"))
        assertFalse(FOCUSFLOW_FORMAT_GUIDE_INTRO.contains("AI task-backup prompt"))
        assertFalse(FOCUSFLOW_TASK_BACKUP_PROMPT.contains("AI schedule generator"))
        assertTrue(FOCUSFLOW_TASK_BACKUP_PROMPT.contains("FocusFlow task-backup assistant"))
    }

    @Test
    fun focusAllowedPackagesGuidanceMatchesImportSemantics() {
        assertTrue(FOCUSFLOW_FOCUS_ALLOWED_PACKAGES_NOTE.contains("Missing or null"))
        assertTrue(FOCUSFLOW_FOCUS_ALLOWED_PACKAGES_NOTE.contains("empty array means"))
        assertTrue(FOCUSFLOW_FOCUS_ALLOWED_PACKAGES_NOTE.contains("all apps are allowed"))
        assertTrue(FOCUSFLOW_TASK_BACKUP_PROMPT.contains("at most 5,000 package IDs"))
        assertTrue(FOCUSFLOW_TASK_BACKUP_PROMPT.contains("at most 255 characters"))
        assertTrue(
            FOCUSFLOW_TASK_BACKUP_PROMPT.contains(
                """^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$""",
            ),
        )
    }
}
