package com.tbtechs.focusflow.ui.settings

import org.junit.Assert.assertTrue
import org.junit.Test

class FocusFlowAgentGuideTest {
    @Test
    fun copiedGuideIncludesWebsiteAndLocalDocumentationDirections() {
        assertTrue(FOCUSFLOW_AGENT_GUIDE.contains(FOCUSFLOW_WEBSITE_URL))
        assertTrue(FOCUSFLOW_AGENT_GUIDE.contains("Docs option"))
        assertTrue(FOCUSFLOW_AGENT_GUIDE.contains("docs/"))
        assertTrue(FOCUSFLOW_AGENT_GUIDE.contains("[Add the specific change"))
    }
}
