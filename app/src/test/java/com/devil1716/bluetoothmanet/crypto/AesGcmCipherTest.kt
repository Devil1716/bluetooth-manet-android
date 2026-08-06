package com.devil1716.bluetoothmanet.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import javax.crypto.KeyGenerator

class AesGcmCipherTest {
    @Test fun `round trips payload and authenticates associated data`() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val cipher = AesGcmCipher()
        val encrypted = cipher.encrypt(key, "hello mesh".toByteArray(), "header".toByteArray())

        assertArrayEquals("hello mesh".toByteArray(), cipher.decrypt(key, encrypted, "header".toByteArray()))
    }
}
