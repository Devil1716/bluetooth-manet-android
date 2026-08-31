package com.devil1716.bluetoothmanet.crypto

import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

class PacketSigner(storeDir: File) {
    private val privateFile = File(storeDir, "mesh-ecdsa-private.key")
    private val publicFile = File(storeDir, "mesh-ecdsa-public.key")
    private val privateKey: PrivateKey
    private val publicKey: PublicKey

    init {
        storeDir.mkdirs()
        if (privateFile.isFile && publicFile.isFile) {
            val factory = KeyFactory.getInstance("EC")
            privateKey = factory.generatePrivate(PKCS8EncodedKeySpec(privateFile.readBytes()))
            publicKey = factory.generatePublic(X509EncodedKeySpec(publicFile.readBytes()))
        } else {
            val generator = KeyPairGenerator.getInstance("EC")
            generator.initialize(ECGenParameterSpec("secp256r1"))
            val pair = generator.generateKeyPair()
            privateKey = pair.private
            publicKey = pair.public
            privateFile.writeBytes(privateKey.encoded)
            publicFile.writeBytes(publicKey.encoded)
        }
    }

    fun publicKeyHex(): String = toHex(publicKey.encoded)

    fun sign(canonical: String): String {
        val signature = Signature.getInstance(ALGORITHM)
        signature.initSign(privateKey)
        signature.update(canonical.toByteArray(Charsets.UTF_8))
        return toHex(signature.sign())
    }

    fun verify(canonical: String, signatureHex: String, publicKeyHex: String): Boolean {
        if (canonical.isBlank() || signatureHex.isBlank() || publicKeyHex.isBlank()) return false
        return runCatching {
            val factory = KeyFactory.getInstance("EC")
            val key = factory.generatePublic(X509EncodedKeySpec(fromHex(publicKeyHex)))
            val signature = Signature.getInstance(ALGORITHM)
            signature.initVerify(key)
            signature.update(canonical.toByteArray(Charsets.UTF_8))
            signature.verify(fromHex(signatureHex))
        }.getOrDefault(false)
    }

    companion object {
        private const val ALGORITHM = "SHA256withECDSA"

        @JvmStatic
        fun toHex(bytes: ByteArray): String {
            val hex = StringBuilder(bytes.size * 2)
            for (value in bytes) hex.append(String.format("%02x", value))
            return hex.toString()
        }

        @JvmStatic
        fun fromHex(hex: String): ByteArray {
            val clean = hex.trim()
            require(clean.length % 2 == 0) { "Invalid hex" }
            return ByteArray(clean.length / 2) { index ->
                clean.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
        }
    }
}
