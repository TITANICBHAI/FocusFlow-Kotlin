package com.tbtechs.focusflow.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.data.model.AppSettings
import com.tbtechs.focusflow.ui.theme.BrandPrimary
import com.tbtechs.focusflow.ui.theme.DarkBorder
import com.tbtechs.focusflow.ui.theme.DarkTextPrimary
import com.tbtechs.focusflow.ui.theme.DarkTextSecondary
import kotlin.math.roundToInt

private const val MIN_TEXT_SCALE_PERCENT = 80
private const val MAX_TEXT_SCALE_PERCENT = 150
private const val TEXT_SCALE_SLIDER_STEPS = MAX_TEXT_SCALE_PERCENT - MIN_TEXT_SCALE_PERCENT - 1

@Composable
internal fun TextSizeSection(
    settings: AppSettings,
    onUpdate: (AppSettings) -> Unit,
) {
    val generalPercent = scaleToPercent(settings.generalTextScale)

    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsSliderRow(
            title = "General",
            description = "Default for Focus, Stats, Settings, and Defense. Home stays at 100%.",
            valuePercent = generalPercent,
            onValueChange = { percent ->
                onUpdate(settings.copy(generalTextScale = percentToScale(percent)))
            },
            onValueChangeFinished = {},
        )
        HorizontalDivider(color = DarkBorder, thickness = 1.dp)
        SettingsSliderRow(
            title = "Focus",
            description = if (settings.focusTextScale == null) "Matches General" else "Custom size",
            valuePercent = settings.focusTextScale?.let(::scaleToPercent) ?: generalPercent,
            onValueChange = { percent ->
                onUpdate(settings.copy(focusTextScale = percentToScale(percent)))
            },
            onValueChangeFinished = {},
            onReset = if (settings.focusTextScale == null) null else {
                { onUpdate(settings.copy(focusTextScale = null)) }
            },
        )
        HorizontalDivider(color = DarkBorder, thickness = 1.dp)
        SettingsSliderRow(
            title = "Stats",
            description = if (settings.statsTextScale == null) "Matches General" else "Custom size",
            valuePercent = settings.statsTextScale?.let(::scaleToPercent) ?: generalPercent,
            onValueChange = { percent ->
                onUpdate(settings.copy(statsTextScale = percentToScale(percent)))
            },
            onValueChangeFinished = {},
            onReset = if (settings.statsTextScale == null) null else {
                { onUpdate(settings.copy(statsTextScale = null)) }
            },
        )
        HorizontalDivider(color = DarkBorder, thickness = 1.dp)
        SettingsSliderRow(
            title = "Settings",
            description = if (settings.settingsTextScale == null) "Matches General" else "Custom size",
            valuePercent = settings.settingsTextScale?.let(::scaleToPercent) ?: generalPercent,
            onValueChange = { percent ->
                onUpdate(settings.copy(settingsTextScale = percentToScale(percent)))
            },
            onValueChangeFinished = {},
            onReset = if (settings.settingsTextScale == null) null else {
                { onUpdate(settings.copy(settingsTextScale = null)) }
            },
        )
        HorizontalDivider(color = DarkBorder, thickness = 1.dp)
        SettingsSliderRow(
            title = "Defense",
            description = if (settings.defenseTextScale == null) "Matches General" else "Custom size",
            valuePercent = settings.defenseTextScale?.let(::scaleToPercent) ?: generalPercent,
            onValueChange = { percent ->
                onUpdate(settings.copy(defenseTextScale = percentToScale(percent)))
            },
            onValueChangeFinished = {},
            onReset = if (settings.defenseTextScale == null) null else {
                { onUpdate(settings.copy(defenseTextScale = null)) }
            },
        )
    }
}

@Composable
private fun SettingsSliderRow(
    title: String,
    description: String,
    valuePercent: Int,
    onValueChange: (Int) -> Unit,
    onValueChangeFinished: () -> Unit,
    onReset: (() -> Unit)? = null,
) {
    var sliderValue by remember(valuePercent) { mutableStateOf(valuePercent) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkTextPrimary,
                )
                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = DarkTextSecondary,
                    lineHeight = 16.sp,
                )
            }
            if (onReset != null) {
                TextButton(
                    onClick = onReset,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text("Use General", fontSize = 12.sp)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Slider(
                value = sliderValue.toFloat(),
                onValueChange = { newValue ->
                    sliderValue = newValue.roundToInt().coerceIn(
                        MIN_TEXT_SCALE_PERCENT,
                        MAX_TEXT_SCALE_PERCENT,
                    )
                },
                onValueChangeFinished = {
                    if (sliderValue != valuePercent) onValueChange(sliderValue)
                    onValueChangeFinished()
                },
                valueRange = MIN_TEXT_SCALE_PERCENT.toFloat()..MAX_TEXT_SCALE_PERCENT.toFloat(),
                steps = TEXT_SCALE_SLIDER_STEPS,
                colors = SliderDefaults.colors(
                    thumbColor = BrandPrimary,
                    activeTrackColor = BrandPrimary,
                ),
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "$title text size" },
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$sliderValue%",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = BrandPrimary,
                textAlign = TextAlign.End,
                modifier = Modifier.width(42.dp),
            )
        }
    }
}

private fun scaleToPercent(scale: Float): Int =
    (scale * 100f).roundToInt().coerceIn(MIN_TEXT_SCALE_PERCENT, MAX_TEXT_SCALE_PERCENT)

private fun percentToScale(percent: Int): Float =
    percent.coerceIn(MIN_TEXT_SCALE_PERCENT, MAX_TEXT_SCALE_PERCENT) / 100f