package com.ultrasys.ultrachat.util

/**
 * Простое состояние «приложение на экране». Нужно, чтобы не показывать
 * уведомление о сообщении, которое пользователь и так видит в открытом чате.
 */
object AppVisibility {
    @Volatile
    var isForeground: Boolean = false
}
