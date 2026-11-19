package com.ultrasys.ultrachat.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ultrasys.ultrachat.data.AppSettings
import com.ultrasys.ultrachat.data.ThemeVariant

private val UltraTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 36.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.8).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 21.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.5.sp,
        lineHeight = 21.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 19.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.1.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.2.sp,
    ),
)

private val UltraShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(22.dp),
)

@Composable
fun UltraChatTheme(
    settings: AppSettings,
    content: @Composable () -> Unit,
) {
    val palette = remember(settings.theme, settings.accent) {
        paletteFor(settings.theme, settings.accent)
    }
    val colorScheme = remember(palette) {
        if (palette.isLight) {
            lightColorScheme(
                primary = palette.accent,
                onPrimary = palette.onAccent,
                primaryContainer = lerp(palette.panelElevated, palette.accent, 0.18f),
                onPrimaryContainer = palette.textPrimary,
                secondary = palette.accentSoft,
                onSecondary = palette.onAccent,
                secondaryContainer = palette.panelSoft,
                onSecondaryContainer = palette.textPrimary,
                tertiary = palette.creatorGold,
                onTertiary = Color(0xFF2A1A02),
                background = palette.background,
                onBackground = palette.textPrimary,
                surface = palette.panel,
                onSurface = palette.textPrimary,
                surfaceVariant = palette.panelElevated,
                onSurfaceVariant = palette.textSecondary,
                error = palette.danger,
                onError = Color.White,
                outline = palette.line,
                outlineVariant = palette.line.copy(alpha = 0.55f),
            )
        } else {
            darkColorScheme(
                primary = palette.accent,
                onPrimary = palette.onAccent,
                primaryContainer = lerp(palette.panelElevated, palette.accent, 0.24f),
                onPrimaryContainer = palette.textPrimary,
                secondary = palette.accentSoft,
                onSecondary = Color(0xFF06202B),
                secondaryContainer = palette.panelSoft,
                onSecondaryContainer = palette.textPrimary,
                tertiary = palette.creatorGold,
                onTertiary = Color(0xFF2A1A02),
                background = palette.background,
                onBackground = palette.textPrimary,
                surface = palette.panel,
                onSurface = palette.textPrimary,
                surfaceVariant = palette.panelElevated,
                onSurfaceVariant = palette.textSecondary,
                error = palette.danger,
                onError = Color(0xFF2C0710),
                outline = palette.line,
                outlineVariant = palette.line.copy(alpha = 0.55f),
            )
        }
    }
    val density = LocalDensity.current
    val scaledDensity = remember(density, settings.fontSize.scale) {
        Density(density.density, density.fontScale * settings.fontSize.scale)
    }

    CompositionLocalProvider(
        LocalUltraPalette provides palette,
        LocalDensity provides scaledDensity,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = UltraTypography,
            shapes = UltraShapes,
            content = content,
        )
    }
}
