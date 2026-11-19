package com.ultrasys.ultrachat.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.ultrasys.ultrachat.data.BubbleStyle
import com.ultrasys.ultrachat.data.ChatWallpaper

/**
 * Фон переписки. Обои рисуются поверх базового градиента, а затемнение
 * управляется ползунком из настроек.
 */
@Composable
fun WallpaperBackdrop(
    wallpaper: ChatWallpaper,
    dim: Float,
    modifier: Modifier = Modifier,
) {
    val palette = UltraTheme.colors
    val brush = when (wallpaper) {
        ChatWallpaper.DEFAULT -> Brush.verticalGradient(
            listOf(palette.background, palette.backgroundDeep),
        )
        ChatWallpaper.NIGHT -> Brush.verticalGradient(
            listOf(Color(0xFF0A1020), Color(0xFF04060D)),
        )
        ChatWallpaper.OCEAN -> Brush.verticalGradient(
            listOf(Color(0xFF0A2A31), Color(0xFF041216)),
        )
        ChatWallpaper.FOREST -> Brush.verticalGradient(
            listOf(Color(0xFF10251A), Color(0xFF07110B)),
        )
        ChatWallpaper.SAND -> Brush.verticalGradient(
            listOf(Color(0xFFE7D7BC), Color(0xFFD6C1A0)),
        )
        ChatWallpaper.PLAIN -> SolidColor(palette.background)
    }

    Box(modifier = modifier.background(brush)) {
        if (wallpaper != ChatWallpaper.PLAIN) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                when (wallpaper) {
                    ChatWallpaper.DEFAULT -> {
                        drawCircle(
                            color = palette.accent.copy(alpha = 0.055f),
                            radius = size.minDimension * 0.55f,
                            center = Offset(size.width * 0.08f, size.height * 0.16f),
                        )
                        drawCircle(
                            color = palette.accent.copy(alpha = 0.035f),
                            radius = size.minDimension * 0.46f,
                            center = Offset(size.width * 0.96f, size.height * 0.82f),
                        )
                    }

                    ChatWallpaper.NIGHT -> {
                        repeat(58) { index ->
                            val x = ((index * 37) % 101) / 100f * size.width
                            val y = ((index * 61) % 97) / 100f * size.height
                            val radius = if (index % 7 == 0) 1.5.dp.toPx() else 0.8.dp.toPx()
                            val alpha = if (index % 4 == 0) 0.30f else 0.14f
                            drawCircle(
                                color = Color.White.copy(alpha = alpha),
                                radius = radius,
                                center = Offset(x, y),
                            )
                        }
                    }

                    ChatWallpaper.OCEAN -> {
                        repeat(8) { index ->
                            val y = size.height * (index + 1) / 9f
                            drawLine(
                                color = palette.accentSoft.copy(alpha = 0.07f),
                                start = Offset(0f, y),
                                end = Offset(size.width, y + 10.dp.toPx()),
                                strokeWidth = 1.4.dp.toPx(),
                            )
                        }
                    }

                    ChatWallpaper.FOREST -> {
                        repeat(22) { index ->
                            val x = ((index * 29) % 100) / 100f * size.width
                            drawLine(
                                color = Color(0xFF9AD8A8).copy(alpha = 0.055f),
                                start = Offset(x, 0f),
                                end = Offset(x + 70.dp.toPx(), size.height),
                                strokeWidth = 1.2.dp.toPx(),
                            )
                        }
                    }

                    ChatWallpaper.SAND -> {
                        repeat(90) { index ->
                            val x = ((index * 43) % 101) / 100f * size.width
                            val y = ((index * 71) % 101) / 100f * size.height
                            drawCircle(
                                color = Color(0xFF6B4E2E).copy(alpha = 0.09f),
                                radius = if (index % 5 == 0) 1.4.dp.toPx() else 0.7.dp.toPx(),
                                center = Offset(x, y),
                            )
                        }
                    }

                    ChatWallpaper.PLAIN -> Unit
                }
            }
        }

        if (dim > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.background.copy(alpha = dim.coerceIn(0f, 0.85f))),
            )
        }
    }
}

fun bubbleShapeFor(style: BubbleStyle, mine: Boolean): RoundedCornerShape = when (style) {
    BubbleStyle.ROUNDED -> if (mine) {
        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    }

    BubbleStyle.SOFT -> RoundedCornerShape(22.dp)

    BubbleStyle.COMPACT -> if (mine) {
        RoundedCornerShape(10.dp, 10.dp, 3.dp, 10.dp)
    } else {
        RoundedCornerShape(10.dp, 10.dp, 10.dp, 3.dp)
    }
}
