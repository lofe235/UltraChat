package com.ultrasys.ultrachat.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ultrasys.ultrachat.data.ChatMessage
import com.ultrasys.ultrachat.data.UserProfile
import com.ultrasys.ultrachat.net.TransportPhase
import com.ultrasys.ultrachat.ui.components.GlassPanel
import com.ultrasys.ultrachat.ui.components.UltraBackground
import com.ultrasys.ultrachat.ui.screens.AdminScreen
import com.ultrasys.ultrachat.ui.screens.ChatScreen
import com.ultrasys.ultrachat.ui.screens.DiscoverScreen
import com.ultrasys.ultrachat.ui.screens.OnboardingScreen
import com.ultrasys.ultrachat.ui.screens.SettingsScreen
import com.ultrasys.ultrachat.ui.screens.SignUpScreen
import com.ultrasys.ultrachat.ui.theme.Danger
import com.ultrasys.ultrachat.ui.theme.NeonCyan
import com.ultrasys.ultrachat.ui.theme.PanelElevated
import com.ultrasys.ultrachat.ui.theme.SystemBarsAppearance
import com.ultrasys.ultrachat.ui.theme.TextPrimary
import com.ultrasys.ultrachat.ui.theme.TextSecondary
import com.ultrasys.ultrachat.ui.theme.UltraChatTheme
import com.ultrasys.ultrachat.ui.theme.UltraTheme
import com.ultrasys.ultrachat.util.MessageNotifier
import com.ultrasys.ultrachat.util.NetworkPermissions
import kotlinx.coroutines.launch

