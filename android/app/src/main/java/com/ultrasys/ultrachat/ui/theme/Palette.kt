package com.ultrasys.ultrachat.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.ultrasys.ultrachat.data.AccentTone
import com.ultrasys.ultrachat.data.ThemeVariant

/**
 * Полная палитра приложения. Все экраны читают цвета отсюда, поэтому
 * смена темы и акцента применяется мгновенно и без перезапуска.
 */
data class UltraPalette(
    val isLight: Boolean,
    val background: Color,
    val backgroundDeep: Color,
    val panel: Color,
    val panelElevated: Color,
    val panelSoft: Color,
    val line: Color,
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val bubbleMine: Color,
    val bubbleOther: Color,
    val bubbleMineText: Color,
    val bubbleOtherText: Color,
    val bubbleMineTime: Color,
    val bubbleOtherTime: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val creatorGold: Color,
    val creatorAmber: Color,
    val danger: Color,
    val success: Color,
)

private data class SurfaceSet(
    val isLight: Boolean,
    val background: Color,
    val backgroundDeep: Color,
    val panel: Color,
    val panelElevated: Color,
    val panelSoft: Color,
    val line: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
)

private fun surfacesFor(variant: ThemeVariant): SurfaceSet = when (variant) {
    ThemeVariant.TELEGRAM -> SurfaceSet(
        isLight = false,
        background = Color(0xFF0E1621),
        backgroundDeep = Color(0xFF0A1118),
        panel = Color(0xFF17212B),
        panelElevated = Color(0xFF1C2733),
        panelSoft = Color(0xFF243240),
        line = Color(0xFF263441),
        textPrimary = Color(0xFFF4F7FA),
        textSecondary = Color(0xFF8A9AA8),
        textTertiary = Color(0xFF61707E),
    )

    ThemeVariant.MIDNIGHT -> SurfaceSet(
        isLight = false,
        background = Color(0xFF070B14),
        backgroundDeep = Color(0xFF04060C),
        panel = Color(0xFF0F1622),
        panelElevated = Color(0xFF151F2E),
        panelSoft = Color(0xFF1D2A3D),
        line = Color(0xFF223149),
        textPrimary = Color(0xFFEEF4FF),
        textSecondary = Color(0xFF7F91A8),
        textTertiary = Color(0xFF586A80),
    )

    ThemeVariant.GRAPHITE -> SurfaceSet(
        isLight = false,
        background = Color(0xFF121417),
        backgroundDeep = Color(0xFF0C0E10),
        panel = Color(0xFF1A1D21),
        panelElevated = Color(0xFF22262B),
        panelSoft = Color(0xFF2C3137),
        line = Color(0xFF343A41),
        textPrimary = Color(0xFFF2F4F6),
        textSecondary = Color(0xFF9AA1A9),
        textTertiary = Color(0xFF6E767F),
    )

    ThemeVariant.OCEAN -> SurfaceSet(
        isLight = false,
        background = Color(0xFF071A1E),
        backgroundDeep = Color(0xFF041012),
        panel = Color(0xFF0E252A),
        panelElevated = Color(0xFF143037),
        panelSoft = Color(0xFF1B3D45),
        line = Color(0xFF24505A),
        textPrimary = Color(0xFFEAFBFF),
        textSecondary = Color(0xFF7FB3BE),
        textTertiary = Color(0xFF5A8A94),
    )

    ThemeVariant.FOREST -> SurfaceSet(
        isLight = false,
        background = Color(0xFF0B1710),
        backgroundDeep = Color(0xFF07100B),
        panel = Color(0xFF12251A),
        panelElevated = Color(0xFF193020),
        panelSoft = Color(0xFF213D2A),
        line = Color(0xFF2B4E36),
        textPrimary = Color(0xFFF0FBF2),
        textSecondary = Color(0xFF8FB39A),
        textTertiary = Color(0xFF64806D),
    )

    ThemeVariant.AMETHYST -> SurfaceSet(
        isLight = false,
        background = Color(0xFF120E1C),
        backgroundDeep = Color(0xFF0B0813),
        panel = Color(0xFF1B1528),
        panelElevated = Color(0xFF241C34),
        panelSoft = Color(0xFF2E2442),
        line = Color(0xFF3B2F52),
        textPrimary = Color(0xFFF7F2FF),
        textSecondary = Color(0xFFA79ABF),
        textTertiary = Color(0xFF7C6E94),
    )

    ThemeVariant.LIGHT -> SurfaceSet(
        isLight = true,
        background = Color(0xFFF4F7FA),
        backgroundDeep = Color(0xFFE9EFF5),
        panel = Color(0xFFFFFFFF),
        panelElevated = Color(0xFFF0F4F8),
        panelSoft = Color(0xFFE2E9F0),
        line = Color(0xFFD6DEE7),
        textPrimary = Color(0xFF0F1A24),
        textSecondary = Color(0xFF5E6E7E),
        textTertiary = Color(0xFF8A98A6),
    )
}

fun paletteFor(variant: ThemeVariant, accentTone: AccentTone): UltraPalette {
    val surface = surfacesFor(variant)
    val accent = Color(accentTone.argb)
    val onAccent = if (accent.luminance() > 0.40f) Color(0xFF07131B) else Color.White
    val bubbleMine = if (surface.isLight) {
        accent
    } else {
        lerp(surface.panelElevated, accent, 0.42f)
    }
    val bubbleOther = if (surface.isLight) Color.White else surface.panelElevated

    return UltraPalette(
        isLight = surface.isLight,
        background = surface.background,
        backgroundDeep = surface.backgroundDeep,
        panel = surface.panel,
        panelElevated = surface.panelElevated,
        panelSoft = surface.panelSoft,
        line = surface.line,
        accent = accent,
        accentSoft = if (surface.isLight) {
            lerp(accent, Color.Black, 0.12f)
        } else {
            lerp(accent, Color.White, 0.22f)
        },
        onAccent = onAccent,
        bubbleMine = bubbleMine,
        bubbleOther = bubbleOther,
        bubbleMineText = if (surface.isLight) onAccent else Color.White,
        bubbleOtherText = surface.textPrimary,
        bubbleMineTime = if (surface.isLight) {
            onAccent.copy(alpha = 0.62f)
        } else {
            Color.White.copy(alpha = 0.62f)
        },
        bubbleOtherTime = surface.textTertiary,
        textPrimary = surface.textPrimary,
        textSecondary = surface.textSecondary,
        textTertiary = surface.textTertiary,
        creatorGold = if (surface.isLight) Color(0xFFA9791C) else Color(0xFFE8C56A),
        creatorAmber = if (surface.isLight) Color(0xFFB8860B) else Color(0xFFE0A94A),
        danger = if (surface.isLight) Color(0xFFC23A4A) else Color(0xFFE86A7A),
        success = if (surface.isLight) Color(0xFF2E8B57) else Color(0xFF6BCB8F),
    )
}

val LocalUltraPalette = staticCompositionLocalOf {
    paletteFor(ThemeVariant.TELEGRAM, AccentTone.BLUE)
}

object UltraTheme {
    val colors: UltraPalette
        @androidx.compose.runtime.Composable
        @androidx.compose.runtime.ReadOnlyComposable
        get() = LocalUltraPalette.current
}
