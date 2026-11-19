package com.ultrasys.ultrachat.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// Имена сохранены для совместимости со всеми экранами, но теперь
// значения берутся из активной палитры, а не из констант.
val Void: Color
    @Composable @ReadOnlyComposable get() = LocalUltraPalette.current.background

val VoidDeep: Color
    @Composable @ReadOnlyComposable get() = LocalUltraPalette.current.backgroundDeep

val Panel: Color
    @Composable @ReadOnlyComposable get() = LocalUltraPalette.current.panel

val PanelElevated: Color
    @Composable @ReadOnlyComposable get() = LocalUltraPalette.current.panelElevated

val PanelSoft: Color
    @Composable @ReadOnlyComposable get() = LocalUltraPalette.current.panelSoft

val Line: Color
    @Composable @ReadOnlyComposable get() = LocalUltraPalette.current.line

val TelegramBlue: Color
    @Composable @ReadOnlyComposable get() = LocalUltraPalette.current.accent

val TelegramBlueSoft: Color
    @Composable @ReadOnlyComposable get() = LocalUltraPalette.current.accentSoft

val BubbleMine: Color
    @Composable @ReadOnlyComposable get() = LocalUltraPalette.current.bubbleMine

val BubbleOther: Color
    @Composable @ReadOnlyComposable get() = LocalUltraPalette.current.bubbleOther

val NeonCyan: Color
    @Composable @ReadOnlyComposable get() = LocalUltraPalette.current.accent

val NeonAqua: Color
    @Composable @ReadOnlyComposable get() = LocalUltraPalette.current.accentSoft

val NeonBlue: Color
    @Composable @ReadOnlyComposable get() = LocalUltraPalette.current.accent

val NeonViolet: Color
    @Composable @ReadOnlyComposable get() = LocalUltraPalette.current.textSecondary

val CreatorGold: Color
    @Composable @ReadOnlyComposable get() = LocalUltraPalette.current.creatorGold

val CreatorAmber: Color
    @Composable @ReadOnlyComposable get() = LocalUltraPalette.current.creatorAmber

val TextPrimary: Color
    @Composable @ReadOnlyComposable get() = LocalUltraPalette.current.textPrimary

val TextSecondary: Color
    @Composable @ReadOnlyComposable get() = LocalUltraPalette.current.textSecondary

val TextTertiary: Color
    @Composable @ReadOnlyComposable get() = LocalUltraPalette.current.textTertiary

val Danger: Color
    @Composable @ReadOnlyComposable get() = LocalUltraPalette.current.danger

val Success: Color
    @Composable @ReadOnlyComposable get() = LocalUltraPalette.current.success
