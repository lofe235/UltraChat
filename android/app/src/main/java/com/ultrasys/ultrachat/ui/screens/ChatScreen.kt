package com.ultrasys.ultrachat.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ultrasys.ultrachat.data.AppSettings
import com.ultrasys.ultrachat.data.ChatMessage
import com.ultrasys.ultrachat.data.ImageCodec
import com.ultrasys.ultrachat.data.ListDensity
import com.ultrasys.ultrachat.data.UserProfile
import com.ultrasys.ultrachat.net.LinkChannel
import com.ultrasys.ultrachat.net.LinkPhase
import com.ultrasys.ultrachat.net.LinkState
import com.ultrasys.ultrachat.net.RemotePeer
import com.ultrasys.ultrachat.net.TransportPhase
import com.ultrasys.ultrachat.net.WireProtocol
import com.ultrasys.ultrachat.ui.components.Glyph
import com.ultrasys.ultrachat.ui.components.GlyphIcon
import com.ultrasys.ultrachat.ui.components.UltraAvatar
import com.ultrasys.ultrachat.ui.components.UltraDivider
import com.ultrasys.ultrachat.ui.components.UltraIconButton
import com.ultrasys.ultrachat.ui.theme.CreatorAmber
import com.ultrasys.ultrachat.ui.theme.CreatorGold
import com.ultrasys.ultrachat.ui.theme.Danger
import com.ultrasys.ultrachat.ui.theme.Panel
import com.ultrasys.ultrachat.ui.theme.PanelElevated
import com.ultrasys.ultrachat.ui.theme.PanelSoft
import com.ultrasys.ultrachat.ui.theme.TelegramBlue
import com.ultrasys.ultrachat.ui.theme.TextPrimary
import com.ultrasys.ultrachat.ui.theme.TextSecondary
import com.ultrasys.ultrachat.ui.theme.TextTertiary
import com.ultrasys.ultrachat.ui.theme.UltraTheme
import com.ultrasys.ultrachat.ui.theme.WallpaperBackdrop
import com.ultrasys.ultrachat.ui.theme.bubbleShapeFor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(
    profile: UserProfile,
    settings: AppSettings,
    remote: RemotePeer?,
    linkState: LinkState,
    messages: List<ChatMessage>,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onPickImage: () -> Unit,
    onEditMessage: (String, String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onDeleteChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissError: () -> Unit,
    peerTitle: String? = null,
    peerIsCreator: Boolean = false,
    isLobby: Boolean = false,
) {
    var draft by rememberSaveable(isLobby, remote?.deviceId) { mutableStateOf("") }
    var fullscreenImage by remember { mutableStateOf<String?>(null) }
    var actionMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var deletingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var confirmDeleteChat by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val palette = UltraTheme.colors
    val haptics = LocalHapticFeedback.current
    val dense = settings.density == ListDensity.COMPACT
    val serverChannel = linkState.activeChannel == LinkChannel.SERVER
    val serverMode = isLobby || serverChannel
    val connected = if (isLobby) linkState.serverOnline else
        remote != null && linkState.activeChannel != LinkChannel.NONE
    val canSend = if (isLobby) {
        linkState.serverOnline && draft.isNotBlank()
    } else {
        remote != null && draft.isNotBlank()
    }
    val headerTitle = if (isLobby) {
        "Общий чат"
    } else {
        remote?.displayName ?: peerTitle ?: "Собеседник"
    }
    val headerCreator = if (isLobby) false else (remote?.isCreator ?: peerIsCreator)

    fun submit() {
        val clean = draft.trim()
        val ready = if (isLobby) linkState.serverOnline else remote != null
        if (clean.isNotEmpty() && ready) {
            if (settings.haptics) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            onSend(clean)
            draft = ""
        }
    }

    BackHandler(enabled = fullscreenImage != null) {
        fullscreenImage = null
    }

    LaunchedEffect(messages.size, messages.lastOrNull()?.id, settings.animations) {
        if (messages.isNotEmpty()) {
            if (settings.animations) {
                listState.animateScrollToItem(messages.lastIndex)
            } else {
                listState.scrollToItem(messages.lastIndex)
            }
        }
    }

    val statusText = when {
        isLobby && linkState.serverOnline -> "Общий чат · на связи"
        isLobby && linkState.server.phase == TransportPhase.ERROR -> "Общий чат · нет связи"
        isLobby -> "Общий чат · подключение…"
        remote == null && peerTitle != null -> "История · нет соединения"
        remote == null -> "Подключение…"
        connected && serverChannel -> if (linkState.serverOnline) {
            "Сервер · подключено"
        } else {
            "Сервер · переподключение…"
        }
        connected -> "Bluetooth · подключено"
        linkState.phase == LinkPhase.RECOVERING -> "Восстановление связи…"
        linkState.phase == LinkPhase.ERROR -> "Нет связи"
        serverChannel -> "Сервер"
        settings.serverUrl.isNotBlank() -> "Сервер"
        else -> "Bluetooth"
    }
    val statusColor = when {
        isLobby && linkState.serverOnline -> TelegramBlue
        isLobby && linkState.server.phase == TransportPhase.ERROR -> Danger
        isLobby -> CreatorAmber
        remote == null -> TextTertiary
        connected && serverChannel && linkState.serverOnline -> TelegramBlue
        connected -> TelegramBlue
        linkState.phase == LinkPhase.RECOVERING -> CreatorAmber
        linkState.phase == LinkPhase.ERROR -> Danger
        else -> TextSecondary
    }

    Box(modifier = Modifier.fillMaxSize()) {
        WallpaperBackdrop(
            wallpaper = settings.wallpaper,
            dim = settings.wallpaperDim,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding(),
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Panel)
                .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UltraIconButton(
                glyph = Glyph.BACK,
                contentDescription = "Назад к чатам",
                onClick = onBack,
                size = 40.dp,
            )
            UltraAvatar(
                displayName = headerTitle,
                size = 40.dp,
                isCreator = headerCreator,
                avatarId = remote?.avatarId,
                photoBase64 = remote?.avatarPhoto,
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = headerTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (headerCreator) CreatorGold else TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isLobby) {
                        Spacer(Modifier.width(5.dp))
                        GlyphIcon(
                            glyph = Glyph.LOCK,
                            modifier = Modifier.size(11.dp),
                            tint = TextTertiary,
                            strokeWidth = 1.8.dp,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlyphIcon(
                        glyph = if (serverMode || settings.serverUrl.isNotBlank()) {
                            Glyph.SERVER
                        } else {
                            Glyph.BLUETOOTH
                        },
                        modifier = Modifier.size(10.dp),
                        tint = statusColor,
                        strokeWidth = 1.8.dp,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!isLobby) {
                UltraIconButton(
                    glyph = Glyph.TRASH,
                    contentDescription = "Удалить чат",
                    onClick = { confirmDeleteChat = true },
                    size = 40.dp,
                    tint = Danger,
                )
            }
            UltraIconButton(
                glyph = Glyph.SETTINGS,
                contentDescription = "Настройки",
                onClick = onOpenSettings,
                size = 40.dp,
                tint = TextSecondary,
            )
        }

        UltraDivider()

        if (settings.showLinkBanners) {
            if (linkState.error != null) {
                ChatBanner(
                    text = linkState.error,
                    accent = Danger,
                    glyph = Glyph.BOLT,
                    onDismiss = onDismissError,
                )
            } else if (isLobby && !linkState.serverOnline) {
                ChatBanner(
                    text = "Сервер переподключается. Сообщения общего чата отправятся, когда связь вернётся.",
                    accent = CreatorAmber,
                    glyph = Glyph.SERVER,
                )
            } else if (serverChannel && remote != null && !linkState.serverOnline) {
                ChatBanner(
                    text = "Сервер переподключается. Сообщения отправятся, когда связь вернётся.",
                    accent = CreatorAmber,
                    glyph = Glyph.SERVER,
                )
            } else if (linkState.phase == LinkPhase.RECOVERING) {
                ChatBanner(
                    text = linkState.lastSwitch
                        ?: "Связь потеряна. Пробуем восстановить Bluetooth…",
                    accent = CreatorAmber,
                    glyph = Glyph.BLUETOOTH,
                )
            } else if (remote == null && !isLobby) {
                ChatBanner(
                    text = "Подключаемся к собеседнику…",
                    accent = TelegramBlue,
                    glyph = if (settings.serverUrl.isNotBlank()) Glyph.SERVER else Glyph.BLUETOOTH,
                )
            } else if (linkState.queuedMessages > 0) {
                ChatBanner(
                    text = "Ждут отправки: ${linkState.queuedMessages}",
                    accent = CreatorAmber,
                    glyph = Glyph.BOLT,
                )
            }
        }

        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isLobby) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(TelegramBlue.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            GlyphIcon(
                                glyph = Glyph.CHAT,
                                modifier = Modifier.size(34.dp),
                                tint = TelegramBlue,
                                strokeWidth = 1.9.dp,
                            )
                        }
                    } else {
                        UltraAvatar(
                            displayName = remote?.displayName ?: profile.displayName,
                            size = 76.dp,
                            isCreator = remote?.isCreator == true,
                            avatarId = remote?.avatarId ?: profile.avatarId,
                            photoPath = if (remote == null) profile.avatarPhoto else null,
                            photoBase64 = remote?.avatarPhoto,
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = when {
                            isLobby -> "Общий чат"
                            remote == null -> "Устанавливаем связь"
                            connected -> "Начните общение"
                            else -> "Ждём восстановления"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = when {
                            isLobby -> "Сообщения видят все пользователи. " +
                                "Будьте вежливы и не делитесь личными данными."
                            serverChannel -> "Сообщения и фото идут через сервер. Собеседник может быть где угодно."
                            else -> "Сообщения идут напрямую по Bluetooth. Интернет не нужен."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    horizontal = 12.dp,
                    vertical = if (dense) 6.dp else 10.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(if (dense) 1.dp else 4.dp),
            ) {
                items(items = messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        settings = settings,
                        dense = dense,
                        isGroup = isLobby,
                        onPhotoClick = { fullscreenImage = it },
                        onLongPress = { actionMessage = message },
                    )
                }
            }
        }

        UltraDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Panel)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            val canAttach = remote != null || isLobby
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(PanelElevated)
                    .clickable(enabled = canAttach) { if (canAttach) onPickImage() },
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(
                    glyph = Glyph.WALLPAPER,
                    modifier = Modifier.size(19.dp),
                    tint = if (canAttach) TelegramBlue else TextTertiary,
                    strokeWidth = 1.9.dp,
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(PanelElevated)
                    .heightIn(min = 44.dp),
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it.take(WireProtocol.MAX_TEXT_LENGTH) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
                    cursorBrush = SolidColor(TelegramBlue),
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send,
                    ),
                    keyboardActions = KeyboardActions(onSend = { submit() }),
                    decorationBox = { innerTextField ->
                        Box {
                            if (draft.isEmpty()) {
                                Text(
                                    text = "Сообщение",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextTertiary,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (canSend) TelegramBlue else PanelSoft)
                    .clickable(enabled = canSend) { submit() },
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(
                    glyph = Glyph.SEND,
                    modifier = Modifier.size(19.dp),
                    tint = if (canSend) palette.onAccent else TextTertiary,
                    strokeWidth = 2.dp,
                )
            }
        }
        }

        fullscreenImage?.let { path ->
            FullscreenPhotoViewer(path = path, onClose = { fullscreenImage = null })
        }
    }

    actionMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { actionMessage = null },
            title = { Text("Сообщение") },
            text = {
                Column {
                    if (message.kind == ChatMessage.KIND_TEXT) {
                        MessageActionRow(
                            label = "Редактировать",
                            glyph = Glyph.TEXT,
                            onClick = {
                                actionMessage = null
                                editingMessage = message
                            },
                        )
                    }
                    MessageActionRow(
                        label = "Удалить",
                        glyph = Glyph.TRASH,
                        color = Danger,
                        onClick = {
                            actionMessage = null
                            deletingMessage = message
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { actionMessage = null }) { Text("Отмена") }
            },
        )
    }

    editingMessage?.let { message ->
        var editText by remember(message.id) { mutableStateOf(message.text) }
        AlertDialog(
            onDismissRequest = { editingMessage = null },
            title = { Text("Редактировать сообщение") },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it.take(WireProtocol.MAX_TEXT_LENGTH) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 6,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = editText.trim().isNotEmpty(),
                    onClick = {
                        val clean = editText.trim()
                        if (clean.isNotEmpty()) {
                            onEditMessage(message.id, clean)
                            editingMessage = null
                        }
                    },
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingMessage = null }) { Text("Отмена") }
            },
        )
    }

    deletingMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { deletingMessage = null },
            title = { Text("Удалить сообщение?") },
            text = { Text("Сообщение исчезнет у вас и у собеседника.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deletingMessage = null
                        onDeleteMessage(message.id)
                    },
                ) {
                    Text("Удалить", color = Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingMessage = null }) { Text("Отмена") }
            },
        )
    }

    if (confirmDeleteChat && !isLobby) {
        AlertDialog(
            onDismissRequest = { confirmDeleteChat = false },
            title = { Text("Удалить чат?") },
            text = { Text("Вся переписка удалится у вас и у собеседника.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteChat = false
                        onDeleteChat()
                    },
                ) {
                    Text("Удалить", color = Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteChat = false }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun MessageActionRow(
    label: String,
    glyph: Glyph,
    color: Color = TextPrimary,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(
            glyph = glyph,
            modifier = Modifier.size(18.dp),
            tint = color,
            strokeWidth = 1.8.dp,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = color,
        )
    }
}

@Composable
private fun FullscreenPhotoViewer(path: String, onClose: () -> Unit) {
    val bitmap by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(
        initialValue = null,
        path,
    ) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            ImageCodec.loadBitmap(path, 2400)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onClose() },
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            androidx.compose.foundation.Image(
                bitmap = image.asImageBitmap(),
                contentDescription = "Фото",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
            )
        } else {
            Text(
                text = "Загрузка…",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable { onClose() },
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(
                glyph = Glyph.CLOSE,
                modifier = Modifier.size(18.dp),
                tint = Color.White,
                strokeWidth = 2.dp,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessagePhoto(
    path: String,
    mine: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val bitmap by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(
        initialValue = null,
        path,
    ) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.ultrasys.ultrachat.data.ImageCodec.loadBitmap(path, 1000)
        }
    }
    val image = bitmap
    if (image != null) {
        val aspect = image.width.toFloat() / image.height.toFloat()
        val width: androidx.compose.ui.unit.Dp
        val height: androidx.compose.ui.unit.Dp
        if (aspect >= 1f) {
            width = 240.dp
            height = (240f / aspect).coerceAtLeast(120f).dp
        } else {
            height = 220.dp
            width = (220f * aspect).coerceAtLeast(120f).dp
        }
        androidx.compose.foundation.Image(
            bitmap = image.asImageBitmap(),
            contentDescription = "Фото",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(width)
                .height(height)
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        )
    } else {
        Box(
            modifier = Modifier
                .width(200.dp)
                .height(120.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(PanelSoft)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(
                glyph = Glyph.WALLPAPER,
                modifier = Modifier.size(24.dp),
                tint = TextTertiary,
            )
        }
    }
}

@Composable
private fun ChatBanner(
    text: String,
    accent: Color,
    glyph: Glyph,
    onDismiss: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.1f))
            .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(
            glyph = glyph,
            modifier = Modifier.size(14.dp),
            tint = accent,
            strokeWidth = 1.8.dp,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
        if (onDismiss != null) {
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(
                    glyph = Glyph.CLOSE,
                    modifier = Modifier.size(13.dp),
                    tint = TextSecondary,
                    strokeWidth = 1.7.dp,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessage,
    settings: AppSettings,
    dense: Boolean,
    isGroup: Boolean,
    onPhotoClick: (String) -> Unit,
    onLongPress: () -> Unit,
) {
    val palette = UltraTheme.colors
    val mine = message.isMine
    val showSender = isGroup && !mine
    val shape = bubbleShapeFor(settings.bubbleStyle, mine)
    val bubbleColor = if (mine) palette.bubbleMine else palette.bubbleOther
    val textColor = if (mine) palette.bubbleMineText else palette.bubbleOtherText
    val timeColor = if (mine) palette.bubbleMineTime else palette.bubbleOtherTime

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .background(bubbleColor)
                .then(
                    if (mine) {
                        Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = onLongPress,
                        )
                    } else {
                        Modifier
                    },
                )
                .padding(
                    horizontal = if (message.isImage && message.imagePath != null) 4.dp else 12.dp,
                    vertical = if (message.isImage && message.imagePath != null) {
                        4.dp
                    } else if (dense) {
                        5.dp
                    } else {
                        8.dp
                    },
                ),
        ) {
            Column {
                if (showSender) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (message.isCreator) {
                            GlyphIcon(
                                glyph = Glyph.CROWN,
                                modifier = Modifier.size(10.dp),
                                tint = CreatorGold,
                                strokeWidth = 1.6.dp,
                            )
                            Spacer(Modifier.width(3.dp))
                        }
                        Text(
                            text = message.sender,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (message.isCreator) CreatorGold else TelegramBlue,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                }
                if (message.isImage && message.imagePath != null) {
                    MessagePhoto(
                        path = message.imagePath,
                        mine = mine,
                        onClick = { onPhotoClick(message.imagePath) },
                        onLongClick = onLongPress,
                    )
                } else {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor,
                    )
                }
                if (settings.showTimestamps || message.isCreator || mine) {
                    Spacer(Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (message.isCreator) {
                            GlyphIcon(
                                glyph = Glyph.CROWN,
                                modifier = Modifier.size(9.dp),
                                tint = CreatorGold,
                                strokeWidth = 1.6.dp,
                            )
                            Spacer(Modifier.width(3.dp))
                        }
                        if (message.editedAt > 0L) {
                            Text(
                                text = "изм.",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = timeColor,
                            )
                            Spacer(Modifier.width(3.dp))
                        }
                        if (settings.showTimestamps) {
                            Text(
                                text = timeLabel(message.timestamp),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = timeColor,
                            )
                        }
                        if (mine && !isGroup) {
                            Spacer(Modifier.width(3.dp))
                            GlyphIcon(
                                glyph = if (message.isRead) Glyph.CHECKS else Glyph.CHECK,
                                modifier = Modifier.size(if (message.isRead) 16.dp else 12.dp),
                                tint = if (message.isRead) TelegramBlue else timeColor,
                                strokeWidth = 1.8.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun timeLabel(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
