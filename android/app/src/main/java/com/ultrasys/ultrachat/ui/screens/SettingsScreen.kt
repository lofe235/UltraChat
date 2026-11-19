package com.ultrasys.ultrachat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ultrasys.ultrachat.data.AccentTone
import com.ultrasys.ultrachat.data.AppSettings
import com.ultrasys.ultrachat.data.BubbleStyle
import com.ultrasys.ultrachat.data.ChatWallpaper
import com.ultrasys.ultrachat.data.FontSizeOption
import com.ultrasys.ultrachat.data.ListDensity
import com.ultrasys.ultrachat.data.ThemeVariant
import com.ultrasys.ultrachat.data.isSelfServerAddress
import com.ultrasys.ultrachat.net.LinkChannel
import com.ultrasys.ultrachat.net.LinkState
import com.ultrasys.ultrachat.net.NearbyPeer
import com.ultrasys.ultrachat.net.TransportPhase
import com.ultrasys.ultrachat.ui.components.GlassPanel
import com.ultrasys.ultrachat.ui.components.Glyph
import com.ultrasys.ultrachat.ui.components.GlyphIcon
import com.ultrasys.ultrachat.ui.components.MiniActionButton
import com.ultrasys.ultrachat.ui.components.PrimaryActionButton
import com.ultrasys.ultrachat.ui.components.SettingsActionRow
import com.ultrasys.ultrachat.ui.components.SettingsHeader
import com.ultrasys.ultrachat.ui.components.SettingsLabel
import com.ultrasys.ultrachat.ui.components.SettingsSection
import com.ultrasys.ultrachat.ui.components.SettingsSegmented
import com.ultrasys.ultrachat.ui.components.SettingsSwitchRow
import com.ultrasys.ultrachat.ui.components.StatusDot
import com.ultrasys.ultrachat.ui.components.UltraAvatar
import com.ultrasys.ultrachat.ui.components.UltraDivider
import com.ultrasys.ultrachat.util.NetworkPermissions
import com.ultrasys.ultrachat.ui.theme.UltraTheme
import com.ultrasys.ultrachat.ui.theme.WallpaperBackdrop
import com.ultrasys.ultrachat.ui.theme.bubbleShapeFor
import com.ultrasys.ultrachat.ui.theme.paletteFor
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: AppSettings,
    environment: NetworkPermissions.Snapshot,
    linkState: LinkState,
    onBack: () -> Unit,
    onChange: (AppSettings) -> Unit,
    onReset: () -> Unit,
    onClearHistory: () -> Unit,
    onDeleteAccount: () -> Unit,
    onScanToggle: () -> Unit,
    onConnect: (NearbyPeer) -> Unit,
    onDisconnect: () -> Unit,
    onOpenActiveChat: () -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
) {
    val palette = UltraTheme.colors
    val haptics = LocalHapticFeedback.current

    var showResetDialog by rememberSaveable { mutableStateOf(false) }
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    var serverUrl by rememberSaveable(settings.serverUrl) { mutableStateOf(settings.serverUrl) }
    var serverToken by rememberSaveable(settings.serverToken) {
        mutableStateOf(settings.serverToken)
    }
    var serverSaved by rememberSaveable { mutableStateOf(false) }

    fun apply(updated: AppSettings) {
        if (settings.haptics) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        onChange(updated)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        SettingsHeader(
            title = "Настройки",
            subtitle = "Оформление, чат и поведение",
            onBack = onBack,
            onReset = { showResetDialog = true },
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "preview") {
                SettingsPreview(settings)
            }

            item(key = "appearance") {
                SettingsSection(
                    title = "Оформление",
                    subtitle = "Тема и акцентный цвет",
                    icon = Glyph.PALETTE,
                ) {
                    SettingsLabel("Тема")
                    ThemePicker(
                        selected = settings.theme,
                        accent = settings.accent,
                        onSelect = { apply(settings.copy(theme = it)) },
                    )
                    SettingsLabel("Акцент")
                    AccentPicker(
                        selected = settings.accent,
                        onSelect = { apply(settings.copy(accent = it)) },
                    )
                }
            }

            item(key = "wallpaper") {
                SettingsSection(
                    title = "Обои чата",
                    subtitle = "Фон переписки и затемнение",
                    icon = Glyph.WALLPAPER,
                ) {
                    WallpaperPicker(
                        selected = settings.wallpaper,
                        dim = settings.wallpaperDim,
                        onSelect = { apply(settings.copy(wallpaper = it)) },
                        onDimChange = { onChange(settings.copy(wallpaperDim = it)) },
                    )
                }
            }

            item(key = "chat") {
                SettingsSection(
                    title = "Чат",
                    subtitle = "Текст, пузыри и плотность",
                    icon = Glyph.CHAT,
                ) {
                    SettingsLabel("Размер текста")
                    SettingsSegmented(
                        options = FontSizeOption.values().map { it.label },
                        selectedIndex = settings.fontSize.ordinal,
                        onSelect = {
                            apply(settings.copy(fontSize = FontSizeOption.values()[it]))
                        },
                    )
                    SettingsLabel("Форма пузырей")
                    SettingsSegmented(
                        options = BubbleStyle.values().map { it.label },
                        selectedIndex = settings.bubbleStyle.ordinal,
                        onSelect = {
                            apply(settings.copy(bubbleStyle = BubbleStyle.values()[it]))
                        },
                    )
                    SettingsLabel("Плотность списка")
                    SettingsSegmented(
                        options = ListDensity.values().map { it.label },
                        selectedIndex = settings.density.ordinal,
                        onSelect = {
                            apply(settings.copy(density = ListDensity.values()[it]))
                        },
                    )
                    Spacer(Modifier.height(4.dp))
                    SettingsSwitchRow(
                        glyph = Glyph.CLOCK,
                        title = "Время сообщений",
                        subtitle = "Показывать часы рядом с текстом",
                        checked = settings.showTimestamps,
                        onCheckedChange = { apply(settings.copy(showTimestamps = it)) },
                    )
                    SettingsSwitchRow(
                        glyph = Glyph.VIBRATE,
                        title = "Вибрация",
                        subtitle = "Отклик при отправке и выборе",
                        checked = settings.haptics,
                        onCheckedChange = { apply(settings.copy(haptics = it)) },
                    )
                    SettingsSwitchRow(
                        glyph = Glyph.SUN,
                        title = "Не гасить экран",
                        subtitle = "Держать дисплей включённым в чате",
                        checked = settings.keepScreenOn,
                        onCheckedChange = { apply(settings.copy(keepScreenOn = it)) },
                    )
                    SettingsSwitchRow(
                        glyph = Glyph.INFO,
                        title = "Плашки состояния",
                        subtitle = "Подсказки о связи и отправке",
                        checked = settings.showLinkBanners,
                        onCheckedChange = { apply(settings.copy(showLinkBanners = it)) },
                    )
                }
            }

            item(key = "bluetooth") {
                SettingsSection(
                    title = "Bluetooth",
                    subtitle = "Запасной канал без интернета",
                    icon = Glyph.BLUETOOTH,
                ) {
                    val bluetoothPeers = linkState.peers.filter {
                        it.channel == LinkChannel.BLUETOOTH
                    }
                    val activeBluetooth = linkState.remote != null &&
                        linkState.activeChannel == LinkChannel.BLUETOOTH
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(
                            color = when {
                                activeBluetooth -> palette.success
                                environment.bluetoothEnabled -> palette.accent
                                else -> palette.textTertiary
                            },
                            pulsing = linkState.scanning ||
                                linkState.phase == com.ultrasys.ultrachat.net.LinkPhase.CONNECTING,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when {
                                    activeBluetooth -> "Подключено по Bluetooth"
                                    linkState.scanning -> "Идёт поиск устройств"
                                    environment.bluetoothEnabled -> "Bluetooth включён"
                                    else -> "Bluetooth выключен"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.textPrimary,
                            )
                            Text(
                                text = "Основной канал — сервер. Bluetooth нужен только без интернета.",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textSecondary,
                            )
                        }
                    }

                    if (environment.missingPermissions.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Нет разрешений на поиск устройств рядом.",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.creatorGold,
                            )
                            MiniActionButton(
                                text = "Разрешить",
                                onClick = onRequestPermissions,
                                accent = palette.creatorGold,
                            )
                        }
                    } else if (!environment.bluetoothSupported) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "На этом устройстве Bluetooth Classic не поддерживается.",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.danger,
                        )
                    } else if (!environment.bluetoothEnabled) {
                        Spacer(Modifier.height(8.dp))
                        MiniActionButton(
                            text = "Включить Bluetooth",
                            onClick = onOpenBluetoothSettings,
                        )
                    } else if (!environment.locationEnabled) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Для поиска на этой прошивке нужна геолокация.",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textSecondary,
                            )
                            MiniActionButton(
                                text = "Настройки",
                                onClick = onOpenLocationSettings,
                            )
                        }
                    }

                    if (activeBluetooth) {
                        Spacer(Modifier.height(10.dp))
                        SettingsActionRow(
                            glyph = Glyph.CHAT,
                            title = linkState.remote?.displayName ?: "Bluetooth-собеседник",
                            subtitle = "Открыть переписку",
                            accent = palette.textPrimary,
                            onClick = onOpenActiveChat,
                        )
                        UltraDivider(Modifier.padding(vertical = 4.dp))
                        SettingsActionRow(
                            glyph = Glyph.CLOSE,
                            title = "Отключиться",
                            subtitle = "Закрыть Bluetooth-соединение",
                            accent = palette.danger,
                            onClick = onDisconnect,
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    PrimaryActionButton(
                        text = if (linkState.scanning) "Остановить поиск" else "Найти устройства рядом",
                        onClick = onScanToggle,
                        leadingGlyph = if (linkState.scanning) Glyph.CLOSE else Glyph.SEARCH,
                    )

                    if (linkState.scanning) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = palette.accent,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Поиск… найдено: ${bluetoothPeers.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textSecondary,
                            )
                        }
                    }

                    if (bluetoothPeers.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        bluetoothPeers.forEach { peer ->
                            SettingsActionRow(
                                glyph = Glyph.BLUETOOTH,
                                title = peer.name,
                                subtitle = peer.detail,
                                accent = palette.textPrimary,
                                onClick = { onConnect(peer) },
                            )
                        }
                    }

                    if (linkState.error != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = linkState.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.danger,
                        )
                    }
                }
            }

            item(key = "behavior") {
                SettingsSection(
                    title = "Поведение",
                    subtitle = "Связь и анимации",
                    icon = Glyph.BOLT,
                ) {
                    SettingsSwitchRow(
                        glyph = Glyph.SEARCH,
                        title = "Автопоиск Bluetooth",
                        subtitle = "Начинать поиск при открытии настроек Bluetooth",
                        checked = settings.autoScan,
                        onCheckedChange = { apply(settings.copy(autoScan = it)) },
                    )
                    SettingsSwitchRow(
                        glyph = Glyph.REFRESH,
                        title = "Автопереподключение",
                        subtitle = "Восстанавливать Bluetooth-связь",
                        checked = settings.autoReconnect,
                        onCheckedChange = { apply(settings.copy(autoReconnect = it)) },
                    )
                    SettingsSwitchRow(
                        glyph = Glyph.BOLT,
                        title = "Анимации",
                        subtitle = "Плавные переходы между экранами",
                        checked = settings.animations,
                        onCheckedChange = { apply(settings.copy(animations = it)) },
                    )
                }
            }

            item(key = "server") {
                SettingsSection(
                    title = "Сервер",
                    subtitle = "Основной канал связи",
                    icon = Glyph.SERVER,
                ) {
                    Row(
                        modifier = Modifier.padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusDot(
                            color = when (linkState.server.phase) {
                                TransportPhase.CONNECTED -> palette.success
                                TransportPhase.ERROR -> palette.danger
                                else -> palette.textTertiary
                            },
                            pulsing = linkState.server.phase == TransportPhase.CONNECTING,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = when (linkState.server.phase) {
                                TransportPhase.CONNECTED -> "Сервер на связи"
                                TransportPhase.ERROR -> linkState.server.detail
                                    ?: "Нет связи с сервером"
                                TransportPhase.CONNECTING -> "Подключение к серверу…"
                                TransportPhase.DISABLED -> "Сервер отключён"
                                else -> "Ожидание подключения"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary,
                        )
                    }
                    ServerFields(
                        serverUrl = serverUrl,
                        serverToken = serverToken,
                        saved = serverSaved,
                        onUrlChange = {
                            serverUrl = it.take(300)
                            serverSaved = false
                        },
                        onTokenChange = {
                            serverToken = it.take(300)
                            serverSaved = false
                        },
                        onSave = {
                            serverSaved = true
                            onChange(
                                settings.copy(
                                    serverUrl = serverUrl.trim(),
                                    serverToken = serverToken.trim(),
                                ),
                            )
                        },
                    )
                }
            }

            item(key = "data") {
                SettingsSection(
                    title = "Данные",
                    subtitle = "История и сброс оформления",
                    icon = Glyph.DATABASE,
                ) {
                    SettingsActionRow(
                        glyph = Glyph.REFRESH,
                        title = "Сбросить настройки",
                        subtitle = "Вернуть стандартную тему и поведение",
                        accent = palette.creatorGold,
                        onClick = { showResetDialog = true },
                    )
                    UltraDivider(Modifier.padding(vertical = 6.dp))
                    SettingsActionRow(
                        glyph = Glyph.TRASH,
                        title = "Очистить историю",
                        subtitle = "Удалить все сообщения на устройстве",
                        accent = palette.danger,
                        onClick = { showClearDialog = true },
                    )
                    UltraDivider(Modifier.padding(vertical = 6.dp))
                    SettingsActionRow(
                        glyph = Glyph.USER,
                        title = "Удалить аккаунт",
                        subtitle = "Профиль, история и настройки — заново",
                        accent = palette.danger,
                        onClick = { showDeleteDialog = true },
                    )
                }
            }

            item(key = "about") {
                SettingsSection(
                    title = "О приложении",
                    subtitle = "UltraChat 1.7.1",
                    icon = Glyph.INFO,
                ) {
                    Text(
                        text = "Серверный мессенджер: сообщения и фото идут через " +
                            "сервер из любой точки мира. Bluetooth остаётся запасным " +
                            "каналом, когда интернета нет.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlyphIcon(
                            glyph = Glyph.SHIELD,
                            modifier = Modifier.size(13.dp),
                            tint = palette.success,
                            strokeWidth = 1.6.dp,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Сервер · фото · история на устройстве",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textTertiary,
                        )
                    }
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            containerColor = palette.panelElevated,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "Сбросить настройки?",
                    style = MaterialTheme.typography.headlineMedium,
                    color = palette.textPrimary,
                )
            },
            text = {
                Text(
                    text = "Тема, обои и поведение вернутся к стандартным. " +
                        "Профиль и история сообщений останутся на месте.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        onReset()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = palette.accent),
                ) {
                    Text("Сбросить", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = palette.textSecondary),
                ) {
                    Text("Отмена")
                }
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = palette.panelElevated,
            shape = RoundedCornerShape(20.dp),
            icon = {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(palette.danger.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(
                        glyph = Glyph.USER,
                        modifier = Modifier.size(24.dp),
                        tint = palette.danger,
                        strokeWidth = 1.9.dp,
                    )
                }
            },
            title = {
                Text(
                    text = "Удалить аккаунт?",
                    style = MaterialTheme.typography.headlineMedium,
                    color = palette.textPrimary,
                    textAlign = TextAlign.Center,
                )
            },
            text = {
                Text(
                    text = "Профиль, история чатов и настройки будут удалены с устройства. " +
                        "Вы вернётесь к экрану создания аккаунта. Восстановить данные нельзя.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteAccount()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = palette.danger),
                ) {
                    Text("Удалить", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = palette.textSecondary),
                ) {
                    Text("Отмена")
                }
            },
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = palette.panelElevated,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "Очистить историю?",
                    style = MaterialTheme.typography.headlineMedium,
                    color = palette.textPrimary,
                )
            },
            text = {
                Text(
                    text = "Все сохранённые сообщения на этом устройстве будут удалены. " +
                        "Действие нельзя отменить.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        onClearHistory()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = palette.danger),
                ) {
                    Text("Удалить", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = palette.textSecondary),
                ) {
                    Text("Отмена")
                }
            },
        )
    }
}

