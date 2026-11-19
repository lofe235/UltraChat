package com.ultrasys.ultrachat.data

/**
 * Простые локальные аватары: цветной круг с эмодзи или инициалами.
 * Никаких загрузок из сети — набор всегда доступен офлайн.
 */
data class AvatarPreset(
    val id: String,
    val emoji: String,
    val color: Long,
)

object AvatarPresets {
    const val DEFAULT_ID = "initials"

    val all: List<AvatarPreset> = listOf(
        AvatarPreset(DEFAULT_ID, "", 0xFF5B9CFF),
        AvatarPreset("fox", "🦊", 0xFFF09A5B),
        AvatarPreset("panda", "🐼", 0xFF9AA7B4),
        AvatarPreset("frog", "🐸", 0xFF62C98B),
        AvatarPreset("octopus", "🐙", 0xFFB58CFF),
        AvatarPreset("owl", "🦉", 0xFF7C9BFF),
        AvatarPreset("tiger", "🐯", 0xFFF2B24C),
        AvatarPreset("koala", "🐨", 0xFF8FA9C4),
        AvatarPreset("wolf", "🐺", 0xFF7F92A8),
        AvatarPreset("unicorn", "🦄", 0xFFF28BC2),
        AvatarPreset("monkey", "🐵", 0xFFD89B6A),
        AvatarPreset("cat", "🐱", 0xFFF0C45A),
    )

    fun byId(id: String?): AvatarPreset =
        all.firstOrNull { it.id == id } ?: all.first()

    fun normalizedId(id: String?): String = byId(id).id
}
