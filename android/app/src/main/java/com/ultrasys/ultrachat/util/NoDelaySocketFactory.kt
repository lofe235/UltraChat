package com.ultrasys.ultrachat.util

import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory

/**
 * Отключает алгоритм Нейгла на клиентских сокетах.
 *
 * Без этого маленький текстовый кадр может ждать, пока уйдёт предыдущий
 * пакет, и сообщение приходит на 100–300 мс позже. Для чата это заметно.
 */
object NoDelaySocketFactory : SocketFactory() {
    private fun Socket.noDelay(): Socket = apply {
        runCatching { tcpNoDelay = true }
    }

    override fun createSocket(): Socket = Socket().noDelay()

    override fun createSocket(host: String, port: Int): Socket =
        Socket(host, port).noDelay()

    override fun createSocket(
        host: String,
        port: Int,
        localHost: InetAddress,
        localPort: Int,
    ): Socket = Socket(host, port, localHost, localPort).noDelay()

    override fun createSocket(host: InetAddress, port: Int): Socket =
        Socket(host, port).noDelay()

    override fun createSocket(
        address: InetAddress,
        port: Int,
        localAddress: InetAddress,
        localPort: Int,
    ): Socket = Socket(address, port, localAddress, localPort).noDelay()
}
