package com.ultrasys.ultrachat.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ultrasys.ultrachat.data.AppSettings
import com.ultrasys.ultrachat.data.ChatMessage
import com.ultrasys.ultrachat.data.ChatThread
import com.ultrasys.ultrachat.data.ListDensity
import com.ultrasys.ultrachat.data.UserProfile
import com.ultrasys.ultrachat.net.LinkState
import com.ultrasys.ultrachat.net.ServerNotice
import com.ultrasys.ultrachat.net.TransportPhase
import com.ultrasys.ultrachat.ui.UserSearchState
import com.ultrasys.ultrachat.ui.components.GlassPanel
import com.ultrasys.ultrachat.ui.components.Glyph
import com.ultrasys.ultrachat.ui.components.GlyphIcon
import com.ultrasys.ultrachat.ui.components.PrimaryActionButton
import com.ultrasys.ultrachat.ui.components.StatusDot
import com.ultrasys.ultrachat.ui.components.UltraAvatar
import com.ultrasys.ultrachat.ui.components.UltraIconButton
import com.ultrasys.ultrachat.ui.theme.CreatorAmber
import com.ultrasys.ultrachat.ui.theme.CreatorGold
import com.ultrasys.ultrachat.ui.theme.Danger
import com.ultrasys.ultrachat.ui.theme.Panel
import com.ultrasys.ultrachat.ui.theme.Success
import com.ultrasys.ultrachat.ui.theme.TelegramBlue
import com.ultrasys.ultrachat.ui.theme.TextPrimary
import com.ultrasys.ultrachat.ui.theme.TextSecondary
import com.ultrasys.ultrachat.ui.theme.TextTertiary

/**
 * Главный экран серверного мессенджера: только чаты и кнопка «+».
 * Bluetooth сюда больше не выносится — он живёт в настройках как запасной канал.
 */
