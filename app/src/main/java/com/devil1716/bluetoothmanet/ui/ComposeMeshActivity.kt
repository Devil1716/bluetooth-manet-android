package com.devil1716.bluetoothmanet.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
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
import com.devil1716.bluetoothmanet.MeshDiagnostics
import com.devil1716.bluetoothmanet.MeshIo
import com.devil1716.bluetoothmanet.MeshService
import com.devil1716.bluetoothmanet.PeerDevice
import com.devil1716.bluetoothmanet.update.AppUpdater
import com.devil1716.bluetoothmanet.update.UpdateListener
import com.devil1716.bluetoothmanet.update.UpdatePhase
import java.io.File
import java.util.LinkedHashMap
import java.util.Locale
import java.util.concurrent.Executors

class ComposeMeshActivity : ComponentActivity() {
    private val viewModel: MeshHomeViewModel by lazy {
        ViewModelProvider(this)[MeshHomeViewModel::class.java]
    }
    private val updateListener = UpdateListener { state ->
        if (!isDestroyed) runOnUiThread { viewModel.setUpdate(state) }
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
            if (uri == null) {
                viewModel.setFileTransfer(FileTransferUi(phase = FileTransferPhase.NONE))
                return@registerForActivityResult
            }
            if (destination.isNullOrBlank()) {
                viewModel.setFileTransfer(
                    FileTransferUi(
                        phase = FileTransferPhase.FAILED,
                        error = "Couldn't send the file. Open the chat again and pick it once more."
                    )
                )
                return@registerForActivityResult
            }
            ioExecutor.execute {
                try {
                    val size = queryFileSize(uri)
                    val name = queryDisplayName(uri)
                    if (size > 0 && MeshIo.exceedsLimit(size, MeshIo.MAX_FILE_BYTES)) {
                        postFileError(name, "This file is larger than 2 MB. Choose a smaller one. Nothing was sent.")
                        return@execute
                    }
                    postOnUi {
                        viewModel.setFileTransfer(
                            FileTransferUi(
                                phase = FileTransferPhase.PREPARING,
                                fileName = name,
                                sizeLabel = formatByteSize(size),
                                completed = 0,
                                total = 0
                            )
                        )
                    }
                    contentResolver.openInputStream(uri).use { input ->
                        if (input == null) throw java.io.IOException("missing")
                        val bytes = MeshIo.readBounded(input, MeshIo.MAX_FILE_BYTES)
                        startMeshService()
                        val sent = MeshService.sendFile(this, destination, name, bytes)
                        postOnUi {
                            viewModel.setFileTransfer(
                                FileTransferUi(
                                    phase = if (sent) FileTransferPhase.SENDING else FileTransferPhase.FAILED,
                                    fileName = name,
                                    sizeLabel = formatByteSize(bytes.size.toLong()),
                                    completed = 0,
                                    total = 0,
                                    error = if (sent) "" else "Connection lost. The file is still on your phone — try again when someone is nearby."
                                )
                            )
                            if (sent) {
                                viewModel.onMeshStatus(null, null, "Signed file transfer queued: $name")
                            }
                        }
                    }
                } catch (_: MeshIo.FileTooLargeException) {
                    postFileError(null, "This file is larger than 2 MB. Choose a smaller one. Nothing was sent.")
                } catch (_: OutOfMemoryError) {
                    postFileError(null, "This file is too large for this phone to send.")
                } catch (_: SecurityException) {
                    postFileError(null, "MESH lost access to that file. Pick it again.")
                } catch (_: Exception) {
                    postFileError(null, "Couldn't open the file. It may have been moved. Pick it again.")
                }
            }
        }

