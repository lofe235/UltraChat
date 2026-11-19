package com.ultrasys.ultrachat.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ultrasys.ultrachat.data.AvatarPreset
import com.ultrasys.ultrachat.data.AvatarPresets
import com.ultrasys.ultrachat.data.UserProfile
import com.ultrasys.ultrachat.ui.components.GlassPanel
import com.ultrasys.ultrachat.ui.components.Glyph
import com.ultrasys.ultrachat.ui.components.GlyphIcon
import com.ultrasys.ultrachat.ui.components.LogoMark
import com.ultrasys.ultrachat.ui.components.PrimaryActionButton
import com.ultrasys.ultrachat.ui.components.UltraAvatar
import com.ultrasys.ultrachat.ui.components.UltraIconButton
import com.ultrasys.ultrachat.ui.theme.CreatorGold
import com.ultrasys.ultrachat.ui.theme.Danger
import com.ultrasys.ultrachat.ui.theme.Line
import com.ultrasys.ultrachat.ui.theme.PanelElevated
import com.ultrasys.ultrachat.ui.theme.TelegramBlue
import com.ultrasys.ultrachat.ui.theme.TextPrimary
import com.ultrasys.ultrachat.ui.theme.TextSecondary
import com.ultrasys.ultrachat.ui.theme.TextTertiary

/**
 * Локальная регистрация и редактирование профиля. Никаких аккаунтов:
 * всё сохраняется в SharedPreferences на этом устройстве.
 */
