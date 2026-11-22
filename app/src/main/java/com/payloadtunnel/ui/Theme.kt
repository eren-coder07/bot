package com.payloadtunnel.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette from the design: olive-black background, lime accent.
val Lime = Color(0xFFD4F53C)
val LimeDim = Color(0xFF8FB82A)
val LimeDeep = Color(0xFF4F6B12)
val OnLime = Color(0xFF141A08)
val BgTop = Color(0xFF1C2512)
val BgBottom = Color(0xFF090C07)
val Card = Color(0xFF151B10)
val CardHigh = Color(0xFF1C2416)
val CardBorder = Color(0xFF2A3420)
val TextPrimary = Color(0xFFF2F5EC)
val TextSecondary = Color(0xFF9AA38E)
val Signal = Color(0xFF7DBA3A)

val Connected = Color(0xFF8FD14F)
val Working = Color(0xFFF5C842)
val Failed = Color(0xFFFF6B5E)

private val Colors = darkColorScheme(
    primary = Lime,
    onPrimary = OnLime,
    primaryContainer = LimeDeep,
    onPrimaryContainer = Lime,
    secondary = LimeDim,
    onSecondary = OnLime,
    background = BgBottom,
    onBackground = TextPrimary,
    surface = Card,
    onSurface = TextPrimary,
    surfaceVariant = CardHigh,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = Card,
    surfaceContainerHigh = CardHigh,
    surfaceContainerHighest = CardHigh,
    surfaceContainerLow = Card,
    surfaceContainerLowest = BgBottom,
    outline = CardBorder,
    outlineVariant = CardBorder,
    error = Failed,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Typography(), content = content)
}
