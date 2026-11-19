package com.ultrasys.ultrachat

import android.app.Application
import com.ultrasys.ultrachat.net.ConnectionHub
import com.ultrasys.ultrachat.util.MessageNotifier
import com.ultrasys.ultrachat.util.RelayService

class UltraChatApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MessageNotifier.ensureChannel(this)
        RelayService.ensureChannel(this)
        // Одно соединение на процесс: оно продолжает работать, когда экран
        // закрыт, и принимает сообщения в фоне.
        ConnectionHub.init(this)
    }
}