@Composable
fun OnboardingScreen(
    profile: UserProfile?,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onSave: (String, String) -> String?,
    onUnlockCreator: (code: String, nickname: String, avatarId: String) -> Boolean,
) {
    var nickname by rememberSaveable(profile?.nickname) {
        mutableStateOf(profile?.nickname.orEmpty())
    }
    var avatarId by rememberSaveable(profile?.avatarId) {
        mutableStateOf(AvatarPresets.normalizedId(profile?.avatarId))
    }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var showCodeDialog by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    var codeError by rememberSaveable { mutableStateOf(false) }

    var tapCount by remember { mutableIntStateOf(0) }
    var firstTapAt by remember { mutableLongStateOf(0L) }

    val isCreator = profile?.isCreator == true
    val isEditing = profile != null

    fun handleAvatarTap() {
        if (isCreator || showCodeDialog) return
        val now = SystemClock.elapsedRealtime()
        if (firstTapAt == 0L || now - firstTapAt > TAP_WINDOW_MS) {
            firstTapAt = now
            tapCount = 0
        }
        tapCount += 1
        if (tapCount >= SECRET_TAPS) {
            tapCount = 0
            firstTapAt = 0L
            code = ""
            codeError = false
            showCodeDialog = true
        }
    }

    fun submitProfile() {
        error = onSave(nickname, avatarId)
    }

    fun dismissDialog() {
        showCodeDialog = false
        code = ""
        codeError = false
    }

    fun confirmCode() {
        if (code.isBlank()) {
            codeError = true
            return
        }
        if (onUnlockCreator(code, nickname, avatarId)) {
            dismissDialog()
        } else {
            codeError = true
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = TelegramBlue,
        unfocusedBorderColor = Line,
        errorBorderColor = Danger,
        focusedContainerColor = PanelElevated,
        unfocusedContainerColor = PanelElevated,
        errorContainerColor = PanelElevated,
        cursorColor = TelegramBlue,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        errorTextColor = TextPrimary,
        focusedLabelColor = TelegramBlue,
        unfocusedLabelColor = TextSecondary,
        errorLabelColor = Danger,
        focusedPlaceholderColor = TextTertiary,
        unfocusedPlaceholderColor = TextTertiary,
    )

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (canGoBack) {
                    UltraIconButton(
                        glyph = Glyph.BACK,
                        contentDescription = "Назад",
                        onClick = onBack,
                        size = 38.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                } else {
                    LogoMark(size = 38.dp)
                    Spacer(Modifier.width(10.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (canGoBack) "Профиль" else "UltraChat",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                    )
                    Text(
                        text = if (canGoBack) "Настройки профиля" else "Локальный мессенджер",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                    )
                }
                UltraIconButton(
                    glyph = Glyph.SETTINGS,
                    contentDescription = "Настройки",
                    onClick = onOpenSettings,
                    size = 38.dp,
                    tint = TextSecondary,
                )
            }

            Spacer(Modifier.height(30.dp))
            Text(
                text = if (isEditing) "Ваш профиль" else "Добро пожаловать",
                style = MaterialTheme.typography.displayMedium,
                color = TextPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Выберите имя и аватар. Всё хранится только на этом устройстве.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )

            Spacer(Modifier.height(24.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { handleAvatarTap() },
                    ),
                ) {
                    UltraAvatar(
                        displayName = if (nickname.isBlank()) "Ultra" else nickname,
                        size = 96.dp,
                        isCreator = isCreator,
                        avatarId = avatarId,
                        photoPath = profile?.avatarPhoto,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = when {
                        isCreator -> "СОЗДАТЕЛЬ"
                        tapCount >= 5 -> "·  ·  ·"
                        else -> "ЛОКАЛЬНЫЙ ПРОФИЛЬ"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCreator) CreatorGold else TextTertiary,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = "Аватар",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
            )
            Spacer(Modifier.height(10.dp))
            AvatarPicker(
                selectedId = avatarId,
                displayName = if (nickname.isBlank()) "Ultra" else nickname,
                onSelect = { avatarId = it },
            )

            if (isCreator) {
                Spacer(Modifier.height(20.dp))
                GlassPanel(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = CreatorGold.copy(alpha = 0.4f),
                    padding = PaddingValues(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(CreatorGold.copy(alpha = 0.14f))
                                .border(1.dp, CreatorGold.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            GlyphIcon(
                                glyph = Glyph.CROWN,
                                modifier = Modifier.size(19.dp),
                                tint = CreatorGold,
                                strokeWidth = 1.9.dp,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Статус Создателя",
                                style = MaterialTheme.typography.titleMedium,
                                color = CreatorGold,
                            )
                            Text(
                                text = "Префикс ${UserProfile.CREATOR_PREFIX} виден собеседнику.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = "Имя",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = nickname,
                onValueChange = { value ->
                    if (value.length <= UserProfile.MAX_NICKNAME_LENGTH) {
                        nickname = value
                        error = null
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Никнейм") },
                placeholder = { Text("Например, Нова") },
                leadingIcon = {
                    GlyphIcon(
                        glyph = Glyph.USER,
                        modifier = Modifier.size(18.dp),
                        tint = if (error != null) Danger else TextSecondary,
                    )
                },
                supportingText = {
                    Text(
                        text = error ?: "До ${UserProfile.MAX_NICKNAME_LENGTH} символов",
                        color = if (error != null) Danger else TextTertiary,
                    )
                },
                isError = error != null,
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submitProfile() }),
            )

            Spacer(Modifier.height(20.dp))
            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(TelegramBlue.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        GlyphIcon(
                            glyph = Glyph.SERVER,
                            modifier = Modifier.size(21.dp),
                            tint = TelegramBlue,
                            strokeWidth = 1.9.dp,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Сервер",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                        )
                        Text(
                            text = "Основной канал связи. Bluetooth — запасной, без интернета.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            PrimaryActionButton(
                text = if (isEditing) "Сохранить" else "Начать общение",
                onClick = { submitProfile() },
                enabled = nickname.isNotBlank(),
                leadingGlyph = Glyph.CHAT,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphIcon(
                    glyph = Glyph.SHIELD,
                    modifier = Modifier.size(13.dp),
                    tint = TextTertiary,
                    strokeWidth = 1.6.dp,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Без интернета · история на устройстве",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                )
            }
            Spacer(Modifier.height(28.dp))
        }

        if (showCodeDialog) {
            AlertDialog(
                onDismissRequest = { dismissDialog() },
                containerColor = PanelElevated,
                shape = RoundedCornerShape(20.dp),
                icon = {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(CreatorGold.copy(alpha = 0.13f))
                            .border(1.dp, CreatorGold.copy(alpha = 0.45f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        GlyphIcon(
                            glyph = Glyph.CROWN,
                            modifier = Modifier.size(26.dp),
                            tint = CreatorGold,
                            strokeWidth = 1.9.dp,
                        )
                    }
                },
                title = {
                    Text(
                        text = "Скрытый доступ",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Введите код Создателя. Особый статус сохранится " +
                                "на устройстве и будет виден собеседникам.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = code,
                            onValueChange = { value ->
                                code = value.filter(Char::isDigit).take(8)
                                codeError = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = codeError,
                            label = { Text("Код") },
                            placeholder = { Text("••••") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.NumberPassword,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { confirmCode() }),
                            shape = RoundedCornerShape(14.dp),
                            colors = fieldColors,
                        )
                        if (codeError) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Код не подходит",
                                style = MaterialTheme.typography.bodySmall,
                                color = Danger,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { confirmCode() },
                        colors = ButtonDefaults.textButtonColors(contentColor = CreatorGold),
                    ) {
                        Text("Активировать", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { dismissDialog() },
                        colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary),
                    ) {
                        Text("Отмена")
                    }
                },
            )
        }
    }
}

@Composable
private fun AvatarPicker(
    selectedId: String,
    displayName: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AvatarPresets.all.chunked(4).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { preset ->
                    AvatarChoice(
                        preset = preset,
                        selected = preset.id == selectedId,
                        displayName = displayName,
                        onClick = { onSelect(preset.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(4 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AvatarChoice(
    preset: AvatarPreset,
    selected: Boolean,
    displayName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = Color(preset.color)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(accent.copy(alpha = if (selected) 0.30f else 0.15f))
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) TelegramBlue else accent.copy(alpha = 0.35f),
                    shape = CircleShape,
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (preset.emoji.isNotBlank()) {
                Text(
                    text = preset.emoji,
                    fontSize = 23.sp,
                    lineHeight = 27.sp,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    text = UserProfile.initials(displayName),
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(19.dp)
                    .clip(CircleShape)
                    .background(TelegramBlue)
                    .border(2.dp, PanelElevated, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(
                    glyph = Glyph.CHECK,
                    modifier = Modifier.size(10.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.2.dp,
                )
            }
        }
    }
}

private const val SECRET_TAPS = 10
private const val TAP_WINDOW_MS = 5_000L
