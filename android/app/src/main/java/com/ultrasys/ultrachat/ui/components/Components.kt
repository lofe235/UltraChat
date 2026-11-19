package com.ultrasys.ultrachat.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ultrasys.ultrachat.data.AvatarPresets
import com.ultrasys.ultrachat.data.ImageCodec
import com.ultrasys.ultrachat.data.UserProfile
import com.ultrasys.ultrachat.net.LinkPhase
import com.ultrasys.ultrachat.ui.theme.CreatorAmber
import com.ultrasys.ultrachat.ui.theme.CreatorGold
import com.ultrasys.ultrachat.ui.theme.Danger
import com.ultrasys.ultrachat.ui.theme.Line
import com.ultrasys.ultrachat.ui.theme.Panel
import com.ultrasys.ultrachat.ui.theme.PanelElevated
import com.ultrasys.ultrachat.ui.theme.PanelSoft
import com.ultrasys.ultrachat.ui.theme.Success
import com.ultrasys.ultrachat.ui.theme.TelegramBlue
import com.ultrasys.ultrachat.ui.theme.TextPrimary
import com.ultrasys.ultrachat.ui.theme.TextSecondary
import com.ultrasys.ultrachat.ui.theme.TextTertiary
import com.ultrasys.ultrachat.ui.theme.Void
import com.ultrasys.ultrachat.ui.theme.VoidDeep

/**
 * Фон приложения: спокойный тёмный градиент без сетки и неоновых ореолов.
 */
@Composable
fun UltraBackground(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Void, VoidDeep))),
    )
}

@Composable
fun LogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(TelegramBlue),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(
            glyph = Glyph.CHAT,
            modifier = Modifier.size(size * 0.52f),
            tint = MaterialTheme.colorScheme.onPrimary,
            strokeWidth = 2.dp,
        )
    }
}

/**
 * Аватар с эмодзи или инициалами. Корона Создателя остаётся поверх любого аватара.
 */
@Composable
fun UltraAvatar(
    displayName: String,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    isCreator: Boolean = false,
    avatarId: String? = null,
    photoPath: String? = null,
    photoBase64: String? = null,
) {
    val preset = AvatarPresets.byId(avatarId)
    val accent = Color(preset.color)
    val density = LocalDensity.current
    val emojiSize = with(density) { (size.toPx() * 0.46f).toSp() }
    val initialsSize = with(density) { (size.toPx() * 0.33f).toSp() }
    val photo = rememberAvatarPhoto(photoPath, photoBase64)

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(accent.copy(alpha = 0.34f), PanelElevated),
                    ),
                )
                .border(
                    width = if (isCreator) 1.8.dp else 1.dp,
                    color = if (isCreator) CreatorGold else accent.copy(alpha = 0.45f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (photo != null) {
                Image(
                    bitmap = photo,
                    contentDescription = displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                )
            } else if (preset.emoji.isNotBlank()) {
                Text(
                    text = preset.emoji,
                    style = TextStyle(
                        fontSize = emojiSize,
                        lineHeight = emojiSize,
                    ),
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    text = UserProfile.initials(displayName),
                    color = if (isCreator) CreatorGold else TextPrimary,
                    style = TextStyle(
                        fontSize = initialsSize,
                        lineHeight = initialsSize,
                        fontWeight = FontWeight.Bold,
                    ),
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (isCreator) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(size * 0.34f)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(CreatorGold, CreatorAmber)))
                    .border(1.dp, VoidDeep, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(
                    glyph = Glyph.CROWN,
                    modifier = Modifier.size(size * 0.18f),
                    tint = Color(0xFF2A1A02),
                    strokeWidth = 1.5.dp,
                )
            }
        }
    }
}

@Composable
private fun rememberAvatarPhoto(path: String?, base64: String?): androidx.compose.ui.graphics.ImageBitmap? {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, path, base64) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (!base64.isNullOrBlank()) {
                ImageCodec.drawableFrom(base64)
            } else {
                ImageCodec.loadBitmap(path, 256)
            }
        }
    }
    return bitmap?.asImageBitmap()
}

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(16.dp),
    shape: Shape = RoundedCornerShape(16.dp),
    borderColor: Color = Line,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(shape)
            .background(Panel)
            .border(1.dp, borderColor, shape)
            .padding(padding),
        content = content,
    )
}

@Composable
fun UltraIconButton(
    glyph: Glyph,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    tint: Color = TextPrimary,
    background: Color = Color.Transparent,
    borderColor: Color = Color.Transparent,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .border(1.dp, borderColor, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(
            glyph = glyph,
            modifier = Modifier.size(size * 0.46f),
            tint = tint,
            strokeWidth = 2.dp,
        )
    }
}

@Composable
fun StatusDot(
    color: Color,
    modifier: Modifier = Modifier,
    pulsing: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "status-dot")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "status-pulse",
    )
    Canvas(modifier.size(10.dp)) {
        val radius = size.minDimension / 2f
        if (pulsing) {
            drawCircle(
                color = color.copy(alpha = (1f - pulse) * 0.28f),
                radius = radius * (0.6f + pulse * 0.8f),
                center = center,
            )
        }
        drawCircle(color = color, radius = radius * 0.62f, center = center)
    }
}

@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingGlyph: Glyph? = null,
) {
    val shape = RoundedCornerShape(14.dp)
    val active = enabled && !loading
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(shape)
            .background(if (active) TelegramBlue else PanelSoft)
            .clickable(enabled = active, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
            )
        } else {
            leadingGlyph?.let { glyph ->
                GlyphIcon(
                    glyph = glyph,
                    modifier = Modifier.size(18.dp),
                    tint = if (active) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        TextTertiary
                    },
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = if (active) MaterialTheme.colorScheme.onPrimary else TextTertiary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
fun MiniActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    accent: Color = TelegramBlue,
) {
    val shape = RoundedCornerShape(10.dp)
    val active = enabled && !loading
    Row(
        modifier = modifier
            .clip(shape)
            .background(accent.copy(alpha = if (active) 0.14f else 0.05f))
            .clickable(enabled = active, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(13.dp),
                color = accent,
                strokeWidth = 1.6.dp,
            )
        } else {
            Text(
                text = text,
                color = if (active) accent else TextTertiary,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun UltraDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Line.copy(alpha = 0.7f)),
    )
}

fun linkPhaseLabel(phase: LinkPhase): String = when (phase) {
    LinkPhase.IDLE -> "Готов к поиску"
    LinkPhase.SCANNING -> "Поиск устройств"
    LinkPhase.CONNECTING -> "Подключение"
    LinkPhase.CONNECTED -> "Bluetooth подключён"
    LinkPhase.RECOVERING -> "Восстановление связи"
    LinkPhase.ERROR -> "Нужно внимание"
}

@Composable
fun linkPhaseColor(phase: LinkPhase): Color = when (phase) {
    LinkPhase.IDLE -> TextSecondary
    LinkPhase.SCANNING -> TelegramBlue
    LinkPhase.CONNECTING -> CreatorAmber
    LinkPhase.CONNECTED -> Success
    LinkPhase.RECOVERING -> CreatorAmber
    LinkPhase.ERROR -> Danger
}