@Composable
fun UltraChatRoot(viewModel: UltraChatViewModel) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    val profileCanGoBack by viewModel.profileCanGoBack.collectAsStateWithLifecycle()
    val environment by viewModel.environment.collectAsStateWithLifecycle()
    val linkState by viewModel.linkState.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val threads by viewModel.threads.collectAsStateWithLifecycle()
    val activeThreadId by viewModel.activeThreadId.collectAsStateWithLifecycle()
    val userSearch by viewModel.userSearch.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val lobbyPinVisible by viewModel.lobbyPinVisible.collectAsStateWithLifecycle()
    val admin by viewModel.admin.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    fun notify(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.refreshEnvironment()
        viewModel.onPermissionResult(viewModel.environment.value.runtimeGranted)
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) viewModel.sendImage(uri)
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            notify("Уведомления выключены — включите их в настройках системы")
        }
    }

    var notificationAsked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(profile) {
        if (profile != null &&
            !notificationAsked &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !MessageNotifier.hasPermission(context)
        ) {
            notificationAsked = true
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun pickPhoto() {
        photoPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    fun requestPermissions() {
        viewModel.markScanPendingAfterPermission()
        permissionLauncher.launch(NetworkPermissions.requiredRuntimePermissions())
    }

    fun toggleScan() {
        when {
            linkState.scanning -> viewModel.stopScanning()
            !environment.runtimeGranted -> requestPermissions()
            else -> viewModel.startScanning()
        }
    }

    fun openSystemSettings(action: String) {
        runCatching {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            notify("Не удалось открыть системные настройки")
        }
    }

    LaunchedEffect(Unit) {
        viewModel.toasts.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    var settingsAutoScanDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(screen, environment.runtimeGranted, settings.autoScan) {
        if (screen == AppScreen.SETTINGS &&
            environment.runtimeGranted &&
            settings.autoScan &&
            !settingsAutoScanDone
        ) {
            settingsAutoScanDone = true
            viewModel.startScanning()
        }
        if (screen != AppScreen.SETTINGS) settingsAutoScanDone = false
    }

    BackHandler(
        enabled = screen == AppScreen.CHAT ||
            screen == AppScreen.SETTINGS ||
            screen == AppScreen.ADMIN ||
            (screen == AppScreen.ONBOARDING && profileCanGoBack),
    ) {
        when (screen) {
            AppScreen.CHAT -> viewModel.openDiscover()
            AppScreen.SETTINGS -> viewModel.closeSettings()
            AppScreen.ADMIN -> viewModel.closeAdminPanel()
            AppScreen.ONBOARDING -> if (profileCanGoBack) viewModel.backFromProfile()
            else -> Unit
        }
    }

    val view = LocalView.current
    UltraChatTheme(settings) {
        val palette = UltraTheme.colors
        SystemBarsAppearance(lightBackground = palette.isLight)
        val navBarBottom = WindowInsets.navigationBars
            .asPaddingValues()
            .calculateBottomPadding()

        SideEffect {
            view.keepScreenOn = settings.keepScreenOn && screen == AppScreen.CHAT
        }

        Box(modifier = Modifier.fillMaxSize()) {
            UltraBackground()

            Crossfade(
                targetState = screen,
                animationSpec = tween(
                    durationMillis = if (settings.animations) 240 else 0,
                ),
                label = "ultrachat-screen",
            ) { target ->
                when (target) {
                    AppScreen.ONBOARDING -> {
                        val currentProfile = profile
                        if (currentProfile == null) {
                            SignUpScreen(
                                onOpenSettings = viewModel::openSettings,
                                onComplete = { nickname, avatarId, photoPath ->
                                    viewModel.completeSignUp(nickname, avatarId, photoPath)
                                },
                            )
                        } else {
                            OnboardingScreen(
                                profile = currentProfile,
                                canGoBack = profileCanGoBack,
                                onBack = viewModel::backFromProfile,
                                onOpenSettings = viewModel::openSettings,
                                onSave = viewModel::saveProfile,
                                onUnlockCreator = { code, nickname, avatarId ->
                                    viewModel.unlockCreator(code, nickname, avatarId)
                                },
                            )
                        }
                    }

                    AppScreen.DISCOVER -> {
                        val currentProfile = profile
                        if (currentProfile == null) {
                            SignUpScreen(
                                onOpenSettings = viewModel::openSettings,
                                onComplete = { nickname, avatarId, photoPath ->
                                    viewModel.completeSignUp(nickname, avatarId, photoPath)
                                },
                            )
                        } else {
                            DiscoverScreen(
                                profile = currentProfile,
                                settings = settings,
                                linkState = linkState,
                                threads = threads,
                                userSearch = userSearch,
                                notice = notice,
                                onDismissNotice = viewModel::dismissNotice,
                                onOpenThread = viewModel::openThread,
                                onDeleteThread = viewModel::deleteChat,
                                onSearchUsers = viewModel::searchUsers,
                                onAddContact = viewModel::addContact,
                                onClearSearch = viewModel::clearUserSearch,
                                onOpenProfile = viewModel::openProfile,
                                onOpenSettings = viewModel::openSettings,
                                onOpenAdmin = viewModel::openAdminPanel,
                            )
                        }
                    }

                    AppScreen.CHAT -> {
                        val liveRemote = linkState.remote
                        val thread = activeThreadId?.let { id -> threads.firstOrNull { it.peerId == id } }
                        ChatScreen(
                            profile = profile ?: UserProfile("Ultra"),
                            settings = settings,
                            remote = liveRemote?.takeIf {
                                activeThreadId == null || it.deviceId == activeThreadId
                            },
                            peerTitle = if (activeThreadId == ChatMessage.LOBBY_ID) {
                                "Общий чат"
                            } else {
                                thread?.title
                            },
                            peerIsCreator = thread?.isCreator == true,
                            isLobby = activeThreadId == ChatMessage.LOBBY_ID,
                            linkState = linkState,
                            messages = messages,
                            onBack = viewModel::openDiscover,
                            onSend = viewModel::sendMessage,
                            onPickImage = { pickPhoto() },
                            onEditMessage = viewModel::editMessage,
                            onDeleteMessage = viewModel::deleteMessage,
                            onDeleteChat = { viewModel.deleteChat() },
                            onOpenSettings = viewModel::openSettings,
                            onDismissError = viewModel::clearLinkError,
                        )
                    }

                    AppScreen.SETTINGS -> {
                        SettingsScreen(
                            settings = settings,
                            environment = environment,
                            linkState = linkState,
                            onBack = viewModel::closeSettings,
                            onChange = viewModel::updateSettings,
                            onReset = viewModel::resetSettings,
                            onClearHistory = viewModel::clearHistory,
                            onDeleteAccount = viewModel::deleteAccount,
                            onScanToggle = { toggleScan() },
                            onConnect = viewModel::connect,
                            onDisconnect = viewModel::disconnect,
                            onOpenActiveChat = viewModel::openChat,
                            onRequestPermissions = { requestPermissions() },
                            onOpenLocationSettings = {
                                openSystemSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                            },
                            onOpenBluetoothSettings = {
                                openSystemSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
                            },
                        )
                    }

                    AppScreen.ADMIN -> {
                        AdminScreen(
                            state = admin,
                            onBack = viewModel::closeAdminPanel,
                            onRefresh = viewModel::refreshAdminState,
                            onLogout = viewModel::logoutAdmin,
                            onClearLobby = viewModel::adminClearLobby,
                            onBan = viewModel::adminBan,
                            onUnban = viewModel::adminUnban,
                            onKick = viewModel::adminKick,
                            onDeleteUser = viewModel::adminDeleteUser,
                            onSendNotice = { title, text, version, url ->
                                viewModel.adminSendNotice(title, text, version, url)
                            },
                            onClearNotice = viewModel::adminClearNotice,
                            onDismissError = viewModel::clearAdminError,
                            onMessage = { notify(it) },
                        )
                    }
                }
            }

            // В чате продолжаем цвет панели ввода на системную зону кнопок снизу.
            if (screen == AppScreen.CHAT && navBarBottom > 0.dp) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(navBarBottom)
                        .background(palette.panel),
                )
            }

            // Плашка о подключении: висит снизу, пока связи с сервером нет,
            // и сама исчезает, как только канал поднялся.
            val serverPhase = linkState.server.phase
            val showServerBanner = screen == AppScreen.DISCOVER &&
                profile != null &&
                settings.serverUrl.isNotBlank() &&
                serverPhase != TransportPhase.CONNECTED &&
                serverPhase != TransportPhase.DISABLED
            AnimatedVisibility(
                visible = showServerBanner,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 3 },
                exit = fadeOut(tween(140)) + slideOutVertically(tween(140)) { it / 3 },
            ) {
                GlassPanel(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = palette.line,
                    padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = palette.accent,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            // Показываем причину, а не общую фразу: по ней
                            // видно, ждём сеть, регистрируемся или сервер молчит.
                            text = linkState.server.detail?.takeIf { it.isNotBlank() }
                                ?: if (serverPhase == TransportPhase.ERROR) {
                                    "Нет связи с сервером, переподключаюсь…"
                                } else {
                                    "Подключение к серверу…"
                                },
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textPrimary,
                        )
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .imePadding()
                    .padding(16.dp),
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = PanelElevated,
                    contentColor = TextPrimary,
                    actionColor = NeonCyan,
                    dismissActionContentColor = TextSecondary,
                    shape = RoundedCornerShape(16.dp),
                )
            }
        }

        if (lobbyPinVisible) {
            var pin by remember { mutableStateOf("") }
            var pinError by remember { mutableStateOf<String?>(null) }

            fun submitPin() {
                if (!viewModel.submitLobbyPin(pin)) {
                    pinError = "Неверный PIN"
                }
            }

            AlertDialog(
                onDismissRequest = viewModel::dismissLobbyPin,
                title = { Text("Общий чат") },
                text = {
                    Column {
                        Text(
                            text = "Введите PIN-код общего чата.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = pin,
                            onValueChange = {
                                pin = it.filter(Char::isDigit).take(8)
                                pinError = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("PIN") },
                            singleLine = true,
                            isError = pinError != null,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.NumberPassword,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { submitPin() }),
                        )
                        pinError?.let { error ->
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = Danger,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { submitPin() }) { Text("Войти") }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissLobbyPin) { Text("Отмена") }
                },
            )
        }

        if (admin.loginVisible) {
            var password by remember { mutableStateOf("") }

            fun submitPassword() {
                viewModel.submitAdminPassword(password)
            }

            AlertDialog(
                onDismissRequest = {
                    if (!admin.loginLoading) viewModel.dismissAdminLogin()
                },
                title = { Text("Админ-панель") },
                text = {
                    Column {
                        Text(
                            text = "Введите пароль администратора.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it.take(64) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Пароль") },
                            singleLine = true,
                            isError = admin.passwordError != null,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { submitPassword() }),
                        )
                        admin.passwordError?.let { error ->
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = Danger,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !admin.loginLoading,
                        onClick = { submitPassword() },
                    ) {
                        if (admin.loginLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = NeonCyan,
                            )
                        } else {
                            Text("Войти")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !admin.loginLoading,
                        onClick = viewModel::dismissAdminLogin,
                    ) {
                        Text("Отмена")
                    }
                },
            )
        }
    }
}
