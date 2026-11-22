package com.payloadtunnel.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.payloadtunnel.R

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

private val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val poppinsFont = GoogleFont("Poppins")

val Poppins = FontFamily(
    Font(googleFont = poppinsFont, fontProvider = fontProvider, weight = FontWeight.Light),
    Font(googleFont = poppinsFont, fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = poppinsFont, fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = poppinsFont, fontProvider = fontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = poppinsFont, fontProvider = fontProvider, weight = FontWeight.Bold),
    Font(googleFont = poppinsFont, fontProvider = fontProvider, weight = FontWeight.ExtraBold),
)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 57.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 45.sp),
    displaySmall = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 36.sp),
    headlineLarge = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 32.sp),
    headlineMedium = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 28.sp),
    headlineSmall = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
    titleLarge = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 16.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Normal, fontSize = 16.sp, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Normal, fontSize = 14.sp, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Normal, fontSize = 12.sp, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.5.sp),
)

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
    MaterialTheme(colorScheme = Colors, typography = AppTypography, content = content)
}
