package com.tbtechs.focusflow.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskTagsTest {
    @Test
    fun addTaskTagsFromDraft_trimsTagsAndSplitsCommaSeparatedValues() {
        val result = addTaskTagsFromDraft(
            tags = emptyList(),
            draft = "  #work, study \n #personal ",
        )

        assertEquals(listOf("work", "study", "personal"), result)
    }

    @Test
    fun addTaskTagsFromDraft_ignoresBlankAndCaseInsensitiveDuplicates() {
        val result = addTaskTagsFromDraft(
            tags = listOf("Work", "home"),
            draft = " work, , HOME, reading ",
        )

        assertEquals(listOf("Work", "home", "reading"), result)
    }

    @Test
    fun addTaskTagsFromDraft_preservesExistingTagsWhenDraftIsBlank() {
        val existing = listOf("work", "personal")

        assertEquals(existing, addTaskTagsFromDraft(existing, " , \n "))
    }
}
