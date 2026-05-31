package com.zasenjc.mediatree.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Pantone2905 = Color(0xFF2F7DF6)
private val Pantone283 = Color(0xFF69A8FF)
private val MistBackground = Color(0xFFF4F9FF)
private val MistSurface = Color(0xFFFAFDFF)
private val CoolGray90 = Color(0xFF14234A)
private val CoolGray70 = Color(0xFF465A7F)
private val CoolGray20 = Color(0xFFDDE7F6)
private val SuccessGreen = Color(0xFF5AB963)
private val WarningOrange = Color(0xFFFF9F1A)
private val ErrorRed = Color(0xFFFF4949)

private val MediaTreeLightScheme = lightColorScheme(
    primary = Pantone2905,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E9FF),
    onPrimaryContainer = Color(0xFF001A42),
    secondary = Pantone283,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8F3FF),
    onSecondaryContainer = Color(0xFF06254D),
    tertiary = WarningOrange,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE6B8),
    onTertiaryContainer = Color(0xFF3E2500),
    background = MistBackground,
    onBackground = CoolGray90,
    surface = MistSurface,
    onSurface = CoolGray90,
    surfaceVariant = Color(0xFFEAF2FC),
    onSurfaceVariant = CoolGray70,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8FBFF),
    surfaceContainer = Color(0xFFF0F6FF),
    surfaceContainerHigh = Color(0xFFE8F2FF),
    surfaceContainerHighest = Color(0xFFDDEBFF),
    outline = Color(0xFF8FA0BB),
    outlineVariant = CoolGray20,
    error = ErrorRed,
    onError = Color.White,
)

private val MediaTreeDarkScheme = darkColorScheme(
    primary = Color(0xFF66AAFF),
    onPrimary = Color(0xFF001B3F),
    primaryContainer = Color(0xFF163B73),
    onPrimaryContainer = Color(0xFFDCEBFF),
    secondary = Color(0xFF8CC0FF),
    onSecondary = Color(0xFF072348),
    secondaryContainer = Color(0xFF223E66),
    onSecondaryContainer = Color(0xFFE6F1FF),
    tertiary = Color(0xFFFFC46B),
    onTertiary = Color(0xFF3F2700),
    tertiaryContainer = Color(0xFF60450E),
    onTertiaryContainer = Color(0xFFFFE6BB),
    background = Color(0xFF060A12),
    onBackground = Color(0xFFE6ECF6),
    surface = Color(0xFF101825),
    onSurface = Color(0xFFE6ECF6),
    surfaceVariant = Color(0xFF27364A),
    onSurfaceVariant = Color(0xFFC4D0E2),
    surfaceContainerLowest = Color(0xFF050810),
    surfaceContainerLow = Color(0xFF0C1420),
    surfaceContainer = Color(0xFF121D2C),
    surfaceContainerHigh = Color(0xFF1A2738),
    surfaceContainerHighest = Color(0xFF223247),
    outline = Color(0xFF7E91AD),
    outlineVariant = Color(0xFF33445C),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val MediaTreeTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 17.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
    ),
)

private val MediaTreeShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

@Composable
fun MediaTreeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) MediaTreeDarkScheme else MediaTreeLightScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MediaTreeTypography,
        shapes = MediaTreeShapes,
        content = content,
    )
}
