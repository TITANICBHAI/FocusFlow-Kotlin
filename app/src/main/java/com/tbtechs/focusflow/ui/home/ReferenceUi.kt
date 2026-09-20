package com.tbtechs.focusflow.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardActions
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tbtechs.focusflow.ui.theme.BrandPrimary

internal val RefBackground = Color(0xFF0F172A)
internal val RefHeader = Color(0xFF1E293B)
internal val RefCard = Color(0xFF202B3D)
internal val RefBorder = Color(0xFF334155)
internal val RefMuted = Color(0xFF64748B)
internal val RefSecondary = Color(0xFF94A3B8)
internal val RefText = Color(0xFFF1F5F9)
internal val RefGreen = Color(0xFF34D399)
internal val RefBlue = Color(0xFF3B82F6)
internal val RefAmber = Color(0xFFF59E0B)
internal val RefRed = Color(0xFFEF4444)

@Composable
internal fun ReferencePill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    selectedColor: Color = BrandPrimary,
    onClick: (() -> Unit)? = null,
) {
    val shape = CircleShape
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) selectedColor else RefCard)
            .border(
                width = if (selected) 0.dp else 1.dp,
                color = if (selected) Color.Transparent else RefBorder,
                shape = shape,
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = RefText,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun ReferenceSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = RefSecondary,
    )
}

@Composable
internal fun ReferenceField(
    value: String,
    placeholder: String = "",
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minHeight: androidx.compose.ui.unit.Dp? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            if (placeholder.isNotEmpty()) {
                Text(placeholder, color = RefMuted, fontSize = 18.sp)
            }
        },
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = modifier
            .fillMaxWidth()
            .then(if (minHeight != null) Modifier.heightIn(min = minHeight) else Modifier),
        shape = RoundedCornerShape(18.dp),
        textStyle = androidx.compose.material3.LocalTextStyle.current.copy(
            fontSize = 18.sp,
            color = RefText,
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = RefCard,
            unfocusedContainerColor = RefCard,
            disabledContainerColor = RefCard,
            focusedBorderColor = RefBorder,
            unfocusedBorderColor = RefBorder,
            cursorColor = BrandPrimary,
            focusedTextColor = RefText,
            unfocusedTextColor = RefText,
        ),
    )
}

@Composable
internal fun ReferenceToggleCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(RefCard)
            .border(1.dp, RefBorder, RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = RefText)
            Spacer(Modifier.size(4.dp))
            Text(description, fontSize = 16.sp, color = RefSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = BrandPrimary,
                uncheckedThumbColor = Color(0xFFCBD5E1),
                uncheckedTrackColor = Color(0xFF475569),
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
internal fun ReferenceTaskAction(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(80.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(29.dp),
            )
        }
        Spacer(Modifier.size(8.dp))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}