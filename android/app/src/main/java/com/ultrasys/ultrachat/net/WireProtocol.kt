package com.ultrasys.ultrachat.net

import com.ultrasys.ultrachat.data.AvatarPresets
import com.ultrasys.ultrachat.data.ChatMessage
import com.ultrasys.ultrachat.data.UserProfile
import org.json.JSONArray
import org.json.JSONObject

data class HelloFrame(
    val deviceId: String,
    val nickname: String,
    val displayName: String,
    val isCreator: Boolean,
    val model: String,
    val bluetoothName: String,
    val avatarId: String,
    val sentAt: Long,
    val avatarPhoto: String? = null,
)

data class ChatFrame(
    val id: String,
    val sender: String,
    val isCreator: Boolean,
    val text: String,
    val sentAt: Long,
    val fromUserId: String? = null,
    val toUserId: String? = null,
    val kind: String = ChatMessage.KIND_TEXT,
    val image: String? = null,
    val media: String? = null,
    val readAt: Long = 0L,
    val editedAt: Long = 0L,
)

data class ServerUser(
    val id: String,
    val nickname: String,
    val displayName: String,
    val isCreator: Boolean,
    val avatarId: String,
    val online: Boolean,
)

/** Уведомление от администратора (как на веб-версии). */
data class ServerNotice(
    val id: String,
    val title: String,
    val text: String,
    val version: String,
    val url: String,
    val createdAt: Long,
)

sealed interface WireFrame {
    data class Hello(val value: HelloFrame) : WireFrame
    data class Chat(val value: ChatFrame) : WireFrame
    data class Ping(val sentAt: Long) : WireFrame
    data class Pong(val sentAt: Long) : WireFrame

    data class Welcome(
        val userId: String,
        val nickname: String,
        val displayName: String,
        val isCreator: Boolean,
        val avatarId: String,
        val online: Int,
    ) : WireFrame

    data class Roster(val users: List<ServerUser>) : WireFrame
    data class History(val peer: String?, val messages: List<ChatFrame>) : WireFrame
    data class Ack(val id: String, val delivered: Boolean) : WireFrame
    data class ReadReceipt(val peer: String, val upTo: Long) : WireFrame
    data class Edit(
        val id: String,
        val text: String,
        val editedAt: Long,
        val fromUserId: String?,
        val toUserId: String?,
    ) : WireFrame
    data class Delete(
        val id: String,
        val fromUserId: String?,
        val toUserId: String?,
    ) : WireFrame
    data class ChatDeleted(val peer: String) : WireFrame
    data class Notice(val value: ServerNotice) : WireFrame
    data class NoticeCleared(val at: Long) : WireFrame
    data class LobbyCleared(val at: Long) : WireFrame
    data class ServerError(val code: String, val message: String) : WireFrame
}

/**
 * Небольшой построчный JSON-протокол. JSON сам экранирует переводы строк,
 * поэтому BufferedReader.readLine() работает как надёжный фрейминг.
 *
 * Bluetooth использует только hello/chat/ping/pong. Сервер добавляет
 * welcome/roster/history/ack/error — старые клиенты их просто игнорируют.
 */
object WireProtocol {
    const val VERSION = 1
    const val MAX_TEXT_LENGTH = 4_000
    const val MAX_FRAME_LENGTH = 1_400_000

    fun hello(
        profile: UserProfile,
        deviceId: String,
        model: String,
        bluetoothName: String,
        avatarPhoto: String? = null,
    ): String = JSONObject()
        .put("v", VERSION)
        .put("type", "hello")
        .put("deviceId", deviceId)
        .put("nickname", profile.nickname)
        .put("displayName", profile.displayName)
        .put("creator", profile.isCreator)
        .put("avatar", profile.avatarId)
        .put("avatarPhoto", avatarPhoto ?: JSONObject.NULL)
        .put("model", model)
        .put("btName", bluetoothName)
        .put("ts", System.currentTimeMillis())
        .toString()

    fun chat(
        id: String,
        sender: String,
        isCreator: Boolean,
        text: String,
        sentAt: Long = System.currentTimeMillis(),
        to: String? = null,
        kind: String = ChatMessage.KIND_TEXT,
        image: String? = null,
        media: String? = null,
    ): String = JSONObject()
        .put("v", VERSION)
        .put("type", "chat")
        .put("id", id)
        .put("sender", sender)
        .put("creator", isCreator)
        .put("text", text.take(MAX_TEXT_LENGTH))
        .put("kind", kind)
        .put("image", image ?: JSONObject.NULL)
        .put("media", media ?: JSONObject.NULL)
        .put("ts", sentAt)
        .put("to", to ?: JSONObject.NULL)
        .toString()

