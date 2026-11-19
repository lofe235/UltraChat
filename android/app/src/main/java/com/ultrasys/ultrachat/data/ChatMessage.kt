package com.ultrasys.ultrachat.data

import com.ultrasys.ultrachat.net.LinkChannel

data class ChatMessage(
    val id: String,
    val peerId: String,
    val sender: String,
    val text: String,
    val timestamp: Long,
    val isMine: Boolean,
    val isCreator: Boolean,
    val channel: LinkChannel,
    val kind: String = KIND_TEXT,
    val imagePath: String? = null,
    val isRead: Boolean = false,
    val editedAt: Long = 0L,
) {
    val isImage: Boolean get() = kind == KIND_IMAGE

    companion object {
        const val KIND_TEXT = "text"
        const val KIND_IMAGE = "image"

        /** Псевдо-ID общего чата: отдельный «собеседник» в истории и списке. */
        const val LOBBY_ID = "lobby"
    }
}
