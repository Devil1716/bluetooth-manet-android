package com.devil1716.bluetoothmanet.crypto

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

class PacketSigner private constructor(
    private val privateKey: PrivateKey,
    private val publicKey: PublicKey
) {
    constructor(storeDir: File) : this(loadOrCreateFileKeys(storeDir))

    private constructor(keys: Pair<PrivateKey, PublicKey>) : this(keys.first, keys.second)

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
        private const val KEYSTORE_ALIAS = "manet.identity.ecdsa.p256"

        @JvmStatic
        @JvmOverloads
        fun createForApp(
            context: Context,
            storeDir: File = File(context.applicationContext.filesDir, "identity")
        ): PacketSigner {
            val privateFile = File(storeDir, "mesh-ecdsa-private.key")
            if (privateFile.isFile) return PacketSigner(storeDir)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return runCatching { fromAndroidKeystore() }.getOrElse { PacketSigner(storeDir) }
            }
            return PacketSigner(storeDir)
        }

        private fun loadOrCreateFileKeys(storeDir: File): Pair<PrivateKey, PublicKey> {
            storeDir.mkdirs()
            val privateFile = File(storeDir, "mesh-ecdsa-private.key")
            val publicFile = File(storeDir, "mesh-ecdsa-public.key")
            val factory = KeyFactory.getInstance("EC")
            if (privateFile.isFile && publicFile.isFile) {
                return factory.generatePrivate(PKCS8EncodedKeySpec(privateFile.readBytes())) to
                    factory.generatePublic(X509EncodedKeySpec(publicFile.readBytes()))
            }
            val generator = KeyPairGenerator.getInstance("EC")
            generator.initialize(ECGenParameterSpec("secp256r1"))
            val pair = generator.generateKeyPair()
            privateFile.writeBytes(pair.private.encoded)
            publicFile.writeBytes(pair.public.encoded)
            return pair.private to pair.public
        }

        private fun fromAndroidKeystore(): PacketSigner {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
                val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
                generator.initialize(
                    KeyGenParameterSpec.Builder(
                        KEYSTORE_ALIAS,
                        KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                    )
                        .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .setUserAuthenticationRequired(false)
                        .build()
                )
                generator.generateKeyPair()
            }
            val entry = keyStore.getEntry(KEYSTORE_ALIAS, null) as KeyStore.PrivateKeyEntry
            return PacketSigner(entry.privateKey, entry.certificate.publicKey)
        }

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
