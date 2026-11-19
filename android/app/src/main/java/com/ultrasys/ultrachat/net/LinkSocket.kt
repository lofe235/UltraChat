package com.ultrasys.ultrachat.net

import android.bluetooth.BluetoothSocket
import java.io.InputStream
import java.io.OutputStream

/**
 * Минимальная абстракция над сокетом: BluetoothSocket отдаёт обычные
 * байтовые потоки, которых достаточно для построчного JSON-протокола.
 */
internal interface LinkSocket {
    val input: InputStream
    val output: OutputStream

    fun configure()
    fun setReadTimeout(millis: Int)
    fun close()
}

internal class RfcommLinkSocket(private val socket: BluetoothSocket) : LinkSocket {
    override val input: InputStream = socket.inputStream
    override val output: OutputStream = socket.outputStream

    override fun configure() = Unit

    override fun setReadTimeout(millis: Int) = Unit

    override fun close() {
        runCatching { socket.close() }
    }
}
