package com.tbtechs.focusflow.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.scaledSp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FocusFlowFileGuideScreen(onBack: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    var copied by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "FocusFlow File Guide",
                        color = DarkTextPrimary,
                        fontSize = 18.scaledSp,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = DarkTextPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = DarkTextPrimary,
                ),
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "A project overview and copy-ready instructions for a coding agent. " +
                    "Copy this guide, then add the exact change you want.",
                color = DarkTextSecondary,
                fontSize = 14.scaledSp,
                lineHeight = 21.scaledSp,
            )

            Button(
                onClick = {
                    clipboard.setText(AnnotatedString(FOCUSFLOW_AGENT_GUIDE))
                    copied = true
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
            ) {
                Text(
                    if (copied) "Guide copied" else "Copy agent-ready guide",
                    color = Color.White,
                    fontSize = 14.scaledSp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Text(
                "Website and Docs",
                color = DarkTextPrimary,
                fontSize = 16.scaledSp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                FOCUSFLOW_WEBSITE_URL,
                modifier = Modifier.clickable {
                    uriHandler.openUri(FOCUSFLOW_WEBSITE_URL)
                },
                color = BrandPrimary,
                fontSize = 14.scaledSp,
            )
            Text(
                "The guide tells the agent to use the website’s Docs option and the relevant local files under docs/.",
                color = DarkTextSecondary,
                fontSize = 13.scaledSp,
                lineHeight = 19.scaledSp,
            )

            SelectionContainer {
                Text(
                    text = FOCUSFLOW_AGENT_GUIDE,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkCard)
                        .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    color = DarkTextSecondary,
                    fontSize = 13.scaledSp,
                    lineHeight = 19.scaledSp,
                )
            }
        }
    }
}