@Composable
fun DiscoverScreen(
    profile: UserProfile,
    settings: AppSettings,
    linkState: LinkState,
    threads: List<ChatThread>,
    userSearch: UserSearchState,
    notice: ServerNotice?,
    onDismissNotice: () -> Unit,
    onOpenThread: (String) -> Unit,
    onDeleteThread: (String) -> Unit,
    onSearchUsers: (String) -> Unit,
    onAddContact: (com.ultrasys.ultrachat.net.ServerUser) -> Unit,
    onClearSearch: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAdmin: () -> Unit,
) {
    val dense = settings.density == ListDensity.COMPACT
    var showAddContact by rememberSaveable { mutableStateOf(false) }
    var threadToDelete by remember { mutableStateOf<ChatThread?>(null) }
    var adminTapCount by remember { mutableStateOf(0) }
    var lastAdminTapAt by remember { mutableStateOf(0L) }

    // Скрытый вход в админ-панель: пять быстрых нажатий по заголовку.
    fun registerAdminTap() {
        val now = System.currentTimeMillis()
        if (now - lastAdminTapAt > 2500L) adminTapCount = 0
        adminTapCount += 1
        lastAdminTapAt = now
        if (adminTapCount >= 5) {
            adminTapCount = 0
            onOpenAdmin()
        }
    }

    val directThreads = threads.filter { it.peerId != ChatMessage.LOBBY_ID }
    val lobbyThread = threads.firstOrNull { it.peerId == ChatMessage.LOBBY_ID }

    val serverPhase = linkState.server.phase
    val serverOnline = serverPhase == TransportPhase.CONNECTED
    val statusLabel = when {
        settings.serverUrl.isBlank() -> "Сервер не настроен"
        serverOnline -> "Сервер · на связи"
        serverPhase == TransportPhase.ERROR -> "Сервер · нет связи"
        serverPhase == TransportPhase.CONNECTING -> "Подключение к серверу…"
        else -> linkState.server.detail?.takeIf { it.isNotBlank() } ?: "Сервер · подключение…"
    }
    val statusColor = when {
        serverOnline -> Success
        serverPhase == TransportPhase.ERROR -> Danger
        else -> TextTertiary
    }

    fun openAddContact() {
        onClearSearch()
        showAddContact = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.clickable(onClick = onOpenProfile)) {
                UltraAvatar(
                    displayName = profile.displayName,
                    size = 44.dp,
                    isCreator = profile.isCreator,
                    avatarId = profile.avatarId,
                    photoPath = profile.avatarPhoto,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "UltraChat",
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { registerAdminTap() }
                        .padding(vertical = 1.dp, horizontal = 2.dp),
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(
                        color = statusColor,
                        pulsing = serverPhase == TransportPhase.CONNECTING,
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (serverOnline) Success else TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            UltraIconButton(
                glyph = Glyph.PLUS,
                contentDescription = "Добавить собеседника",
                onClick = { openAddContact() },
                tint = TelegramBlue,
            )
            Spacer(Modifier.width(4.dp))
            UltraIconButton(
                glyph = Glyph.SETTINGS,
                contentDescription = "Настройки",
                onClick = onOpenSettings,
                tint = TextSecondary,
            )
        }

        if (settings.serverUrl.isBlank()) {
            GlassPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                borderColor = Danger.copy(alpha = 0.35f),
                padding = PaddingValues(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlyphIcon(
                        glyph = Glyph.SERVER,
                        modifier = Modifier.size(18.dp),
                        tint = Danger,
                        strokeWidth = 1.8.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Укажите адрес сервера в настройках — без него чаты не работают.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
            }
        }

        notice?.let { currentNotice ->
            GlassPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                borderColor = CreatorAmber.copy(alpha = 0.45f),
                padding = PaddingValues(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(CreatorAmber.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        GlyphIcon(
                            glyph = Glyph.INFO,
                            modifier = Modifier.size(15.dp),
                            tint = CreatorAmber,
                            strokeWidth = 1.9.dp,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentNotice.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                        )
                        if (currentNotice.text.isNotBlank()) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = currentNotice.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                        }
                        if (currentNotice.version.isNotBlank()) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = "Версия ${currentNotice.version}",
                                style = MaterialTheme.typography.labelSmall,
                                color = CreatorAmber,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onDismissNotice),
                        contentAlignment = Alignment.Center,
                    ) {
                        GlyphIcon(
                            glyph = Glyph.CLOSE,
                            modifier = Modifier.size(14.dp),
                            tint = TextSecondary,
                            strokeWidth = 1.8.dp,
                        )
                    }
                }
            }
        }

        // Общий чат закреплён сверху: он есть у всех и не удаляется.
        LobbyRow(
            thread = lobbyThread,
            dense = dense,
            online = serverOnline,
            onClick = { onOpenThread(ChatMessage.LOBBY_ID) },
        )

        if (directThreads.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(TelegramBlue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(
                        glyph = Glyph.SERVER,
                        modifier = Modifier.size(32.dp),
                        tint = TelegramBlue,
                        strokeWidth = 1.9.dp,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Пока нет личных чатов",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Общий чат уже доступен выше. Нажмите «+», чтобы найти " +
                        "человека по нику и написать лично.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))
                PrimaryActionButton(
                    text = "Добавить собеседника",
                    onClick = { openAddContact() },
                    leadingGlyph = Glyph.PLUS,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(if (dense) 4.dp else 7.dp),
            ) {
                item(key = "chats-header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Личные чаты",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextSecondary,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${directThreads.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary,
                        )
                    }
                }
                items(items = directThreads, key = { it.peerId }) { thread ->
                    val peer = linkState.peers.firstOrNull { it.id == thread.peerId }
                    ChatThreadRow(
                        thread = thread,
                        dense = dense,
                        online = peer?.isOnline == true,
                        avatarId = peer?.avatarId,
                        isCreator = thread.isCreator || peer?.isCreator == true,
                        onClick = { onOpenThread(thread.peerId) },
                        onLongClick = { threadToDelete = thread },
                    )
                }
                item(key = "footer") {
                    Text(
                        text = "Сервер: ${serverHost(settings.serverUrl)} · долгое нажатие на чат — удалить",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }

    if (showAddContact) {
        AddContactDialog(
            state = userSearch,
            serverOnline = serverOnline,
            onQueryChange = onSearchUsers,
            onAdd = { user ->
                showAddContact = false
                onAddContact(user)
            },
            onDismiss = {
                showAddContact = false
                onClearSearch()
            },
        )
    }

    threadToDelete?.let { thread ->
        AlertDialog(
            onDismissRequest = { threadToDelete = null },
            title = { Text("Удалить чат?") },
            text = {
                Text("Переписка с ${thread.title} удалится у вас и у собеседника.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        threadToDelete = null
                        onDeleteThread(thread.peerId)
                    },
                ) {
                    Text("Удалить", color = Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { threadToDelete = null }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun LobbyRow(
    thread: ChatThread?,
    dense: Boolean,
    online: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = if (dense) 3.dp else 5.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Panel.copy(alpha = 0.85f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = if (dense) 7.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(TelegramBlue.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(
                    glyph = Glyph.CHAT,
                    modifier = Modifier.size(23.dp),
                    tint = TelegramBlue,
                    strokeWidth = 1.9.dp,
                )
            }
            if (online) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(Panel)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(Success),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Общий чат",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(6.dp))
                GlyphIcon(
                    glyph = Glyph.LOCK,
                    modifier = Modifier.size(10.dp),
                    tint = TextTertiary,
                    strokeWidth = 1.8.dp,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = when {
                    thread == null -> "Все пользователи · вход по PIN"
                    thread.lastText.isBlank() -> "Все пользователи"
                    thread.lastIsMine -> "Вы: ${thread.lastText}"
                    else -> thread.lastText
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = threadTime(thread?.lastTimestamp ?: 0L),
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
            )
            if ((thread?.count ?: 0) > 1) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = thread?.count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = TelegramBlue,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatThreadRow(
    thread: ChatThread,
    dense: Boolean,
    online: Boolean,
    avatarId: String?,
    isCreator: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (dense) Panel.copy(alpha = 0.55f) else Panel.copy(alpha = 0.75f))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 10.dp, vertical = if (dense) 7.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            UltraAvatar(
                displayName = thread.title,
                size = 48.dp,
                isCreator = isCreator,
                avatarId = avatarId,
            )
            if (online) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(Panel)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(Success),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = thread.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCreator) CreatorGold else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (thread.lastIsMine) "Вы: ${thread.lastText}" else thread.lastText,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = threadTime(thread.lastTimestamp),
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
            )
            if (thread.count > 1) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = thread.count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                )
            }
        }
    }
}

private fun threadTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val now = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    val sameDay = now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) &&
        now.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR)
    val pattern = if (sameDay) "HH:mm" else "dd.MM"
    return java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
        .format(java.util.Date(timestamp))
}

private fun serverHost(url: String): String = url
    .substringAfter("://", url)
    .substringBefore('/')
    .ifBlank { "не указан" }
