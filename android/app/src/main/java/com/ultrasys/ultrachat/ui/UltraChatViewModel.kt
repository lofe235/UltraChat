package com.ultrasys.ultrachat.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ultrasys.ultrachat.data.AvatarPresets
import com.ultrasys.ultrachat.data.AppSettings
import com.ultrasys.ultrachat.data.ChatHistoryStore
import com.ultrasys.ultrachat.data.ChatMessage
import com.ultrasys.ultrachat.data.ChatThread
import com.ultrasys.ultrachat.data.ImageCodec
import com.ultrasys.ultrachat.data.ProfileStore
import com.ultrasys.ultrachat.data.SettingsStore
import com.ultrasys.ultrachat.data.UserProfile
import com.ultrasys.ultrachat.net.AdminApi
import com.ultrasys.ultrachat.net.AdminState
import com.ultrasys.ultrachat.net.ConnectionHub
import com.ultrasys.ultrachat.net.LinkChannel
import com.ultrasys.ultrachat.net.LinkState
import com.ultrasys.ultrachat.net.NearbyPeer
import com.ultrasys.ultrachat.net.RemotePeer
import com.ultrasys.ultrachat.net.ServerNotice
import com.ultrasys.ultrachat.net.ServerUser
import com.ultrasys.ultrachat.net.SmartLinkManager
import com.ultrasys.ultrachat.net.WireProtocol
import com.ultrasys.ultrachat.util.AppVisibility
import com.ultrasys.ultrachat.util.MessageNotifier
import com.ultrasys.ultrachat.util.NetworkPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppScreen {
    ONBOARDING,
    DISCOVER,
    CHAT,
    SETTINGS,
    ADMIN,
}

/** Состояние поиска людей для кнопки «+» на главном экране. */
data class UserSearchState(
    val query: String = "",
    val loading: Boolean = false,
    val users: List<ServerUser> = emptyList(),
    val error: String? = null,
)

/** Состояние скрытой админ-панели. Токен хранится только в памяти. */
data class AdminUiState(
    val loginVisible: Boolean = false,
    val loginLoading: Boolean = false,
    val passwordError: String? = null,
    val token: String = "",
    val data: AdminState? = null,
    val loading: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
)

