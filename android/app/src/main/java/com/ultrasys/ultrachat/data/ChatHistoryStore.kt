package com.ultrasys.ultrachat.data

import android.content.Context
import com.ultrasys.ultrachat.net.LinkChannel
import org.json.JSONArray
import org.json.JSONObject

/** Строка списка чатов: сохраняется локально и доступна без подключения. */
data class ChatThread(
    val peerId: String,
    val title: String,
    val isCreator: Boolean,
    val lastText: String,
    val lastTimestamp: Long,
    val lastIsMine: Boolean,
    val count: Int,
    val channel: LinkChannel = LinkChannel.NONE,
)

/**
 * Локальная история. При потере канала и повторном подключении к тому же
 * устройству переписка восстанавливается из SharedPreferences.
 */
class ChatHistoryStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    fun load(peerId: String): List<ChatMessage> = synchronized(lock) {
        val raw = prefs.getString(keyFor(peerId), null) ?: return@synchronized emptyList()
        runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val channel = runCatching {
                        LinkChannel.valueOf(item.optString("channel", LinkChannel.NONE.name))
                    }.getOrDefault(LinkChannel.NONE)
                    add(
                        ChatMessage(
                            id = item.optString("id"),
                            peerId = item.optString("peerId", peerId),
                            sender = item.optString("sender"),
                            text = item.optString("text"),
                            timestamp = item.optLong("timestamp"),
                            isMine = item.optBoolean("isMine"),
                            isCreator = item.optBoolean("isCreator"),
                            channel = channel,
                            kind = item.optString("kind", ChatMessage.KIND_TEXT),
                            imagePath = item.optString("imagePath").ifBlank { null },
                            isRead = item.optBoolean("isRead", false),
                            editedAt = item.optLong("editedAt", 0L),
                        ),
                    )
                }
            }.filter { it.id.isNotBlank() && (it.text.isNotBlank() || it.imagePath != null) }
                .sortedBy { it.timestamp }
        }.getOrDefault(emptyList())
    }

    fun append(peerId: String, message: ChatMessage) = synchronized(lock) {
        val current = load(peerId).toMutableList()
        val index = current.indexOfFirst { it.id == message.id }
        if (index >= 0) {
            val existing = current[index]
            val merged = existing.copy(
                text = if (message.editedAt > existing.editedAt) message.text else existing.text,
                isRead = existing.isRead || message.isRead,
                editedAt = maxOf(existing.editedAt, message.editedAt),
                imagePath = existing.imagePath ?: message.imagePath,
                kind = if (message.imagePath != null) message.kind else existing.kind,
            )
            if (merged == existing) return@synchronized
            current[index] = merged
        } else {
            current += message
        }
        save(peerId, current.takeLast(MAX_MESSAGES))
    }

    fun markRead(peerId: String, upTo: Long) = synchronized(lock) {
        if (upTo <= 0) return@synchronized
        val current = load(peerId)
        var changed = false
        val updated = current.map { message ->
            if (message.isMine && !message.isRead && message.timestamp <= upTo) {
                changed = true
                message.copy(isRead = true)
            } else {
                message
            }
        }
        if (changed) save(peerId, updated)
    }

    fun updateEdited(peerId: String, id: String, text: String, editedAt: Long) = synchronized(lock) {
        val current = load(peerId).toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index < 0) return@synchronized
        val existing = current[index]
        if (existing.editedAt > editedAt) return@synchronized
        current[index] = existing.copy(text = text, editedAt = editedAt)
        save(peerId, current)
    }

    fun removeMessage(peerId: String, id: String) = synchronized(lock) {
        val current = load(peerId)
        if (current.none { it.id == id }) return@synchronized
        save(peerId, current.filterNot { it.id == id })
    }

    fun deleteThread(peerId: String) = synchronized(lock) {
        prefs.edit()
            .remove(keyFor(peerId))
            .remove(peerTitleKeyFor(peerId))
            .remove(peerCreatorKeyFor(peerId))
            .apply()
    }

    private fun save(peerId: String, messages: List<ChatMessage>) {
        val array = JSONArray()
        messages.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("peerId", item.peerId)
                    .put("sender", item.sender)
                    .put("text", item.text)
                    .put("timestamp", item.timestamp)
                    .put("isMine", item.isMine)
                    .put("isCreator", item.isCreator)
                    .put("channel", item.channel.name)
                    .put("kind", item.kind)
                    .put("imagePath", item.imagePath ?: "")
                    .put("isRead", item.isRead)
                    .put("editedAt", item.editedAt),
            )
        }
        prefs.edit().putString(keyFor(peerId), array.toString()).apply()
    }

    /**
     * Запоминает имя собеседника из рукопожатия. Без этого в списке чатов
     * нельзя показать имя, пока собеседник ещё не отправил ни одного сообщения.
     */
    fun savePeer(peerId: String, title: String, isCreator: Boolean) {
        val clean = title.trim()
        if (peerId.isBlank() || clean.isEmpty()) return
        synchronized(lock) {
            prefs.edit()
                .putString(peerTitleKeyFor(peerId), clean)
                .putBoolean(peerCreatorKeyFor(peerId), isCreator)
                .apply()
        }
    }

    fun clearAll() = synchronized(lock) {
        val keys = prefs.all.keys.filter {
            it.startsWith(HISTORY_PREFIX) || it.startsWith("peer_")
        }
        if (keys.isEmpty()) return@synchronized
        val editor = prefs.edit()
        keys.forEach(editor::remove)
        editor.apply()
    }

    /**
     * Все сохранённые переписки, свежие сверху. Сюда же попадают контакты,
     * добавленные кнопкой «+», даже если переписки с ними ещё не было.
     */
    fun threads(): List<ChatThread> = synchronized(lock) {
        val peerIds = buildSet {
            prefs.all.keys
                .filter { it.startsWith(HISTORY_PREFIX) }
                .mapTo(this) { it.removePrefix(HISTORY_PREFIX) }
            prefs.all.keys
                .filter { it.startsWith(PEER_TITLE_PREFIX) }
                .mapTo(this) { it.removePrefix(PEER_TITLE_PREFIX) }
        }
        peerIds
            .mapNotNull { peerId ->
                val messages = load(peerId)
                val last = messages.lastOrNull()
                val remote = messages.lastOrNull { !it.isMine }
                val savedTitle = prefs.getString(peerTitleKeyFor(peerId), null)
                    ?.takeIf { it.isNotBlank() }
                if (last == null && savedTitle == null) return@mapNotNull null
                ChatThread(
                    peerId = peerId,
                    title = savedTitle
                        ?: remote?.sender?.takeIf { it.isNotBlank() }
                        ?: "Собеседник",
                    isCreator = prefs.getBoolean(
                        peerCreatorKeyFor(peerId),
                        remote?.isCreator == true,
                    ),
                    lastText = when {
                        last == null -> "Начните переписку"
                        last.kind == ChatMessage.KIND_IMAGE -> "Фото"
                        else -> last.text
                    },
                    lastTimestamp = last?.timestamp ?: 0L,
                    lastIsMine = last?.isMine == true,
                    count = messages.size,
                    channel = messages.lastOrNull { it.channel != LinkChannel.NONE }?.channel
                        ?: LinkChannel.SERVER,
                )
            }
            .sortedWith(compareByDescending<ChatThread> { it.lastTimestamp }.thenBy { it.title })
    }

    private fun keyFor(peerId: String): String = "$HISTORY_PREFIX$peerId"

    private fun peerTitleKeyFor(peerId: String): String = "$PEER_TITLE_PREFIX$peerId"

    private fun peerCreatorKeyFor(peerId: String): String = "$PEER_CREATOR_PREFIX$peerId"

    private companion object {
        const val PREFS_NAME = "ultrachat_history"
        const val HISTORY_PREFIX = "history_"
        const val PEER_TITLE_PREFIX = "peer_title_"
        const val PEER_CREATOR_PREFIX = "peer_creator_"
        const val MAX_MESSAGES = 400
    }
}
