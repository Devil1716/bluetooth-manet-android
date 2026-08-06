package com.devil1716.bluetoothmanet.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/**
 * Compose migration entry point. It stays separate from the legacy launcher until
 * repositories and the BLE GATT transport are connected to the Compose screens.
 */
class ComposeMeshActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MeshApp() }
    }
}