class UltraChatViewModel(application: Application) :
    AndroidViewModel(application),
    SmartLinkManager.Listener {

    private val profileStore = ProfileStore(application)
    private val historyStore = ChatHistoryStore(application)
    private val settingsStore = SettingsStore(application)

    private val _profile = MutableStateFlow(profileStore.load())
    val profile: StateFlow<UserProfile?> = _profile.asStateFlow()

    private val _settings = MutableStateFlow(settingsStore.load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _screen = MutableStateFlow(
        if (_profile.value == null) AppScreen.ONBOARDING else AppScreen.DISCOVER,
    )
    val screen: StateFlow<AppScreen> = _screen.asStateFlow()

    private val _profileCanGoBack = MutableStateFlow(false)
    val profileCanGoBack: StateFlow<Boolean> = _profileCanGoBack.asStateFlow()

    private val _environment = MutableStateFlow(NetworkPermissions.snapshot(application))
    val environment: StateFlow<NetworkPermissions.Snapshot> = _environment.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    /** Сохранённые переписки для вкладки «Чаты». */
    private val _threads = MutableStateFlow<List<ChatThread>>(emptyList())
    val threads: StateFlow<List<ChatThread>> = _threads.asStateFlow()

    /** Открытая переписка из истории (может быть без активного соединения). */
    private val _activeThreadId = MutableStateFlow<String?>(null)
    val activeThreadId: StateFlow<String?> = _activeThreadId.asStateFlow()

    /** Поиск людей по нику для добавления в чаты. */
    private val _userSearch = MutableStateFlow(UserSearchState())
    val userSearch: StateFlow<UserSearchState> = _userSearch.asStateFlow()
    private var userSearchJob: Job? = null

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 6)
    val toasts: SharedFlow<String> = _toasts.asSharedFlow()

    /** PIN-запрос для входа в общий чат (1352). */
    private val _lobbyPinVisible = MutableStateFlow(false)
    val lobbyPinVisible: StateFlow<Boolean> = _lobbyPinVisible.asStateFlow()

    /** Текущее уведомление от администратора (как на веб-версии). */
    private val _notice = MutableStateFlow<ServerNotice?>(null)
    val notice: StateFlow<ServerNotice?> = _notice.asStateFlow()

    /** Скрытая админ-панель: пароль, данные, загрузка. */
    private val _admin = MutableStateFlow(AdminUiState())
    val admin: StateFlow<AdminUiState> = _admin.asStateFlow()
    private val adminApi = AdminApi {
        _settings.value.serverUrl.ifBlank { AppSettings.BUILT_IN_SERVER_URL }
    }
    private var adminReturnScreen: AppScreen? = null
    private var lobbyPinPendingOpen = false

    // Соединение живёт в процессе, а не в экране: так оно переживает
    // сворачивание приложения и продолжает принимать сообщения в фоне.
    private val smartLink = ConnectionHub.manager
    val linkState: StateFlow<LinkState> = smartLink.state

    private var profileReturnScreen: AppScreen? = null
    private var settingsReturnScreen: AppScreen? = null
    private var currentPeerId: String? = null
    private var loadedHistoryFor: String? = null
    private val knownMessageIds = mutableSetOf<String>()
    private var refreshThreadsJob: Job? = null
    private var pendingScanAfterPermission = false

    init {
        ConnectionHub.attach(this)
        smartLink.setAutoReconnect(_settings.value.autoReconnect)
        refreshEnvironment()
        refreshThreads()
        val settings = _settings.value
        if (settings.serverUrl.isNotBlank()) {
            smartLink.configureServer(settings.serverUrl, settings.serverToken)
        }
    }

    fun refreshThreads() {
        // При всплеске сообщений не перечитываем все файлы на каждое:
        // обновляем список один раз через 120 мс.
        refreshThreadsJob?.cancel()
        refreshThreadsJob = viewModelScope.launch {
            delay(120)
            val list = withContext(Dispatchers.IO) { historyStore.threads() }
            _threads.value = list
        }
    }

    /** Открыть сохранённую переписку без подключения (только чтение истории). */
    fun openThread(peerId: String) {
        if (peerId == ChatMessage.LOBBY_ID) {
            openLobby()
            return
        }
        MessageNotifier.cancel(getApplication(), peerId)
        _activeThreadId.value = peerId
        loadedHistoryFor = null
        currentPeerId = peerId
        knownMessageIds.clear()
        _messages.value = emptyList()
        _screen.value = AppScreen.CHAT
        val thread = _threads.value.firstOrNull { it.peerId == peerId }
        // Основной канал — сервер. Даже новый контакт без переписки сразу
        // получает серверное соединение, а Bluetooth остаётся запасным.
        if (thread?.channel != LinkChannel.BLUETOOTH && smartLink.isServerConnected()) {
            smartLink.attachServerConversation(
                peerId = peerId,
                fallbackName = thread?.title ?: "Собеседник",
                isCreator = thread?.isCreator == true,
            )
        }
        ensureConversationLoaded(peerId)
    }

    /** Вход в общий чат. В приложении он защищён PIN-кодом 1352. */
    fun openLobby() {
        if (!lobbyUnlockedThisProcess) {
            lobbyPinPendingOpen = true
            _lobbyPinVisible.value = true
            return
        }
        openLobbyInternal()
    }

    /** @return true, если PIN верный. */
    fun submitLobbyPin(pin: String): Boolean {
        if (pin.trim() != LOBBY_PIN) return false
        lobbyUnlockedThisProcess = true
        _lobbyPinVisible.value = false
        if (lobbyPinPendingOpen) {
            lobbyPinPendingOpen = false
            openLobbyInternal()
        }
        return true
    }

    fun dismissLobbyPin() {
        lobbyPinPendingOpen = false
        _lobbyPinVisible.value = false
    }

    private fun openLobbyInternal() {
        _lobbyPinVisible.value = false
        MessageNotifier.cancel(getApplication(), ChatMessage.LOBBY_ID)
        _activeThreadId.value = ChatMessage.LOBBY_ID
        loadedHistoryFor = null
        currentPeerId = ChatMessage.LOBBY_ID
        knownMessageIds.clear()
        _messages.value = emptyList()
        _screen.value = AppScreen.CHAT
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                historyStore.savePeer(ChatMessage.LOBBY_ID, "Общий чат", false)
            }
            refreshThreads()
        }
        if (smartLink.isServerConnected()) smartLink.requestLobbyHistory()
        ensureConversationLoaded(ChatMessage.LOBBY_ID)
    }

    /** Поиск людей на сервере. Запросы дебаунсятся, чтобы не дёргать API. */
    fun searchUsers(query: String) {
        val clean = query.trim().take(48)
        userSearchJob?.cancel()
        _userSearch.value = _userSearch.value.copy(query = query.take(48), error = null)
        if (clean.length < 2) {
            _userSearch.value = _userSearch.value.copy(loading = false, users = emptyList())
            return
        }
        if (!smartLink.isServerConnected()) {
            _userSearch.value = _userSearch.value.copy(
                loading = false,
                users = emptyList(),
                error = "Нет связи с сервером. Проверьте интернет.",
            )
            return
        }
        userSearchJob = viewModelScope.launch {
            delay(300)
            _userSearch.update { it.copy(loading = true, error = null) }
            runCatching { smartLink.searchServerUsers(clean) }
                .onSuccess { users ->
                    if (_userSearch.value.query.trim() != clean) return@onSuccess
                    _userSearch.value = _userSearch.value.copy(
                        loading = false,
                        users = users,
                        error = null,
                    )
                }
                .onFailure { err ->
                    _userSearch.value = _userSearch.value.copy(
                        loading = false,
                        users = emptyList(),
                        error = err.message ?: "Не удалось найти людей",
                    )
                }
        }
    }

    fun clearUserSearch() {
        userSearchJob?.cancel()
        _userSearch.value = UserSearchState()
    }

    /** Добавляет найденного человека в список чатов и сразу открывает переписку. */
    fun addContact(user: ServerUser) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                historyStore.savePeer(user.id, user.displayName, user.isCreator)
            }
            _userSearch.value = UserSearchState()
            refreshThreads()
            openThread(user.id)
            _toasts.tryEmit("${user.displayName} добавлен в чаты")
        }
    }

    fun refreshEnvironment() {
        _environment.value = NetworkPermissions.snapshot(getApplication())
    }

    fun markScanPendingAfterPermission() {
        pendingScanAfterPermission = true
    }

    fun onPermissionResult(granted: Boolean) {
        refreshEnvironment()
        if (granted) {
            if (pendingScanAfterPermission) {
                pendingScanAfterPermission = false
                startScanning()
            }
        } else {
            pendingScanAfterPermission = false
            _toasts.tryEmit("Без разрешений нельзя найти устройства рядом")
        }
    }

    fun startScanning() {
        refreshEnvironment()
        if (!_environment.value.runtimeGranted) {
            _toasts.tryEmit("Нужны разрешения Bluetooth")
            return
        }
        smartLink.start()
        if (!smartLink.startScanning()) {
            _toasts.tryEmit("Поиск сейчас недоступен")
        }
    }

    fun stopScanning() {
        smartLink.stopScanning()
    }

    fun connect(peer: NearbyPeer) {
        _messages.value = emptyList()
        knownMessageIds.clear()
        loadedHistoryFor = null
        currentPeerId = null
        _activeThreadId.value = null
        _screen.value = AppScreen.CHAT
        smartLink.connect(peer)
    }

    fun disconnect() {
        smartLink.disconnect()
        _messages.value = emptyList()
        knownMessageIds.clear()
        loadedHistoryFor = null
        currentPeerId = null
        _activeThreadId.value = null
        _screen.value = AppScreen.DISCOVER
        refreshThreads()
    }

    fun sendMessage(text: String) {
        val lobby = currentPeerId == ChatMessage.LOBBY_ID ||
            _activeThreadId.value == ChatMessage.LOBBY_ID
        val sent = if (lobby) {
            smartLink.sendLobbyMessage(text)
        } else {
            smartLink.sendMessage(text)
        }
        if (!sent) {
            _toasts.tryEmit(
                if (lobby) "Нет связи с сервером — сообщение не отправлено"
                else "Собеседник ещё не подключён",
            )
        }
    }

    /** Сжимает выбранное фото и отправляет собеседнику или в общий чат. */
    fun sendImage(uri: android.net.Uri) {
        viewModelScope.launch {
            val prepared = withContext(Dispatchers.IO) {
                val bitmap = ImageCodec.loadFromUri(getApplication(), uri, ImageCodec.MAX_CHAT_SIDE)
                    ?: return@withContext null
                val encoded = ImageCodec.encodeChatPhoto(bitmap) ?: return@withContext null
                val file = ImageCodec.saveBytes(
                    getApplication(),
                    encoded.bytes,
                    "out_${System.currentTimeMillis()}.${encoded.extension}",
                ) ?: return@withContext null
                Triple(encoded.bytes, encoded.mimeType, file.absolutePath)
            }
            if (prepared == null) {
                _toasts.tryEmit("Не удалось открыть фото")
                return@launch
            }
            val lobby = currentPeerId == ChatMessage.LOBBY_ID ||
                _activeThreadId.value == ChatMessage.LOBBY_ID
            val sent = if (lobby) {
                smartLink.sendLobbyImage(prepared.first, prepared.second, prepared.third)
            } else {
                smartLink.sendImage(prepared.first, prepared.second, prepared.third)
            }
            if (!sent) {
                _toasts.tryEmit(
                    if (lobby) "Нет связи с сервером — фото не отправлено"
                    else "Собеседник ещё не подключён",
                )
            }
        }
    }

    fun openChat() {
        if (linkState.value.remote != null) {
            linkState.value.remote?.deviceId?.let { MessageNotifier.cancel(getApplication(), it) }
            _activeThreadId.value = null
            _screen.value = AppScreen.CHAT
            markConversationRead(linkState.value.remote?.deviceId)
        }
    }

    /** Удаляет переписку локально и, если сервер доступен, у собеседника. */
    fun deleteChat(peerId: String? = null) {
        val target = peerId
            ?: currentPeerId
            ?: _activeThreadId.value
            ?: linkState.value.remote?.deviceId
            ?: return
        // Общий чат не удаляется как личная переписка.
        if (target == ChatMessage.LOBBY_ID) return
        viewModelScope.launch {
            smartLink.deleteChat(target)
            withContext(Dispatchers.IO) { historyStore.deleteThread(target) }
            MessageNotifier.cancel(getApplication(), target)
            val wasActive = currentPeerId == target || _activeThreadId.value == target
            if (wasActive) {
                _messages.value = emptyList()
                knownMessageIds.clear()
                loadedHistoryFor = null
                currentPeerId = null
                _activeThreadId.value = null
                if (linkState.value.remote?.deviceId == target) smartLink.disconnect()
                _screen.value = AppScreen.DISCOVER
            }
            refreshThreads()
            _toasts.tryEmit("Чат удалён")
        }
    }

    fun editMessage(id: String, rawText: String) {
        val text = rawText.trim().take(WireProtocol.MAX_TEXT_LENGTH)
        if (text.isEmpty()) return
        val message = _messages.value.firstOrNull { it.id == id } ?: return
        if (!message.isMine || message.kind != ChatMessage.KIND_TEXT) return
        if (!smartLink.editMessage(id, text)) {
            _toasts.tryEmit("Нет связи с сервером — сообщение не изменено")
            return
        }
        val now = System.currentTimeMillis()
        _messages.update { list ->
            list.map { item ->
                if (item.id == id) item.copy(text = text, editedAt = now) else item
            }
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                historyStore.updateEdited(message.peerId, id, text, now)
            }
            refreshThreads()
        }
    }

    fun deleteMessage(id: String) {
        val message = _messages.value.firstOrNull { it.id == id } ?: return
        if (!message.isMine) return
        if (!smartLink.deleteMessage(id)) {
            _toasts.tryEmit("Нет связи с сервером — сообщение не удалено")
            return
        }
        _messages.update { list -> list.filterNot { it.id == id } }
        knownMessageIds.remove(id)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { historyStore.removeMessage(message.peerId, id) }
            refreshThreads()
        }
    }

    fun openDiscover() {
        refreshEnvironment()
        refreshThreads()
        _screen.value = AppScreen.DISCOVER
    }

    fun updateSettings(updated: AppSettings) {
        val previous = _settings.value
        if (updated == previous) return
        _settings.value = updated
        settingsStore.save(updated)
        if (updated.autoReconnect != previous.autoReconnect) {
            smartLink.setAutoReconnect(updated.autoReconnect)
        }
        if (updated.serverUrl != previous.serverUrl || updated.serverToken != previous.serverToken) {
            if (updated.serverUrl.isBlank()) {
                smartLink.stopServer()
            } else {
                smartLink.configureServer(updated.serverUrl, updated.serverToken)
            }
        }
    }

    fun resetSettings() {
        _settings.value = settingsStore.reset()
        smartLink.setAutoReconnect(_settings.value.autoReconnect)
        smartLink.stopServer()
        _toasts.tryEmit("Настройки сброшены")
    }

    fun clearHistory() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { historyStore.clearAll() }
            MessageNotifier.cancelAll(getApplication())
            _messages.value = emptyList()
            knownMessageIds.clear()
            loadedHistoryFor = null
            currentPeerId = null
            _activeThreadId.value = null
            refreshThreads()
            _toasts.tryEmit("История очищена")
        }
    }

    fun openSettings() {
        if (_screen.value == AppScreen.SETTINGS) return
        settingsReturnScreen = _screen.value
        _screen.value = AppScreen.SETTINGS
    }

    fun closeSettings() {
        _screen.value = settingsReturnScreen
            ?: if (_profile.value == null) AppScreen.ONBOARDING else AppScreen.DISCOVER
        settingsReturnScreen = null
    }

    fun openProfile() {
        if (_screen.value == AppScreen.ONBOARDING) return
        profileReturnScreen = _screen.value
        _profileCanGoBack.value = true
        _screen.value = AppScreen.ONBOARDING
    }

    fun backFromProfile() {
        val target = profileReturnScreen ?: return
        profileReturnScreen = null
        _profileCanGoBack.value = false
        _screen.value = target
    }

    /**
     * @return текст ошибки или null, если профиль сохранён.
     */
    fun saveProfile(raw: String, avatarId: String): String? {
        val current = _profile.value
        val photo = if (current != null && current.avatarId == avatarId) {
            current.avatarPhoto
        } else {
            null
        }
        val updated = profileStore.saveProfile(raw, avatarId, photo)
            ?: return "Введите никнейм (до ${UserProfile.MAX_NICKNAME_LENGTH} символов)"
        _profile.value = updated
        smartLink.refreshIdentity()
        _profileCanGoBack.value = false
        _screen.value = profileReturnScreen ?: AppScreen.DISCOVER
        profileReturnScreen = null
        _toasts.tryEmit("Профиль сохранён")
        return null
    }

    /** Регистрация нового аккаунта: ник, аватар-эмодзи или фото. */
    fun completeSignUp(nickname: String, avatarId: String, photoPath: String?): String? {
        val updated = profileStore.saveProfile(nickname, avatarId, photoPath)
            ?: return "Введите никнейм (до ${UserProfile.MAX_NICKNAME_LENGTH} символов)"
        _profile.value = updated
        _profileCanGoBack.value = false
        profileReturnScreen = null
        _screen.value = AppScreen.DISCOVER
        smartLink.refreshIdentity()
        return null
    }

    /** Удаление аккаунта: профиль, история и настройки — как впервые. */
    fun deleteAccount() {
        viewModelScope.launch {
            smartLink.disconnect()
            smartLink.stopServer()
            withContext(Dispatchers.IO) {
                historyStore.clearAll()
                profileStore.clear()
                settingsStore.reset()
                runCatching {
                    ImageCodec.mediaDir(getApplication()).deleteRecursively()
                }
            }
            MessageNotifier.cancelAll(getApplication())
            _messages.value = emptyList()
            _threads.value = emptyList()
            knownMessageIds.clear()
            loadedHistoryFor = null
            currentPeerId = null
            _activeThreadId.value = null
            _profile.value = null
            _settings.value = settingsStore.load()
            _profileCanGoBack.value = false
            profileReturnScreen = null
            settingsReturnScreen = null
            _screen.value = AppScreen.ONBOARDING
            _toasts.tryEmit("Аккаунт удалён")
        }
    }

    fun unlockCreator(
        code: String,
        nickname: String = "",
        avatarId: String = AvatarPresets.DEFAULT_ID,
    ): Boolean {
        val updated = profileStore.activateCreator(code, nickname, avatarId) ?: return false
        _profile.value = updated
        smartLink.refreshIdentity()
        _toasts.tryEmit("Скрытый статус Создателя активирован")
        return true
    }

    fun clearLinkError() {
        smartLink.clearError()
    }

    override fun onRemotePeerReady(peer: RemotePeer) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                historyStore.savePeer(peer.deviceId, peer.displayName, peer.isCreator)
            }
            ensureConversationLoaded(peer.deviceId)
            refreshThreads()
        }
    }

    override fun onIncomingMessage(message: ChatMessage) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            val lobby = message.peerId == ChatMessage.LOBBY_ID
            val chatIsOpen = AppVisibility.isForeground &&
                _screen.value == AppScreen.CHAT &&
                currentPeerId == message.peerId
            if (!chatIsOpen) {
                val body = when {
                    lobby && message.isImage -> "${message.sender}: 📷 Фото"
                    lobby -> "${message.sender}: ${message.text}"
                    message.isImage -> "📷 Фото"
                    else -> message.text
                }
                MessageNotifier.notifyMessage(
                    context = getApplication(),
                    peerId = message.peerId,
                    title = if (lobby) "Общий чат" else message.sender,
                    body = body,
                    isCreator = message.isCreator && !lobby,
                )
            }
            if (conversationActive(message.peerId) && knownMessageIds.add(message.id)) {
                _messages.update { current ->
                    if (current.any { it.id == message.id }) {
                        current
                    } else {
                        (current + message).sortedBy { it.timestamp }
                    }
                }
            }
            if (chatIsOpen && !lobby) smartLink.markRead(message.peerId, message.timestamp)
            viewModelScope.launch(Dispatchers.IO) {
                historyStore.savePeer(
                    message.peerId,
                    if (lobby) "Общий чат" else message.sender,
                    message.isCreator,
                )
                historyStore.append(message.peerId, message)
            }
            refreshThreads()
        }
    }

    override fun onLocalMessage(message: ChatMessage) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            val lobby = message.peerId == ChatMessage.LOBBY_ID
            ensureConversationLoaded(message.peerId)
            if (knownMessageIds.add(message.id)) {
                _messages.update { current ->
                    if (current.any { it.id == message.id }) current else current + message
                }
            }
            viewModelScope.launch(Dispatchers.IO) {
                historyStore.savePeer(
                    message.peerId,
                    if (lobby) "Общий чат" else message.sender,
                    message.isCreator,
                )
                historyStore.append(message.peerId, message)
            }
            refreshThreads()
        }
    }

    override fun onServerToken(token: String) {
        val current = _settings.value
        if (current.serverToken == token) return
        val updated = current.copy(serverToken = token)
        _settings.value = updated
        viewModelScope.launch(Dispatchers.IO) {
            settingsStore.save(updated)
        }
    }

    override fun onServerHistory(peerId: String, messages: List<ChatMessage>) {
        val lobby = peerId == ChatMessage.LOBBY_ID
        if (messages.isEmpty()) {
            // Пустая история общего чата означает, что его очистили, пока
            // приложение было офлайн. Локальную копию тоже убираем.
            if (lobby) {
                viewModelScope.launch {
                    withContext(Dispatchers.IO) { historyStore.deleteThread(peerId) }
                    if (conversationActive(peerId)) {
                        _messages.value = emptyList()
                        knownMessageIds.clear()
                    }
                    refreshThreads()
                }
            }
            return
        }
        viewModelScope.launch {
            val merged = withContext(Dispatchers.IO) {
                messages.forEach { historyStore.append(peerId, it) }
                messages.lastOrNull { !it.isMine }?.let { remote ->
                    historyStore.savePeer(peerId, remote.sender, remote.isCreator)
                }
                historyStore.load(peerId)
            }
            if (merged.isEmpty()) return@launch
            if (conversationActive(peerId)) {
                val pending = _messages.value.filter { it.peerId == peerId }
                val all = (merged + pending)
                    .distinctBy { it.id }
                    .sortedBy { it.timestamp }
                knownMessageIds.clear()
                knownMessageIds.addAll(all.map { it.id })
                _messages.value = all
                markConversationRead(peerId)
            }
            refreshThreads()
        }
    }

    override fun onServerRead(peerId: String, upTo: Long) {
        if (upTo <= 0L) return
        // В общем чате отчётов о прочтении нет.
        if (peerId == ChatMessage.LOBBY_ID) return
        viewModelScope.launch {
            if (conversationActive(peerId)) {
                _messages.update { list ->
                    list.map { message ->
                        if (message.isMine &&
                            message.peerId == peerId &&
                            message.timestamp <= upTo &&
                            !message.isRead
                        ) {
                            message.copy(isRead = true)
                        } else {
                            message
                        }
                    }
                }
            }
            withContext(Dispatchers.IO) { historyStore.markRead(peerId, upTo) }
            refreshThreads()
        }
    }

    override fun onServerMessageEdited(
        peerId: String,
        id: String,
        text: String,
        editedAt: Long,
    ) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                historyStore.updateEdited(peerId, id, text, editedAt)
            }
            if (conversationActive(peerId)) {
                _messages.update { list ->
                    list.map { message ->
                        if (message.id == id) {
                            message.copy(
                                text = text,
                                editedAt = maxOf(message.editedAt, editedAt),
                            )
                        } else {
                            message
                        }
                    }
                }
            }
            refreshThreads()
        }
    }

    override fun onServerMessageDeleted(peerId: String, id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { historyStore.removeMessage(peerId, id) }
            if (conversationActive(peerId)) {
                _messages.update { list -> list.filterNot { it.id == id } }
                knownMessageIds.remove(id)
            }
            refreshThreads()
        }
    }

    override fun onServerChatDeleted(peerId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { historyStore.deleteThread(peerId) }
            MessageNotifier.cancel(getApplication(), peerId)
            val wasActive = currentPeerId == peerId || _activeThreadId.value == peerId
            if (wasActive) {
                _messages.value = emptyList()
                knownMessageIds.clear()
                loadedHistoryFor = null
                currentPeerId = null
                _activeThreadId.value = null
                if (linkState.value.remote?.deviceId == peerId) smartLink.disconnect()
                _screen.value = AppScreen.DISCOVER
            }
            refreshThreads()
            _toasts.tryEmit("Собеседник удалил чат")
        }
    }

    override fun onServerNotice(notice: ServerNotice) {
        _notice.value = notice
    }

    override fun onServerNoticeCleared() {
        _notice.value = null
    }

    override fun onServerLobbyCleared() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { historyStore.deleteThread(ChatMessage.LOBBY_ID) }
            MessageNotifier.cancel(getApplication(), ChatMessage.LOBBY_ID)
            if (conversationActive(ChatMessage.LOBBY_ID)) {
                _messages.value = emptyList()
                knownMessageIds.clear()
            }
            refreshThreads()
            _toasts.tryEmit("Общий чат очищен администратором")
        }
    }

    /** Скрыть уведомление администратора до следующего подключения. */
    fun dismissNotice() {
        _notice.value = null
    }

    // ---------------------------------------------------------------------
    // Скрытая админ-панель
    // ---------------------------------------------------------------------

    /** Пять нажатий по заголовку UltraChat открывают вход в админ-панель. */
    fun openAdminPanel() {
        if (_screen.value == AppScreen.ADMIN) return
        if (_admin.value.token.isNotBlank()) {
            adminReturnScreen = _screen.value
            _screen.value = AppScreen.ADMIN
            refreshAdminState()
            return
        }
        _admin.update {
            it.copy(
                loginVisible = true,
                loginLoading = false,
                passwordError = null,
                error = null,
            )
        }
    }

    fun dismissAdminLogin() {
        _admin.update {
            it.copy(loginVisible = false, loginLoading = false, passwordError = null)
        }
    }

    fun submitAdminPassword(password: String) {
        val clean = password.trim()
        if (clean.isEmpty()) {
            _admin.update { it.copy(passwordError = "Введите пароль") }
            return
        }
        if (_admin.value.loginLoading) return
        _admin.update { it.copy(loginLoading = true, passwordError = null) }
        viewModelScope.launch {
            runCatching { adminApi.login(clean) }
                .onSuccess { session ->
                    if (_screen.value != AppScreen.ADMIN) {
                        adminReturnScreen = _screen.value
                    }
                    _admin.update {
                        it.copy(
                            loginVisible = false,
                            loginLoading = false,
                            passwordError = null,
                            token = session.token,
                            error = null,
                        )
                    }
                    _screen.value = AppScreen.ADMIN
                    refreshAdminState()
                }
                .onFailure { err ->
                    _admin.update {
                        it.copy(
                            loginLoading = false,
                            passwordError = err.message ?: "Не удалось войти",
                        )
                    }
                }
        }
    }

    fun refreshAdminState() {
        val token = _admin.value.token
        if (token.isBlank() || _admin.value.loading) return
        _admin.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { adminApi.state(token) }
                .onSuccess { data ->
                    _admin.update { it.copy(loading = false, data = data, error = null) }
                }
                .onFailure { err -> handleAdminFailure(err) }
        }
    }

    fun closeAdminPanel() {
        _screen.value = adminReturnScreen ?: AppScreen.DISCOVER
        adminReturnScreen = null
        _admin.update { it.copy(error = null, busy = false) }
    }

    fun logoutAdmin() {
        _admin.value = AdminUiState()
        _screen.value = adminReturnScreen ?: AppScreen.DISCOVER
        adminReturnScreen = null
        _toasts.tryEmit("Вы вышли из админ-панели")
    }

    fun clearAdminError() {
        _admin.update { it.copy(error = null) }
    }

    fun adminClearLobby() {
        runAdminAction("Общий чат очищен") { token ->
            adminApi.clearLobby(token)
            Unit
        }
    }

    fun adminBan(kind: String, value: String, reason: String) {
        val clean = value.trim()
        if (clean.isEmpty()) {
            _toasts.tryEmit(if (kind == "ip") "Укажите IP-адрес" else "Укажите ID устройства")
            return
        }
        if (kind == "ip" && isLoopbackIp(clean)) {
            // На alwaysdata все клиенты приходят через прокси с ::1.
            // Бан такого адреса заблокировал бы вообще всех.
            _toasts.tryEmit(
                "IP ::1 — это прокси хостинга. Забаньте устройство: IP-бан заблокирует всех.",
            )
            return
        }
        runAdminAction(if (kind == "ip") "IP-адрес забанен" else "Устройство забанено") { token ->
            adminApi.ban(token, kind, clean, reason.trim())
        }
    }

    fun adminUnban(kind: String, value: String) {
        runAdminAction("Бан снят") { token ->
            adminApi.unban(token, kind, value)
        }
    }

    fun adminKick(userId: String) {
        runAdminAction("Пользователь отключён") { token ->
            adminApi.kick(token, userId)
            Unit
        }
    }

    fun adminDeleteUser(userId: String) {
        runAdminAction("Аккаунт удалён вместе с сообщениями") { token ->
            adminApi.deleteUser(token, userId)
            Unit
        }
    }

    fun adminSendNotice(
        title: String,
        text: String,
        version: String = "",
        url: String = "",
    ) {
        if (title.trim().isEmpty() && text.trim().isEmpty()) {
            _toasts.tryEmit("Введите заголовок или текст уведомления")
            return
        }
        runAdminAction("Уведомление отправлено") { token ->
            adminApi.sendNotice(
                token = token,
                title = title.trim(),
                text = text.trim(),
                version = version.trim(),
                url = url.trim(),
            )
            Unit
        }
    }

    fun adminClearNotice() {
        runAdminAction("Уведомление снято") { token ->
            adminApi.clearNotice(token)
        }
    }

    private fun runAdminAction(success: String, block: suspend (String) -> Unit) {
        val token = _admin.value.token
        if (token.isBlank()) {
            _toasts.tryEmit("Сессия администратора истекла")
            return
        }
        if (_admin.value.busy) return
        _admin.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            runCatching { block(token) }
                .onSuccess {
                    _admin.update { it.copy(busy = false) }
                    _toasts.tryEmit(success)
                    refreshAdminState()
                }
                .onFailure { err -> handleAdminFailure(err) }
        }
    }

    private fun handleAdminFailure(err: Throwable) {
        val message = err.message?.takeIf { it.isNotBlank() } ?: "Ошибка админ-панели"
        val expired = message.contains("Сессия истекла", ignoreCase = true)
        _admin.update {
            it.copy(
                loading = false,
                busy = false,
                data = if (expired) null else it.data,
                token = if (expired) "" else it.token,
                loginVisible = if (expired) true else it.loginVisible,
                passwordError = if (expired) message else it.passwordError,
                error = if (expired) null else message,
            )
        }
        _toasts.tryEmit(message)
    }

    private fun markConversationRead(peerId: String?) {
        if (peerId.isNullOrBlank()) return
        if (peerId == ChatMessage.LOBBY_ID) return
        if (!AppVisibility.isForeground) return
        if (!conversationActive(peerId)) return
        val upTo = _messages.value
            .lastOrNull { it.peerId == peerId && !it.isMine }
            ?.timestamp
            ?: return
        if (upTo > 0L) smartLink.markRead(peerId, upTo)
    }

    private fun conversationActive(peerId: String): Boolean {
        if (_screen.value != AppScreen.CHAT) return false
        if (_activeThreadId.value == peerId) return true
        if (_activeThreadId.value == null && linkState.value.remote?.deviceId == peerId) return true
        return currentPeerId == peerId
    }

    private fun ensureConversationLoaded(peerId: String) {
        if (loadedHistoryFor == peerId) return
        loadedHistoryFor = peerId
        currentPeerId = peerId
        val pending = _messages.value.filter { it.peerId == peerId }
        viewModelScope.launch {
            val history = withContext(Dispatchers.IO) { historyStore.load(peerId) }
            if (loadedHistoryFor != peerId) return@launch
            val merged = (history + pending + _messages.value.filter { it.peerId == peerId })
                .distinctBy { it.id }
                .sortedBy { it.timestamp }
            knownMessageIds.clear()
            knownMessageIds.addAll(merged.map { it.id })
            _messages.value = merged
            if (conversationActive(peerId)) markConversationRead(peerId)
        }
    }

    override fun onCleared() {
        // Связь не выключаем: её держит RelayService, иначе уведомления
        // перестанут приходить, как только экран закроют.
        ConnectionHub.detach(this)
        super.onCleared()
    }

    companion object {
        /** PIN общего чата в приложении (на вебе общий чат без пароля). */
        const val LOBBY_PIN = "1352"

        /**
         * PIN спрашивается один раз за запуск приложения и не сохраняется
         * на диск: после перезапуска процесса защита снова активна.
         */
        private var lobbyUnlockedThisProcess = false
    }
}

/**
 * Loopback-адрес принадлежит reverse-proxy хостинга, а не пользователю.
 * На alwaysdata через него проходят все клиенты, поэтому банить его нельзя.
 */
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
