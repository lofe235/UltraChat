package com.ultrasys.ultrachat.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ultrasys.ultrachat.data.AvatarPresets
import com.ultrasys.ultrachat.data.ImageCodec
import com.ultrasys.ultrachat.data.UserProfile
import com.ultrasys.ultrachat.ui.components.Glyph
import com.ultrasys.ultrachat.ui.components.GlyphIcon
import com.ultrasys.ultrachat.ui.components.LogoMark
import com.ultrasys.ultrachat.ui.components.PrimaryActionButton
import com.ultrasys.ultrachat.ui.components.UltraAvatar
import com.ultrasys.ultrachat.ui.components.UltraIconButton
import com.ultrasys.ultrachat.ui.theme.Line
import com.ultrasys.ultrachat.ui.theme.PanelElevated
import com.ultrasys.ultrachat.ui.theme.TelegramBlue
import com.ultrasys.ultrachat.ui.theme.TextPrimary
import com.ultrasys.ultrachat.ui.theme.TextSecondary
import com.ultrasys.ultrachat.ui.theme.TextTertiary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Создание аккаунта в три шага, как в Telegram:
 * приветствие → никнейм → аватар (эмодзи или фото с обрезкой).
 */
@Composable
fun SignUpScreen(
    onOpenSettings: () -> Unit,
    onComplete: (nickname: String, avatarId: String, photoPath: String?) -> Unit,
) {
    val context = LocalContext.current
    var step by remember { mutableStateOf(0) }
    var nickname by remember { mutableStateOf("") }
    var avatarId by remember { mutableStateOf(AvatarPresets.DEFAULT_ID) }
    var photoPath by remember { mutableStateOf<String?>(null) }
    var cropSource by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loadingPhoto by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        loadingPhoto = true
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                ImageCodec.loadFromUri(context, uri, 1200)
            }
            loadingPhoto = false
            if (bitmap == null) {
                error = "Не удалось открыть фото"
            } else {
                cropSource = bitmap
                error = null
            }
        }
    }

    BackHandler(enabled = step > 0) {
        step -= 1
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = TelegramBlue,
        unfocusedBorderColor = Line,
        focusedContainerColor = PanelElevated,
        unfocusedContainerColor = PanelElevated,
        cursorColor = TelegramBlue,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        focusedPlaceholderColor = TextTertiary,
        unfocusedPlaceholderColor = TextTertiary,
    )

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .padding(horizontal = 22.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LogoMark(size = 34.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "UltraChat",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                UltraIconButton(
                    glyph = Glyph.SETTINGS,
                    contentDescription = "Настройки",
                    onClick = onOpenSettings,
                    size = 36.dp,
                    tint = TextSecondary,
                )
            }

            Spacer(Modifier.height(18.dp))

            StepDots(step = step, total = 3)

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    (slideInHorizontally(tween(320)) { it / 3 } + fadeIn(tween(260))) togetherWith
                        (slideOutHorizontally(tween(240)) { -it / 3 } + fadeOut(tween(180)))
                },
                label = "signup-step",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { current ->
                when (current) {
                    0 -> WelcomeStep()
                    1 -> NicknameStep(
                        nickname = nickname,
                        onNickname = {
                            nickname = it
                            error = null
                        },
                        fieldColors = fieldColors,
                        onDone = { if (nickname.trim().length >= 2) step = 2 },
                    )
                    else -> AvatarStep(
                        nickname = nickname,
                        avatarId = avatarId,
                        photoPath = photoPath,
                        loading = loadingPhoto,
                        onPickEmoji = {
                            avatarId = it
                            photoPath = null
                        },
                        onPickPhoto = {
                            picker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        onRemovePhoto = {
                            photoPath = null
                            avatarId = AvatarPresets.DEFAULT_ID
                        },
                    )
                }
            }

            if (error != null) {
                Text(
                    text = error ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                )
            }

            PrimaryActionButton(
                text = when (step) {
                    0 -> "Продолжить"
                    1 -> "Продолжить"
                    else -> "Начать общение"
                },
                onClick = {
                    when (step) {
                        0 -> step = 1
                        1 -> {
                            val clean = UserProfile.normalizedNickname(nickname)
                            if (clean.length < 2) {
                                error = "Никнейм минимум 2 символа"
                            } else {
                                nickname = clean
                                step = 2
                            }
                        }
                        else -> {
                            val clean = UserProfile.normalizedNickname(nickname)
                            if (clean.isEmpty()) {
                                error = "Введите никнейм"
                            } else {
                                onComplete(clean, avatarId, photoPath)
                            }
                        }
                    }
                },
                enabled = step == 0 || nickname.trim().length >= 2,
                leadingGlyph = if (step == 2) Glyph.CHAT else Glyph.CHEVRON_RIGHT,
            )

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "UltraChat",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = com.ultrasys.ultrachat.BuildConfig.VERSION_NAME,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                )
            }
            Spacer(Modifier.height(14.dp))
        }
    }

    val source = cropSource
    if (source != null) {
        AvatarCropDialog(
            source = source,
            onCancel = { cropSource = null },
            onCropped = { bitmap ->
                cropSource = null
                loadingPhoto = true
                val file = ImageCodec.saveAvatar(context, bitmap)
                loadingPhoto = false
                if (file != null) {
                    photoPath = file.absolutePath
                    error = null
                } else {
                    error = "Не удалось сохранить фото"
                }
            },
        )
    }
}

