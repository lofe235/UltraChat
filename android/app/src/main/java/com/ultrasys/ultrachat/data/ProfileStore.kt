package com.ultrasys.ultrachat.data

import android.content.Context
import java.util.UUID

/**
 * SharedPreferences — единственное локальное хранилище профиля.
 * deviceId стабилен между запусками и используется как ключ истории чата.
 */
class ProfileStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): UserProfile? {
        val nickname = prefs.getString(KEY_NICKNAME, null)?.trim().orEmpty()
        if (nickname.isEmpty()) return null
        return UserProfile(
            nickname = nickname,
            isCreator = prefs.getBoolean(KEY_CREATOR, false),
            avatarId = AvatarPresets.normalizedId(prefs.getString(KEY_AVATAR, null)),
            avatarPhoto = prefs.getString(KEY_PHOTO, null)?.ifBlank { null },
        )
    }

    /**
     * @return null, если код не подходит.
     */
    fun activateCreator(
        code: String,
        nickname: String? = null,
        avatarId: String? = null,
    ): UserProfile? {
        if (code.trim() != CREATOR_CODE) return null
        val current = load() ?: nickname
            ?.let { UserProfile.normalizedNickname(it) }
            ?.takeIf { it.isNotEmpty() }
            ?.let {
                UserProfile(
                    nickname = it,
                    avatarId = AvatarPresets.normalizedId(avatarId),
                )
            }
            ?: return null
        if (current.isCreator) return current
        val updated = current.copy(isCreator = true)
        store(updated)
        return updated
    }

    fun saveProfile(
        rawNickname: String,
        avatarId: String?,
        avatarPhoto: String? = null,
    ): UserProfile? {
        val nickname = UserProfile.normalizedNickname(rawNickname)
        if (nickname.isEmpty()) return null
        val current = load()
        val updated = UserProfile(
            nickname = nickname,
            isCreator = current?.isCreator == true,
            avatarId = AvatarPresets.normalizedId(avatarId),
            avatarPhoto = avatarPhoto?.ifBlank { null },
        )
        store(updated)
        return updated
    }

    /** Полное удаление аккаунта: новый deviceId, пустой профиль. */
    fun clear() {
        prefs.edit().clear().apply()
    }

    fun deviceId(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, generated).apply()
        return generated
    }

    private fun store(profile: UserProfile) {
        prefs.edit()
            .putString(KEY_NICKNAME, profile.nickname)
            .putBoolean(KEY_CREATOR, profile.isCreator)
            .putString(KEY_AVATAR, profile.avatarId)
            .putString(KEY_PHOTO, profile.avatarPhoto ?: "")
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "ultrachat_profile"
        const val KEY_NICKNAME = "nickname"
        const val KEY_CREATOR = "creator"
        const val KEY_AVATAR = "avatar"
        const val KEY_PHOTO = "avatar_photo"
        const val KEY_DEVICE_ID = "device_id"

        // Скрытый код создателя. Намеренно не выводится в интерфейсе.
        const val CREATOR_CODE = "2252"
    }
}
