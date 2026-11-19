package com.ultrasys.ultrachat.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Один живой Bluetooth-сокет с построчным JSON-обменом,
 * heartbeat-пакетами и защитой от повторного закрытия.
 */
internal class LinkSession(
    val channel: LinkChannel,
    private val linkSocket: LinkSocket,
    private val scope: CoroutineScope,
    private val onFrame: (LinkSession, WireFrame) -> Unit,
    private val onClosed: (LinkSession, String) -> Unit,
) {
    val id: String = UUID.randomUUID().toString()

    @Volatile
    var remoteHello: HelloFrame? = null

    @Volatile
    var closed: Boolean = false
        private set

    private val closeOnce = AtomicBoolean(false)
    private val writeLock = Any()
    private val reader = BufferedReader(
        InputStreamReader(linkSocket.input, StandardCharsets.UTF_8),
    )
    private val writer = BufferedWriter(
        OutputStreamWriter(linkSocket.output, StandardCharsets.UTF_8),
    )
    private var readerJob: Job? = null
    private var heartbeatJob: Job? = null

    fun start() {
        linkSocket.configure()
        linkSocket.setReadTimeout(SOCKET_TIMEOUT_MS)
        readerJob = scope.launch(Dispatchers.IO) { readLoop() }
        heartbeatJob = scope.launch(Dispatchers.IO) { heartbeatLoop() }
    }

    fun send(frame: WireFrame): Boolean {
        val raw = when (frame) {
            is WireFrame.Hello -> return false
            is WireFrame.Chat -> WireProtocol.chat(
                id = frame.value.id,
                sender = frame.value.sender,
                isCreator = frame.value.isCreator,
                text = frame.value.text,
                sentAt = frame.value.sentAt,
                kind = frame.value.kind,
                image = frame.value.image,
            )

            is WireFrame.Ping -> WireProtocol.ping()
            is WireFrame.Pong -> WireProtocol.pong()
            else -> return false
        }
        return sendRaw(raw)
    }

    fun sendRaw(raw: String): Boolean {
        if (closed) return false
        val delivered = synchronized(writeLock) {
            if (closed) return false
            try {
                writer.write(raw)
                writer.newLine()
                writer.flush()
                true
            } catch (_: IOException) {
                false
            }
        }
        if (!delivered) close("Ошибка отправки в канал")
        return delivered
    }

    fun close(reason: String) {
        if (!closeOnce.compareAndSet(false, true)) return
        closed = true
        heartbeatJob?.cancel()
        runCatching { writer.close() }
        linkSocket.close()
        readerJob?.cancel()
        onClosed(this, reason)
    }

    private suspend fun readLoop() {
        try {
            while (!closed) {
                val line = reader.readLine() ?: break
                if (line.length > WireProtocol.MAX_FRAME_LENGTH) continue
                when (val frame = WireProtocol.parse(line)) {
                    is WireFrame.Ping -> sendRaw(WireProtocol.pong())
                    is WireFrame.Pong -> Unit
                    null -> Unit
                    else -> onFrame(this, frame)
                }
            }
            close("Собеседник закрыл канал")
        } catch (_: SocketTimeoutException) {
            close("Канал не отвечает")
        } catch (_: IOException) {
            close("Соединение потеряно")
        } catch (_: Exception) {
            close("Ошибка чтения канала")
        }
    }

    private suspend fun heartbeatLoop() {
        while (scope.isActive && !closed) {
            delay(HEARTBEAT_MS)
            if (closed) break
            if (!sendRaw(WireProtocol.ping())) {
                close("Канал не отвечает")
                break
            }
        }
    }

    private companion object {
        const val SOCKET_TIMEOUT_MS = 55_000
        const val HEARTBEAT_MS = 15_000L
    }
}
