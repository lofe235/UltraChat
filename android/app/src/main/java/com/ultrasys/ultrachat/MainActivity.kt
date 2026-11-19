package com.ultrasys.ultrachat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.ultrasys.ultrachat.ui.UltraChatRoot
import com.ultrasys.ultrachat.ui.UltraChatViewModel
import com.ultrasys.ultrachat.util.AppVisibility
import com.ultrasys.ultrachat.util.MessageNotifier
import com.ultrasys.ultrachat.util.RelayService

class MainActivity : ComponentActivity() {

    private val viewModel: UltraChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UltraChatRoot(viewModel)
        }
        consumeNotificationIntent(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeNotificationIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.isForeground = true
        // Сервис держит соединение и уведомления даже после сворачивания.
        RelayService.start(this)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshEnvironment()
    }

    override fun onStop() {
        AppVisibility.isForeground = false
        super.onStop()
    }

    private fun consumeNotificationIntent(source: android.content.Intent?) {
        val peerId = source?.getStringExtra(MessageNotifier.EXTRA_PEER_ID) ?: return
        source.removeExtra(MessageNotifier.EXTRA_PEER_ID)
        if (viewModel.profile.value != null) {
            viewModel.openThread(peerId)
        }
    }
}