    private val enableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.appendLog("Bluetooth setup finished.")
            if (bluetoothAdapter?.isEnabled == true) {
                preloadBondedDevices()
                maybeAutoStartMesh()
            }
        }

    private val discoverableLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.appendLog("Visible-to-devices request finished.")
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            getSharedPreferences("mesh", MODE_PRIVATE).edit().putBoolean("permissions_asked", true).apply()
            val granted = result.isNotEmpty() && result.values.all { it } && missingPermissions().isEmpty()
            refreshPermissionState()
            viewModel.appendLog(if (granted) "Bluetooth permission granted." else "Bluetooth permission was denied.")
            if (granted) {
                preloadBondedDevices()
                maybeAutoStartMesh()
            }
        }

    private val unknownSourcesLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls()) {
                AppUpdater.continueInstall(this, updateListener)
            } else {
                viewModel.setUpdate(
                    viewModel.uiState.value.update.copy(
                        phase = UpdatePhase.NEEDS_PERMISSION,
                        message = "Install permission is still off. Allow Mesh to install updates."
                    )
                )
            }
        }

    private val discoveryReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) as BluetoothDevice?
                    if (device?.address != null) {
                        val name = try {
                            device.name
                        } catch (_: SecurityException) {
                            null
                        }
                        discoveredPeers[device.address] = PeerDevice(name, device.address)
                        viewModel.setClassicPeers(discoveredPeers.values.toList())
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> viewModel.appendLog("Looking for paired devices…")
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED ->
                    viewModel.appendLog(String.format(Locale.US, "Found %d device(s).", discoveredPeers.size))
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    refreshPermissionState()
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (state == BluetoothAdapter.STATE_OFF) {
                        viewModel.markMeshStopped()
                        viewModel.appendLog("Bluetooth turned off. Queued messages stay on this phone.")
                    }
                }
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
            val fileName = intent.getStringExtra("file_name")
            val hasFileProgress = intent.hasExtra("file_total")
            val fileProgress = when {
                filePath != null -> {
                    lastReceivedFilePath = filePath
                    "File saved. Tap the message to open: $fileName"
                }
                hasFileProgress ->
                    "File $fileName: " +
                        "${intent.getIntExtra("file_completed", 0)}/${intent.getIntExtra("file_total", 0)}"
                else -> null
            }
            val transferUpdate = when {
                filePath != null || hasFileProgress -> fileTransferFromProgress(
                    fileName = fileName,
                    completed = intent.getIntExtra("file_completed", if (filePath != null) 1 else 0),
                    total = intent.getIntExtra("file_total", if (filePath != null) 1 else 0),
                    receivedPath = filePath,
                    previous = viewModel.uiState.value.fileTransfer
                )
                else -> null
            }
            viewModel.onMeshStatus(intent.getStringExtra("message"), peers, fileProgress, transferUpdate)
            if (intent.getBooleanExtra("nearby_changed", false)) {
                viewModel.onNearbyRosterChanged(intent.getIntExtra("nearby_count", 0))
            }
            if (intent.getBooleanExtra("mesh_stopped", false)) {
                viewModel.markMeshStopped()
            }
            intent.getStringExtra("identity_conflict")?.let { node ->
                viewModel.noteIdentityConflict(node, intent.getStringExtra("identity_fingerprint").orEmpty())
            }
            if (filePath != null) viewModel.refresh()
        }
    }

    private val installResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != AppUpdater.ACTION_INSTALL_RESULT) return
            AppUpdater.handleInstallResult(this@ComposeMeshActivity, intent, updateListener)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingFileDestination = savedInstanceState?.getString(STATE_FILE_DESTINATION)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "This phone does not support Bluetooth, so MESH cannot run.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val discoveryFilter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(this, discoveryReceiver, discoveryFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(this, meshEventReceiver, IntentFilter(MeshService.ACTION_MESSAGE_EVENT), ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(this, meshStatusReceiver, IntentFilter(MeshService.ACTION_MESH_STATUS), ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(
            this,
            installResultReceiver,
            IntentFilter(AppUpdater.ACTION_INSTALL_RESULT),
            ContextCompat.RECEIVER_EXPORTED
        )
        receiversRegistered = true
        MeshDiagnostics.record("lifecycle", "activity_create")

        viewModel.appendLog("Mesh v${BuildConfig.VERSION_NAME} ready.")
        refreshPermissionState()
        preloadBondedDevices()
        maybeAutoStartMesh()
        window.decorView.post { AppUpdater.checkQuietly(this, BuildConfig.VERSION_NAME, updateListener) }

        setContent {
            MeshApp(
                viewModel = viewModel,
                actions = MeshActions(
                    startMesh = { startListening() },
                    stopMesh = { stopMesh() },
                    enableBluetooth = { ensureBluetoothEnabled() },
                    makeDiscoverable = { requestDiscoverableMode() },
                    discoverPeers = { startDiscovery() },
                    connectPeer = { peer ->
                        MeshService.connectToAddress(this, peer.address)
                        viewModel.appendLog("Connecting to ${peer.name ?: peer.address}…")
                    },
                    sendMessage = { destination, body ->
                        if (!viewModel.uiState.value.meshStarted) {
                            Toast.makeText(this, "Turn on nearby chat first.", Toast.LENGTH_SHORT).show()
                            false
                        } else {
                            startMeshService()
                            val sent = MeshService.sendMessage(this, destination, body)
                            if (!sent) {
                                Toast.makeText(this, "Couldn't send. Check Bluetooth permission, then try again.", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.refresh()
                            }
                            sent
                        }
                    },
                    pickFile = { destination ->
                        if (destination.isBlank()) {
                            Toast.makeText(this, "Open a chat before sending a file.", Toast.LENGTH_SHORT).show()
                        } else if (!viewModel.uiState.value.meshStarted) {
                            viewModel.setFileTransfer(
                                FileTransferUi(
                                    phase = FileTransferPhase.FAILED,
                                    error = "Turn on nearby chat before sending a file."
                                )
                            )
                        } else {
                            pendingFileDestination = destination
                            startMeshService()
                            filePickerLauncher.launch("*/*")
                        }
                    },
                    openFile = { path -> openReceivedFile(path) },
                    checkUpdate = { applyMeshUpdate() },
                    applyUpdate = { applyMeshUpdate() },
                    dismissUpdate = { viewModel.dismissUpdate() },
                    uninstallForUpdate = { uninstallForUpdate() },
                    openUpdatePage = { openUpdatePage() },
                    openLegacyConsole = {
                        startActivity(Intent(this, MainActivity::class.java))
                    },
                    copyNodeId = { copyNodeId() },
                    shareNodeId = { shareNodeId() },
                    requestPermissions = { requestNeededPermissions() },
                    openAppSettings = { openAppSettings() },
                    openLocationSettings = { openLocationSettings() },
                    openNotificationSettings = { openNotificationSettings() }
                )
            )
        }
    }

    private fun requestNeededPermissions() {
        val permissions = missingPermissions()
        getSharedPreferences("mesh", MODE_PRIVATE).edit().putBoolean("permissions_asked", true).apply()
        if (permissions.isNotEmpty()) permissionLauncher.launch(permissions.toTypedArray())
        else maybeAutoStartMesh()
    }

    private fun missingPermissions(): List<String> {
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
        return permissions
    }

    private fun refreshPermissionState() {
        val missing = missingPermissions()
        val asked = getSharedPreferences("mesh", MODE_PRIVATE).getBoolean("permissions_asked", false)
        val permanentlyDenied = asked && missing.any { !shouldShowRequestPermissionRationale(it) }
        val locationOff = isLocationServicesOff()
        val bluetoothOff = bluetoothAdapter?.isEnabled != true
        viewModel.setPermissionState(
            PermissionUi(
                allGranted = missing.isEmpty(),
                missingCount = missing.size,
                permanentlyDenied = permanentlyDenied,
                locationServicesOff = locationOff,
                bluetoothOff = bluetoothOff,
                showRationale = missing.isNotEmpty() && !permanentlyDenied
            )
        )
    }

    private fun isLocationServicesOff(): Boolean {
        val locationManager = getSystemService(LOCATION_SERVICE) as? LocationManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            !locationManager.isLocationEnabled
        } else {
            !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
                !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    private fun maybeAutoStartMesh() {
        refreshPermissionState()
        val permission = viewModel.uiState.value.permission
        if (permission.readyForMesh) {
            startListening()
        }
    }

    private fun copyNodeId() {
        val nodeId = viewModel.uiState.value.nodeId
        if (nodeId.isBlank()) return
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Mesh node ID", nodeId))
        viewModel.markNodeIdCopied()
        viewModel.appendLog("Copied ID $nodeId")
    }

    private fun shareNodeId() {
        val nodeId = viewModel.uiState.value.nodeId
        if (nodeId.isBlank()) return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Chat with me on Mesh. My ID is $nodeId — no pairing.")
        }
        startActivity(Intent.createChooser(send, "Share my ID"))
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
        )
    }

    private fun openLocationSettings() {
        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }

    private fun openNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
        }
        startActivity(intent)
    }

    private fun maybeAdd(permissions: MutableList<String>, permission: String) {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(permission)
        }
    }

    private fun ensureBluetoothEnabled() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestNeededPermissions()
            return
        }
        if (bluetoothAdapter?.isEnabled == false) {
            try {
                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } catch (_: SecurityException) {
                requestNeededPermissions()
            }
        } else {
            viewModel.appendLog("Bluetooth is already on.")
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestNeededPermissions()
            return
        }
        if (adapter.isEnabled != true) {
            viewModel.appendLog("Turn Bluetooth on to find devices.")
            ensureBluetoothEnabled()
            return
        }
        discoveredPeers.clear()
        preloadBondedDevices()
        try {
            if (adapter.isDiscovering) adapter.cancelDiscovery()
            if (adapter.startDiscovery()) viewModel.appendLog("Looking for paired devices…")
            else viewModel.appendLog("Couldn't start device search.")
        } catch (_: SecurityException) {
            requestNeededPermissions()
        }
    }

    private fun startListening() {
        refreshPermissionState()
        val permission = viewModel.uiState.value.permission
        if (!permission.allGranted) {
            viewModel.appendLog("Allow Bluetooth before turning on nearby chat.")
            requestNeededPermissions()
            return
        }
        if (permission.bluetoothOff) {
            viewModel.appendLog("Turn Bluetooth on before nearby chat.")
            ensureBluetoothEnabled()
            return
        }
        startMeshService()
        viewModel.markMeshStarted()
        viewModel.appendLog("Nearby chat is on.")
        MeshDiagnostics.record("lifecycle", "mesh_start")
        if (permission.locationServicesOff) {
            viewModel.appendLog("Turn on Location so this phone can find nearby chats.")
        }
    }

    private fun stopMesh() {
        val intent = Intent(this, MeshService::class.java).setAction(MeshService.ACTION_STOP)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, intent)
            } else {
                startService(intent)
            }
        } catch (_: IllegalStateException) {
            stopService(Intent(this, MeshService::class.java))
        }
        viewModel.markMeshStopped()
        MeshDiagnostics.record("lifecycle", "mesh_stop")
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

    private fun queryFileSize(uri: Uri): Long {
        contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getLong(0)
        }
        return -1L
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

    private fun startAppUpdate() {
        applyMeshUpdate()
    }

    private fun applyMeshUpdate() {
        when (viewModel.uiState.value.update.phase) {
            UpdatePhase.NEEDS_PERMISSION ->
                unknownSourcesLauncher.launch(AppUpdater.unknownSourcesIntent(this))
            UpdatePhase.SIGNATURE_CONFLICT -> uninstallForUpdate()
            else -> AppUpdater.startFromBanner(this, BuildConfig.VERSION_NAME, updateListener)
        }
    }

    private fun uninstallForUpdate() {
        copyNodeId()
        Toast.makeText(
            this,
            "Node ID copied. Uninstall Mesh, then install the new APK.",
            Toast.LENGTH_LONG
        ).show()
        startActivity(AppUpdater.uninstallIntent(this))
        openUpdatePage()
    }

    private fun openUpdatePage() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppUpdater.latestApkPage())))
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
        if (viewModel.uiState.value.permission.readyForMesh && !viewModel.uiState.value.meshStarted) {
            maybeAutoStartMesh()
        }
        AppUpdater.installPendingIfReady(this, updateListener)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingFileDestination?.let { outState.putString(STATE_FILE_DESTINATION, it) }
    }

    override fun onDestroy() {
        if (receiversRegistered) {
            runCatching { unregisterReceiver(discoveryReceiver) }
            runCatching { unregisterReceiver(meshEventReceiver) }
            runCatching { unregisterReceiver(meshStatusReceiver) }
            runCatching { unregisterReceiver(installResultReceiver) }
        }
        ioExecutor.shutdown()
        super.onDestroy()
    }

    private fun postOnUi(block: () -> Unit) {
        if (isDestroyed) return
        runOnUiThread {
            if (!isDestroyed) block()
        }
    }

    private fun postFileError(fileName: String?, message: String) {
        postOnUi {
            viewModel.setFileTransfer(
                FileTransferUi(
                    phase = FileTransferPhase.FAILED,
                    fileName = fileName.orEmpty(),
                    error = message
                )
            )
        }
    }

    companion object {
        private const val STATE_FILE_DESTINATION = "pending_file_destination"
    }
}
