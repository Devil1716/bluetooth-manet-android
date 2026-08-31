package com.devil1716.bluetoothmanet.bluetooth.gatt

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class LengthPrefixedCodecTest {
    @Test
    fun `assembles chunks that arrive out of packet boundaries`() {
        val payload = ByteArray(300) { it.toByte() }
        val encoded = LengthPrefixedCodec.encode(payload)
        val assembler = LengthPrefixedAssembler()

        val first = assembler.offer(encoded.copyOfRange(0, 50))
        val second = assembler.offer(encoded.copyOfRange(50, encoded.size))

        assertTrue(first.isEmpty())
        assertEquals(1, second.size)
        assertArrayEquals(payload, second[0])
    }

    @Test
    fun `rejects oversized declared length`() {
        val huge = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(LengthPrefixedCodec.MAX_PAYLOAD_BYTES + 1)
            .array()
        try {
            LengthPrefixedAssembler().offer(huge)
            org.junit.Assert.fail("expected invalid length")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message!!.contains("Invalid BLE payload length"))
        }
    }
}