    fun read(peer: String, upTo: Long): String = JSONObject()
        .put("v", VERSION)
        .put("type", "read")
        .put("peer", peer)
        .put("upTo", upTo)
        .toString()

    fun edit(id: String, text: String): String = JSONObject()
        .put("v", VERSION)
        .put("type", "edit")
        .put("id", id)
        .put("text", text.take(MAX_TEXT_LENGTH))
        .toString()

    fun deleteMessage(id: String): String = JSONObject()
        .put("v", VERSION)
        .put("type", "delete")
        .put("id", id)
        .toString()

    fun deleteChat(peer: String): String = JSONObject()
        .put("v", VERSION)
        .put("type", "delete_chat")
        .put("peer", peer)
        .toString()

    fun historyRequest(peer: String?, limit: Int = 100): String = JSONObject()
        .put("v", VERSION)
        .put("type", "history")
        .put("peer", peer ?: "lobby")
        .put("limit", limit)
        .toString()

    fun typing(to: String): String = JSONObject()
        .put("v", VERSION)
        .put("type", "typing")
        .put("to", to)
        .toString()

    fun ping(): String = JSONObject()
        .put("v", VERSION)
        .put("type", "ping")
        .put("ts", System.currentTimeMillis())
        .toString()

    fun pong(): String = JSONObject()
        .put("v", VERSION)
        .put("type", "pong")
        .put("ts", System.currentTimeMillis())
        .toString()

    fun parse(raw: String): WireFrame? {
        if (raw.length > MAX_FRAME_LENGTH) return null
        return runCatching {
            val json = JSONObject(raw)
            if (json.optInt("v", 0) != VERSION) return@runCatching null
            when (json.optString("type")) {
                "hello" -> parseHello(json)
                "chat" -> parseChat(json)?.let { WireFrame.Chat(it) }
                "welcome" -> parseWelcome(json)
                "roster" -> parseRoster(json)
                "history" -> parseHistory(json)
                "ack" -> parseAck(json)
                "read" -> parseReadReceipt(json)
                "edit" -> parseEdit(json)
                "delete" -> parseDelete(json)
                "chat_deleted" -> parseChatDeleted(json)
                "notice" -> parseNotice(json)
                "notice_cleared" -> WireFrame.NoticeCleared(json.optLong("at", System.currentTimeMillis()))
                "lobby_cleared" -> WireFrame.LobbyCleared(json.optLong("at", System.currentTimeMillis()))
                "error" -> parseError(json)
                "ping" -> WireFrame.Ping(json.optLong("ts"))
                "pong" -> WireFrame.Pong(json.optLong("ts"))
                else -> null
            }
        }.getOrNull()
    }

    private fun parseHello(json: JSONObject): WireFrame? {
        val deviceId = json.optString("deviceId").take(96)
        val nickname = json.optString("nickname").take(48)
        if (deviceId.isBlank() || nickname.isBlank()) return null
        val isCreator = json.optBoolean("creator", false)
        val displayName = json.optString("displayName").ifBlank {
            if (isCreator) "${UserProfile.CREATOR_PREFIX} $nickname" else nickname
        }.take(96)
        return WireFrame.Hello(
            HelloFrame(
                deviceId = deviceId,
                nickname = nickname,
                displayName = displayName,
                isCreator = isCreator,
                model = json.optString("model").take(64),
                bluetoothName = json.optString("btName").take(96),
                avatarId = AvatarPresets.normalizedId(json.optString("avatar").take(32)),
                sentAt = json.optLong("ts", System.currentTimeMillis()),
                avatarPhoto = json.optString("avatarPhoto").ifBlank { null },
            ),
        )
    }

    private fun parseChat(json: JSONObject, fallbackSender: String? = null): ChatFrame? {
        val id = json.optString("id").take(96)
        val sender = json.optString("sender").take(96).ifBlank { fallbackSender.orEmpty() }
        val text = json.optString("text").take(MAX_TEXT_LENGTH)
        val kind = json.optString("kind").take(16).ifBlank { ChatMessage.KIND_TEXT }
        val image = json.optString("image").takeIf { it.isNotBlank() }
        if (id.isBlank() || sender.isBlank()) return null
        if (kind == ChatMessage.KIND_IMAGE && image == null) return null
        if (kind != ChatMessage.KIND_IMAGE && text.isBlank()) return null
        return ChatFrame(
            id = id,
            sender = sender,
            isCreator = json.optBoolean("creator", false),
            text = text,
            sentAt = json.optLong("ts", System.currentTimeMillis()),
            fromUserId = json.optString("from").take(96).ifBlank { null },
            toUserId = json.optString("to").take(96).ifBlank { null },
            kind = if (kind == ChatMessage.KIND_IMAGE) ChatMessage.KIND_IMAGE else ChatMessage.KIND_TEXT,
            image = image,
            readAt = json.optLong("readAt", 0L),
            editedAt = json.optLong("editedAt", 0L),
        )
    }

