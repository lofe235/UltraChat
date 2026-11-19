package com.ultrasys.ultrachat.data

/**
 * Локальный профиль пользователя. Префикс создателя является частью отображаемого
 * имени и передаётся по воздуху внутри каждого сообщения.
 */
data class UserProfile(
    val nickname: String,
    val isCreator: Boolean = false,
    val avatarId: String = AvatarPresets.DEFAULT_ID,
    val avatarPhoto: String? = null,
) {
    val displayName: String
        get() = buildString {
            if (isCreator) {
                append(CREATOR_PREFIX)
                append(' ')
            }
            append(nickname)
        }

    companion object {
        const val CREATOR_PREFIX = "[Создатель]"
        const val MAX_NICKNAME_LENGTH = 24

        fun normalizedNickname(raw: String): String = raw
            .trim()
            .removePrefix(CREATOR_PREFIX)
            .trim()
            .replace(Regex("\\s+"), " ")
            .take(MAX_NICKNAME_LENGTH)

        fun initials(displayName: String): String {
            val clean = displayName
                .replace(CREATOR_PREFIX, "")
                .trim()
            if (clean.isEmpty()) return "UC"
            return clean
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .take(2)
                .joinToString("") { it.first().uppercase() }
                .ifBlank { "UC" }
        }
    }
}
