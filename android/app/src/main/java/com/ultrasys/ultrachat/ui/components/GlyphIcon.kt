package com.ultrasys.ultrachat.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ultrasys.ultrachat.ui.theme.TextPrimary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class Glyph {
    BLUETOOTH,
    SEND,
    BACK,
    CROWN,
    LOCK,
    USER,
    REFRESH,
    CLOSE,
    RADAR,
    SEARCH,
    SHIELD,
    BOLT,
    CHECK,
    CHECKS,
    CHEVRON_RIGHT,
    SIGNAL,
    POWER,
    CHAT,
    SETTINGS,
    PALETTE,
    TEXT,
    WALLPAPER,
    TRASH,
    INFO,
    SERVER,
    MOON,
    SUN,
    VIBRATE,
    CLOCK,
    DATABASE,
    PLUS,
}

/**
 * Небольшой набор векторных глифов на Canvas — без внешних icon-библиотек,
 * чтобы APK оставался лёгким, а стиль — единым.
 */
@Composable
fun GlyphIcon(
    glyph: Glyph,
    modifier: Modifier = Modifier,
    tint: Color = TextPrimary,
    strokeWidth: Dp = 1.9.dp,
) {
    Canvas(modifier = modifier) {
        val unit = size.minDimension / 24f
        val stroke = Stroke(
            width = strokeWidth.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        fun p(x: Float, y: Float) = Offset(x * unit, y * unit)
        fun arc(cx: Float, cy: Float, radius: Float, start: Float, sweep: Float) {
            drawArc(
                color = tint,
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = p(cx - radius, cy - radius),
                size = Size(radius * 2 * unit, radius * 2 * unit),
                style = stroke,
            )
        }

        when (glyph) {
            Glyph.BLUETOOTH -> {
                drawLine(tint, p(8f, 7f), p(17f, 16f), stroke.width, StrokeCap.Round)
                drawLine(tint, p(17f, 8f), p(8f, 17f), stroke.width, StrokeCap.Round)
                drawLine(tint, p(12f, 2.5f), p(12f, 21.5f), stroke.width, StrokeCap.Round)
                drawLine(tint, p(12f, 2.5f), p(17f, 8f), stroke.width, StrokeCap.Round)
                drawLine(tint, p(12f, 21.5f), p(17f, 16f), stroke.width, StrokeCap.Round)
            }

            Glyph.SEND -> {
                val path = Path().apply {
                    moveTo(3f * unit, 12f * unit)
                    lineTo(21f * unit, 3.5f * unit)
                    lineTo(14f * unit, 21f * unit)
                    lineTo(11f * unit, 13.2f * unit)
                    close()
                }
                drawPath(path, tint)
            }

            Glyph.BACK -> {
                drawLine(tint, p(15f, 4f), p(7f, 12f), stroke.width, StrokeCap.Round)
                drawLine(tint, p(7f, 12f), p(15f, 20f), stroke.width, StrokeCap.Round)
            }

            Glyph.CROWN -> {
                val path = Path().apply {
                    moveTo(3.5f * unit, 7.5f * unit)
                    lineTo(8.2f * unit, 12.5f * unit)
                    lineTo(12f * unit, 5f * unit)
                    lineTo(15.8f * unit, 12.5f * unit)
                    lineTo(20.5f * unit, 7.5f * unit)
                    lineTo(19f * unit, 18f * unit)
                    lineTo(5f * unit, 18f * unit)
                    close()
                }
                drawPath(path, tint, style = stroke)
                drawLine(tint, p(5.3f, 21f), p(18.7f, 21f), stroke.width, StrokeCap.Round)
            }

            Glyph.LOCK -> {
                drawRoundRect(
                    color = tint,
                    topLeft = p(5f, 10.3f),
                    size = Size(14f * unit, 10.8f * unit),
                    cornerRadius = CornerRadius(3.1f * unit),
                    style = stroke,
                )
                arc(12f, 10.6f, 4.4f, 180f, 180f)
                drawCircle(tint, radius = 1.25f * unit, center = p(12f, 15.5f))
                drawLine(tint, p(12f, 16.5f), p(12f, 18.5f), stroke.width, StrokeCap.Round)
            }

            Glyph.USER -> {
                drawCircle(tint, radius = 3.7f * unit, center = p(12f, 7.7f), style = stroke)
                val path = Path().apply {
                    moveTo(4.5f * unit, 21f * unit)
                    cubicTo(
                        5.2f * unit,
                        15.3f * unit,
                        8.5f * unit,
                        13.2f * unit,
                        12f * unit,
                        13.2f * unit,
                    )
                    cubicTo(
                        15.5f * unit,
                        13.2f * unit,
                        18.8f * unit,
                        15.3f * unit,
                        19.5f * unit,
                        21f * unit,
                    )
                }
                drawPath(path, tint, style = stroke)
            }

            Glyph.REFRESH -> {
                arc(12f, 12f, 8f, 40f, 290f)
                val path = Path().apply {
                    moveTo(18.6f * unit, 3.1f * unit)
                    lineTo(19.2f * unit, 8.1f * unit)
                    lineTo(14.2f * unit, 7.5f * unit)
                }
                drawPath(path, tint, style = stroke)
            }

            Glyph.CLOSE -> {
                drawLine(tint, p(6f, 6f), p(18f, 18f), stroke.width, StrokeCap.Round)
                drawLine(tint, p(18f, 6f), p(6f, 18f), stroke.width, StrokeCap.Round)
            }

            Glyph.RADAR -> {
                drawCircle(tint, radius = 9f * unit, center = p(12f, 12f), style = stroke)
                drawCircle(tint.copy(alpha = 0.55f), radius = 5.2f * unit, center = p(12f, 12f), style = stroke)
                drawLine(tint, p(12f, 12f), p(19f, 5f), stroke.width, StrokeCap.Round)
                drawCircle(tint, radius = 1.1f * unit, center = p(12f, 12f))
            }

            Glyph.SEARCH -> {
                drawCircle(
                    color = tint,
                    radius = 7f * unit,
                    center = p(10.5f, 10.5f),
                    style = stroke,
                )
                drawLine(
                    tint,
                    p(15.7f, 15.7f),
                    p(21f, 21f),
                    stroke.width,
                    StrokeCap.Round,
                )
            }

            Glyph.SHIELD -> {
                val path = Path().apply {
                    moveTo(12f * unit, 2.5f * unit)
                    lineTo(20f * unit, 5.5f * unit)
                    lineTo(20f * unit, 11.5f * unit)
                    cubicTo(
                        20f * unit,
                        16.8f * unit,
                        16.4f * unit,
                        20f * unit,
                        12f * unit,
                        21.8f * unit,
                    )
                    cubicTo(
                        7.6f * unit,
                        20f * unit,
                        4f * unit,
                        16.8f * unit,
                        4f * unit,
                        11.5f * unit,
                    )
                    lineTo(4f * unit, 5.5f * unit)
                    close()
                }
                drawPath(path, tint, style = stroke)
                drawLine(tint, p(8.2f, 12f), p(11f, 14.8f), stroke.width, StrokeCap.Round)
                drawLine(tint, p(11f, 14.8f), p(16.2f, 9.2f), stroke.width, StrokeCap.Round)
            }

            Glyph.BOLT -> {
                val path = Path().apply {
                    moveTo(13.5f * unit, 2f * unit)
                    lineTo(5f * unit, 13.4f * unit)
                    lineTo(11.4f * unit, 13.4f * unit)
                    lineTo(10.5f * unit, 22f * unit)
                    lineTo(19f * unit, 10.6f * unit)
                    lineTo(12.6f * unit, 10.6f * unit)
                    close()
                }
                drawPath(path, tint, style = stroke)
            }

            Glyph.CHECK -> {
                drawLine(tint, p(4.5f, 12.5f), p(9.5f, 17.5f), stroke.width, StrokeCap.Round)
                drawLine(tint, p(9.5f, 17.5f), p(19.5f, 6.5f), stroke.width, StrokeCap.Round)
            }

            Glyph.CHECKS -> {
                drawLine(tint, p(2.5f, 12.5f), p(7f, 17f), stroke.width, StrokeCap.Round)
                drawLine(tint, p(7f, 17f), p(15.5f, 6.5f), stroke.width, StrokeCap.Round)
                drawLine(tint, p(9.5f, 12.5f), p(14f, 17f), stroke.width, StrokeCap.Round)
                drawLine(tint, p(14f, 17f), p(21.5f, 6.5f), stroke.width, StrokeCap.Round)
            }

            Glyph.CHEVRON_RIGHT -> {
                drawLine(tint, p(9f, 4.5f), p(16.5f, 12f), stroke.width, StrokeCap.Round)
                drawLine(tint, p(16.5f, 12f), p(9f, 19.5f), stroke.width, StrokeCap.Round)
            }

            Glyph.SIGNAL -> {
                drawLine(tint, p(5f, 19f), p(5f, 16f), stroke.width, StrokeCap.Round)
                drawLine(tint, p(10f, 19f), p(10f, 13f), stroke.width, StrokeCap.Round)
                drawLine(tint, p(15f, 19f), p(15f, 9f), stroke.width, StrokeCap.Round)
                drawLine(tint, p(20f, 19f), p(20f, 5f), stroke.width, StrokeCap.Round)
            }

            Glyph.POWER -> {
                arc(12f, 13f, 7.6f, 125f, 290f)
                drawLine(tint, p(12f, 2.5f), p(12f, 10.5f), stroke.width, StrokeCap.Round)
            }

            Glyph.CHAT -> {
                val path = Path().apply {
                    moveTo(4f * unit, 5f * unit)
                    lineTo(20f * unit, 5f * unit)
                    lineTo(20f * unit, 16f * unit)
                    lineTo(12f * unit, 16f * unit)
                    lineTo(7.5f * unit, 20f * unit)
                    lineTo(7.5f * unit, 16f * unit)
                    lineTo(4f * unit, 16f * unit)
                    close()
                }
                drawPath(path, tint, style = stroke)
                drawLine(tint, p(8f, 9f), p(16f, 9f), stroke.width, StrokeCap.Round)
                drawLine(tint, p(8f, 12.3f), p(14f, 12.3f), stroke.width, StrokeCap.Round)
            }

            Glyph.SETTINGS -> {
                drawCircle(tint, radius = 6.1f * unit, center = p(12f, 12f), style = stroke)
                drawCircle(tint, radius = 2.1f * unit, center = p(12f, 12f), style = stroke)
                repeat(8) { index ->
                    val angle = index * PI / 4.0
                    val start = Offset(
                        (12f + cos(angle).toFloat() * 6.8f) * unit,
                        (12f + sin(angle).toFloat() * 6.8f) * unit,
                    )
                    val end = Offset(
                        (12f + cos(angle).toFloat() * 9.3f) * unit,
                        (12f + sin(angle).toFloat() * 9.3f) * unit,
                    )
                    drawLine(tint, start, end, stroke.width, StrokeCap.Round)
                }
            }

            Glyph.PALETTE -> {
                drawCircle(tint, radius = 8.4f * unit, center = p(12f, 12f), style = stroke)
                drawCircle(tint, radius = 1.5f * unit, center = p(8.2f, 9.2f))
                drawCircle(tint, radius = 1.5f * unit, center = p(12f, 7.4f))
                drawCircle(tint, radius = 1.5f * unit, center = p(15.8f, 9.2f))
                drawCircle(tint, radius = 1.5f * unit, center = p(16.4f, 13.2f))
            }

            Glyph.TEXT -> {
                drawLine(tint, p(6.5f, 19f), p(12f, 5f), stroke.width, StrokeCap.Round)
                drawLine(tint, p(12f, 5f), p(17.5f, 19f), stroke.width, StrokeCap.Round)
                drawLine(tint, p(9f, 14f), p(15f, 14f), stroke.width, StrokeCap.Round)
            }

            Glyph.WALLPAPER -> {
                drawRoundRect(
                    color = tint,
                    topLeft = p(3.5f, 4.5f),
                    size = Size(17f * unit, 15f * unit),
                    cornerRadius = CornerRadius(3f * unit),
                    style = stroke,
                )
                drawCircle(tint, radius = 1.7f * unit, center = p(8.2f, 9.2f))
                val path = Path().apply {
                    moveTo(5f * unit, 17f * unit)
                    lineTo(10.2f * unit, 11.6f * unit)
                    lineTo(13.6f * unit, 15f * unit)
                    lineTo(16.4f * unit, 12.2f * unit)
                    lineTo(19f * unit, 15f * unit)
                }
                drawPath(path, tint, style = stroke)
            }

            Glyph.TRASH -> {
                drawLine(tint, p(5f, 7f), p(19f, 7f), stroke.width, StrokeCap.Round)
                drawLine(tint, p(9f, 7f), p(9f, 4.5f), stroke.width, StrokeCap.Round)
                drawLine(tint, p(9f, 4.5f), p(15f, 4.5f), stroke.width, StrokeCap.Round)
                drawLine(tint, p(15f, 4.5f), p(15f, 7f), stroke.width, StrokeCap.Round)
                drawRoundRect(
                    color = tint,
                    topLeft = p(7f, 7f),
                    size = Size(10f * unit, 13f * unit),
                    cornerRadius = CornerRadius(2.2f * unit),
                    style = stroke,
                )
                drawLine(tint, p(10.3f, 10.5f), p(10.3f, 16.7f), stroke.width, StrokeCap.Round)
                drawLine(tint, p(13.7f, 10.5f), p(13.7f, 16.7f), stroke.width, StrokeCap.Round)
            }

            Glyph.INFO -> {
                drawCircle(tint, radius = 8.7f * unit, center = p(12f, 12f), style = stroke)
                drawCircle(tint, radius = 1.15f * unit, center = p(12f, 7.9f))
                drawLine(tint, p(12f, 11f), p(12f, 16.7f), stroke.width, StrokeCap.Round)
            }

            Glyph.SERVER -> {
                repeat(3) { index ->
                    val top = 4f + index * 6f
                    drawRoundRect(
                        color = tint,
                        topLeft = p(4f, top),
                        size = Size(16f * unit, 4.4f * unit),
                        cornerRadius = CornerRadius(1.5f * unit),
                        style = stroke,
                    )
                    drawCircle(tint, radius = 0.8f * unit, center = p(7f, top + 2.2f))
                    drawLine(
                        tint,
                        p(10.5f, top + 2.2f),
                        p(16.8f, top + 2.2f),
                        stroke.width * 0.8f,
                        StrokeCap.Round,
                    )
                }
            }

            Glyph.MOON -> {
                val path = Path().apply {
                    moveTo(15.8f * unit, 3.4f * unit)
                    cubicTo(
                        10.2f * unit, 5.2f * unit,
                        8.4f * unit, 12.2f * unit,
                        12.8f * unit, 16.8f * unit,
                    )
                    cubicTo(
                        14.6f * unit, 18.7f * unit,
                        17.2f * unit, 19.4f * unit,
                        19.4f * unit, 18.7f * unit,
                    )
                    cubicTo(
                        14.8f * unit, 21.8f * unit,
                        8.1f * unit, 20.1f * unit,
                        5.6f * unit, 15.2f * unit,
                    )
                    cubicTo(
                        3.1f * unit, 10.3f * unit,
                        6.4f * unit, 4.5f * unit,
                        11.9f * unit, 3.5f * unit,
                    )
                    cubicTo(
                        13.2f * unit, 3.3f * unit,
                        14.6f * unit, 3.3f * unit,
                        15.8f * unit, 3.4f * unit,
                    )
                    close()
                }
                drawPath(path, tint, style = stroke)
            }

            Glyph.SUN -> {
                drawCircle(tint, radius = 4.2f * unit, center = p(12f, 12f), style = stroke)
                repeat(8) { index ->
                    val angle = index * PI / 4.0
                    val start = Offset(
                        (12f + cos(angle).toFloat() * 6.2f) * unit,
                        (12f + sin(angle).toFloat() * 6.2f) * unit,
                    )
                    val end = Offset(
                        (12f + cos(angle).toFloat() * 8.8f) * unit,
                        (12f + sin(angle).toFloat() * 8.8f) * unit,
                    )
                    drawLine(tint, start, end, stroke.width, StrokeCap.Round)
                }
            }

            Glyph.VIBRATE -> {
                drawRoundRect(
                    color = tint,
                    topLeft = p(8f, 3.5f),
                    size = Size(8f * unit, 17f * unit),
                    cornerRadius = CornerRadius(2.2f * unit),
                    style = stroke,
                )
                drawLine(tint, p(10.5f, 17.5f), p(13.5f, 17.5f), stroke.width, StrokeCap.Round)
                arc(5.3f, 12f, 2.2f, -55f, 110f)
                arc(3.4f, 12f, 4.1f, -55f, 110f)
                arc(18.7f, 12f, 2.2f, 125f, 110f)
                arc(20.6f, 12f, 4.1f, 125f, 110f)
            }

            Glyph.CLOCK -> {
                drawCircle(tint, radius = 8.6f * unit, center = p(12f, 12f), style = stroke)
                drawLine(tint, p(12f, 12f), p(12f, 6.8f), stroke.width, StrokeCap.Round)
                drawLine(tint, p(12f, 12f), p(16f, 14.2f), stroke.width, StrokeCap.Round)
            }

            Glyph.DATABASE -> {
                drawOval(
                    color = tint,
                    topLeft = p(5f, 3.2f),
                    size = Size(14f * unit, 5.6f * unit),
                    style = stroke,
                )
                drawLine(tint, p(5f, 6f), p(5f, 18f), stroke.width, StrokeCap.Round)
                drawLine(tint, p(19f, 6f), p(19f, 18f), stroke.width, StrokeCap.Round)
                drawArc(
                    color = tint,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = p(5f, 15.2f),
                    size = Size(14f * unit, 5.6f * unit),
                    style = stroke,
                )
                drawArc(
                    color = tint,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = p(5f, 9.4f),
                    size = Size(14f * unit, 5.6f * unit),
                    style = stroke,
                )
            }

            Glyph.PLUS -> {
                drawLine(tint, p(12f, 4.5f), p(12f, 19.5f), stroke.width, StrokeCap.Round)
                drawLine(tint, p(4.5f, 12f), p(19.5f, 12f), stroke.width, StrokeCap.Round)
            }

        }
    }
}
