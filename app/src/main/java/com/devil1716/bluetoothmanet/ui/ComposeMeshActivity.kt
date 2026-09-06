package com.devil1716.bluetoothmanet.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import com.devil1716.bluetoothmanet.BuildConfig
import com.devil1716.bluetoothmanet.MainActivity
import com.devil1716.bluetoothmanet.MeshService
import com.devil1716.bluetoothmanet.PeerDevice
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.LinkedHashMap
import java.util.Locale
import java.util.concurrent.Executors

class ComposeMeshActivity : ComponentActivity() {
    private val viewModel: MeshHomeViewModel by lazy {
        ViewModelProvider(this)[MeshHomeViewModel::class.java]
    }
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val discoveredPeers = LinkedHashMap<String, PeerDevice>()
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private var pendingFileDestination: String? = null
    private var lastReceivedFilePath: String? = null
    private var receiversRegistered = false

    private val filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            val destination = pendingFileDestination
            pendingFileDestination = null
            if (uri == null || destination.isNullOrBlank()) return@registerForActivityResult
            ioExecutor.execute {
                try {
                    contentResolver.openInputStream(uri).use { input ->
                        if (input == null) throw IllegalStateException("Could not open file")
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) output.write(buffer, 0, read)
                        val name = queryDisplayName(uri)
                        startMeshService()
                        val sent = MeshService.sendFile(this, destination, name, output.toByteArray())
                        runOnUiThread {
                            viewModel.onMeshStatus(
                                null,
                                null,
                                if (sent) "Signed file transfer queued: $name"
                                else "File transfer failed. Start mesh and check the event log."
                            )
                        }
                    }
                } catch (error: Exception) {
                    runOnUiThread { viewModel.onMeshStatus(null, null, "File error: ${error.message}") }
                }
            }
        }

    private val enableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.appendLog("Bluetooth enable flow finished.")
            if (bluetoothAdapter?.isEnabled == true) {
                preloadBondedDevices()
                startMeshService()
            }
        }

    private val discoverableLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.appendLog("Discoverable request finished.")
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = result.values.all { it }
            viewModel.appendLog(if (granted) "Permissions granted." else "Some Bluetooth permissions were denied.")
            if (granted) {
                preloadBondedDevices()
                startMeshService()
            }
        }

    private val discoveryReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) as BluetoothDevice?
                    if (device?.address != null) {
                        discoveredPeers[device.address] = PeerDevice(device.name, device.address)
                        viewModel.setClassicPeers(discoveredPeers.values.toList())
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> viewModel.appendLog("Discovery started...")
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED ->
                    viewModel.appendLog(String.format(Locale.US, "Discovery finished. %d peer(s) listed.", discoveredPeers.size))
            }
        }
    }

    private val meshEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != MeshService.ACTION_MESSAGE_EVENT) return
            viewModel.refresh()
        }
    }

    private val meshStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val peers = intent.getStringArrayListExtra("peers")
            val filePath = intent.getStringExtra("file_path")
            val fileProgress = when {
                filePath != null -> {
                    lastReceivedFilePath = filePath
                    val fileName = intent.getStringExtra("file_name")
                    Toast.makeText(context, "File received: $fileName", Toast.LENGTH_LONG).show()
                    "Verified file saved. Tap the message to open: $fileName"
                }
                intent.hasExtra("file_total") ->
                    "File ${intent.getStringExtra("file_name")}: " +
                        "${intent.getIntExtra("file_completed", 0)}/${intent.getIntExtra("file_total", 0)}"
                else -> null
            }
            viewModel.onMeshStatus(intent.getStringExtra("message"), peers, fileProgress)
            if (filePath != null) viewModel.refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not supported on this device.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        ContextCompat.registerReceiver(this, discoveryReceiver, IntentFilter(BluetoothDevice.ACTION_FOUND), ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(this, discoveryReceiver, IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED), ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(this, discoveryReceiver, IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED), ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(this, meshEventReceiver, IntentFilter(MeshService.ACTION_MESSAGE_EVENT), ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(this, meshStatusReceiver, IntentFilter(MeshService.ACTION_MESH_STATUS), ContextCompat.RECEIVER_NOT_EXPORTED)
        receiversRegistered = true

        viewModel.appendLog("Mesh v${BuildConfig.VERSION_NAME} ready. Messages and files are ECDSA-signed.")
        requestNeededPermissions()
        preloadBondedDevices()

        setContent {
            MeshApp(
                viewModel = viewModel,
                actions = MeshActions(
                    startMesh = { startListening() },
                    enableBluetooth = { ensureBluetoothEnabled() },
                    makeDiscoverable = { requestDiscoverableMode() },
                    discoverPeers = { startDiscovery() },
                    connectPeer = { peer ->
                        MeshService.connectToAddress(this, peer.address)
                        viewModel.appendLog("Connecting to ${peer.address} over BLE and RFCOMM...")
                    },
                    sendMessage = { destination, body ->
                        startMeshService()
                        val sent = MeshService.sendMessage(this, destination, body)
                        Toast.makeText(
                            this,
                            if (sent) "Message sent into the mesh." else "Message was not sent. Check mesh setup.",
                            Toast.LENGTH_SHORT
                        ).show()
                        if (sent) viewModel.refresh()
                        sent
                    },
                    pickFile = { destination ->
                        if (destination.isBlank()) {
                            Toast.makeText(this, "Enter the destination node ID before sending a file.", Toast.LENGTH_SHORT).show()
                        } else {
                            pendingFileDestination = destination
                            startMeshService()
                            filePickerLauncher.launch("*/*")
                        }
                    },
                    openFile = { path -> openReceivedFile(path) },
                    checkUpdate = { openGithubUpdate() },
                    openLegacyConsole = {
                        startActivity(Intent(this, MainActivity::class.java))
                    }
                )
            )
        }
    }

    private fun requestNeededPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            maybeAdd(permissions, Manifest.permission.BLUETOOTH_CONNECT)
            maybeAdd(permissions, Manifest.permission.BLUETOOTH_SCAN)
            maybeAdd(permissions, Manifest.permission.BLUETOOTH_ADVERTISE)
            maybeAdd(permissions, Manifest.permission.ACCESS_FINE_LOCATION)
            maybeAdd(permissions, Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                maybeAdd(permissions, Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            maybeAdd(permissions, Manifest.permission.ACCESS_FINE_LOCATION)
            maybeAdd(permissions, Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (permissions.isNotEmpty()) permissionLauncher.launch(permissions.toTypedArray())
        else startMeshService()
    }

    private fun maybeAdd(permissions: MutableList<String>, permission: String) {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(permission)
        }
    }

    private fun ensureBluetoothEnabled() {
        if (bluetoothAdapter?.isEnabled == false) {
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } else {
            viewModel.appendLog("Bluetooth is already enabled.")
        }
    }

    private fun requestDiscoverableMode() {
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
        intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
        discoverableLauncher.launch(intent)
    }

    @SuppressLint("MissingPermission")
    private fun startDiscovery() {
        val adapter = bluetoothAdapter ?: return
        if (adapter.isEnabled != true) {
            viewModel.appendLog("Bluetooth is OFF. Enable it before discovery.")
            ensureBluetoothEnabled()
            return
        }
        discoveredPeers.clear()
        preloadBondedDevices()
        if (adapter.isDiscovering) adapter.cancelDiscovery()
        if (adapter.startDiscovery()) viewModel.appendLog("Discovery started...")
        else viewModel.appendLog("Failed to start discovery.")
    }

    private fun startListening() {
        startMeshService()
        viewModel.appendLog("Mesh started: BLE dual-role (no pairing) plus RFCOMM for classic/Windows peers.")
        val locationManager = getSystemService(LOCATION_SERVICE) as? android.location.LocationManager
        if (locationManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !locationManager.isLocationEnabled) {
            viewModel.appendLog("Turn on Location in Android settings. Many phones will not BLE-scan while Location is off.")
        }
    }

    @SuppressLint("MissingPermission")
    private fun preloadBondedDevices() {
        val adapter = bluetoothAdapter ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) return
        adapter.bondedDevices?.forEach { device ->
            discoveredPeers[device.address] = PeerDevice(device.name, device.address)
        }
        viewModel.setClassicPeers(discoveredPeers.values.toList())
    }

    private fun startMeshService() {
        val nodeId = viewModel.uiState.value.nodeId
        getSharedPreferences("mesh", MODE_PRIVATE).edit().putString("node_id", nodeId).apply()
        val serviceIntent = Intent(this, MeshService::class.java).putExtra("node_id", nodeId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun queryDisplayName(uri: Uri): String {
        var fallback = uri.lastPathSegment ?: "shared-file"
        if (fallback.contains("/")) fallback = fallback.substringAfterLast('/')
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val name = cursor.getString(0)
                if (!name.isNullOrBlank()) return name
            }
        }
        return fallback
    }

    private fun openReceivedFile(path: String?) {
        if (path.isNullOrBlank()) return
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(this, "File is no longer on disk.", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val mime = android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(android.webkit.MimeTypeMap.getFileExtensionFromUrl(file.name))
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(Intent.createChooser(intent, "Open file"))
        } catch (_: Exception) {
            Toast.makeText(this, "No app can open this file.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openGithubUpdate() {
        viewModel.appendLog("Checking GitHub for the latest APK...")
        Thread {
            try {
                val connection = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/vnd.github+json")
                    connectTimeout = 15_000
                    readTimeout = 15_000
                }
                if (connection.responseCode !in 200..299) {
                    throw IllegalStateException("GitHub API returned ${connection.responseCode}")
                }
                val payload = BufferedReader(InputStreamReader(connection.inputStream)).readText()
                connection.disconnect()
                val release = JSONObject(payload)
                val tagName = release.optString("tag_name", "latest")
                val assets: JSONArray? = release.optJSONArray("assets")
                var apkUrl: String? = null
                if (assets != null) {
                    for (index in 0 until assets.length()) {
                        val asset = assets.getJSONObject(index)
                        if (asset.optString("name") == "app-debug.apk") {
                            apkUrl = asset.optString("browser_download_url")
                            break
                        }
                    }
                }
                val resolved = if (apkUrl.isNullOrEmpty()) LATEST_RELEASE_PAGE else apkUrl
                runOnUiThread {
                    viewModel.appendLog("Opening GitHub release $tagName...")
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(resolved)))
                    Toast.makeText(this, "Opening the latest release in your browser.", Toast.LENGTH_LONG).show()
                }
            } catch (_: Exception) {
                runOnUiThread {
                    viewModel.appendLog("Update check failed. Opening releases page instead.")
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LATEST_RELEASE_PAGE)))
                    Toast.makeText(this, "Could not resolve the APK directly. Opening releases page.", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    override fun onDestroy() {
        if (receiversRegistered) {
            unregisterReceiver(discoveryReceiver)
            unregisterReceiver(meshEventReceiver)
            unregisterReceiver(meshStatusReceiver)
        }
        ioExecutor.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val LATEST_RELEASE_API =
            "https://api.github.com/repos/Devil1716/bluetooth-manet-android/releases/latest"
        private const val LATEST_RELEASE_PAGE =
            "https://github.com/Devil1716/bluetooth-manet-android/releases/latest"
    }
}
