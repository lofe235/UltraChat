package com.ultrasys.ultrachat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ultrasys.ultrachat.net.AdminBan
import com.ultrasys.ultrachat.net.AdminUser
import com.ultrasys.ultrachat.ui.AdminUiState
import com.ultrasys.ultrachat.ui.components.GlassPanel
import com.ultrasys.ultrachat.ui.components.Glyph
import com.ultrasys.ultrachat.ui.components.GlyphIcon
import com.ultrasys.ultrachat.ui.components.MiniActionButton
import com.ultrasys.ultrachat.ui.components.PrimaryActionButton
import com.ultrasys.ultrachat.ui.components.SettingsSection
import com.ultrasys.ultrachat.ui.components.StatusDot
import com.ultrasys.ultrachat.ui.components.UltraAvatar
import com.ultrasys.ultrachat.ui.components.UltraDivider
import com.ultrasys.ultrachat.ui.components.UltraIconButton
import com.ultrasys.ultrachat.ui.theme.CreatorAmber
import com.ultrasys.ultrachat.ui.theme.CreatorGold
import com.ultrasys.ultrachat.ui.theme.Danger
import com.ultrasys.ultrachat.ui.theme.Success
import com.ultrasys.ultrachat.ui.theme.TelegramBlue
import com.ultrasys.ultrachat.ui.theme.TextPrimary
import com.ultrasys.ultrachat.ui.theme.TextSecondary
import com.ultrasys.ultrachat.ui.theme.TextTertiary
import com.ultrasys.ultrachat.ui.theme.UltraTheme

/**
 * Скрытая админ-панель приложения.
 *
 * Повторяет возможности веб-админки: статистика, уведомления, очистка
 * общего чата, баны по нику/устройству/IP, разбан, кик и удаление аккаунтов.
 * Токен администратора хранится только в памяти ViewModel.
 */
