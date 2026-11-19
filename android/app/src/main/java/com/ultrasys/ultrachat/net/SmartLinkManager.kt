package com.ultrasys.ultrachat.net

import android.annotation.SuppressLint
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Build
import android.util.Base64
import com.ultrasys.ultrachat.data.AvatarPresets
import com.ultrasys.ultrachat.data.ChatMessage
import com.ultrasys.ultrachat.data.ImageCodec
import com.ultrasys.ultrachat.data.ProfileStore
import com.ultrasys.ultrachat.data.UserProfile
import com.ultrasys.ultrachat.util.NetworkPermissions
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Диспетчер локальной связи. Единственный канал — классический Bluetooth
 * RFCOMM: он работает без интернета, роутера и Wi-Fi Direct.
 *
 * Исходящие сообщения, которые не удалось записать в сокет, ждут в очереди
 * и отправляются после восстановления соединения. История чата хранится
 * отдельно и не зависит от состояния Bluetooth.
 */
class SmartLinkManager(
    context: Context,
    private val scope: CoroutineScope,
    private val profileStore: ProfileStore,
    private val listener: Listener,
) : BluetoothTransport.Listener, ServerTransport.Listener {

    interface Listener {
        fun onRemotePeerReady(peer: RemotePeer)
        fun onIncomingMessage(message: ChatMessage)
        fun onLocalMessage(message: ChatMessage)
        fun onServerToken(token: String)
        fun onServerHistory(peerId: String, messages: List<ChatMessage>)
        fun onServerMessageEdited(peerId: String, id: String, text: String, editedAt: Long)
        fun onServerMessageDeleted(peerId: String, id: String)
        fun onServerChatDeleted(peerId: String)
        fun onServerRead(peerId: String, upTo: Long)
        fun onServerNotice(notice: ServerNotice)
        fun onServerNoticeCleared()
        fun onServerLobbyCleared()
    }

    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(LinkState())
    val state: StateFlow<LinkState> = _state.asStateFlow()

    private val bluetoothTransport = BluetoothTransport(appContext, scope, this)
    private val serverTransport = ServerTransport(appContext, scope, profileStore, this)

    private val sessionLock = Any()
    private var session: LinkSession? = null
    private val queueLock = Any()
    private val pendingFrames = ArrayDeque<PendingFrame>()
    private val pendingServerFrames = ArrayDeque<WireFrame.Chat>()

    private var bluetoothPeers: List<NearbyPeer> = emptyList()
    private var serverPeers: List<NearbyPeer> = emptyList()
    private var serverConnected = false
    private var serverUsersCount = 0

    private var started = false
    private var shuttingDown = false
    private var scanTimeoutJob: Job? = null
    private var reconnectJob: Job? = null
    private var remoteAddress: String = ""
    private var remoteBluetoothName: String = ""

    @Volatile
    private var autoReconnectEnabled = true

    fun setAutoReconnect(enabled: Boolean) {
        autoReconnectEnabled = enabled
        if (!enabled) cancelReconnect()
    }

    // ---------------------------------------------------------------------
    // Серверный канал
    // ---------------------------------------------------------------------

    fun configureServer(url: String, token: String) {
        if (url.isBlank()) {
            serverTransport.stop()
            serverPeers = emptyList()
            serverConnected = false
            serverUsersCount = 0
            publishPeers()
            return
        }
        serverTransport.start(url, token)
    }

    fun stopServer() {
        serverTransport.stop()
        serverPeers = emptyList()
        serverConnected = false
        serverUsersCount = 0
        publishPeers()
    }

    /** Открывает сохранённую переписку через сервер, даже если она не в ростере. */
    fun attachServerConversation(peerId: String, fallbackName: String, isCreator: Boolean): Boolean {
        if (!serverTransport.isConnected()) return false
        val known = serverPeers.firstOrNull { it.id == peerId }
        val remote = RemotePeer(
            deviceId = peerId,
            nickname = known?.name ?: fallbackName,
            displayName = known?.name ?: fallbackName,
            isCreator = known?.isCreator ?: isCreator,
            model = "",
            avatarId = known?.avatarId ?: AvatarPresets.DEFAULT_ID,
        )
        _state.update {
            it.copy(
                remote = remote,
                activeChannel = LinkChannel.SERVER,
                connectingTo = null,
                phase = LinkPhase.CONNECTED,
                error = null,
            )
        }
        listener.onRemotePeerReady(remote)
        serverTransport.requestHistory(peerId)
        return true
    }

    fun isServerConnected(): Boolean = serverTransport.isConnected()

    /** Запрашивает историю общего чата (peer = lobby). */
    fun requestLobbyHistory(limit: Int = 100): Boolean =
        serverTransport.requestHistory(ChatMessage.LOBBY_ID, limit)

    /** Отправка в общий чат: сообщение видно всем, кроме отправителя. */
    fun sendLobbyMessage(text: String): Boolean {
        val clean = text.trim().take(WireProtocol.MAX_TEXT_LENGTH)
        if (clean.isEmpty()) return false
        return sendTo(
            peerId = ChatMessage.LOBBY_ID,
            kind = ChatMessage.KIND_TEXT,
            text = clean,
            imageBase64 = null,
            localPath = null,
        )
    }

    fun sendLobbyImage(bytes: ByteArray, mimeType: String, localPath: String): Boolean =
        sendImageTo(ChatMessage.LOBBY_ID, bytes, mimeType, localPath)

    suspend fun searchServerUsers(query: String): List<ServerUser> =
        serverTransport.searchUsers(query)

    fun start() {
        if (started) return
        started = true
        bluetoothTransport.start()
    }

    fun startScanning(): Boolean {
        if (!NetworkPermissions.snapshot(appContext).runtimeGranted) {
            _state.update {
                it.copy(
                    error = "Сначала разрешите доступ к Bluetooth",
                    phase = LinkPhase.ERROR,
                )
            }
            return false
        }
        if (_state.value.remote != null) {
            _state.update { it.copy(error = "Сначала отключитесь от текущего собеседника") }
            return false
        }
        start()
        _state.update {
            it.copy(
                scanning = true,
                phase = LinkPhase.SCANNING,
                error = null,
                lastSwitch = null,
                connectingTo = null,
            )
        }
        bluetoothTransport.startDiscovery()
        scanTimeoutJob?.cancel()
        scanTimeoutJob = scope.launch {
            delay(SCAN_DURATION_MS)
            stopScanning()
        }
        return true
    }

    fun stopScanning() {
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        bluetoothTransport.stopDiscovery()
        _state.update {
            if (it.scanning) {
                it.copy(
                    scanning = false,
                    phase = when {
                        it.remote != null && it.activeChannel != LinkChannel.NONE ->
                            LinkPhase.CONNECTED
                        it.remote != null -> LinkPhase.RECOVERING
                        it.phase == LinkPhase.ERROR -> LinkPhase.ERROR
                        else -> LinkPhase.IDLE
                    },
                )
            } else {
                it
            }
        }
    }

    fun connect(peer: NearbyPeer) {
        if (peer.channel == LinkChannel.SERVER) {
            connectServerPeer(peer)
            return
        }
        if (!NetworkPermissions.snapshot(appContext).runtimeGranted) {
            _state.update {
                it.copy(error = "Нет разрешений для Bluetooth", phase = LinkPhase.ERROR)
            }
            return
        }
        start()
        stopScanning()
        closeSession("Смена собеседника")
        clearPending()
        cancelReconnect()
        remoteAddress = peer.address
        remoteBluetoothName = peer.name
        _state.update {
            it.copy(
                remote = null,
                activeChannel = LinkChannel.NONE,
                connectingTo = peer.name,
                phase = LinkPhase.CONNECTING,
                error = null,
                lastSwitch = null,
                queuedMessages = 0,
            )
        }
        if (!bluetoothTransport.connect(peer.address)) {
            _state.update {
                it.copy(
                    error = "Bluetooth-соединение сейчас недоступно",
                    phase = LinkPhase.ERROR,
                )
            }
        }
    }

    private fun connectServerPeer(peer: NearbyPeer) {
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        bluetoothTransport.stopDiscovery()
        closeSession("Смена собеседника")
        cancelReconnect()
        clearPending()
        remoteAddress = ""
        remoteBluetoothName = ""
        val remote = RemotePeer(
            deviceId = peer.id,
            nickname = peer.name,
            displayName = peer.name,
            isCreator = peer.isCreator,
            model = "",
            avatarId = peer.avatarId ?: AvatarPresets.DEFAULT_ID,
        )
        _state.update {
            it.copy(
                remote = remote,
                activeChannel = LinkChannel.SERVER,
                connectingTo = null,
                phase = LinkPhase.CONNECTED,
                error = null,
                lastSwitch = null,
                scanning = false,
            )
        }
        listener.onRemotePeerReady(remote)
        serverTransport.requestHistory(peer.id)
    }

    fun disconnect() {
        val current = _state.value
        if (current.activeChannel == LinkChannel.SERVER) {
            clearPending()
            _state.update {
                it.copy(
                    remote = null,
                    activeChannel = LinkChannel.NONE,
                    phase = LinkPhase.IDLE,
                    connectingTo = null,
                    lastSwitch = null,
                    queuedMessages = 0,
                )
            }
            return
        }
        closeSession("Собеседник отключён")
        clearPending()
        cancelReconnect()
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        bluetoothTransport.stopDiscovery()
        remoteAddress = ""
        remoteBluetoothName = ""
        _state.update {
            LinkState(
                bluetooth = it.bluetooth.copy(
                    phase = if (it.bluetooth.phase == TransportPhase.DISABLED) {
                        TransportPhase.DISABLED
                    } else {
                        TransportPhase.READY
                    },
                ),
                server = it.server,
                peers = it.peers,
                serverOnline = it.serverOnline,
                serverPeers = it.serverPeers,
            )
        }
    }

    /**
     * @return false, если соединение ещё не установило личность собеседника.
     */
    fun sendMessage(text: String): Boolean {
        val clean = text.trim().take(WireProtocol.MAX_TEXT_LENGTH)
        if (clean.isEmpty()) return false
        val peerId = _state.value.remote?.deviceId ?: return false
        return sendTo(
            peerId = peerId,
            kind = ChatMessage.KIND_TEXT,
            text = clean,
            imageBase64 = null,
            localPath = null,
        )
    }

    /**
     * Отправка фото. Картинка уже сжата и лежит локально: в кадр уходит base64,
     * в историю — путь к файлу.
     */
    /**
     * Отправка фото. Байты сначала уходят на сервер отдельным HTTP-запросом,
     * а в WebSocket летит только ссылка. Поэтому текст в любом чате больше
     * не ждёт, пока догрузится большой base64-кадр с фото.
     */
    fun sendImage(bytes: ByteArray, mimeType: String, localPath: String): Boolean {
        val peerId = _state.value.remote?.deviceId ?: return false
        return sendImageTo(peerId, bytes, mimeType, localPath)
    }

    private fun sendImageTo(
        peerId: String,
        bytes: ByteArray,
        mimeType: String,
        localPath: String,
    ): Boolean {
        if (bytes.isEmpty()) return false
        val lobby = peerId == ChatMessage.LOBBY_ID
        if (!lobby && _state.value.remote == null) return false
        val profile = profileStore.load() ?: return false
        val serverChannel = lobby || _state.value.activeChannel == LinkChannel.SERVER
        if (!serverChannel) {
            // Bluetooth-канал не умеет отдельную HTTP-загрузку.
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            return sendTo(
                peerId = peerId,
                kind = ChatMessage.KIND_IMAGE,
                text = "",
                imageBase64 = base64,
                localPath = localPath,
            )
        }

        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val targetId = if (lobby) ChatMessage.LOBBY_ID else peerId
        listener.onLocalMessage(
            ChatMessage(
                id = id,
                peerId = targetId,
                sender = profile.displayName,
                text = "",
                timestamp = now,
                isMine = true,
                isCreator = profile.isCreator,
                channel = LinkChannel.SERVER,
                kind = ChatMessage.KIND_IMAGE,
                imagePath = localPath,
            ),
        )

        // Без связи кладём base64 в очередь, как раньше: после переподключения
        // flushServerQueue отправит кадр целиком.
        if (!serverTransport.isConnected()) {
            val frame = imageFrame(
                id = id,
                profile = profile,
                targetId = targetId,
                lobby = lobby,
                now = now,
                imageBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
                mediaPath = null,
            )
            synchronized(queueLock) { pendingServerFrames.addLast(frame) }
            updateQueuedCount()
            return true
        }

        scope.launch(Dispatchers.IO) {
            val uploadedPath = serverTransport.uploadImage(bytes, mimeType)
            val frame = if (uploadedPath != null) {
                imageFrame(
                    id = id,
                    profile = profile,
                    targetId = targetId,
                    lobby = lobby,
                    now = now,
                    imageBase64 = null,
                    mediaPath = uploadedPath,
                )
            } else {
                // Сервер старой версии или загрузка не удалась — отправляем
                // base64 через WebSocket, чтобы фото не потерялось.
                imageFrame(
                    id = id,
                    profile = profile,
                    targetId = targetId,
                    lobby = lobby,
                    now = now,
                    imageBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
                    mediaPath = null,
                )
            }
            val value = frame.value
            val sent = serverTransport.sendChat(
                to = value.toUserId,
                id = value.id,
                sender = value.sender,
                isCreator = value.isCreator,
                text = value.text,
                sentAt = value.sentAt,
                kind = value.kind,
                imageBase64 = value.image,
                mediaPath = value.media,
            )
            if (!sent) {
                synchronized(queueLock) { pendingServerFrames.addLast(frame) }
                updateQueuedCount()
            }
        }
        return true
    }

    private fun imageFrame(
        id: String,
        profile: UserProfile,
        targetId: String,
        lobby: Boolean,
        now: Long,
        imageBase64: String?,
        mediaPath: String?,
    ): WireFrame.Chat = WireFrame.Chat(
        ChatFrame(
            id = id,
            sender = profile.displayName,
            isCreator = profile.isCreator,
            text = "",
            sentAt = now,
            toUserId = if (lobby) null else targetId,
            kind = ChatMessage.KIND_IMAGE,
            image = imageBase64,
            media = mediaPath,
        ),
    )

    /** Сообщает серверу, что входящие от peer до upTo прочитаны. */
    fun markRead(peerId: String, upTo: Long) {
        if (_state.value.activeChannel != LinkChannel.SERVER) return
        serverTransport.markRead(peerId, upTo)
    }

    fun editMessage(id: String, text: String): Boolean {
        if (!serverTransport.isConnected()) return false
        return serverTransport.editMessage(id, text)
    }

    fun deleteMessage(id: String): Boolean {
        if (!serverTransport.isConnected()) return false
        return serverTransport.deleteMessage(id)
    }

    fun deleteChat(peerId: String): Boolean {
        if (_state.value.activeChannel != LinkChannel.SERVER) return false
        return serverTransport.deleteChat(peerId)
    }

    private fun sendTo(
        peerId: String,
        kind: String,
        text: String,
        imageBase64: String?,
        localPath: String?,
    ): Boolean {
        val lobby = peerId == ChatMessage.LOBBY_ID
        val remote = if (lobby) null else _state.value.remote ?: return false
        val profile = profileStore.load() ?: return false
        val now = System.currentTimeMillis()
        val serverChannel = lobby || _state.value.activeChannel == LinkChannel.SERVER
        val targetId = if (lobby) ChatMessage.LOBBY_ID else peerId
        val frame = WireFrame.Chat(
            ChatFrame(
                id = UUID.randomUUID().toString(),
                sender = profile.displayName,
                isCreator = profile.isCreator,
                text = text,
                sentAt = now,
                // В общий чат уходит to = null, личные — на конкретного получателя.
                toUserId = if (serverChannel && !lobby) targetId else null,
                kind = kind,
                image = imageBase64,
            ),
        )
        val message = ChatMessage(
            id = frame.value.id,
            peerId = targetId,
            sender = profile.displayName,
            text = text,
            timestamp = now,
            isMine = true,
            isCreator = profile.isCreator,
            channel = if (serverChannel) LinkChannel.SERVER else LinkChannel.BLUETOOTH,
            kind = kind,
            imagePath = localPath,
        )

        if (serverChannel) {
            val sent = serverTransport.sendChat(
                to = if (lobby) null else targetId,
                id = frame.value.id,
                sender = profile.displayName,
                isCreator = profile.isCreator,
                text = text,
                sentAt = now,
                kind = kind,
                imageBase64 = imageBase64,
            )
            if (!sent) {
                synchronized(queueLock) { pendingServerFrames.addLast(frame) }
                updateQueuedCount()
            }
            listener.onLocalMessage(message)
            return true
        }

        val targetRemote = remote ?: return false
        // Запись в сокет — блокирующая операция. Её нельзя выполнять в
        // главном потоке, иначе Android выбросит NetworkOnMainThreadException.
        val active = activeSession()
        if (active != null) {
            scope.launch(Dispatchers.IO) {
                if (!active.send(frame)) {
                    synchronized(queueLock) {
                        pendingFrames.addLast(PendingFrame(targetRemote.deviceId, frame))
                    }
                    updateQueuedCount()
                }
            }
        } else {
            synchronized(queueLock) {
                pendingFrames.addLast(PendingFrame(targetRemote.deviceId, frame))
            }
            updateQueuedCount()
        }
        listener.onLocalMessage(message)
        return true
    }

    fun refreshIdentity() {
        val profile = profileStore.load() ?: return
        val hello = WireProtocol.hello(
            profile = profile,
            deviceId = profileStore.deviceId(),
            model = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            bluetoothName = bluetoothTransport.localName(),
            avatarPhoto = ImageCodec.avatarPayload(profile.avatarPhoto),
        )
        val target = synchronized(sessionLock) {
            session?.takeIf { it.remoteHello != null && !it.closed }
        }
        if (target != null) {
            scope.launch(Dispatchers.IO) {
                if (!target.closed) target.sendRaw(hello)
            }
        }
        serverTransport.refreshIdentity()
    }

    fun clearError() {
        _state.update {
            it.copy(
                error = null,
                phase = when {
                    it.remote != null && it.activeChannel != LinkChannel.NONE -> LinkPhase.CONNECTED
                    it.remote != null -> LinkPhase.RECOVERING
                    it.scanning -> LinkPhase.SCANNING
                    else -> LinkPhase.IDLE
                },
            )
        }
    }

    fun shutdown() {
        shuttingDown = true
        closeSession("Приложение закрыто")
        clearPending()
        scanTimeoutJob?.cancel()
        cancelReconnect()
        serverTransport.stop()
        bluetoothTransport.shutdown()
        started = false
    }

    // ---------------------------------------------------------------------
    // События Bluetooth-транспорта
    // ---------------------------------------------------------------------

    override fun onBluetoothPeers(peers: List<NearbyPeer>) {
        bluetoothPeers = peers
        publishPeers()
    }

    override fun onBluetoothStatus(status: TransportStatus) {
        _state.update { current ->
            var error = current.error
            var phase = current.phase
            var scanning = current.scanning
            when (status.phase) {
                TransportPhase.ERROR -> {
                    if (current.remote == null && !current.scanning) {
                        error = status.detail
                        phase = LinkPhase.ERROR
                    } else if (current.remote != null && current.activeChannel == LinkChannel.NONE) {
                        phase = LinkPhase.RECOVERING
                    }
                }

                TransportPhase.DISABLED -> {
                    scanning = false
                    if (current.remote == null) {
                        error = status.detail
                        phase = LinkPhase.ERROR
                    }
                }

                TransportPhase.CONNECTED -> {
                    if (current.remote == null) phase = LinkPhase.CONNECTING
                }

                TransportPhase.READY -> {
                    if (current.scanning) {
                        scanning = false
                        phase = LinkPhase.IDLE
                    } else if (current.remote == null &&
                        current.connectingTo == null &&
                        current.phase == LinkPhase.ERROR
                    ) {
                        error = null
                        phase = LinkPhase.IDLE
                    }
                }

                else -> Unit
            }
            current.copy(
                bluetooth = status,
                scanning = scanning,
                error = error,
                phase = phase,
            )
        }
        if (status.phase == TransportPhase.ERROR &&
            _state.value.remote != null &&
            activeSession() == null
        ) {
            scheduleReconnect()
        }
    }

    @SuppressLint("MissingPermission")
    override fun onBluetoothSocket(socket: BluetoothSocket) {
        val address = runCatching { socket.remoteDevice?.address }.getOrNull().orEmpty()
        attachSocket(RfcommLinkSocket(socket), address)
    }

    // ---------------------------------------------------------------------
    // Сокет и протокол
    // ---------------------------------------------------------------------

    private fun attachSocket(linkSocket: LinkSocket, address: String) {
        if (shuttingDown) {
            linkSocket.close()
            return
        }
        if (address.isNotBlank()) remoteAddress = address
        val newSession = LinkSession(
            channel = LinkChannel.BLUETOOTH,
            linkSocket = linkSocket,
            scope = scope,
            onFrame = ::handleFrame,
            onClosed = ::handleSessionClosed,
        )
        val replaced = synchronized(sessionLock) {
            val old = session
            session = newSession
            old
        }
        replaced?.close("Заменено новым сокетом")

        _state.update {
            if (it.phase != LinkPhase.CONNECTED) {
                it.copy(phase = LinkPhase.CONNECTING, error = null)
            } else {
                it.copy(error = null)
            }
        }
        newSession.start()
        val helloProfile = profileStore.load() ?: UserProfile("Ultra")
        val hello = WireProtocol.hello(
            profile = helloProfile,
            deviceId = profileStore.deviceId(),
            model = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            bluetoothName = bluetoothTransport.localName(),
            avatarPhoto = ImageCodec.avatarPayload(helloProfile.avatarPhoto),
        )
        if (!newSession.sendRaw(hello)) {
            newSession.close("Не удалось отправить HELLO")
        }
    }

    private fun handleFrame(session: LinkSession, frame: WireFrame) {
        when (frame) {
            is WireFrame.Hello -> handleHello(session, frame.value)
            is WireFrame.Chat -> handleChat(session, frame.value)
            is WireFrame.Ping, is WireFrame.Pong -> Unit
            else -> Unit
        }
    }

    private fun handleHello(session: LinkSession, hello: HelloFrame) {
        if (synchronized(sessionLock) { this.session !== session }) return
        val currentRemote = _state.value.remote
        if (currentRemote != null && currentRemote.deviceId != hello.deviceId) {
            session.close("Подключился другой собеседник")
            return
        }

        session.remoteHello = hello
        remoteBluetoothName = hello.bluetoothName
        val peer = RemotePeer(
            deviceId = hello.deviceId,
            nickname = hello.nickname,
            displayName = hello.displayName,
            isCreator = hello.isCreator,
            model = hello.model,
            avatarId = hello.avatarId,
            avatarPhoto = hello.avatarPhoto,
        )
        val reconnected = currentRemote != null &&
            currentRemote.deviceId == hello.deviceId &&
            _state.value.activeChannel == LinkChannel.NONE
        _state.update {
            it.copy(
                remote = peer,
                activeChannel = LinkChannel.BLUETOOTH,
                connectingTo = null,
                phase = LinkPhase.CONNECTED,
                lastSwitch = if (reconnected) {
                    "Bluetooth-соединение восстановлено"
                } else {
                    it.lastSwitch
                },
                error = null,
            )
        }
        cancelReconnect()
        listener.onRemotePeerReady(peer)
        flushQueue()
    }

    private fun handleChat(session: LinkSession, chat: ChatFrame) {
        val remote = _state.value.remote ?: return
        val sessionPeer = session.remoteHello ?: return
        if (sessionPeer.deviceId != remote.deviceId) return
        val displaySender = if (
            chat.isCreator &&
            !chat.sender.startsWith(UserProfile.CREATOR_PREFIX)
        ) {
            "${UserProfile.CREATOR_PREFIX} ${chat.sender}"
        } else {
            chat.sender
        }
        listener.onIncomingMessage(
            ChatMessage(
                id = chat.id,
                peerId = remote.deviceId,
                sender = displaySender,
                text = chat.text,
                timestamp = chat.sentAt,
                isMine = false,
                isCreator = chat.isCreator,
                channel = LinkChannel.BLUETOOTH,
                kind = chat.kind,
                imagePath = chat.image?.let { payload ->
                    ImageCodec.saveBase64(appContext, payload, "in_${chat.id}.jpg")?.absolutePath
                },
            ),
        )
    }

    private fun handleSessionClosed(closed: LinkSession, reason: String) {
        if (shuttingDown) return
        val wasCurrent = synchronized(sessionLock) {
            if (session !== closed) {
                false
            } else {
                session = null
                true
            }
        }
        if (!wasCurrent) return

        val current = _state.value
        val remote = current.remote
        _state.update {
            it.copy(
                activeChannel = LinkChannel.NONE,
                phase = when {
                    remote != null && autoReconnectEnabled -> LinkPhase.RECOVERING
                    remote != null -> LinkPhase.ERROR
                    current.connectingTo != null -> LinkPhase.ERROR
                    else -> LinkPhase.IDLE
                },
                lastSwitch = if (remote != null) {
                    if (autoReconnectEnabled) {
                        "Bluetooth-соединение потеряно. Пробуем восстановить…"
                    } else {
                        "Bluetooth-соединение потеряно"
                    }
                } else {
                    current.lastSwitch
                },
                error = if (remote == null && current.connectingTo != null) {
                    "Не удалось установить соединение: $reason"
                } else {
                    current.error
                },
                bluetooth = it.bluetooth.copy(
                    phase = if (it.bluetooth.phase == TransportPhase.DISABLED) {
                        TransportPhase.DISABLED
                    } else {
                        TransportPhase.READY
                    },
                    detail = reason,
                ),
            )
        }
        if (remote != null && autoReconnectEnabled) scheduleReconnect()
    }

    // ---------------------------------------------------------------------
    // События серверного транспорта
    // ---------------------------------------------------------------------

    override fun onServerStatus(status: TransportStatus, online: Int) {
        serverConnected = status.phase == TransportPhase.CONNECTED
        _state.update {
            it.copy(
                server = status,
                serverOnline = serverConnected,
                serverPeers = serverUsersCount,
            )
        }
    }

    override fun onServerToken(token: String) {
        listener.onServerToken(token)
    }

    override fun onServerWelcome(userId: String, displayName: String) {
        serverConnected = true
        flushServerQueue()
        _state.update {
            it.copy(
                serverOnline = true,
                server = TransportStatus(TransportPhase.CONNECTED, "Сервер · на связи"),
            )
        }
    }

    override fun onServerRoster(users: List<ServerUser>) {
        val me = serverTransport.currentUserId()
        val list = users
            .filter { it.id != me }
            .map { user ->
                NearbyPeer(
                    id = user.id,
                    name = user.displayName,
                    channel = LinkChannel.SERVER,
                    address = user.id,
                    detail = if (user.online) "Сервер · в сети" else "Сервер · не в сети",
                    isOnline = user.online,
                    avatarId = user.avatarId,
                    isCreator = user.isCreator,
                )
            }
        serverUsersCount = list.size
        serverPeers = list
        publishPeers()

        // Если открыт чат с серверным собеседником, обновляем его имя/аватар.
        val remote = _state.value.remote
        if (remote != null && _state.value.activeChannel == LinkChannel.SERVER) {
            val fresh = users.firstOrNull { it.id == remote.deviceId }
            if (fresh != null &&
                (fresh.displayName != remote.displayName ||
                    fresh.isCreator != remote.isCreator ||
                    fresh.avatarId != remote.avatarId)
            ) {
                val updated = remote.copy(
                    nickname = fresh.nickname,
                    displayName = fresh.displayName,
                    isCreator = fresh.isCreator,
                    avatarId = fresh.avatarId,
                )
                _state.update { it.copy(remote = updated) }
                listener.onRemotePeerReady(updated)
            }
        }
    }

    override fun onServerMessage(chat: ChatFrame) {
        val me = serverTransport.currentUserId()
        if (me.isBlank()) return
        if (chat.fromUserId == me) return
        // to = null — публичное сообщение общего чата.
        val lobby = chat.toUserId == null
        val peerId = if (lobby) {
            ChatMessage.LOBBY_ID
        } else {
            chat.fromUserId ?: chat.toUserId.takeIf { it != me } ?: return
        }
        // Скачивание фото может занять время и не должно блокировать
        // чтение WebSocket, иначе очередь сообщений встанет.
        if (chat.kind == ChatMessage.KIND_IMAGE) {
            scope.launch(Dispatchers.IO) {
                listener.onIncomingMessage(toChatMessage(chat, peerId, isMine = false))
            }
        } else {
            listener.onIncomingMessage(toChatMessage(chat, peerId, isMine = false))
        }
    }

    override fun onServerHistory(peer: String?, messages: List<ChatFrame>) {
        val lobby = peer.isNullOrBlank() || peer == ChatMessage.LOBBY_ID
        val cleanPeer = if (lobby) ChatMessage.LOBBY_ID else peer ?: return
        val me = serverTransport.currentUserId()
        scope.launch(Dispatchers.IO) {
            val mapped = messages.mapNotNull { chat ->
                if (lobby) {
                    toChatMessage(chat, ChatMessage.LOBBY_ID, isMine = chat.fromUserId == me)
                } else {
                    val mine = chat.fromUserId == me
                    val owner = if (mine) chat.toUserId ?: cleanPeer else chat.fromUserId ?: cleanPeer
                    if (owner != cleanPeer) null else toChatMessage(chat, cleanPeer, mine)
                }
            }
            if (mapped.isNotEmpty() || lobby) listener.onServerHistory(cleanPeer, mapped)
        }
    }

    override fun onServerAck(id: String, delivered: Boolean) {
        // Сервер сохранил сообщение в любом случае: недоставленное
        // придёт адресату из истории при следующем подключении.
    }

    override fun onServerRead(peer: String, upTo: Long) {
        listener.onServerRead(peer, upTo)
    }

    override fun onServerEdit(
        id: String,
        text: String,
        editedAt: Long,
        fromUserId: String?,
        toUserId: String?,
    ) {
        val peerId = peerFor(fromUserId, toUserId) ?: return
        listener.onServerMessageEdited(peerId, id, text, editedAt)
    }

    override fun onServerDelete(id: String, fromUserId: String?, toUserId: String?) {
        val peerId = peerFor(fromUserId, toUserId) ?: return
        listener.onServerMessageDeleted(peerId, id)
    }

    override fun onServerChatDeleted(peer: String) {
        listener.onServerChatDeleted(peer)
    }

    override fun onServerNotice(notice: ServerNotice) {
        listener.onServerNotice(notice)
    }

    override fun onServerNoticeCleared() {
        listener.onServerNoticeCleared()
    }

    override fun onServerLobbyCleared() {
        listener.onServerLobbyCleared()
    }

    /** Для события от сервера вычисляет, с какой перепиской связан кадр. */
    private fun peerFor(fromUserId: String?, toUserId: String?): String? {
        val me = serverTransport.currentUserId()
        // to = null означает сообщение общего чата.
        if (toUserId == null && fromUserId != null) return ChatMessage.LOBBY_ID
        return when {
            fromUserId != null && fromUserId != me -> fromUserId
            toUserId != null && toUserId != me -> toUserId
            else -> null
        }
    }

    override fun onServerError(code: String, message: String) {
        val friendly = when (code) {
            "bad_image" -> "Фото слишком большое или неподдерживаемого формата"
            "rate_limited" -> "Слишком много сообщений. Подождите пару секунд"
            "unknown_user" -> "Получатель не найден на сервере"
            "device_mismatch" -> "Токен сервера относится к другому устройству"
            "unauthorized" -> "Токен сервера недействителен"
            "hello_timeout" -> "Сервер не дождался рукопожатия"
            "account_deleted" -> "Аккаунт удалён администратором"
            else -> message
        }
        _state.update { it.copy(error = friendly) }
    }

    private fun toChatMessage(chat: ChatFrame, peerId: String, isMine: Boolean): ChatMessage {
        val displaySender = if (
            chat.isCreator &&
            !chat.sender.startsWith(UserProfile.CREATOR_PREFIX)
        ) {
            "${UserProfile.CREATOR_PREFIX} ${chat.sender}"
        } else {
            chat.sender
        }
        val localImage = chat.image?.let { path ->
            val safeId = chat.id.replace(Regex("[^A-Za-z0-9_-]"), "_").take(80)
            serverTransport.resolveMediaUrl(path)?.let { url ->
                ImageCodec.downloadToFile(appContext, url, "srv_$safeId.jpg")?.absolutePath
            }
        }
        val fallbackText = if (chat.kind == ChatMessage.KIND_IMAGE && localImage == null) {
            "📷 Фото"
        } else {
            chat.text
        }
        return ChatMessage(
            id = chat.id,
            peerId = peerId,
            sender = displaySender,
            text = fallbackText,
            timestamp = chat.sentAt,
            isMine = isMine,
            isCreator = chat.isCreator,
            channel = LinkChannel.SERVER,
            kind = if (localImage != null) chat.kind else ChatMessage.KIND_TEXT,
            imagePath = localImage,
            isRead = isMine && chat.readAt > 0L,
            editedAt = chat.editedAt,
        )
    }

    private fun publishPeers() {
        _state.update {
            it.copy(
                peers = serverPeers + bluetoothPeers,
                serverPeers = serverPeers.size,
                serverOnline = serverConnected,
            )
        }
    }

    private fun flushServerQueue() {
        if (!serverTransport.isConnected()) return
        val failed = mutableListOf<WireFrame.Chat>()
        synchronized(queueLock) {
            while (pendingServerFrames.isNotEmpty()) {
                val frame = pendingServerFrames.removeFirst()
                val value = frame.value
                val sent = serverTransport.sendChat(
                    to = value.toUserId,
                    id = value.id,
                    sender = value.sender,
                    isCreator = value.isCreator,
                    text = value.text,
                    sentAt = value.sentAt,
                    kind = value.kind,
                    imageBase64 = value.image,
                    mediaPath = value.media,
                )
                if (!sent) {
                    failed += frame
                    break
                }
            }
            failed.asReversed().forEach { pendingServerFrames.addFirst(it) }
        }
        updateQueuedCount()
    }

    // ---------------------------------------------------------------------
    // Автоматическое переподключение
    // ---------------------------------------------------------------------

    private fun scheduleReconnect() {
        if (!autoReconnectEnabled) return
        val remote = _state.value.remote ?: return
        if (reconnectJob?.isActive == true) return
        if (synchronized(sessionLock) { session != null }) return
        if (!bluetoothTransport.isReady()) return
        // Переподключение инициирует только сторона с меньшим deviceId,
        // иначе оба телефона одновременно создадут по два сокета.
        if (profileStore.deviceId() >= remote.deviceId) return
        if (remoteAddress.isBlank()) {
            remoteAddress = _state.value.peers.firstOrNull {
                it.name.equals(remoteBluetoothName, ignoreCase = true)
            }?.address.orEmpty()
        }
        val address = remoteAddress
        if (address.isBlank()) return

        reconnectJob = scope.launch(Dispatchers.IO) {
            repeat(MAX_RECONNECT_ATTEMPTS) { attempt ->
                delay(if (attempt == 0) FIRST_RECONNECT_DELAY_MS else RECONNECT_DELAY_MS)
                if (!isActive || shuttingDown) return@launch
                if (_state.value.remote?.deviceId != remote.deviceId) return@launch
                if (activeSession() != null) return@launch
                if (synchronized(sessionLock) { session != null }) return@launch
                if (!bluetoothTransport.isReady()) return@launch
                bluetoothTransport.connect(address)
                delay(CONNECT_WAIT_MS)
                if (activeSession() != null) return@launch
            }
        }
    }

    private fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
    }

    // ---------------------------------------------------------------------
    // Очередь, состояние и утилиты
    // ---------------------------------------------------------------------

    private fun activeSession(): LinkSession? = synchronized(sessionLock) {
        session?.takeIf { it.remoteHello != null && !it.closed }
    }

    private fun flushQueue() {
        scope.launch(Dispatchers.IO) {
            val remote = _state.value.remote ?: return@launch
            val active = activeSession() ?: return@launch
            val failed = mutableListOf<PendingFrame>()
            synchronized(queueLock) {
                while (pendingFrames.isNotEmpty()) {
                    val item = pendingFrames.removeFirst()
                    if (item.peerId != remote.deviceId) continue
                    if (!active.send(item.frame)) {
                        failed += item
                        break
                    }
                }
                failed.asReversed().forEach { pendingFrames.addFirst(it) }
            }
            updateQueuedCount()
        }
    }

    private fun clearPending() {
        synchronized(queueLock) {
            pendingFrames.clear()
            pendingServerFrames.clear()
        }
    }

    private fun updateQueuedCount() {
        val count = synchronized(queueLock) {
            pendingFrames.size + pendingServerFrames.size
        }
        _state.update { it.copy(queuedMessages = count) }
    }

    private fun closeSession(reason: String) {
        val current = synchronized(sessionLock) {
            val copy = session
            session = null
            copy
        }
        current?.close(reason)
    }

    private data class PendingFrame(
        val peerId: String,
        val frame: WireFrame.Chat,
    )

    private companion object {
        const val SCAN_DURATION_MS = 30_000L
        const val MAX_RECONNECT_ATTEMPTS = 4
        const val FIRST_RECONNECT_DELAY_MS = 1_200L
        const val RECONNECT_DELAY_MS = 3_500L
        const val CONNECT_WAIT_MS = 6_000L
    }
}
