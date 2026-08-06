package com.devil1716.bluetoothmanet.mesh.domain

import java.util.UUID

enum class PacketType {
    DISCOVERY, HANDSHAKE, MESSAGE, ACKNOWLEDGEMENT, PRESENCE, TYPING,
    READ_RECEIPT, REACTION, FILE, VOICE, IMAGE, SYSTEM, HEARTBEAT
}

data class MeshPacket(
    val packetId: String = UUID.randomUUID().toString(),
    val sourceId: String,
    val destinationId: String,
    val previousHop: String? = null,
    val nextHop: String? = null,
    val ttl: Int = 8,
    val hopCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val type: PacketType,
    val encryptedPayload: ByteArray,
    val signature: ByteArray? = null,
    val checksum: ByteArray? = null
) {
    fun expired(now: Long = System.currentTimeMillis(), maxAgeMs: Long = 24 * 60 * 60 * 1000L): Boolean =
        ttl <= 0 || now - timestamp > maxAgeMs

    fun forwarded(previous: String, next: String? = null): MeshPacket = copy(
        previousHop = previous,
        nextHop = next,
        ttl = ttl - 1,
        hopCount = hopCount + 1
    )
}