@Composable
private fun SettingsPreview(settings: AppSettings) {
    val palette = UltraTheme.colors
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        padding = PaddingValues(0.dp),
        borderColor = palette.accent.copy(alpha = 0.32f),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(216.dp),
        ) {
            WallpaperBackdrop(
                wallpaper = settings.wallpaper,
                dim = settings.wallpaperDim,
                modifier = Modifier.fillMaxSize(),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 9.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UltraAvatar(displayName = "Нова", size = 30.dp)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Нова",
                            style = MaterialTheme.typography.labelLarge,
                            color = palette.textPrimary,
                        )
                        Text(
                            text = "Bluetooth · в сети",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.success,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    PreviewBubble(
                        text = "Привет! Как тебе оформление?",
                        time = "12:30",
                        mine = true,
                        settings = settings,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    PreviewBubble(
                        text = "Отлично, всё видно",
                        time = "12:31",
                        mine = false,
                        settings = settings,
                    )
                }
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(17.dp))
                            .background(palette.panelElevated)
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = "Сообщение",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textTertiary,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(palette.accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        GlyphIcon(
                            glyph = Glyph.SEND,
                            modifier = Modifier.size(15.dp),
                            tint = palette.onAccent,
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewBubble(
    text: String,
    time: String,
    mine: Boolean,
    settings: AppSettings,
) {
    val palette = UltraTheme.colors
    Box(
        modifier = Modifier
            .widthIn(max = 226.dp)
            .clip(bubbleShapeFor(settings.bubbleStyle, mine))
            .background(if (mine) palette.bubbleMine else palette.bubbleOther)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Column {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = if (mine) palette.bubbleMineText else palette.bubbleOtherText,
            )
            if (settings.showTimestamps) {
                Text(
                    text = time,
                    modifier = Modifier.align(Alignment.End),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = if (mine) palette.bubbleMineTime else palette.bubbleOtherTime,
                )
            }
        }
    }
}

@Composable
private fun ThemePicker(
    selected: ThemeVariant,
    accent: AccentTone,
    onSelect: (ThemeVariant) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeVariant.values().asList().chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { theme ->
                    ThemeCard(
                        theme = theme,
                        accent = accent,
                        selected = theme == selected,
                        onClick = { onSelect(theme) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ThemeCard(
    theme: ThemeVariant,
    accent: AccentTone,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val preview = paletteFor(theme, accent)
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(preview.panel)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) preview.accent else preview.line,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(17.dp)
                    .clip(CircleShape)
                    .background(preview.accent),
            )
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(17.dp)
                    .clip(CircleShape)
                    .background(preview.background),
            )
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(17.dp)
                    .clip(CircleShape)
                    .background(preview.panelElevated),
            )
            Spacer(Modifier.weight(1f))
            if (selected) {
                GlyphIcon(
                    glyph = Glyph.CHECK,
                    modifier = Modifier.size(13.dp),
                    tint = preview.accent,
                    strokeWidth = 2.2.dp,
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = theme.label,
            style = MaterialTheme.typography.labelLarge,
            color = preview.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = theme.description,
            style = MaterialTheme.typography.bodySmall,
            color = preview.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AccentPicker(
    selected: AccentTone,
    onSelect: (AccentTone) -> Unit,
) {
    val palette = UltraTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccentTone.values().forEach { tone ->
            val color = Color(tone.argb)
            val active = tone == selected
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (active) 3.dp else 1.dp,
                        color = if (active) palette.textPrimary else color.copy(alpha = 0.55f),
                        shape = CircleShape,
                    )
                    .clickable { onSelect(tone) },
                contentAlignment = Alignment.Center,
            ) {
                if (active) {
                    GlyphIcon(
                        glyph = Glyph.CHECK,
                        modifier = Modifier.size(13.dp),
                        tint = if (color.luminance() > 0.40f) Color(0xFF07131B) else Color.White,
                        strokeWidth = 2.2.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun WallpaperPicker(
    selected: ChatWallpaper,
    dim: Float,
    onSelect: (ChatWallpaper) -> Unit,
    onDimChange: (Float) -> Unit,
) {
    val palette = UltraTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChatWallpaper.values().asList().chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { wallpaper ->
                    WallpaperCard(
                        wallpaper = wallpaper,
                        dim = dim,
                        selected = wallpaper == selected,
                        onClick = { onSelect(wallpaper) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Затемнение",
                style = MaterialTheme.typography.labelMedium,
                color = palette.textSecondary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${(dim * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = palette.textTertiary,
            )
        }
        Slider(
            value = dim,
            onValueChange = onDimChange,
            valueRange = 0f..0.8f,
            colors = SliderDefaults.colors(
                thumbColor = palette.accent,
                activeTrackColor = palette.accent,
                inactiveTrackColor = palette.panelSoft,
            ),
        )
    }
}

@Composable
private fun WallpaperCard(
    wallpaper: ChatWallpaper,
    dim: Float,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = UltraTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(palette.panelElevated)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) palette.accent else palette.line,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(5.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            WallpaperBackdrop(
                wallpaper = wallpaper,
                dim = dim,
                modifier = Modifier.fillMaxSize(),
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(17.dp)
                        .clip(CircleShape)
                        .background(palette.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(
                        glyph = Glyph.CHECK,
                        modifier = Modifier.size(9.dp),
                        tint = palette.onAccent,
                        strokeWidth = 2.2.dp,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = wallpaper.label,
            style = MaterialTheme.typography.labelMedium,
            color = palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ServerFields(
    serverUrl: String,
    serverToken: String,
    saved: Boolean,
    onUrlChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    val palette = UltraTheme.colors
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = palette.accent,
        unfocusedBorderColor = palette.line,
        focusedContainerColor = palette.panelElevated,
        unfocusedContainerColor = palette.panelElevated,
        cursorColor = palette.accent,
        focusedTextColor = palette.textPrimary,
        unfocusedTextColor = palette.textPrimary,
        focusedLabelColor = palette.accent,
        unfocusedLabelColor = palette.textSecondary,
        focusedPlaceholderColor = palette.textTertiary,
        unfocusedPlaceholderColor = palette.textTertiary,
    )

    Column {
        Text(
            text = "В сборку уже вшит сервер-релей, поэтому всё работает сразу " +
                "после запуска. Адрес ниже нужен только если вы поднимаете " +
                "сервер на другом ПК. Сообщения и фото идут через интернет, " +
                "собеседники появляются в списке автоматически.",
            style = MaterialTheme.typography.bodySmall,
            color = palette.textTertiary,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = serverUrl,
            onValueChange = onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Адрес сервера") },
            placeholder = { Text(AppSettings.BUILT_IN_SERVER_URL) },
            leadingIcon = {
                GlyphIcon(
                    glyph = Glyph.SERVER,
                    modifier = Modifier.size(17.dp),
                    tint = palette.textSecondary,
                )
            },
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
            ),
        )
        if (isSelfServerAddress(serverUrl)) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Это адрес самого телефона — сервера там нет. Очистите " +
                    "поле, чтобы приложение взяло вшитый сервер.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.creatorGold,
            )
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = serverToken,
            onValueChange = onTokenChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Токен доступа") },
            placeholder = { Text("Секретный токен") },
            leadingIcon = {
                GlyphIcon(
                    glyph = Glyph.LOCK,
                    modifier = Modifier.size(17.dp),
                    tint = palette.textSecondary,
                )
            },
            visualTransformation = PasswordVisualTransformation(),
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
            ),
        )
        Spacer(Modifier.height(10.dp))
        PrimaryActionButton(
            text = if (saved) "Сохранено" else "Сохранить",
            onClick = onSave,
            leadingGlyph = Glyph.CHECK,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (saved) {
                "Параметры сохранены локально"
            } else {
                "Токен хранится только на этом устройстве"
            },
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = if (saved) palette.success else palette.textTertiary,
            textAlign = TextAlign.Center,
        )
    }
}
