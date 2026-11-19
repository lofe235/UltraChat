package com.ultrasys.ultrachat.net

import android.content.Context
import com.ultrasys.ultrachat.data.ChatHistoryStore
import com.ultrasys.ultrachat.data.ChatMessage
import com.ultrasys.ultrachat.data.ProfileStore
import com.ultrasys.ultrachat.data.SettingsStore
import com.ultrasys.ultrachat.util.MessageNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Соединение, которое живёт дольше экрана.
 *
 * Раньше диспетчер связи создавался внутри ViewModel и умирал вместе с
 * активити: свернул приложение — Android выгрузил экран, сокет закрылся,
 * уведомления перестали приходить. Теперь диспетчер один на процесс и
 * работает на области приложения, а экран лишь подписывается на события.
 *
 * Если экрана нет (приложение свёрнуто и активити уничтожена), входящие
 * сообщения сохраняются в историю и показываются уведомлением здесь.
 */
object ConnectionHub {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var appContext: Context
    private lateinit var historyStore: ChatHistoryStore
    private lateinit var settingsStore: SettingsStore

    private var managerInstance: SmartLinkManager? = null

    /** Текущий экран, если он открыт и готов принимать события. */
    @Volatile
    private var uiListener: SmartLinkManager.Listener? = null

    private val bridge = object : SmartLinkManager.Listener {
        override fun onRemotePeerReady(peer: RemotePeer) {
            uiListener?.onRemotePeerReady(peer)
        }

        override fun onIncomingMessage(message: ChatMessage) {
            val ui = uiListener
            if (ui != null) {
                ui.onIncomingMessage(message)
                return
            }
            deliverInBackground(message)
        }

        override fun onLocalMessage(message: ChatMessage) {
            val ui = uiListener
            if (ui != null) {
                ui.onLocalMessage(message)
                return
            }
            scope.launch(Dispatchers.IO) {
                historyStore.append(message.peerId, message)
            }
        }

        override fun onServerToken(token: String) {
            val ui = uiListener
            if (ui != null) {
                ui.onServerToken(token)
                return
            }
            // Токен обновился, пока экрана нет: сохраняем сами, иначе после
            // следующего запуска приложение будет стучаться со старым.
            val current = settingsStore.load()
            if (current.serverToken == token) return
            settingsStore.save(current.copy(serverToken = token))
        }

        override fun onServerHistory(peerId: String, messages: List<ChatMessage>) {
            val ui = uiListener
            if (ui != null) {
                ui.onServerHistory(peerId, messages)
                return
            }
            scope.launch(Dispatchers.IO) {
                messages.forEach { historyStore.append(peerId, it) }
                messages.lastOrNull { !it.isMine }?.let { remote ->
                    historyStore.savePeer(peerId, remote.sender, remote.isCreator)
                }
            }
        }

        override fun onServerMessageEdited(
            peerId: String,
            id: String,
            text: String,
            editedAt: Long,
        ) {
            val ui = uiListener
            if (ui != null) {
                ui.onServerMessageEdited(peerId, id, text, editedAt)
                return
            }
            scope.launch(Dispatchers.IO) {
                historyStore.updateEdited(peerId, id, text, editedAt)
            }
        }

        override fun onServerMessageDeleted(peerId: String, id: String) {
            val ui = uiListener
            if (ui != null) {
                ui.onServerMessageDeleted(peerId, id)
                return
            }
            scope.launch(Dispatchers.IO) {
                historyStore.removeMessage(peerId, id)
            }
        }

        override fun onServerChatDeleted(peerId: String) {
            val ui = uiListener
            if (ui != null) {
                ui.onServerChatDeleted(peerId)
                return
            }
            scope.launch(Dispatchers.IO) {
                historyStore.deleteThread(peerId)
            }
            MessageNotifier.cancel(appContext, peerId)
        }

        override fun onServerRead(peerId: String, upTo: Long) {
            val ui = uiListener
            if (ui != null) {
                ui.onServerRead(peerId, upTo)
                return
            }
            scope.launch(Dispatchers.IO) {
                historyStore.markRead(peerId, upTo)
            }
        }

        override fun onServerNotice(notice: ServerNotice) {
            uiListener?.onServerNotice(notice)
        }

        override fun onServerNoticeCleared() {
            uiListener?.onServerNoticeCleared()
        }

        override fun onServerLobbyCleared() {
            val ui = uiListener
            if (ui != null) {
                ui.onServerLobbyCleared()
                return
            }
            scope.launch(Dispatchers.IO) {
                historyStore.deleteThread(ChatMessage.LOBBY_ID)
            }
            MessageNotifier.cancel(appContext, ChatMessage.LOBBY_ID)
        }
    }

    fun init(context: Context) {
        if (managerInstance != null) return
        val app = context.applicationContext
        appContext = app
        historyStore = ChatHistoryStore(app)
        settingsStore = SettingsStore(app)
        managerInstance = SmartLinkManager(
            context = app,
            scope = scope,
            profileStore = ProfileStore(app),
            listener = bridge,
        ).also { manager ->
            // Связь должна подниматься сама, в том числе когда процесс
            // перезапустил сервис, а экрана ещё нет.
            val settings = settingsStore.load()
            manager.setAutoReconnect(settings.autoReconnect)
            if (settings.serverUrl.isNotBlank()) {
                manager.configureServer(settings.serverUrl, settings.serverToken)
            }
        }
    }

    val manager: SmartLinkManager
        get() = checkNotNull(managerInstance) { "ConnectionHub.init() не вызван" }

    fun attach(listener: SmartLinkManager.Listener) {
        uiListener = listener
    }

    fun detach(listener: SmartLinkManager.Listener) {
        if (uiListener === listener) uiListener = null
    }

    /** Есть ли сейчас экран, который сам показывает входящие. */
    fun hasUi(): Boolean = uiListener != null

    private fun deliverInBackground(message: ChatMessage) {
        val lobby = message.peerId == ChatMessage.LOBBY_ID
        val title = if (lobby) "Общий чат" else message.sender
        val body = when {
            lobby && message.isImage -> "${message.sender}: 📷 Фото"
            lobby -> "${message.sender}: ${message.text}"
            message.isImage -> "📷 Фото"
            else -> message.text
        }
        scope.launch(Dispatchers.IO) {
            historyStore.savePeer(
                message.peerId,
                if (lobby) "Общий чат" else message.sender,
                message.isCreator,
            )
            historyStore.append(message.peerId, message)
        }
        MessageNotifier.notifyMessage(
            context = appContext,
            peerId = message.peerId,
            title = title,
            body = body,
            isCreator = message.isCreator && !lobby,
        )
    }
}
