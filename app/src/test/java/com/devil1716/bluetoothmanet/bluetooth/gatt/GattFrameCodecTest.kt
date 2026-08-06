package com.devil1716.bluetoothmanet.bluetooth.gatt

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GattFrameCodecTest {
    @Test fun `round trips a packet spanning several frames`() {
        val bytes = ByteArray(512) { it.toByte() }
        val codec = GattFrameCodec(maxFramePayloadBytes = 100)
        val frames = codec.fragment("packet-1", bytes)

        val result = codec.reassemble(frames.reversed())

        assertTrue(result.isSuccess)
        assertArrayEquals(bytes, result.getOrThrow())
    }

    @Test fun `rejects incomplete frames`() {
        val codec = GattFrameCodec(maxFramePayloadBytes = 2)
        val frames = codec.fragment("packet-1", byteArrayOf(1, 2, 3))

        assertTrue(codec.reassemble(frames.dropLast(1)).isFailure)
    }
}
