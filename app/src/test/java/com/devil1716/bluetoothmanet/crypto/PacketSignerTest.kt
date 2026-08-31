package com.devil1716.bluetoothmanet.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class PacketSignerTest {
    @Test
    fun `signs messages and rejects tampering`() {
        val dir = Files.createTempDirectory("mesh-signer").toFile()
        val signer = PacketSigner(dir)
        val canon = MeshIntegrity.messageCanon("MSG", "id-1", "A", "B", "hello")
        val signature = signer.sign(canon)

        assertTrue(signer.verify(canon, signature, signer.publicKeyHex()))
        assertFalse(signer.verify(canon.replace("hello", "hallo"), signature, signer.publicKeyHex()))
    }

    @Test
    fun `file hash changes when a byte is flipped`() {
        val original = byteArrayOf(1, 2, 3, 4)
        val tampered = byteArrayOf(1, 2, 9, 4)
        assertEquals(MeshIntegrity.sha256Hex(original).length, 64)
        assertFalse(MeshIntegrity.sha256Hex(original).equals(MeshIntegrity.sha256Hex(tampered), ignoreCase = true))
    }
}
