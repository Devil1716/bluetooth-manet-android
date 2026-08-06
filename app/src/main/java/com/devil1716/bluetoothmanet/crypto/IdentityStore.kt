package com.devil1716.bluetoothmanet.crypto

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import java.security.KeyStore
import javax.crypto.KeyGenerator

class IdentityStore(context: Context) {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val appContext = context.applicationContext

    fun ensureIdentity(alias: String = DEFAULT_ALIAS) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        ensureKeystoreIdentity(alias)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun ensureKeystoreIdentity(alias: String) {
        if (keyStore.containsAlias(alias)) return
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build()
        )
        generator.generateKey()
    }

    fun hasIdentity(alias: String = DEFAULT_ALIAS): Boolean = keyStore.containsAlias(alias)

    companion object { const val DEFAULT_ALIAS = "manet.identity.aes256" }
}
