package com.devil1716.bluetoothmanet.bluetooth.gatt

import java.nio.ByteBuffer
import java.nio.ByteOrder

object LengthPrefixedCodec {
    /** One signed FILE packet is ~1.5 KB; keep a hard cap so a bad length cannot OOM. */
    const val MAX_PAYLOAD_BYTES = 24 * 1024

    fun encode(payload: ByteArray): ByteArray {
        val buffer = ByteBuffer.allocate(4 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(payload.size)
        buffer.put(payload)
        return buffer.array()
    }

    fun chunks(bytes: ByteArray, chunkSize: Int): List<ByteArray> {
        val size = chunkSize.coerceAtLeast(1)
        if (bytes.isEmpty()) return listOf(ByteArray(0))
        val out = ArrayList<ByteArray>((bytes.size + size - 1) / size)
        var index = 0
        while (index < bytes.size) {
            val end = minOf(bytes.size, index + size)
            out.add(bytes.copyOfRange(index, end))
            index = end
        }
        return out
    }
}

class LengthPrefixedAssembler(private val maxPayloadBytes: Int = LengthPrefixedCodec.MAX_PAYLOAD_BYTES) {
    private var buf = ByteArray(256)
    private var size = 0

    fun offer(chunk: ByteArray): List<ByteArray> {
        if (chunk.isNotEmpty()) {
            ensureCapacity(size + chunk.size)
            System.arraycopy(chunk, 0, buf, size, chunk.size)
            size += chunk.size
        }
        val complete = ArrayList<ByteArray>()
        var offset = 0
        while (true) {
            if (size - offset < 4) break
            val length = ByteBuffer.wrap(buf, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (length < 0 || length > maxPayloadBytes) {
                reset()
                throw IllegalArgumentException("Invalid BLE payload length $length")
            }
            if (size - offset < 4 + length) break
            complete += buf.copyOfRange(offset + 4, offset + 4 + length)
            offset += 4 + length
        }
        if (offset > 0) {
            val remaining = size - offset
            if (remaining > 0) System.arraycopy(buf, offset, buf, 0, remaining)
            size = remaining
        }
        return complete
    }

    fun reset() {
        size = 0
    }

    private fun ensureCapacity(needed: Int) {
        val maxBuffered = maxPayloadBytes + 4 + 512
        if (needed > maxBuffered) {
            reset()
            throw IllegalArgumentException("Invalid BLE payload length $needed")
        }
        if (needed <= buf.size) return
        var cap = buf.size
        while (cap < needed) cap *= 2
        buf = buf.copyOf(minOf(cap, maxBuffered))
    }
}
