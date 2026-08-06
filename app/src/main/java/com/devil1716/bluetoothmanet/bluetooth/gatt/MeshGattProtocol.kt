package com.devil1716.bluetoothmanet.bluetooth.gatt

import java.util.UUID
import java.util.zip.CRC32

object MeshGattProtocol {
    val SERVICE_UUID: UUID = UUID.fromString("4ba1562d-8ec0-4b7c-b168-ef3523bf1a01")
    val INBOUND_UUID: UUID = UUID.fromString("4ba1562d-8ec0-4b7c-b168-ef3523bf1a02")
    val OUTBOUND_UUID: UUID = UUID.fromString("4ba1562d-8ec0-4b7c-b168-ef3523bf1a03")
    const val MAX_FRAME_PAYLOAD_BYTES = 180
}

data class GattFrame(
    val packetId: String,
    val index: Int,
    val total: Int,
    val checksum: Long,
    val payload: ByteArray
)

class GattFrameCodec(private val maxFramePayloadBytes: Int = MeshGattProtocol.MAX_FRAME_PAYLOAD_BYTES) {
    init { require(maxFramePayloadBytes in 1..480) }

    fun fragment(packetId: String, bytes: ByteArray): List<GattFrame> {
        require(packetId.isNotBlank())
        val payload = if (bytes.isEmpty()) listOf(ByteArray(0)) else bytes.asList().chunked(maxFramePayloadBytes)
            .map { chunk -> chunk.toByteArray() }
        val checksum = checksum(bytes)
        return payload.mapIndexed { index, chunk ->
            GattFrame(packetId, index, payload.size, checksum, chunk)
        }
    }

    fun reassemble(frames: Collection<GattFrame>): Result<ByteArray> = runCatching {
        require(frames.isNotEmpty()) { "No frames" }
        val packetId = frames.first().packetId
        val total = frames.first().total
        val checksum = frames.first().checksum
        require(total > 0 && frames.size == total) { "Incomplete packet" }
        require(frames.all { it.packetId == packetId && it.total == total && it.checksum == checksum }) {
            "Frame metadata mismatch"
        }
        val ordered = frames.sortedBy { it.index }
        require(ordered.map { it.index } == (0 until total).toList()) { "Missing or duplicate frame" }
        val bytes = ordered.fold(ByteArray(0)) { result, frame -> result + frame.payload }
        require(checksum(bytes) == checksum) { "Checksum mismatch" }
        bytes
    }

    private fun checksum(bytes: ByteArray): Long = CRC32().run { update(bytes); value }
}
