package com.devil1716.bluetoothmanet.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class CipherText(val nonce: ByteArray, val bytes: ByteArray)

class AesGcmCipher(private val secureRandom: SecureRandom = SecureRandom()) {
    fun encrypt(key: SecretKey, plaintext: ByteArray, associatedData: ByteArray = ByteArray(0)): CipherText {
        val nonce = ByteArray(NONCE_SIZE).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(associatedData)
        return CipherText(nonce, cipher.doFinal(plaintext))
    }

    fun decrypt(key: SecretKey, encrypted: CipherText, associatedData: ByteArray = ByteArray(0)): ByteArray {
        require(encrypted.nonce.size == NONCE_SIZE) { "Invalid GCM nonce" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, encrypted.nonce))
        cipher.updateAAD(associatedData)
        return cipher.doFinal(encrypted.bytes)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val NONCE_SIZE = 12
        const val TAG_BITS = 128
    }
}
