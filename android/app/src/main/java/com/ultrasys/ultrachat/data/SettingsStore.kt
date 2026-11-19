package com.ultrasys.ultrachat.data

import android.content.Context

/**
 * SharedPreferences для настроек интерфейса. Значения читаются один раз
 * при старте и обновляются из ViewModel.
 */
class SettingsStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            theme = enumOrDefault(KEY_THEME, defaults.theme),
            accent = enumOrDefault(KEY_ACCENT, defaults.accent),
            wallpaper = enumOrDefault(KEY_WALLPAPER, defaults.wallpaper),
            fontSize = enumOrDefault(KEY_FONT_SIZE, defaults.fontSize),
            bubbleStyle = enumOrDefault(KEY_BUBBLE_STYLE, defaults.bubbleStyle),
            density = enumOrDefault(KEY_DENSITY, defaults.density),
            wallpaperDim = prefs.getFloat(KEY_WALLPAPER_DIM, defaults.wallpaperDim),
            showTimestamps = prefs.getBoolean(KEY_SHOW_TIMESTAMPS, defaults.showTimestamps),
            haptics = prefs.getBoolean(KEY_HAPTICS, defaults.haptics),
            autoScan = prefs.getBoolean(KEY_AUTO_SCAN, defaults.autoScan),
            autoReconnect = prefs.getBoolean(KEY_AUTO_RECONNECT, defaults.autoReconnect),
            animations = prefs.getBoolean(KEY_ANIMATIONS, defaults.animations),
            keepScreenOn = prefs.getBoolean(KEY_KEEP_SCREEN_ON, defaults.keepScreenOn),
            showLinkBanners = prefs.getBoolean(KEY_SHOW_LINK_BANNERS, defaults.showLinkBanners),
            serverUrl = resolveServerUrl(prefs.getString(KEY_SERVER_URL, null)),
            serverToken = prefs.getString(KEY_SERVER_TOKEN, null).orEmpty(),
        )
    }

    fun save(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_THEME, settings.theme.name)
            .putString(KEY_ACCENT, settings.accent.name)
            .putString(KEY_WALLPAPER, settings.wallpaper.name)
            .putString(KEY_FONT_SIZE, settings.fontSize.name)
            .putString(KEY_BUBBLE_STYLE, settings.bubbleStyle.name)
            .putString(KEY_DENSITY, settings.density.name)
            .putFloat(KEY_WALLPAPER_DIM, settings.wallpaperDim)
            .putBoolean(KEY_SHOW_TIMESTAMPS, settings.showTimestamps)
            .putBoolean(KEY_HAPTICS, settings.haptics)
            .putBoolean(KEY_AUTO_SCAN, settings.autoScan)
            .putBoolean(KEY_AUTO_RECONNECT, settings.autoReconnect)
            .putBoolean(KEY_ANIMATIONS, settings.animations)
            .putBoolean(KEY_KEEP_SCREEN_ON, settings.keepScreenOn)
            .putBoolean(KEY_SHOW_LINK_BANNERS, settings.showLinkBanners)
            // Сохраняем уже проверенный адрес: мёртвые (*.loca.lt, 127.0.0.1)
            // не должны оставаться в настройках ни при каких условиях.
            .putString(KEY_SERVER_URL, resolveServerUrl(settings.serverUrl))
            .putString(KEY_SERVER_TOKEN, settings.serverToken)
            .apply()
    }

    fun reset(): AppSettings {
        prefs.edit().clear().apply()
        return AppSettings()
    }

    /**
     * Адрес сервера: сохранённый пользователем, иначе вшитый в сборку.
     * Пустая строка, сохранённая вручную, означает «сервер отключён».
     * Старые вшитые адреса заменяются на актуальный, чтобы после обновления
     * приложение не стучалось в уже несуществующий туннель.
     */
    private fun resolveServerUrl(saved: String?): String {
        if (saved == null) return AppSettings.BUILT_IN_SERVER_URL
        val clean = saved.trim()
        if (clean.isEmpty()) return ""
        if (isRetiredUrl(clean) || isSelfServerAddress(clean)) {
            return AppSettings.BUILT_IN_SERVER_URL
        }
        return clean
    }

    /**
     * Старые адреса на localtunnel больше не работают: имена перехватывают,
     * туннель падает при перезапуске. Такие адреса считаем устаревшими.
     */
    private fun isRetiredUrl(url: String): Boolean {
        val host = hostOfServerUrl(url)
        // tailscale-туннели и loca.lt больше не используются: после обновления
        // приложение должно само перейти на вшитый alwaysdata-адрес.
        return host.endsWith(".loca.lt") ||
            host.endsWith(".ts.net") ||
            url in LEGACY_SERVER_URLS
    }



    private inline fun <reified T : Enum<T>> enumOrDefault(key: String, default: T): T {
        val raw = prefs.getString(key, null) ?: return default
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(default)
    }

    private companion object {
        val LEGACY_SERVER_URLS = setOf(
            "https://ultrachat-235.loca.lt",
            "https://ultrachat-lofer235.loca.lt",
        )
        const val PREFS_NAME = "ultrachat_settings"
        const val KEY_THEME = "theme"
        const val KEY_ACCENT = "accent"
        const val KEY_WALLPAPER = "wallpaper"
        const val KEY_FONT_SIZE = "font_size"
        const val KEY_BUBBLE_STYLE = "bubble_style"
        const val KEY_DENSITY = "density"
        const val KEY_WALLPAPER_DIM = "wallpaper_dim"
        const val KEY_SHOW_TIMESTAMPS = "show_timestamps"
        const val KEY_HAPTICS = "haptics"
        const val KEY_AUTO_SCAN = "auto_scan"
        const val KEY_AUTO_RECONNECT = "auto_reconnect"
        const val KEY_ANIMATIONS = "animations"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        const val KEY_SHOW_LINK_BANNERS = "show_link_banners"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_SERVER_TOKEN = "server_token"
    }
}

/** Хост адреса без схемы, порта и пути. */
internal fun hostOfServerUrl(url: String): String = url
    .substringAfter("://", url)
    .substringBefore('/')
    .substringBefore(':')
    .removePrefix("[")
    .removeSuffix("]")
    .trim()
    .lowercase()

/**
 * Адрес, указывающий на сам телефон (127.0.0.1, localhost). Сервера там нет:
 * такой адрес нужно заменить вшитым, иначе приложение висит на ошибке
 * «Unable to resolve host».
 */
fun isSelfServerAddress(url: String): Boolean {
    val host = hostOfServerUrl(url)
    if (host.isEmpty()) return false
    if (host == "localhost" || host == "::1" || host == "0.0.0.0" || host == "10.0.2.2") return true
    // Петля на самом телефоне. Ловим и «кривые» записи вроде 127.00.1,
    // где октетов не четыре: такой адрес всё равно не разрешается.
    return host == "127" || host.startsWith("127.")
}
