package com.ultrasys.ultrachat.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.util.Log
import com.ultrasys.ultrachat.data.AvatarPresets
import com.ultrasys.ultrachat.data.ImageCodec
import com.ultrasys.ultrachat.data.ProfileStore
import com.ultrasys.ultrachat.data.UserProfile
import com.ultrasys.ultrachat.util.NoDelaySocketFactory
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

/**
 * Канал через UltraChatServer: регистрация, WebSocket, ростер и личные
 * сообщения. Сервер работает как релей, поэтому собеседник может быть
 * в любой точке мира, а не только рядом по Bluetooth.
 */
class ServerTransport(
    context: Context,
    private val scope: CoroutineScope,
    private val profileStore: ProfileStore,
    private val listener: Listener,
) {

    interface Listener {
        fun onServerStatus(status: TransportStatus, online: Int)
        fun onServerToken(token: String)
        fun onServerWelcome(userId: String, displayName: String)
        fun onServerRoster(users: List<ServerUser>)
        fun onServerMessage(chat: ChatFrame)
        fun onServerHistory(peer: String?, messages: List<ChatFrame>)
        fun onServerAck(id: String, delivered: Boolean)
        fun onServerRead(peer: String, upTo: Long)
        fun onServerEdit(
            id: String,
            text: String,
            editedAt: Long,
            fromUserId: String?,
            toUserId: String?,
        )
        fun onServerDelete(id: String, fromUserId: String?, toUserId: String?)
        fun onServerChatDeleted(peer: String)
        fun onServerNotice(notice: ServerNotice)
        fun onServerNoticeCleared()
        fun onServerLobbyCleared()
        fun onServerError(code: String, message: String)
    }

    private val appContext = context.applicationContext
    private val client = OkHttpClient.Builder()
        .socketFactory(NoDelaySocketFactory)
        // Через Funnel первый запрос иногда ждёт установки TLS к краю
        // Tailscale до 18 секунд. Со старым лимитом 15 с регистрация
        // срывалась, и телефон мог вообще не подключиться.
        .connectTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        // Пинг чаще: «мёртвый» сокет (вышка сменилась, Wi-Fi пропал)
        // обнаруживается за ~30 секунд вместо минуты.
        .pingInterval(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        // Туннель (localtunnel) показывает браузерам страницу-заглушку.
        // Заголовок отключает её для API, WebSocket и скачивания фото.
        .addInterceptor { chain ->
            chain.proceed(
                chain.request()
                    .newBuilder()
                    .header("bypass-tunnel-reminder", "true")
                    .build(),
            )
        }
        .build()

    // Загрузка фото идёт отдельным HTTP-запросом, чтобы не блокировать
    // WebSocket с текстом. У обычного client readTimeout = 0 (для WS),
    // поэтому здесь нужен свой лимит.
    private val uploadClient: OkHttpClient by lazy {
        client.newBuilder()
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .callTimeout(35, TimeUnit.SECONDS)
            .build()
    }

    @Volatile
    private var baseUrl: String = ""

    @Volatile
    private var token: String = ""

    @Volatile
    private var userId: String = ""

    @Volatile
    private var enabled: Boolean = false

    @Volatile
    private var shuttingDown: Boolean = false

    @Volatile
    private var socket: WebSocket? = null

    @Volatile
    private var welcomed: Boolean = false

    @Volatile
    private var onlineCount: Int = 0

    private var connectJob: Job? = null
    private var reconnectJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var activeKey: String = ""
    private var reconnectAttempt: Int = 0

    fun isConnected(): Boolean = socket != null && welcomed

    fun currentUserId(): String = userId

    fun currentUrl(): String = baseUrl

    /**
     * Запускает канал. Повторный вызов с теми же адресом и токеном ничего
     * не перезапускает, чтобы настройки не рвали соединение зря.
     */
    fun start(rawUrl: String, rawToken: String) {
        val normalized = normalizeServerUrl(rawUrl)
        if (normalized == null) {
            stop()
            listener.onServerStatus(
                TransportStatus(TransportPhase.DISABLED, "Укажите адрес сервера"),
                0,
            )
            return
        }
        val cleanToken = rawToken.trim()
        val key = "$normalized|$cleanToken"
        if (enabled && key == activeKey && (socket != null || connectJob?.isActive == true)) {
            return
        }
        activeKey = key
        baseUrl = normalized
        token = cleanToken
        enabled = true
        shuttingDown = false
        welcomed = false
        reconnectAttempt = 0
        reconnectJob?.cancel()
        listener.onServerStatus(
            TransportStatus(TransportPhase.CONNECTING, "Подключение к серверу…"),
            0,
        )
        watchNetwork()
        ensureConnected(forceRegister = cleanToken.isBlank())
    }

    fun stop() {
        enabled = false
        shuttingDown = true
        welcomed = false
        reconnectJob?.cancel()
        connectJob?.cancel()
        unwatchNetwork()
        val current = socket
        socket = null
        runCatching { current?.close(1000, "client stop") }
        listener.onServerStatus(
            TransportStatus(TransportPhase.DISABLED, "Сервер отключён"),
            0,
        )
    }

    fun refreshIdentity() {
        val current = socket ?: return
        if (!welcomed) return
        val hello = helloFrame()
        runCatching { current.send(hello) }
    }

    fun requestHistory(peerId: String, limit: Int = 60): Boolean {
        val current = activeSocket() ?: return false
        return current.send(WireProtocol.historyRequest(peerId, limit))
    }

    /**
     * Загружает фото на сервер отдельным HTTP-запросом.
     * @return путь вида `/media/...` или null, если загрузка не удалась.
     */
    suspend fun uploadImage(bytes: ByteArray, mimeType: String): String? =
        withContext(Dispatchers.IO) {
            if (bytes.isEmpty()) return@withContext null
            val base = baseUrl.trimEnd('/')
            val currentToken = token
            if (base.isBlank() || currentToken.isBlank()) return@withContext null
            val url = "$base/api/media?token=" + URLEncoder.encode(currentToken, "UTF-8")
            val body = bytes.toRequestBody(mimeType.toMediaType())
            val request = Request.Builder().url(url).post(body).build()
            runCatching {
                uploadClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val text = response.body?.string().orEmpty()
                    val json = runCatching { JSONObject(text) }.getOrNull()
                        ?: return@use null
                    if (!json.optBoolean("ok", false)) return@use null
                    json.optString("path").takeIf { it.startsWith("/media/") }
                }
            }.getOrNull()
        }

    /**
     * Поиск людей на сервере для кнопки «+». Сервер отдаёт только совпадения,
     * поэтому в списке не оказываются все, кто когда-либо регистрировался.
     */
    suspend fun searchUsers(query: String): List<ServerUser> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.length < 2) return@withContext emptyList()
        val url = "${baseUrl.trimEnd('/')}/api/search"
            .toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("q", clean)
            ?.addQueryParameter("token", token)
            ?.build()
            ?: throw IOException("Некорректный адрес сервера")
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Сервер ответил ${response.code}")
            val json = JSONObject(response.body?.string().orEmpty())
            if (!json.optBoolean("ok", false)) {
                throw IOException(json.optString("error").ifBlank { "Поиск недоступен" })
            }
            val array = json.optJSONArray("users") ?: JSONArray()
            buildList {
                for (index in 0 until minOf(array.length(), 20)) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id").take(96)
                    if (id.isBlank()) continue
                    val nickname = item.optString("nickname").take(48)
                    add(
                        ServerUser(
                            id = id,
                            nickname = nickname,
                            displayName = item.optString("displayName").take(96)
                                .ifBlank { nickname },
                            isCreator = item.optBoolean("creator", false),
                            avatarId = AvatarPresets.normalizedId(
                                item.optString("avatar").take(32),
                            ),
                            online = item.optBoolean("online", false),
                        ),
                    )
                }
            }
        }
    }

    fun sendChat(
        to: String?,
        id: String,
        sender: String,
        isCreator: Boolean,
        text: String,
        sentAt: Long,
        kind: String,
        imageBase64: String?,
        mediaPath: String? = null,
    ): Boolean {
        val current = activeSocket() ?: return false
        val payload = WireProtocol.chat(
            id = id,
            sender = sender,
            isCreator = isCreator,
            text = text,
            sentAt = sentAt,
            to = to,
            kind = kind,
            image = imageBase64,
            media = mediaPath,
        )
        // Лимит WS-кадра на сервере — 4 МБ, фото сжимаем до 300 КБ.
        if (payload.length > MAX_SEND_FRAME) return false
        return current.send(payload)
    }

    fun sendTyping(to: String): Boolean {
        val current = activeSocket() ?: return false
        return current.send(WireProtocol.typing(to))
    }

    fun markRead(peer: String, upTo: Long): Boolean {
        if (peer.isBlank() || upTo <= 0L) return false
        val current = activeSocket() ?: return false
        return current.send(WireProtocol.read(peer, upTo))
    }

    fun editMessage(id: String, text: String): Boolean {
        if (id.isBlank() || text.isBlank()) return false
        val current = activeSocket() ?: return false
        return current.send(WireProtocol.edit(id, text))
    }

    fun deleteMessage(id: String): Boolean {
        if (id.isBlank()) return false
        val current = activeSocket() ?: return false
        return current.send(WireProtocol.deleteMessage(id))
    }

    fun deleteChat(peer: String): Boolean {
        if (peer.isBlank()) return false
        val current = activeSocket() ?: return false
        return current.send(WireProtocol.deleteChat(peer))
    }

    fun resolveMediaUrl(path: String?): String? {
        val clean = path?.trim().orEmpty()
        if (clean.isEmpty()) return null
        if (clean.startsWith("http://") || clean.startsWith("https://")) return clean
        if (baseUrl.isEmpty()) return null
        return if (clean.startsWith("/")) "$baseUrl$clean" else "$baseUrl/$clean"
    }

    private fun activeSocket(): WebSocket? =
        socket?.takeIf { welcomed && enabled && !shuttingDown }

    private fun ensureConnected(forceRegister: Boolean) {
        if (!enabled || shuttingDown) return
        if (connectJob?.isActive == true) return
        connectJob = scope.launch(Dispatchers.IO) {
            Log.d(TAG, "ensureConnected url=$baseUrl token=${if (token.isBlank()) "нет" else "есть"} force=$forceRegister")
            if (token.isBlank() || forceRegister) {
                var registered = false
                for (attempt in 1..REGISTER_ATTEMPTS) {
                    registered = registerBlocking()
                    if (registered) break
                    if (attempt < REGISTER_ATTEMPTS) delay(REGISTER_RETRY_MS)
                }
                if (!registered) {
                    Log.w(TAG, "регистрация не удалась: $lastHttpError")
                    notifyFailure(
                        lastHttpError.ifBlank { "Не удалось зарегистрироваться на сервере" },
                        null,
                        reconnect = true,
                    )
                    return@launch
                }
            }
            openSocket()
        }
    }

    private fun registerBlocking(): Boolean {
        val profile = profileStore.load() ?: return false
        val body = JSONObject()
            .put("deviceId", profileStore.deviceId())
            .put("nickname", profile.nickname)
            .put("displayName", profile.displayName)
            .put("avatarId", profile.avatarId)
            .put("creator", profile.isCreator)
            .put("model", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
            .put("token", token)
            .toString()
        val request = Request.Builder()
            .url("$baseUrl/api/register")
            .post(body.toRequestBody(JSON))
            .build()
        return try {
            Log.d(TAG, "POST $baseUrl/api/register")
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                Log.d(TAG, "register ответ ${response.code}")
                if (!response.isSuccessful) {
                    lastHttpError = "Сервер ответил ${response.code}"
                    return false
                }
                val json = runCatching { JSONObject(text) }.getOrElse {
                    lastHttpError = "Сервер вернул непонятный ответ"
                    return false
                }
                if (!json.optBoolean("ok", false)) {
                    lastHttpError = json.optString("error").ifBlank { "Ошибка регистрации" }
                    return false
                }
                val issued = json.optString("token")
                if (issued.isNotBlank() && issued != token) {
                    token = issued
                    listener.onServerToken(issued)
                }
                val id = json.optString("userId")
                if (id.isNotBlank()) userId = id
                true
            }
        } catch (err: Exception) {
            lastHttpError = err.message ?: "Нет связи с сервером"
            false
        }
    }

    private fun openSocket() {
        if (!enabled || shuttingDown || token.isBlank()) return
        val wsUrl = baseUrl
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://") +
            "/ws?token=" + URLEncoder.encode(token, "UTF-8")
        val request = Request.Builder().url(wsUrl).build()
        val previous = socket
        socket = null
        welcomed = false
        runCatching { previous?.close(1000, "reconnect") }
        val ws = client.newWebSocket(request, SocketListener())
        socket = ws
    }

    private inner class SocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (socket !== webSocket) return
            Log.d(TAG, "ws открыт ${webSocket.request().url.redact()}")
            webSocket.send(helloFrame())
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (socket !== webSocket) return
            val frame = WireProtocol.parse(text) ?: return
            when (frame) {
                is WireFrame.Welcome -> {
                    welcomed = true
                    Log.d(TAG, "welcome от сервера")
                    userId = frame.userId
                    reconnectAttempt = 0
                    onlineCount = frame.online
                    listener.onServerWelcome(frame.userId, frame.displayName)
                    listener.onServerStatus(
                        TransportStatus(TransportPhase.CONNECTED, "Сервер · на связи"),
                        frame.online,
                    )
                }

                is WireFrame.Roster -> {
                    onlineCount = frame.users.count { it.online }
                    listener.onServerRoster(frame.users)
                    listener.onServerStatus(
                        TransportStatus(TransportPhase.CONNECTED, "Сервер · на связи"),
                        onlineCount,
                    )
                }

                is WireFrame.Chat -> listener.onServerMessage(frame.value)
                is WireFrame.History -> listener.onServerHistory(frame.peer, frame.messages)
                is WireFrame.Ack -> listener.onServerAck(frame.id, frame.delivered)
                is WireFrame.ReadReceipt -> listener.onServerRead(frame.peer, frame.upTo)
                is WireFrame.Edit -> listener.onServerEdit(
                    frame.id,
                    frame.text,
                    frame.editedAt,
                    frame.fromUserId,
                    frame.toUserId,
                )
                is WireFrame.Delete -> listener.onServerDelete(
                    frame.id,
                    frame.fromUserId,
                    frame.toUserId,
                )
                is WireFrame.ChatDeleted -> listener.onServerChatDeleted(frame.peer)
                is WireFrame.Notice -> listener.onServerNotice(frame.value)
                is WireFrame.NoticeCleared -> listener.onServerNoticeCleared()
                is WireFrame.LobbyCleared -> listener.onServerLobbyCleared()
                is WireFrame.ServerError -> {
                    if (frame.code == "account_deleted") {
                        // Аккаунт удалён администратором: старый токен больше
                        // недействителен, при следующем подключении сервер
                        // создаст новый аккаунт для этого устройства.
                        token = ""
                        listener.onServerToken("")
                    }
                    listener.onServerError(frame.code, frame.message)
                }
                is WireFrame.Ping -> runCatching { webSocket.send(WireProtocol.pong()) }
                else -> Unit
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            runCatching { webSocket.close(1000, null) }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (socket !== webSocket) return
            Log.d(TAG, "ws закрыт код=$code причина=$reason")
            socket = null
            welcomed = false
            if (!enabled || shuttingDown) return
            listener.onServerStatus(
                TransportStatus(TransportPhase.CONNECTING, "Соединение с сервером потеряно"),
                onlineCount,
            )
            scheduleReconnect(forceRegister = false)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (socket !== webSocket) return
            Log.w(TAG, "ws ошибка http=${response?.code} ${t.javaClass.simpleName}: ${t.message}")
            socket = null
            welcomed = false
            if (!enabled || shuttingDown) return
            val code = response?.code
            if (code == 401 || code == 403) {
                token = ""
                listener.onServerToken("")
            }
            notifyFailure(
                message = t.message?.takeIf { it.isNotBlank() } ?: "Нет связи с сервером",
                httpCode = code,
                reconnect = true,
            )
        }
    }

    private fun notifyFailure(message: String, httpCode: Int?, reconnect: Boolean) {
        if (!enabled || shuttingDown) return
        val detail = if (httpCode != null) "$message (HTTP $httpCode)" else message
        listener.onServerStatus(
            TransportStatus(TransportPhase.ERROR, detail),
            onlineCount,
        )
        if (reconnect) scheduleReconnect(forceRegister = token.isBlank())
    }

    private fun scheduleReconnect(forceRegister: Boolean) {
        if (!enabled || shuttingDown) return
        if (reconnectJob?.isActive == true) return
        reconnectAttempt += 1
        // 1, 2, 4, 8, 8… секунд вместо 2, 4, … 20: после короткого сбоя
        // связь возвращается почти сразу, а не через полминуты.
        val backoff = (RECONNECT_BASE_MS shl (reconnectAttempt - 1).coerceIn(0, 3))
            .coerceAtMost(RECONNECT_MAX_MS)
        reconnectJob = scope.launch(Dispatchers.IO) {
            delay(backoff)
            if (!enabled || shuttingDown) return@launch
            if (socket != null) return@launch
            listener.onServerStatus(
                TransportStatus(
                    TransportPhase.CONNECTING,
                    if (reconnectAttempt <= 1) "Переподключение…" else "Повторная попытка…",
                ),
                onlineCount,
            )
            ensureConnected(forceRegister = forceRegister || token.isBlank())
        }
    }

    /**
     * Android не всегда сообщает о потере сети вовремя: после выхода из
     * «мёртвой» зоны сокет может висеть, а приложение — ждать таймаута.
     * Слушаем системные события и подключаемся сразу, как сеть появилась.
     */
    private fun watchNetwork() {
        if (networkCallback != null) return
        val manager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                reconnectNow()
            }

            override fun onLost(network: Network) {
                dropDeadSocket()
            }
        }
        runCatching {
            manager.registerDefaultNetworkCallback(callback)
            networkCallback = callback
        }
    }

    private fun unwatchNetwork() {
        val callback = networkCallback ?: return
        networkCallback = null
        val manager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return
        runCatching { manager.unregisterNetworkCallback(callback) }
    }

    /** Сеть вернулась — подключаемся немедленно, без ожидания бэкоффа. */
    private fun reconnectNow() {
        if (!enabled || shuttingDown) return
        if (socket != null) return
        reconnectAttempt = 0
        reconnectJob?.cancel()
        listener.onServerStatus(
            TransportStatus(TransportPhase.CONNECTING, "Сеть есть, подключаюсь…"),
            onlineCount,
        )
        ensureConnected(forceRegister = token.isBlank())
    }

    /** Сеть пропала: закрываем сокет, чтобы не ждать пинга-таймаута. */
    private fun dropDeadSocket() {
        if (!enabled || shuttingDown) return
        val current = socket ?: return
        socket = null
        welcomed = false
        runCatching { current.cancel() }
        listener.onServerStatus(
            TransportStatus(TransportPhase.CONNECTING, "Нет сети, ждём подключения…"),
            onlineCount,
        )
    }

    private var lastHttpError: String = ""

    private fun helloFrame(): String {
        val profile = profileStore.load() ?: UserProfile("Ultra")
        return WireProtocol.hello(
            profile = profile,
            deviceId = profileStore.deviceId(),
            model = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            bluetoothName = "",
            avatarPhoto = ImageCodec.avatarPayload(profile.avatarPhoto),
        )
    }

    private companion object {
        const val TAG = "UltraChatNet"
        val JSON = "application/json; charset=utf-8".toMediaType()
        const val MAX_SEND_FRAME = 3_600_000
        const val RECONNECT_BASE_MS = 1_000L
        const val RECONNECT_MAX_MS = 8_000L
        const val REGISTER_ATTEMPTS = 3
        const val REGISTER_RETRY_MS = 2_000L
    }
}

/**
 * Приводит введённый адрес к виду https://host без хвостов и путей.
 * Понимает "host:8080", "http://host/ws" и полноценный URL.
 */
fun normalizeServerUrl(raw: String): String? {
    var value = raw.trim()
    if (value.isEmpty()) return null
    if (!value.startsWith("http://", ignoreCase = true) &&
        !value.startsWith("https://", ignoreCase = true)
    ) {
        val authority = value.substringBefore('/')
        val hostPart = authority.substringBefore(':')
        val local = hostPart.equals("localhost", ignoreCase = true) ||
            hostPart.endsWith(".local", ignoreCase = true) ||
            hostPart.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$"))
        value = (if (local) "http://" else "https://") + value
    }
    val parsed = value.toHttpUrlOrNull() ?: return null
    if (parsed.host.isBlank()) return null
    return parsed.newBuilder()
        .encodedPath("/")
        .query(null)
        .fragment(null)
        .build()
        .toString()
        .trimEnd('/')
}
