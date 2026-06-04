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

private val GreenPlumPrimary = Color(0xFFA8C98B)
private val GreenPlumAccent = Color(0xFF9DBC83)
private val GreenPlumBackground = Color(0xFFF8FBF1)
private val GreenPlumSurface = Color(0xFFFEFFF9)
private val PlumInk = Color(0xFF26351E)
private val PlumSage = Color(0xFF65735E)
private val PlumOutline = Color(0xFFDCE8D1)
private val WarmAmber = Color(0xFFC78A2C)
private val ErrorRed = Color(0xFFFF4949)

private val MediaTreeLightScheme = lightColorScheme(
    primary = GreenPlumPrimary,
    onPrimary = Color(0xFF1D3016),
    primaryContainer = Color(0xFFE8F2D9),
    onPrimaryContainer = Color(0xFF182B10),
    secondary = GreenPlumAccent,
    onSecondary = Color(0xFF1C2F16),
    secondaryContainer = Color(0xFFEAF3DF),
    onSecondaryContainer = Color(0xFF1B2B14),
    tertiary = WarmAmber,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE8C8),
    onTertiaryContainer = Color(0xFF2C1A00),
    background = GreenPlumBackground,
    onBackground = PlumInk,
    surface = GreenPlumSurface,
    onSurface = PlumInk,
    surfaceVariant = Color(0xFFEEF3E6),
    onSurfaceVariant = PlumSage,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFCF4),
    surfaceContainer = Color(0xFFF3F8EA),
    surfaceContainerHigh = Color(0xFFECF2E1),
    surfaceContainerHighest = Color(0xFFE2EBD7),
    outline = Color(0xFF95A58C),
    outlineVariant = PlumOutline,
    error = ErrorRed,
    onError = Color.White,
)

private val MediaTreeDarkScheme = darkColorScheme(
    primary = Color(0xFFBDD9A1),
    onPrimary = Color(0xFF223617),
    primaryContainer = Color(0xFF526D3C),
    onPrimaryContainer = Color(0xFFE8F2D9),
    secondary = Color(0xFFB7D39E),
    onSecondary = Color(0xFF243619),
    secondaryContainer = Color(0xFF4D6740),
    onSecondaryContainer = Color(0xFFEAF3DF),
    tertiary = Color(0xFFF0C881),
    onTertiary = Color(0xFF3F2700),
    tertiaryContainer = Color(0xFF654818),
    onTertiaryContainer = Color(0xFFFFE8C8),
    background = Color(0xFF10140D),
    onBackground = Color(0xFFE6ECD8),
    surface = Color(0xFF181D14),
    onSurface = Color(0xFFE6ECD8),
    surfaceVariant = Color(0xFF343B2D),
    onSurfaceVariant = Color(0xFFD0D8C5),
    surfaceContainerLowest = Color(0xFF0B0F09),
    surfaceContainerLow = Color(0xFF14190F),
    surfaceContainer = Color(0xFF1B2117),
    surfaceContainerHigh = Color(0xFF242B1E),
    surfaceContainerHighest = Color(0xFF2D3527),
    outline = Color(0xFF99A58E),
    outlineVariant = Color(0xFF454E3D),
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
