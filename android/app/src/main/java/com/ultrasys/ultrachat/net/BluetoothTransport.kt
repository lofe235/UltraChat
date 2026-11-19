package com.ultrasys.ultrachat.net

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import com.ultrasys.ultrachat.util.NetworkPermissions
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Классический Bluetooth RFCOMM: сканирование окружения, серверный сокет
 * (listenUsingRfcommWithServiceRecord) и исходящее подключение BluetoothSocket.
 * Это единственный транспорт UltraChat — Wi-Fi Direct не используется.
 */
@SuppressLint("MissingPermission")
class BluetoothTransport(
    private val context: Context,
    private val scope: CoroutineScope,
    private val listener: Listener,
) {
    interface Listener {
        fun onBluetoothPeers(peers: List<NearbyPeer>)
        fun onBluetoothStatus(status: TransportStatus)
        fun onBluetoothSocket(socket: BluetoothSocket)
    }

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val peers = ConcurrentHashMap<String, NearbyPeer>()
    private var receiverRegistered = false
    private var started = false
    private var scanning = false
    private var connecting = false

    @Volatile
    private var serverSocket: BluetoothServerSocket? = null

    @Volatile
    private var outgoingSocket: BluetoothSocket? = null

    private var serverJob: Job? = null
    private var connectJob: Job? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null || !NetworkPermissions.hasBluetoothAccess(context ?: return)) {
                return
            }
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.bluetoothDevice() ?: return
                    val rssiRaw = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                    val rssi = rssiRaw.takeIf { it != Short.MIN_VALUE }?.toInt()
                    addPeer(device, rssi)
                }

                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    scanning = true
                    publish(TransportPhase.SCANNING, "Сканируем Bluetooth…")
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    scanning = false
                    publish(
                        if (adapter?.isEnabled == true) TransportPhase.READY
                        else TransportPhase.DISABLED,
                        if (adapter?.isEnabled == true) "Поиск завершён" else "Bluetooth выключен",
                    )
                }

                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    intent.bluetoothDevice()?.let { addPeer(it, null) }
                }

                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                        BluetoothAdapter.STATE_ON -> {
                            refreshBondedDevices()
                            startServer()
                            publish(TransportPhase.READY, "Bluetooth готов")
                        }

                        BluetoothAdapter.STATE_OFF -> {
                            scanning = false
                            closeServer()
                            publish(TransportPhase.DISABLED, "Bluetooth выключен")
                        }
                    }
                }
            }
        }
    }

    fun start() {
        if (started) return
        val currentAdapter = adapter
        if (currentAdapter == null) {
            publish(TransportPhase.DISABLED, "Bluetooth не поддерживается")
            return
        }
        if (!NetworkPermissions.hasBluetoothAccess(context)) {
            publish(TransportPhase.ERROR, "Нет разрешения Bluetooth")
            return
        }
        started = true
        registerReceiver()
        refreshBondedDevices()
        startServer()
        publish(
            if (currentAdapter.isEnabled) TransportPhase.READY else TransportPhase.DISABLED,
            if (currentAdapter.isEnabled) "Bluetooth готов" else "Bluetooth выключен",
        )
    }

    fun startDiscovery() {
        start()
        val adapter = adapter ?: return
        if (!NetworkPermissions.hasBluetoothAccess(context)) {
            publish(TransportPhase.ERROR, "Нет разрешения Bluetooth")
            return
        }
        if (!runCatching { adapter.isEnabled }.getOrDefault(false)) {
            publish(TransportPhase.DISABLED, "Bluetooth выключен")
            return
        }
        if (scanning) return
        runCatching { adapter.cancelDiscovery() }
        val startedScan = runCatching { adapter.startDiscovery() }.getOrDefault(false)
        scanning = startedScan
        if (startedScan) {
            publish(TransportPhase.SCANNING, "Сканируем Bluetooth…")
        } else {
            publish(TransportPhase.ERROR, "Bluetooth-сканер занят")
        }
    }

    fun stopDiscovery() {
        if (!scanning) return
        scanning = false
        runCatching { adapter?.cancelDiscovery() }
        if (adapter?.isEnabled == true) {
            publish(TransportPhase.READY, "Поиск завершён")
        }
    }

    /**
     * @return true, если попытка соединения запущена.
     */
    fun connect(address: String): Boolean {
        val adapter = adapter ?: return false
        if (!NetworkPermissions.hasBluetoothAccess(context)) {
            publish(TransportPhase.ERROR, "Нет разрешения Bluetooth")
            return false
        }
        if (!runCatching { adapter.isEnabled }.getOrDefault(false)) {
            publish(TransportPhase.DISABLED, "Bluetooth выключен")
            return false
        }
        if (connecting) return false
        connecting = true
        runCatching { adapter.cancelDiscovery() }
        scanning = false
        publish(TransportPhase.CONNECTING, "Подключаемся по Bluetooth…")

        connectJob = scope.launch(Dispatchers.IO) {
            var lastError = "Устройство Bluetooth недоступно"
            try {
                val device = runCatching { adapter.getRemoteDevice(address) }.getOrNull()
                if (device == null) {
                    publish(TransportPhase.ERROR, "Некорректный Bluetooth-адрес")
                    return@launch
                }
                repeat(3) { attempt ->
                    if (!isActive) return@launch
                    var socket: BluetoothSocket? = null
                    try {
                        socket = device.createRfcommSocketToServiceRecord(SERVICE_UUID)
                        outgoingSocket = socket
                        socket.connect()
                        publish(TransportPhase.CONNECTED, "Bluetooth подключён")
                        listener.onBluetoothSocket(socket)
                        return@launch
                    } catch (error: SecurityException) {
                        lastError = "Нет разрешения Bluetooth"
                    } catch (error: IOException) {
                        lastError = friendlyBluetoothError(error)
                        runCatching { socket?.close() }
                        outgoingSocket = null
                        if (attempt < 2) delay(800)
                    } finally {
                        if (outgoingSocket === socket && socket?.isConnected != true) {
                            runCatching { socket?.close() }
                            outgoingSocket = null
                        }
                    }
                }
                publish(TransportPhase.ERROR, lastError)
            } finally {
                connecting = false
            }
        }
        return true
    }

    fun localName(): String = runCatching { adapter?.name }.getOrNull().orEmpty()

    /**
     * Android отдаёт технические тексты вроде «read failed, socket might
     * closed or timeout, read ret: -1». Пользователю они ничего не говорят,
     * поэтому переводим частые случаи в понятную причину.
     */
    private fun friendlyBluetoothError(error: IOException): String {
        val raw = error.message.orEmpty().lowercase()
        return when {
            "read failed" in raw || "socket might closed" in raw || "read ret" in raw ->
                "Bluetooth не отвечает: телефон занят или слишком далеко"
            "refused" in raw ->
                "Телефон отклонил подключение по Bluetooth"
            "timeout" in raw || "timed out" in raw ->
                "Bluetooth не ответил вовремя"
            "unable to start" in raw || "service discovery" in raw ->
                "Bluetooth-служба не найдена на телефоне"
            else -> "Bluetooth-соединение не установлено"
        }
    }

    fun isReady(): Boolean {
        val current = adapter ?: return false
        return runCatching { current.isEnabled }.getOrDefault(false)
    }

    fun shutdown() {
        started = false
        scanning = false
        closeServer()
        runCatching { adapter?.cancelDiscovery() }
        runCatching { outgoingSocket?.close() }
        outgoingSocket = null
        connectJob?.cancel()
        if (receiverRegistered) {
            runCatching { context.unregisterReceiver(receiver) }
            receiverRegistered = false
        }
        publish(TransportPhase.DISABLED, "Bluetooth остановлен")
    }

    private fun startServer() {
        val adapter = adapter ?: return
        if (serverSocket != null) return
        if (serverJob?.isActive == true) return
        if (!NetworkPermissions.hasBluetoothAccess(context)) return
        if (!runCatching { adapter.isEnabled }.getOrDefault(false)) return
        serverJob = scope.launch(Dispatchers.IO) {
            var attempts = 0
            while (isActive && started && attempts < SERVER_ATTEMPTS) {
                attempts++
                try {
                    val server = adapter.listenUsingRfcommWithServiceRecord(
                        SERVICE_NAME,
                        SERVICE_UUID,
                    )
                    serverSocket = server
                    if (!scanning) {
                        publish(TransportPhase.READY, "Bluetooth слушает")
                    }
                    while (isActive) {
                        val socket = try {
                            server.accept()
                        } catch (_: IOException) {
                            break
                        }
                        publish(TransportPhase.CONNECTED, "Входящее Bluetooth-соединение")
                        runCatching { listener.onBluetoothSocket(socket) }
                            .onFailure { runCatching { socket.close() } }
                    }
                    runCatching { server.close() }
                    serverSocket = null
                    if (!isActive || !started) return@launch
                    if (!runCatching { adapter.isEnabled }.getOrDefault(false)) return@launch
                    if (attempts < SERVER_ATTEMPTS) delay(SERVER_RETRY_DELAY_MS)
                } catch (error: SecurityException) {
                    serverSocket = null
                    publish(TransportPhase.ERROR, "Нет разрешения Bluetooth")
                    return@launch
                } catch (error: IOException) {
                    serverSocket = null
                    if (!isActive || !started) return@launch
                    if (!runCatching { adapter.isEnabled }.getOrDefault(false)) return@launch
                    if (attempts >= SERVER_ATTEMPTS) {
                        publish(TransportPhase.ERROR, "Bluetooth-сервер недоступен")
                        return@launch
                    }
                    delay(SERVER_RETRY_DELAY_MS)
                }
            }
        }
    }

    private fun closeServer() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
    }

    private fun registerReceiver() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        runCatching {
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED,
            )
            receiverRegistered = true
        }.onFailure {
            publish(TransportPhase.ERROR, "Не удалось запустить Bluetooth-сканер")
        }
    }

    private fun refreshBondedDevices() {
        val bonded = runCatching { adapter?.bondedDevices }.getOrNull().orEmpty()
        bonded.forEach { addPeer(it, null) }
    }

    private fun addPeer(device: BluetoothDevice, rssi: Int?) {
        if (!NetworkPermissions.hasBluetoothAccess(context)) return
        val name = runCatching { device.name }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: "Bluetooth-устройство"
        val paired = runCatching {
            device.bondState == BluetoothDevice.BOND_BONDED
        }.getOrDefault(false)
        val peer = NearbyPeer(
            id = "bt:${device.address}",
            name = name,
            channel = LinkChannel.BLUETOOTH,
            address = device.address,
            detail = if (paired) "Bluetooth · сопряжено" else "Bluetooth · рядом",
            rssi = rssi,
            isPaired = paired,
        )
        peers[peer.id] = peer
        emitPeers()
    }

    private fun emitPeers() {
        listener.onBluetoothPeers(
            peers.values.sortedWith(
                compareBy<NearbyPeer> { it.name.lowercase() }.thenBy { it.address },
            ),
        )
    }

    private fun publish(phase: TransportPhase, detail: String) {
        listener.onBluetoothStatus(TransportStatus(phase, detail))
    }

    @Suppress("DEPRECATION")
    private fun Intent.bluetoothDevice(): BluetoothDevice? = if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    ) {
        getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
    } else {
        getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
    }

    private companion object {
        const val SERVICE_NAME = "UltraChat P2P"
        const val SERVER_ATTEMPTS = 3
        const val SERVER_RETRY_DELAY_MS = 1_500L
        val SERVICE_UUID: UUID = UUID.fromString("6f9b6b84-1e3d-4f87-b7bc-2252a7d3a8f1")
    }
}
