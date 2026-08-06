package com.devil1716.bluetoothmanet.bluetooth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class TransportState { STOPPED, STARTING, ADVERTISING, SCANNING, CONNECTING, CONNECTED, DEGRADED, FAILED }

data class TransportPeer(
    val deviceId: String,
    val displayName: String?,
    val rssi: Int,
    val connected: Boolean,
    val lastSeen: Long
)

data class ReceivedTransportPacket(val peerId: String, val bytes: ByteArray, val receivedAt: Long)

interface MeshTransport {
    val state: StateFlow<TransportState>
    val peers: StateFlow<List<TransportPeer>>
    val incomingPackets: Flow<ReceivedTransportPacket>

    suspend fun start(): Result<Unit>
    suspend fun stop()
    suspend fun send(peerId: String, bytes: ByteArray): Result<Unit>
}