@Composable
fun AdminScreen(
    state: AdminUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onClearLobby: () -> Unit,
    onBan: (String, String, String) -> Unit,
    onUnban: (String, String) -> Unit,
    onKick: (String) -> Unit,
    onDeleteUser: (String) -> Unit,
    onSendNotice: (String, String, String, String) -> Unit,
    onClearNotice: () -> Unit,
    onDismissError: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val palette = UltraTheme.colors
    val data = state.data
    val users = data?.users.orEmpty()
    val busy = state.busy

    var noticeTitle by rememberSaveable { mutableStateOf("") }
    var noticeText by rememberSaveable { mutableStateOf("") }
    var noticeVersion by rememberSaveable { mutableStateOf("") }
    var noticeUrl by rememberSaveable { mutableStateOf("") }

    var nicknameQuery by rememberSaveable { mutableStateOf("") }
    var deviceValue by rememberSaveable { mutableStateOf("") }
    var ipValue by rememberSaveable { mutableStateOf("") }
    var banReason by rememberSaveable { mutableStateOf("") }

    var nicknameMatches by remember { mutableStateOf<List<AdminUser>?>(null) }
    var userToBan by remember { mutableStateOf<AdminUser?>(null) }
    var userToDelete by remember { mutableStateOf<AdminUser?>(null) }
    var confirmClearLobby by remember { mutableStateOf(false) }

    fun findNicknameMatches(): List<AdminUser> {
        val query = nicknameQuery.trim()
        if (query.isEmpty()) return emptyList()
        val exact = users.filter {
            it.nickname.equals(query, ignoreCase = true) ||
                it.displayName.equals(query, ignoreCase = true)
        }
        if (exact.isNotEmpty()) return exact
        return users.filter {
            it.nickname.contains(query, ignoreCase = true) ||
                it.displayName.contains(query, ignoreCase = true)
        }
    }

    fun startNicknameBan() {
        val matches = findNicknameMatches()
        when {
            nicknameQuery.isBlank() -> onMessage("Введите ник пользователя")
            matches.isEmpty() -> onMessage("Пользователь с таким ником не найден")
            matches.size == 1 -> userToBan = matches.first()
            else -> nicknameMatches = matches
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.panel)
                .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UltraIconButton(
                glyph = Glyph.BACK,
                contentDescription = "Назад",
                onClick = onBack,
                size = 40.dp,
            )
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Админ-панель",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                )
                Text(
                    text = "Скрытый раздел · только для администратора",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            UltraIconButton(
                glyph = Glyph.REFRESH,
                contentDescription = "Обновить",
                onClick = onRefresh,
                size = 40.dp,
                tint = if (state.loading) TelegramBlue else TextSecondary,
            )
            UltraIconButton(
                glyph = Glyph.POWER,
                contentDescription = "Выйти из админ-панели",
                onClick = onLogout,
                size = 40.dp,
                tint = Danger,
            )
        }
        UltraDivider()

        if (state.loading && data == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = palette.accent, strokeWidth = 2.dp)
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            state.error?.let { error ->
                item(key = "admin-error") {
                    GlassPanel(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = Danger.copy(alpha = 0.45f),
                        padding = PaddingValues(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlyphIcon(
                                glyph = Glyph.BOLT,
                                modifier = Modifier.size(16.dp),
                                tint = Danger,
                                strokeWidth = 1.8.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = error,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                            )
                            Text(
                                text = "Скрыть",
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(onClick = onDismissError)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = TelegramBlue,
                            )
                        }
                    }
                }
            }

            item(key = "stats") {
                StatsPanel(state)
            }

            item(key = "quick-actions") {
                SettingsSection(
                    title = "Быстрые действия",
                    icon = Glyph.BOLT,
                    subtitle = "Очистка общего чата и уведомление для всех",
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MiniActionButton(
                            text = "Очистить общий чат",
                            onClick = { confirmClearLobby = true },
                            modifier = Modifier.weight(1f),
                            enabled = !busy,
                            accent = Danger,
                        )
                        if (data?.notice != null) {
                            MiniActionButton(
                                text = "Снять уведомление",
                                onClick = onClearNotice,
                                modifier = Modifier.weight(1f),
                                enabled = !busy,
                                accent = CreatorAmber,
                            )
                        }
                    }
                }
            }

            item(key = "notice") {
                SettingsSection(
                    title = "Уведомление",
                    icon = Glyph.INFO,
                    subtitle = "Показывается всем при подключении",
                ) {
                    data?.notice?.let { notice ->
                        GlassPanel(
                            modifier = Modifier.fillMaxWidth(),
                            borderColor = CreatorAmber.copy(alpha = 0.4f),
                            padding = PaddingValues(10.dp),
                        ) {
                            Text(
                                text = notice.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                            )
                            if (notice.text.isNotBlank()) {
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = notice.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                )
                            }
                            if (notice.version.isNotBlank() || notice.url.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = listOf(notice.version, notice.url)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextTertiary,
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    OutlinedTextField(
                        value = noticeTitle,
                        onValueChange = { noticeTitle = it.take(120) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Заголовок") },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = noticeText,
                        onValueChange = { noticeText = it.take(1000) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Текст") },
                        minLines = 2,
                        maxLines = 5,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = noticeVersion,
                            onValueChange = { noticeVersion = it.take(32) },
                            modifier = Modifier.weight(1f),
                            label = { Text("Версия") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = noticeUrl,
                            onValueChange = { noticeUrl = it.take(500) },
                            modifier = Modifier.weight(2f),
                            label = { Text("Ссылка") },
                            singleLine = true,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    PrimaryActionButton(
                        text = "Отправить уведомление",
                        onClick = {
                            onSendNotice(noticeTitle, noticeText, noticeVersion, noticeUrl)
                            noticeTitle = ""
                            noticeText = ""
                            noticeVersion = ""
                            noticeUrl = ""
                        },
                        enabled = !busy &&
                            (noticeTitle.isNotBlank() || noticeText.isNotBlank()),
                        loading = busy,
                        leadingGlyph = Glyph.SEND,
                    )
                }
            }

            item(key = "ban-form") {
                SettingsSection(
                    title = "Забанить",
                    icon = Glyph.SHIELD,
                    subtitle = "По нику, ID устройства или IP-адресу",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = nicknameQuery,
                            onValueChange = { nicknameQuery = it.take(48) },
                            modifier = Modifier.weight(1f),
                            label = { Text("Ник") },
                            singleLine = true,
                        )
                        MiniActionButton(
                            text = "Найти и забанить",
                            onClick = { startNicknameBan() },
                            modifier = Modifier.padding(top = 8.dp),
                            enabled = !busy,
                            accent = Danger,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = deviceValue,
                        onValueChange = { deviceValue = it.take(160) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("ID устройства") },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = ipValue,
                        onValueChange = { ipValue = it.take(64) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("IP-адрес") },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (isLoopbackIp(ipValue)) {
                            "Это адрес прокси хостинга — бан заблокирует всех. " +
                                "Используйте ID устройства."
                        } else {
                            "На хостинге реальные IP скрыты (все видны как ::1). " +
                                "Банить по IP нельзя — используйте ID устройства."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isLoopbackIp(ipValue)) Danger else TextTertiary,
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = banReason,
                        onValueChange = { banReason = it.take(200) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Причина (необязательно)") },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiniActionButton(
                            text = "Бан устройства",
                            onClick = {
                                onBan("device", deviceValue, banReason)
                                deviceValue = ""
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !busy && deviceValue.isNotBlank(),
                            accent = Danger,
                        )
                        MiniActionButton(
                            text = if (isLoopbackIp(ipValue)) "IP скрыт" else "Бан IP",
                            onClick = {
                                onBan("ip", ipValue, banReason)
                                ipValue = ""
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !busy && ipValue.isNotBlank() && !isLoopbackIp(ipValue),
                            accent = Danger,
                        )
                    }
                }
            }

            item(key = "bans") {
                val bans = data?.bans
                SettingsSection(
                    title = "Баны",
                    icon = Glyph.LOCK,
                    subtitle = "Устройства: ${bans?.devices?.size ?: 0} · " +
                        "IP: ${bans?.ips?.size ?: 0}",
                ) {
                    if (bans == null || bans.total == 0) {
                        Text(
                            text = "Активных банов нет.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary,
                        )
                    } else {
                        bans.devices.forEach { ban ->
                            BanRow(
                                ban = ban,
                                kindLabel = "Устройство",
                                accent = Danger,
                                enabled = !busy,
                                onUnban = { onUnban("device", ban.value) },
                            )
                        }
                        bans.ips.forEach { ban ->
                            BanRow(
                                ban = ban,
                                kindLabel = "IP",
                                accent = CreatorAmber,
                                enabled = !busy,
                                onUnban = { onUnban("ip", ban.value) },
                            )
                        }
                    }
                }
            }

            item(key = "users-header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Пользователи",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = users.size.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                    )
                }
            }

            if (users.isEmpty()) {
                item(key = "users-empty") {
                    Text(
                        text = "Список пользователей пуст.",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                    )
                }
            } else {
                items(items = users, key = { it.id }) { user ->
                    UserRow(
                        user = user,
                        enabled = !busy,
                        onKick = { onKick(user.id) },
                        onBan = { userToBan = user },
                        onDelete = { userToDelete = user },
                    )
                }
            }

            item(key = "footer") {
                Text(
                    text = "Осторожно: удаление аккаунта необратимо. " +
                        "Пользователь сможет зарегистрироваться заново.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                )
            }
        }
    }

    nicknameMatches?.let { matches ->
        AlertDialog(
            onDismissRequest = { nicknameMatches = null },
            title = { Text("Кого забанить?") },
            text = {
                Column {
                    Text(
                        text = "Найдено несколько пользователей. Выберите нужного:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    matches.take(12).forEach { user ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    nicknameMatches = null
                                    userToBan = user
                                }
                                .padding(horizontal = 8.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            UltraAvatar(
                                displayName = user.displayName,
                                size = 34.dp,
                                isCreator = user.isCreator,
                            )
                            Spacer(Modifier.width(9.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = user.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (user.isCreator) CreatorGold else TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "@${user.nickname}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (user.deviceBanned) {
                                Text(
                                    text = "забанен",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Danger,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { nicknameMatches = null }) { Text("Отмена") }
            },
        )
    }

    userToBan?.let { user ->
        AlertDialog(
            onDismissRequest = { userToBan = null },
            title = { Text("Забанить устройство?") },
            text = {
                Text(
                    "Пользователь ${user.displayName} (@${user.nickname}) больше не сможет " +
                        "подключаться с устройства ${user.deviceId.ifBlank { "неизвестно" }}.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        userToBan = null
                        onBan(
                            "device",
                            user.deviceId,
                            banReason.ifBlank { "Бан по нику: @${user.nickname}" },
                        )
                        nicknameQuery = ""
                    },
                ) {
                    Text("Забанить", color = Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { userToBan = null }) { Text("Отмена") }
            },
        )
    }

    userToDelete?.let { user ->
        AlertDialog(
            onDismissRequest = { userToDelete = null },
            title = { Text("Удалить аккаунт?") },
            text = {
                Text(
                    "Аккаунт ${user.displayName} (@${user.nickname}) и все его сообщения " +
                        "будут удалены. Пользователя отключит от сервера, после чего он " +
                        "сможет зарегистрироваться заново. Это действие нельзя отменить.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        userToDelete = null
                        onDeleteUser(user.id)
                    },
                ) {
                    Text("Удалить аккаунт", color = Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { userToDelete = null }) { Text("Отмена") }
            },
        )
    }

    if (confirmClearLobby) {
        AlertDialog(
            onDismissRequest = { confirmClearLobby = false },
            title = { Text("Очистить общий чат?") },
            text = {
                Text("Все сообщения общего чата удалятся у всех пользователей. Это действие нельзя отменить.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearLobby = false
                        onClearLobby()
                    },
                ) {
                    Text("Очистить", color = Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearLobby = false }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun StatsPanel(state: AdminUiState) {
    val data = state.data
    val palette = UltraTheme.colors
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        borderColor = palette.accent.copy(alpha = 0.4f),
        padding = PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(palette.accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(
                    glyph = Glyph.RADAR,
                    modifier = Modifier.size(17.dp),
                    tint = palette.accent,
                    strokeWidth = 1.9.dp,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Сервер UltraChat",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                )
                Text(
                    text = buildString {
                        append("v")
                        append(data?.server?.version?.takeIf { it.isNotBlank() } ?: "—")
                        data?.server?.uptime?.takeIf { it > 0L }?.let {
                            append(" · аптайм ")
                            append(formatUptime(it))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            if (state.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = palette.accent,
                    strokeWidth = 2.dp,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCell("Пользователи", data?.stats?.users ?: 0, Modifier.weight(1f))
            StatCell("Онлайн", data?.stats?.online ?: 0, Modifier.weight(1f), Success)
            StatCell("Сообщения", data?.stats?.messages ?: 0, Modifier.weight(1f))
            StatCell(
                "В лобби",
                data?.stats?.lobbyMessages ?: 0,
                Modifier.weight(1f),
                CreatorAmber,
            )
        }
    }
}

@Composable
private fun StatCell(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
    accent: Color = TextPrimary,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(UltraTheme.colors.panelSoft.copy(alpha = 0.55f))
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleLarge,
            color = accent,
            maxLines = 1,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BanRow(
    ban: AdminBan,
    kindLabel: String,
    accent: Color,
    enabled: Boolean,
    onUnban: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$kindLabel · ${ban.value}",
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (ban.reason.isNotBlank()) {
                Text(
                    text = ban.reason,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        MiniActionButton(
            text = "Снять",
            onClick = onUnban,
            enabled = enabled,
            accent = accent,
        )
    }
}

@Composable
private fun UserRow(
    user: AdminUser,
    enabled: Boolean,
    onKick: () -> Unit,
    onBan: () -> Unit,
    onDelete: () -> Unit,
) {
    val palette = UltraTheme.colors
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (user.online) {
            Success.copy(alpha = 0.35f)
        } else {
            palette.line.copy(alpha = 0.65f)
        },
        padding = PaddingValues(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UltraAvatar(
                displayName = user.displayName,
                size = 40.dp,
                isCreator = user.isCreator,
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = user.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (user.isCreator) CreatorGold else TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (user.online) {
                        Spacer(Modifier.width(6.dp))
                        StatusDot(color = Success)
                    }
                }
                Text(
                    text = "@${user.nickname} · ${user.id}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Устройство: ${user.deviceId.ifBlank { "—" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val ipLabel = when {
                    user.lastIp.isBlank() -> null
                    isLoopbackIp(user.lastIp) -> "IP скрыт хостингом"
                    else -> "IP ${user.lastIp}"
                }
                if (ipLabel != null || user.model.isNotBlank()) {
                    Text(
                        text = listOfNotNull(
                            ipLabel,
                            user.model.takeIf { it.isNotBlank() },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (user.deviceBanned || user.ipBanned) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = when {
                            user.deviceBanned && user.ipBanned -> "Устройство и IP забанены"
                            user.deviceBanned -> "Устройство забанено"
                            else -> "IP забанен"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Danger,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MiniActionButton(
                text = "Кик",
                onClick = onKick,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                accent = CreatorAmber,
            )
            MiniActionButton(
                text = "Бан",
                onClick = onBan,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                accent = Danger,
            )
            MiniActionButton(
                text = "Удалить",
                onClick = onDelete,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                accent = Danger,
            )
        }
    }
}

/** Loopback принадлежит прокси хостинга, а не пользователю. */
private fun isLoopbackIp(value: String): Boolean {
    val ip = value.trim().lowercase()
    if (ip.isEmpty()) return false
    if (ip == "::1" || ip == "::" || ip == "0.0.0.0") return true
    if (ip.startsWith("127.")) return true
    if (ip.startsWith("::ffff:")) {
        val mapped = ip.removePrefix("::ffff:")
        return mapped.startsWith("127.") || mapped == "0.0.0.0"
    }
    return false
}

private fun formatUptime(seconds: Long): String {
    val days = seconds / 86_400
    val hours = (seconds % 86_400) / 3_600
    val minutes = (seconds % 3_600) / 60
    return when {
        days > 0 -> "${days} д ${hours} ч"
        hours > 0 -> "${hours} ч ${minutes} мин"
        else -> "${minutes} мин"
    }
}
