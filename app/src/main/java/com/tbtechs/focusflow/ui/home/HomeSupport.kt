package com.tbtechs.focusflow.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tbtechs.focusflow.ui.theme.scaledSp
import androidx.compose.ui.unit.Dp
import com.tbtechs.focusflow.data.model.Task
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBackground
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkCard
import com.tbtechs.focusflow.ui.theme.DarkSurfaceVariant
import com.tbtechs.focusflow.ui.theme.DarkTextMuted
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import com.tbtechs.focusflow.ui.theme.LocalFocusFlowDimensions

/**
 * Screenshot 6c: ActiveTaskBanner for running or awaiting-decision tasks
 */
@Composable
internal fun ActiveTaskBanner(
    task: Task,
    onOpen: () -> Unit,
    onComplete: () -> Unit,
    onExtend: () -> Unit,
    onSkip: () -> Unit,
    onStartFocus: () -> Unit,
) {
    val dimensions = LocalFocusFlowDimensions.current
    val isRunning = task.isRunningNow()
    val bannerShape = RoundedCornerShape(22.dp)
    val bannerColor = if (isRunning) BrandPrimary else RefAmber
    val whiteOverlay = Color.White.copy(alpha = 0.18f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimensions.screenPadding, vertical = 8.dp)
            .clip(bannerShape)
            .background(bannerColor)
            .border(1.dp, Color.White.copy(alpha = 0.24f), bannerShape)
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(whiteOverlay)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        if (isRunning) "NOW" else "TIME'S UP",
                        fontSize = 10.scaledSp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 0.8.scaledSp,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        task.title,
                        modifier = Modifier.weight(1f),
                        fontSize = 15.scaledSp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                Text(
                    if (isRunning) "Until ${task.endTime.asLocalTime()}"
                    else "Ended ${task.endTime.asLocalTime()} · pick one",
                    fontSize = 12.scaledSp,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(
                    onClick = onComplete,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(whiteOverlay),
                ) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = "Complete",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(
                    onClick = onExtend,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(whiteOverlay),
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = "Extend",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
                if (!isRunning) {
                    IconButton(
                        onClick = onSkip,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(whiteOverlay),
                    ) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Skip",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                } else if (task.focusMode) {
                    IconButton(
                        onClick = onStartFocus,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(whiteOverlay),
                    ) {
                        Icon(
                            Icons.Outlined.Shield,
                            contentDescription = "Start focus",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TaskTagsEditor(
    tags: List<String>,
    onTagsChange: (List<String>) -> Unit,
) {
    var draft by remember(tags) { mutableStateOf("") }

    fun addDraftTags() {
        val candidates = draft
            .split(',', '\n')
            .map { it.trim().removePrefix("#").trim() }
            .filter(String::isNotBlank)
        val updated = tags.toMutableList()
        candidates.forEach { candidate ->
            if (updated.none { it.equals(candidate, ignoreCase = true) }) {
                updated += candidate
            }
        }
        onTagsChange(updated)
        draft = ""
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ReferenceSectionLabel("Tags")
        if (tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tags.forEach { tag ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(RefCard)
                            .border(1.dp, RefBorder, RoundedCornerShape(8.dp))
                            .clickable(
                                onClickLabel = "Remove tag $tag",
                                onClick = { onTagsChange(tags.filterNot { it == tag }) },
                            )
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("#$tag", fontSize = 12.scaledSp, color = RefSecondary)
                            Spacer(Modifier.width(4.dp))
                            Text("×", fontSize = 14.scaledSp, color = RefMuted)
                        }
                    }
                }
            }
        }
        HomeTextField(
            value = draft,
            onValueChange = { draft = it },
            label = "Add a tag",
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { addDraftTags() }),
        )
        Text(
            "Press return to add. Separate multiple tags with commas.",
            fontSize = 12.scaledSp,
            color = RefSecondary,
        )
    }
}

@Composable
internal fun HomeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    singleLine: Boolean = true,
    multilineMinHeight: Dp = 160.dp,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    ReferenceField(
        value = value,
        onValueChange = onValueChange,
        placeholder = label,
        singleLine = singleLine,
        minHeight = if (singleLine) null else multilineMinHeight,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
    )
}

@Composable
internal fun ExtendTaskDialog(task: Task, onDismiss: () -> Unit, onExtend: (Int) -> Unit) {
    var minutes by remember { mutableStateOf("15") }
    val presetOptions = listOf(10, 15, 25, 30, 45, 60)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = DarkCard,
        titleContentColor = DarkTextPrimary,
        textContentColor = DarkTextSecondary,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Alarm, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Extend ${task.title}", fontWeight = FontWeight.Bold, fontSize = 17.scaledSp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Select or enter extra minutes for this task.",
                    fontSize = 13.scaledSp,
                    color = DarkTextSecondary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    presetOptions.take(4).forEach { opt ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(MaterialTheme.shapes.small)
                                .background(if (minutes == opt.toString()) BrandPrimary.copy(alpha = 0.2f) else DarkSurfaceVariant)
                                .border(1.dp, if (minutes == opt.toString()) BrandPrimary else DarkBorder, MaterialTheme.shapes.small)
                                .clickable { minutes = opt.toString() }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "+${opt}m",
                                fontSize = 13.scaledSp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (minutes == opt.toString()) BrandPrimary else DarkTextPrimary,
                            )
                        }
                    }
                }
                HomeTextField(value = minutes, onValueChange = { minutes = it }, label = "Custom Minutes")
            }
        },
        confirmButton = {
            Button(
                onClick = { minutes.toIntOrNull()?.takeIf { it > 0 }?.let(onExtend) },
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.defaultMinSize(minHeight = 44.dp),
            ) {
                Text("Extend")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.defaultMinSize(minHeight = 44.dp),
            ) {
                Text("Cancel", color = DarkTextSecondary)
            }
        },
    )
}
