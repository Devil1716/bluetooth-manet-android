package com.devil1716.bluetoothmanet.bluetooth.gatt

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object LengthPrefixedCodec {
    const val MAX_PAYLOAD_BYTES = 256 * 1024

    fun encode(payload: ByteArray): ByteArray {
        val buffer = ByteBuffer.allocate(4 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(payload.size)
        buffer.put(payload)
        return buffer.array()
    }

    fun chunks(bytes: ByteArray, chunkSize: Int): List<ByteArray> {
        val size = chunkSize.coerceAtLeast(1)
        if (bytes.isEmpty()) return listOf(ByteArray(0))
        return bytes.asList().chunked(size).map { it.toByteArray() }
    }
}

class LengthPrefixedAssembler(private val maxPayloadBytes: Int = LengthPrefixedCodec.MAX_PAYLOAD_BYTES) {
    private val buffer = ByteArrayOutputStream()

    fun offer(chunk: ByteArray): List<ByteArray> {
        if (chunk.isNotEmpty()) buffer.write(chunk)
        val complete = ArrayList<ByteArray>()
        while (true) {
            val data = buffer.toByteArray()
            if (data.size < 4) return complete
            val length = ByteBuffer.wrap(data, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (length < 0 || length > maxPayloadBytes) {
                buffer.reset()
                throw IllegalArgumentException("Invalid BLE payload length $length")
            }
            if (data.size < 4 + length) return complete
            complete += data.copyOfRange(4, 4 + length)
            buffer.reset()
            val remaining = data.size - 4 - length
            if (remaining > 0) buffer.write(data, 4 + length, remaining)
        }
    }

    fun reset() {
        buffer.reset()
    }
}
