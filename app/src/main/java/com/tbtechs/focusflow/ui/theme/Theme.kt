package com.tbtechs.focusflow.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

// FocusFlow Exact Brand Palette matching React reference
val BrandPrimary = Color(0xFF6366F1)       // Indigo 500
val BrandPrimaryHover = Color(0xFF4F46E5)  // Indigo 600
val BrandPrimaryLight = Color(0xFFE0E7FF)  // Indigo 100
val BrandPrimaryDark = Color(0xFF4338CA)   // Indigo 700

// Dark Slate Theme (Reference Dark Background & Surfaces)
val DarkBackground = Color(0xFF0D1322)     // Deep slate navy screen background
val DarkCard = Color(0xFF151C2E)           // Dark slate card surface
val DarkSurfaceVariant = Color(0xFF1C253B) // Secondary card / active hover
val DarkBorder = Color(0xFF263249)         // Subtle slate border

val DarkTextPrimary = Color(0xFFF8FAFC)    // Clean white text
val DarkTextSecondary = Color(0xFF94A3B8)  // Medium slate text
val DarkTextMuted = Color(0xFF64748B)      // Subdued slate text

// Semantic Status Colors
val StatusReady = Color(0xFF10B981)
val StatusReadyBg = Color(0xFF064E3B)
val StatusReadyText = Color(0xFF34D399)

val StatusNotSetUp = Color(0xFFF59E0B)
val StatusNotSetUpBg = Color(0xFF451A03)
val StatusNotSetUpText = Color(0xFFFBBF24)

val StatusMissing = Color(0xFFEF4444)
val StatusMissingBg = Color(0xFF450A0A)
val StatusMissingText = Color(0xFFF87171)

val StatusOptional = Color(0xFF8B5CF6)
val StatusOptionalBg = Color(0xFF2E1065)
val StatusOptionalText = Color(0xFFC084FC)

// Info Banner Lavender Colors
val LavenderBg = Color(0xFFEEF2FF)
val LavenderText = Color(0xFF312E81)
val LavenderBorder = Color(0xFFC7D2FE)

val FocusFlowDarkColorScheme = darkColorScheme(
    primary = BrandPrimary,
    onPrimary = Color.White,
    primaryContainer = BrandPrimaryHover,
    onPrimaryContainer = Color.White,
    secondary = BrandPrimaryLight,
    onSecondary = DarkBackground,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkCard,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    outlineVariant = DarkBorder,
    error = StatusMissing,
    onError = Color.White,
    errorContainer = StatusMissingBg,
    onErrorContainer = StatusMissingText,
)

val FocusFlowLightColorScheme = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = Color.White,
    primaryContainer = BrandPrimaryLight,
    onPrimaryContainer = BrandPrimaryDark,
    secondary = BrandPrimaryLight,
    onSecondary = Color(0xFF1E1B4B),
    background = Color(0xFFF0F2FF),
    onBackground = Color(0xFF1E1B4B),
    surface = Color.White,
    onSurface = Color(0xFF1E1B4B),
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFF6B7280),
    outline = Color(0xFFE5E7EB),
    outlineVariant = Color(0xFFE5E7EB),
    error = Color(0xFFEF4444),
    onError = Color.White,
)

@Composable
fun FocusFlowTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) FocusFlowDarkColorScheme else FocusFlowLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
