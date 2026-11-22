package com.payloadtunnel.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Connected = Color(0xFF2ECC71)
val Working = Color(0xFFF5B041)
val Failed = Color(0xFFE74C3C)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5DADE2),
    onPrimary = Color(0xFF00243A),
    primaryContainer = Color(0xFF0B3B5A),
    onPrimaryContainer = Color(0xFFCDE5FF),
    secondary = Color(0xFF8FD5C3),
    background = Color(0xFF0F1216),
    surface = Color(0xFF0F1216),
    surfaceVariant = Color(0xFF1E242B),
    surfaceContainer = Color(0xFF171C21),
    surfaceContainerHigh = Color(0xFF1E242B),
    error = Color(0xFFFF6B6B),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
