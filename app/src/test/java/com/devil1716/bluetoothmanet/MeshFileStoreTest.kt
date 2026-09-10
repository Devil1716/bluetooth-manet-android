package com.devil1716.bluetoothmanet

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

class MeshFileStoreTest {
    @Test
    fun readLimitedCopiesExactBytes() {
        val payload = "mesh-file".toByteArray()
        val read = MeshFileStore.readLimited(ByteArrayInputStream(payload), MeshFileStore.MAX_SEND_BYTES)
        assertArrayEquals(payload, read)
    }

    @Test
    fun readLimitedRejectsOversizedStreams() {
        val payload = ByteArray(64) { 1 }
        try {
            MeshFileStore.readLimited(ByteArrayInputStream(payload), 16)
            throw AssertionError("expected oversized file to fail")
        } catch (error: Exception) {
            assertTrue(error.message!!.contains("2 MB") || error.message!!.contains("larger"))
        }
    }

    @Test
    fun readLimitedReadsFromFileWithoutNio() {
        val file = File.createTempFile("mesh", ".bin")
        try {
            val payload = byteArrayOf(9, 8, 7, 6)
            file.writeBytes(payload)
            assertArrayEquals(payload, MeshFileStore.readLimited(file, MeshFileStore.MAX_SEND_BYTES))
        } finally {
            file.delete()
        }
    }

    @Test
    fun chatLabelRoundTrip() {
        val label = MeshFileStore.chatLabel("notes.pdf", "/tmp/notes.pdf")
        assertEquals("📎 notes.pdf", MeshFileStore.displayName(label))
        assertEquals("/tmp/notes.pdf", MeshFileStore.embeddedPath(label))
        assertTrue(MeshFileStore.isFileMessage(label))
    }
}