@Composable
private fun StepDots(step: Int, total: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { index ->
            val active = index <= step
            val width by animateFloatAsState(
                targetValue = if (index == step) 22f else 8f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "dot-width",
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .width(width.dp)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(if (active) TelegramBlue else Line),
            )
        }
    }
}

@Composable
private fun WelcomeStep() {
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }

    val scale by animateFloatAsState(
        targetValue = if (revealed) 1f else 0.7f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow),
        label = "welcome-logo",
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .size(104.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(TelegramBlue, Color(0xFF5FD3B3)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("💬", fontSize = 44.sp)
        }
        Spacer(Modifier.height(22.dp))
        Text(
            text = "Добро пожаловать",
            style = MaterialTheme.typography.displayMedium,
            color = TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "UltraChat — мессенджер.\nИз любой точки — через сервер. Без интернета — по Bluetooth.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(26.dp))
        FeatureLine("Сервер", "чаты и фото из любой точки мира", 0, revealed)
        FeatureLine("Bluetooth", "запасной канал без интернета", 1, revealed)
        FeatureLine("История", "переписка хранится на устройстве", 2, revealed)
    }
}

@Composable
private fun FeatureLine(tag: String, text: String, index: Int, revealed: Boolean) {
    AnimatedVisibility(
        visible = revealed,
        enter = fadeIn(tween(260, delayMillis = index * 90)) +
            slideInVertically(tween(320, delayMillis = index * 90)) { it / 2 },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(TelegramBlue.copy(alpha = 0.14f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = tag,
                    style = MaterialTheme.typography.labelSmall,
                    color = TelegramBlue,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
    }
}

@Composable
private fun NicknameStep(
    nickname: String,
    onNickname: (String) -> Unit,
    fieldColors: androidx.compose.material3.TextFieldColors,
    onDone: () -> Unit,
) {
    val praise = when {
        nickname.isBlank() -> "Придумай имя — его увидят рядом"
        nickname.length < 2 -> "Ещё пару символов"
        nickname.length < 6 -> "Неплохо! Можно и поярче"
        nickname.length < 12 -> "Отличный ник — тебя запомнят"
        else -> "Мощно. Настоящий ник для ультра чата"
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Введите никнейм",
            style = MaterialTheme.typography.displayMedium,
            color = TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Так тебя будут видеть другие устройства",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(26.dp))
        OutlinedTextField(
            value = nickname,
            onValueChange = {
                if (it.length <= UserProfile.MAX_NICKNAME_LENGTH) onNickname(it)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Например, Скай") },
            shape = RoundedCornerShape(16.dp),
            textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center),
            colors = fieldColors,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = praise,
            style = MaterialTheme.typography.bodySmall,
            color = if (nickname.trim().length >= 2) TelegramBlue else TextTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AvatarStep(
    nickname: String,
    avatarId: String,
    photoPath: String?,
    loading: Boolean,
    onPickEmoji: (String) -> Unit,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
) {
    var mode by remember { mutableStateOf(if (photoPath != null) 1 else 0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        val scale by animateFloatAsState(
            targetValue = if (loading) 0.92f else 1f,
            animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow),
            label = "avatar-preview",
        )
        Box(
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        ) {
            UltraAvatar(
                displayName = nickname.ifBlank { "Ultra" },
                size = 104.dp,
                avatarId = avatarId,
                photoPath = photoPath,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Выбери аватар",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
        )
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(PanelElevated)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ModeChip("Эмодзи", mode == 0, Modifier.weight(1f)) {
                mode = 0
                if (photoPath != null) onPickEmoji(avatarId)
            }
            ModeChip("Фото", mode == 1, Modifier.weight(1f)) { mode = 1 }
        }

        Spacer(Modifier.height(14.dp))

        if (mode == 0) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AvatarPresets.all.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { preset ->
                            val selected = photoPath == null && preset.id == avatarId
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(PanelElevated)
                                    .then(
                                        if (selected) {
                                            Modifier.border(2.dp, TelegramBlue, RoundedCornerShape(14.dp))
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .clickable { onPickEmoji(preset.id) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (preset.emoji.isBlank()) {
                                    GlyphIcon(
                                        glyph = Glyph.USER,
                                        modifier = Modifier.size(22.dp),
                                        tint = if (selected) TelegramBlue else TextSecondary,
                                    )
                                } else {
                                    Text(text = preset.emoji, fontSize = 26.sp)
                                }
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (photoPath != null) "Фото выбрано" else "Выбери фото — обрежь по кругу",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                PrimaryActionButton(
                    text = if (photoPath != null) "Выбрать другое" else "Загрузить фото",
                    onClick = onPickPhoto,
                    leadingGlyph = Glyph.WALLPAPER,
                )
                if (photoPath != null) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = onRemovePhoto,
                        colors = ButtonDefaults.textButtonColors(contentColor = TextTertiary),
                    ) {
                        Text("Убрать фото")
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ModeChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) TelegramBlue else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) Color.White else TextSecondary,
        )
    }
}

/**
 * Круговой кроп: фото можно двигать и масштабировать, результат — квадрат.
 */
@Composable
private fun AvatarCropDialog(
    source: Bitmap,
    onCancel: () -> Unit,
    onCropped: (Bitmap) -> Unit,
) {
    val density = LocalDensity.current
    val boxPx = with(density) { 280.dp.toPx() }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val baseScale = boxPx / minOf(source.width, source.height)

    fun clamp(newScale: Float, newOffset: Offset): Offset {
        val scaledW = source.width * baseScale * newScale
        val scaledH = source.height * baseScale * newScale
        val maxX = ((scaledW - boxPx) / 2f).coerceAtLeast(0f)
        val maxY = ((scaledH - boxPx) / 2f).coerceAtLeast(0f)
        return Offset(
            newOffset.x.coerceIn(-maxX, maxX),
            newOffset.y.coerceIn(-maxY, maxY),
        )
    }

    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = PanelElevated,
        shape = RoundedCornerShape(22.dp),
        title = {
            Text(
                text = "Обрезка аватара",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .clip(CircleShape)
                        .background(Color.Black)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val next = (scale * zoom).coerceIn(1f, 4f)
                                scale = next
                                offset = clamp(next, offset + pan)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = source.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            },
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Двигай и масштабируй пальцем",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cropSize = boxPx / (baseScale * scale)
                    val centerX = source.width / 2f - offset.x / (baseScale * scale)
                    val centerY = source.height / 2f - offset.y / (baseScale * scale)
                    val left = (centerX - cropSize / 2f).coerceIn(0f, source.width - cropSize)
                    val top = (centerY - cropSize / 2f).coerceIn(0f, source.height - cropSize)
                    val square = Bitmap.createBitmap(
                        source,
                        left.toInt().coerceAtLeast(0),
                        top.toInt().coerceAtLeast(0),
                        cropSize.toInt().coerceAtMost(source.width),
                        cropSize.toInt().coerceAtMost(source.height),
                    )
                    onCropped(ImageCodec.scaleDown(square, ImageCodec.AVATAR_SIDE))
                },
                colors = ButtonDefaults.textButtonColors(contentColor = TelegramBlue),
            ) {
                Text("Готово", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary),
            ) {
                Text("Отмена")
            }
        },
    )
}
