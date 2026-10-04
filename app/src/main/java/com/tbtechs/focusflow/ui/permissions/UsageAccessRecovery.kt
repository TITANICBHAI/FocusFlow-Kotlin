package com.tbtechs.focusflow.ui.permissions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tbtechs.focusflow.data.repository.UsageStatsRepository
import com.tbtechs.focusflow.ui.home.FocusFlowModalCard
import com.tbtechs.focusflow.ui.home.FocusFlowPrimaryButton
import com.tbtechs.focusflow.ui.home.FocusFlowSecondaryButton
import com.tbtechs.focusflow.ui.home.RefSecondary
import com.tbtechs.focusflow.ui.home.RefText
import com.tbtechs.focusflow.ui.theme.scaledSp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun UsageAccessRecovery(usageAccessAttempted: Boolean) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var leftAfterAttempt by remember { mutableStateOf(false) }
    var returnedWithoutPermission by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }
    val currentDismissed by rememberUpdatedState(dismissed)

    DisposableEffect(owner, usageAccessAttempted) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> if (usageAccessAttempted) leftAfterAttempt = true
                Lifecycle.Event.ON_RESUME -> if (leftAfterAttempt) {
                    leftAfterAttempt = false
                    scope.launch {
                        val granted = withContext(Dispatchers.IO) {
                            UsageStatsRepository(context).hasPermission()
                        }
                        if (currentDismissed) return@launch
                        if (granted) completed = true else returnedWithoutPermission = true
                    }
                }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    if (!usageAccessAttempted || !returnedWithoutPermission || completed || dismissed) return

    fun dismiss() {
        dismissed = true
        returnedWithoutPermission = false
    }

    Dialog(
        onDismissRequest = ::dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        FocusFlowModalCard(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            radius = 16.dp,
            contentPadding = 16.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.ShowChart,
                        contentDescription = null,
                        tint = RefText,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        "Usage Access help",
                        modifier = Modifier.padding(start = 8.dp),
                        color = RefText,
                        fontSize = 18.scaledSp,
                    )
                }
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Dismiss",
                    tint = RefSecondary,
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(onClick = ::dismiss)
                        .padding(8.dp),
                )
            }
            Column(
                modifier = Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Usage Access is still off. In Android settings, find FocusFlow and enable “Permit usage access,” then return here.",
                    color = RefSecondary,
                    fontSize = 13.scaledSp,
                    lineHeight = 18.scaledSp,
                )
                FocusFlowPrimaryButton(
                    text = "Open Usage Access Settings",
                    icon = Icons.Outlined.OpenInNew,
                    onClick = {
                        scope.launch { UsageStatsRepository(context).openUsageAccessSettings() }
                    },
                )
                FocusFlowSecondaryButton(text = "Not now", onClick = ::dismiss)
            }
        }
    }
}
