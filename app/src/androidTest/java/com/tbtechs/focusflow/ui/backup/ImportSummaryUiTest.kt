package com.tbtechs.focusflow.ui.backup

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import com.tbtechs.focusflow.data.repository.ImportSummary
import org.junit.Rule
import org.junit.Test

class ImportSummaryUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun successSummaryShowsAppliedSettingsAndTaskOutcomes() {
        composeRule.setContent {
            MaterialTheme {
                ImportSummaryDetails(
                    summary = ImportSummary(
                        settings = true,
                        tasksImported = 2,
                        tasksSkipped = 3,
                        tasksSkippedExisting = 1,
                        invalidTasksSkipped = 2,
                        tasksMarkedSkipped = 1,
                        settingsFieldsApplied = 3,
                    ),
                    restoreTasks = true,
                )
            }
        }

        composeRule.onNodeWithText("2 tasks added; existing tasks were kept.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("3 portable settings fields applied.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("1 existing task ID(s) were kept.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("2 invalid task record(s) were skipped.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("1 past task(s) were added with status Skipped.")
            .assertIsDisplayed()
    }
}
