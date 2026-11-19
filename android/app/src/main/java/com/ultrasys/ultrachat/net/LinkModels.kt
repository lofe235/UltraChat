package com.ultrasys.ultrachat.net

enum class LinkChannel(val shortLabel: String, val title: String) {
    BLUETOOTH("Bluetooth", "Bluetooth"),
    SERVER("Сеть", "Сервер"),
    NONE("—", "Нет соединения"),
}

enum class TransportPhase {
    DISABLED,
    READY,
    SCANNING,
    CONNECTING,
    CONNECTED,
    ERROR,
}

enum class LinkPhase {
    IDLE,
    SCANNING,
    CONNECTING,
    CONNECTED,
    RECOVERING,
    ERROR,
}

data class TransportStatus(
    val phase: TransportPhase = TransportPhase.DISABLED,
    val detail: String? = null,
)

data class NearbyPeer(
    val id: String,
    val name: String,
    val channel: LinkChannel,
    val address: String,
    val detail: String,
    val rssi: Int? = null,
    val isPaired: Boolean = false,
    val isOnline: Boolean = false,
    val avatarId: String? = null,
    val isCreator: Boolean = false,
)

data class RemotePeer(
    val deviceId: String,
    val nickname: String,
    val displayName: String,
    val isCreator: Boolean,
    val model: String,
    val avatarId: String,
    val avatarPhoto: String? = null,
)

data class LinkState(
    val bluetooth: TransportStatus = TransportStatus(),
    val server: TransportStatus = TransportStatus(),
    val peers: List<NearbyPeer> = emptyList(),
    val scanning: Boolean = false,
    val phase: LinkPhase = LinkPhase.IDLE,
    val activeChannel: LinkChannel = LinkChannel.NONE,
    val remote: RemotePeer? = null,
    val connectingTo: String? = null,
    val lastSwitch: String? = null,
    val error: String? = null,
    val queuedMessages: Int = 0,
    val serverOnline: Boolean = false,
    val serverPeers: Int = 0,
)
