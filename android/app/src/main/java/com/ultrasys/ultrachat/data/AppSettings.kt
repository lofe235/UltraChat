package com.ultrasys.ultrachat.data

import com.ultrasys.ultrachat.BuildConfig

/**
 * Пользовательские настройки оформления и поведения.
 * Всё хранится локально, сервер для этого не нужен.
 */
enum class ThemeVariant(val label: String, val description: String) {
    TELEGRAM("Telegram", "Классическая тёмная"),
    MIDNIGHT("Полночь", "Глубокий синий"),
    GRAPHITE("Графит", "Нейтральный серый"),
    OCEAN("Океан", "Холодная бирюза"),
    FOREST("Лес", "Спокойный зелёный"),
    AMETHYST("Аметист", "Мягкий фиолетовый"),
    LIGHT("Светлая", "Дневная тема"),
}

enum class AccentTone(val label: String, val argb: Long) {
    BLUE("Синий", 0xFF2AABEE),
    GREEN("Зелёный", 0xFF43B581),
    PURPLE("Фиолетовый", 0xFF9B7EDE),
    ORANGE("Оранжевый", 0xFFE8A24A),
    PINK("Розовый", 0xFFE06A9A),
    RED("Красный", 0xFFE05A5A),
    CYAN("Бирюзовый", 0xFF3ECFD0),
}

enum class ChatWallpaper(val label: String) {
    DEFAULT("Стандартные"),
    NIGHT("Ночные"),
    OCEAN("Океан"),
    FOREST("Лес"),
    SAND("Песок"),
    PLAIN("Без обоев"),
}

enum class FontSizeOption(val label: String, val scale: Float) {
    SMALL("Мелкий", 0.9f),
    NORMAL("Обычный", 1.0f),
    LARGE("Крупный", 1.1f),
    XL("Очень крупный", 1.2f),
}

enum class BubbleStyle(val label: String) {
    ROUNDED("Круглые"),
    SOFT("Мягкие"),
    COMPACT("Компактные"),
}

enum class ListDensity(val label: String) {
    COMPACT("Компактно"),
    COMFORTABLE("Свободно"),
}

data class AppSettings(
    val theme: ThemeVariant = ThemeVariant.TELEGRAM,
    val accent: AccentTone = AccentTone.BLUE,
    val wallpaper: ChatWallpaper = ChatWallpaper.DEFAULT,
    val fontSize: FontSizeOption = FontSizeOption.NORMAL,
    val bubbleStyle: BubbleStyle = BubbleStyle.ROUNDED,
    val density: ListDensity = ListDensity.COMFORTABLE,
    val wallpaperDim: Float = 0.18f,
    val showTimestamps: Boolean = true,
    val haptics: Boolean = true,
    val autoScan: Boolean = true,
    val autoReconnect: Boolean = true,
    val animations: Boolean = true,
    val keepScreenOn: Boolean = false,
    val showLinkBanners: Boolean = true,
    val serverUrl: String = BUILT_IN_SERVER_URL,
    val serverToken: String = "",
) {
    companion object {
        /**
         * Адрес сервера-релея, вшитый в сборку. Приложение подключается к нему
         * сразу после первого запуска, без ручного ввода в настройках.
         * Если на ПК поднят другой адрес, его можно указать в настройках.
         */
        val BUILT_IN_SERVER_URL: String = BuildConfig.SERVER_URL
    }
}