    private fun parseWelcome(json: JSONObject): WireFrame? {
        val userId = json.optString("userId").take(96)
        if (userId.isBlank()) return null
        val nickname = json.optString("nickname").take(48)
        return WireFrame.Welcome(
            userId = userId,
            nickname = nickname,
            displayName = json.optString("displayName").take(96).ifBlank { nickname },
            isCreator = json.optBoolean("creator", false),
            avatarId = AvatarPresets.normalizedId(json.optString("avatar").take(32)),
            online = json.optInt("online", 0),
        )
    }

    private fun parseRoster(json: JSONObject): WireFrame? {
        val array = json.optJSONArray("users") ?: JSONArray()
        val users = ArrayList<ServerUser>(array.length())
        for (index in 0 until minOf(array.length(), 500)) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("id").take(96)
            if (id.isBlank()) continue
            val nickname = item.optString("nickname").take(48)
            users += ServerUser(
                id = id,
                nickname = nickname,
                displayName = item.optString("displayName").take(96).ifBlank { nickname },
                isCreator = item.optBoolean("creator", false),
                avatarId = AvatarPresets.normalizedId(item.optString("avatar").take(32)),
                online = item.optBoolean("online", false),
            )
        }
        return WireFrame.Roster(users)
    }

    private fun parseHistory(json: JSONObject): WireFrame? {
        val peer = json.optString("peer").take(96)
            .takeIf { it.isNotBlank() && it != "lobby" }
        val array = json.optJSONArray("messages") ?: JSONArray()
        val messages = ArrayList<ChatFrame>(array.length())
        for (index in 0 until minOf(array.length(), 200)) {
            val item = array.optJSONObject(index) ?: continue
            // В history поле sender всегда есть, but fall back to a stub anyway.
            parseChat(item, fallbackSender = "Ultra")?.let(messages::add)
        }
        return WireFrame.History(peer = peer, messages = messages)
    }

    private fun parseAck(json: JSONObject): WireFrame? {
        val id = json.optString("id").take(96)
        if (id.isBlank()) return null
        return WireFrame.Ack(id, json.optBoolean("delivered", false))
    }

    private fun parseReadReceipt(json: JSONObject): WireFrame? {
        val peer = json.optString("peer").take(96)
        val upTo = json.optLong("upTo", 0L)
        if (peer.isBlank() || upTo <= 0L) return null
        return WireFrame.ReadReceipt(peer, upTo)
    }

    private fun parseEdit(json: JSONObject): WireFrame? {
        val id = json.optString("id").take(96)
        val text = json.optString("text").take(MAX_TEXT_LENGTH)
        if (id.isBlank() || text.isBlank()) return null
        return WireFrame.Edit(
            id = id,
            text = text,
            editedAt = json.optLong("editedAt", System.currentTimeMillis()),
            fromUserId = json.optString("from").take(96).ifBlank { null },
            toUserId = json.optString("to").take(96).ifBlank { null },
        )
    }

    private fun parseDelete(json: JSONObject): WireFrame? {
        val id = json.optString("id").take(96)
        if (id.isBlank()) return null
        return WireFrame.Delete(
            id = id,
            fromUserId = json.optString("from").take(96).ifBlank { null },
            toUserId = json.optString("to").take(96).ifBlank { null },
        )
    }

    private fun parseChatDeleted(json: JSONObject): WireFrame? {
        val peer = json.optString("peer").take(96)
        if (peer.isBlank()) return null
        return WireFrame.ChatDeleted(peer)
    }

    private fun parseNotice(json: JSONObject): WireFrame? {
        val id = json.optString("id").take(64)
        val title = json.optString("title").take(120)
        val text = json.optString("text").take(1000)
        if (id.isBlank() || title.isBlank() && text.isBlank()) return null
        return WireFrame.Notice(
            ServerNotice(
                id = id,
                title = title.ifBlank { "Обновление UltraChat" },
                text = text,
                version = json.optString("version").take(32),
                url = json.optString("url").take(500),
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            ),
        )
    }

    private fun parseError(json: JSONObject): WireFrame {
        return WireFrame.ServerError(
            code = json.optString("code").take(48),
            message = json.optString("message").take(240).ifBlank { "Ошибка сервера" },
        )
    }
}
