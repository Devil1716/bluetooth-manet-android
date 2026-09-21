package com.devil1716.bluetoothmanet.bluetooth.gatt

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.devil1716.bluetoothmanet.bluetooth.MeshPeer
import com.devil1716.bluetoothmanet.bluetooth.MeshPresence
import com.devil1716.bluetoothmanet.bluetooth.shouldInitiateLink
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

/**
 * BitChat-style dual-role BLE mesh: this phone is a GATT peripheral and a GATT
 * central at the same time. Nearby MANET nodes are discovered from connectable
 * advertisements; no pairing is required.
 */
class BleGattTransport(
    context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun onLog(message: String)

        /** The roster of mesh phones in range, including ones not yet linked. */
        fun onPeersChanged(peers: List<MeshPeer>)
        fun onPayloadReceived(peerId: String, payload: ByteArray)
    }

    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter
    private val worker = HandlerThread("manet-ble-gatt").apply { start() }
    private val handler = Handler(worker.looper)
    private val links = ConcurrentHashMap<String, GattLink>()
    private val lastAttempt = ConcurrentHashMap<String, Long>()
    private val clientCallbacks = ConcurrentHashMap<String, ClientCallback>()
    private val connectTimeouts = ConcurrentHashMap<String, Runnable>()
    private val presence = MeshPresence()

    private var nodeId = DEFAULT_NODE_ID
    private var running = false
    private var scanning = false
    private var gattServer: BluetoothGattServer? = null
    private var packetCharacteristic: BluetoothGattCharacteristic? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertiseCallback: AdvertiseCallback? = null

    private var lastScanCycleAt = 0L
    private var lastCollisionLogAt = 0L

    /**
     * Expires peers that walked away, dials peers the tie-break rule left
     * stranded, and periodically cycles the scan so a long-running scan is not
     * quietly downgraded by the platform.
     */
    private val maintenance = object : Runnable {
        override fun run() {
            if (!running) return
            val now = System.currentTimeMillis()
            if (presence.prune(now)) publishPeers()
            sweepIdleLinks()
            dialStalledPeers()
            // Android rejects more than a handful of scan starts per 30s and
            // then stops reporting results, so cycle well inside that budget.
            if (now - lastScanCycleAt >= SCAN_CYCLE_INTERVAL_MS) {
                lastScanCycleAt = now
                restartScanning()
            }
            handler.postDelayed(this, MAINTENANCE_INTERVAL_MS)
        }
    }

    fun start(nodeId: String) {
        this.nodeId = MeshPresence.normalizeNodeId(nodeId).ifEmpty { DEFAULT_NODE_ID }
        handler.post { startLocked() }
    }

    /** Records the node ID proven by a signed HELLO over this link. */
    fun noteVerifiedNodeId(address: String, peerNodeId: String) {
        handler.post {
            val key = MeshPresence.normalizeAddress(address) ?: return@post
            links[key]?.nodeId = MeshPresence.normalizeNodeId(peerNodeId)
            if (presence.onVerifiedNodeId(key, peerNodeId, System.currentTimeMillis())) {
                publishPeers()
            }
        }
    }

    /** Mesh phones in range right now, linked or merely advertising. */
    fun nearbyPeers(): List<MeshPeer> = presence.snapshot(System.currentTimeMillis())

    fun stop() {
        pauseInternal(quitWorker = true)
    }

    fun pause() {
        pauseInternal(quitWorker = false)
    }

    private fun pauseInternal(quitWorker: Boolean) {
        val done = java.util.concurrent.CountDownLatch(1)
        val posted = handler.post {
            try {
                stopLocked()
            } finally {
                done.countDown()
            }
        }
        if (posted) {
            try {
                done.await(2, java.util.concurrent.TimeUnit.SECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        } else {
            stopLocked()
        }
        if (quitWorker) {
            worker.quitSafely()
        }
    }

    fun connect(address: String) {
        handler.post {
            val device = runCatching { adapter?.getRemoteDevice(address) }.getOrNull() ?: return@post
            connectAsClient(device, forced = true)
        }
    }

    fun send(bytes: ByteArray, exceptPeerId: String?): Int {
        if (bytes.isEmpty() || links.isEmpty()) return 0
        var sent = 0
        for (link in links.values) {
            if (link.address.equals(exceptPeerId, ignoreCase = true)) continue
            // A half-open link has no writable role yet; counting it would make
            // the mesh manager believe a packet was handed off when it was not.
            if (!link.hasAnyRole()) continue
            handler.post { enqueue(link, bytes) }
            sent++
        }
        return sent
    }

    /**
     * Parks the caller until every writable link has drained under
     * [MAX_QUEUED_BYTES]. File send used to enqueue a whole 2 MB transfer at
     * once (~hundreds of thousands of GATT fragments on a 23-byte MTU), which
     * OOMs low-end phones.
     */
    fun awaitSendWindow() {
        if (android.os.Looper.myLooper() == handler.looper) return
        val deadline = System.currentTimeMillis() + SEND_WINDOW_TIMEOUT_MS
        while (running && System.currentTimeMillis() < deadline) {
            val gate = java.util.concurrent.CountDownLatch(1)
            val crowded = java.util.concurrent.atomic.AtomicBoolean(true)
            if (!handler.post {
                    val ready = links.values.filter { it.hasAnyRole() }
                    // Block only when every live link is full so one slow radio
                    // cannot stall a file that the others can still carry.
                    crowded.set(ready.isNotEmpty() && ready.all { queuedBytes(it) >= MAX_QUEUED_BYTES })
                    gate.countDown()
                }
            ) return
            try {
                if (!gate.await(250, java.util.concurrent.TimeUnit.MILLISECONDS)) return
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
            if (!crowded.get()) return
            try {
                Thread.sleep(8)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
        }
    }

    /** True only when a writable link exists; advertising peers cannot carry data. */
    fun hasPeers(): Boolean = links.values.any { it.hasAnyRole() }

    fun peerLabels(): List<String> = links.values.map { it.label() }

    private fun startLocked() {
        if (running) {
            restartAdvertising()
            return
        }
        if (adapter == null || !adapter.isEnabled) {
            listener.onLog("BLE mesh idle: Bluetooth is off.")
            return
        }
        if (!hasConnectPermission() || !hasScanPermission() || !hasAdvertisePermission()) {
            listener.onLog("BLE mesh idle: Bluetooth permissions are missing.")
            return
        }
        running = true
        // Scan first so nearby phones show up without waiting on GATT service add.
        startScanning()
        if (openServer()) {
            listener.onLog("BLE GATT server registered. Advertising starts after the service is added.")
            handler.postDelayed({
                if (running && advertiseCallback == null) startAdvertising()
            }, 400)
        } else {
            listener.onLog("BLE GATT server could not start; scanning as central only.")
            startAdvertising()
        }
        handler.removeCallbacks(maintenance)
        handler.postDelayed(maintenance, MAINTENANCE_INTERVAL_MS)
        listener.onLog("BLE mesh started as central and peripheral. Pairing is not required.")
    }

    @SuppressLint("MissingPermission")
    private fun stopLocked() {
        running = false
        handler.removeCallbacks(maintenance)
        connectTimeouts.values.forEach { handler.removeCallbacks(it) }
        connectTimeouts.clear()
        stopScanning()
        advertiseCallback?.let { callback ->
            runCatching { advertiser?.stopAdvertising(callback) }
        }
        advertiseCallback = null
        links.values.forEach { link ->
            runCatching { link.gatt?.close() }
        }
        links.clear()
        clientCallbacks.clear()
        lastAttempt.clear()
        presence.clear()
        runCatching { gattServer?.close() }
        gattServer = null
        packetCharacteristic = null
        publishPeers()
    }

    @SuppressLint("MissingPermission")
    private fun openServer(): Boolean {
        if (!hasConnectPermission()) return false
        val server = bluetoothManager.openGattServer(appContext, serverCallback) ?: return false
        val characteristic = BluetoothGattCharacteristic(
            MeshGattProtocol.PACKET_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_WRITE or BluetoothGattCharacteristic.PERMISSION_READ
        )
        val cccd = BluetoothGattDescriptor(
            MeshGattProtocol.CCCD_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        characteristic.addDescriptor(cccd)
        val service = BluetoothGattService(MeshGattProtocol.SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(characteristic)
        if (!server.addService(service)) {
            server.close()
            return false
        }
        gattServer = server
        packetCharacteristic = characteristic
        return true
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertising(txPower: Int = AdvertiseSettings.ADVERTISE_TX_POWER_HIGH) {
        if (!hasAdvertisePermission()) return
        val leAdvertiser = adapter?.bluetoothLeAdvertiser ?: return
        advertiser = leAdvertiser
        advertiseCallback?.let { runCatching { leAdvertiser.stopAdvertising(it) } }
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(txPower)
            .setConnectable(true)
            .setTimeout(0)
            .build()
        val advertiseData = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(MeshGattProtocol.SERVICE_UUID))
            .setIncludeDeviceName(false)
            .build()
        val nodeBytes = nodeId.toByteArray(StandardCharsets.UTF_8).copyOf(minOf(12, nodeId.toByteArray(StandardCharsets.UTF_8).size))
        val scanResponse = AdvertiseData.Builder()
            .addManufacturerData(MeshGattProtocol.MANUFACTURER_ID, nodeBytes)
            .build()
        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                listener.onLog("BLE advertising as node $nodeId (connectable).")
            }

            override fun onStartFailure(errorCode: Int) {
                listener.onLog("BLE advertise failed: $errorCode")
                if (txPower != AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM &&
                    errorCode != AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED
                ) {
                    handler.post { startAdvertising(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM) }
                }
            }
        }
        advertiseCallback = callback
        leAdvertiser.startAdvertising(settings, advertiseData, scanResponse, callback)
    }

    private fun restartAdvertising() {
        if (running && hasAdvertisePermission()) startAdvertising()
    }

    @SuppressLint("MissingPermission")
    private fun startScanning(announce: Boolean = true) {
        if (!hasScanPermission() || scanning) return
        val scanner = adapter?.bluetoothLeScanner ?: return
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(MeshGattProtocol.SERVICE_UUID)).build()
        val settingsBuilder = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setReportDelay(0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            settingsBuilder.setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            settingsBuilder.setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
        }
        val settings = settingsBuilder.build()
        val started = runCatching { scanner.startScan(listOf(filter), settings, scanCallback) }.isSuccess
        scanning = started
        if (started && announce) listener.onLog("BLE scanning for nearby mesh phones...")
    }

    @SuppressLint("MissingPermission")
    private fun stopScanning() {
        if (!scanning) return
        scanning = false
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
    }

    /** Periodic refresh; stays quiet so it does not flood the activity log. */
    private fun restartScanning() {
        stopScanning()
        startScanning(announce = false)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handler.post { onScan(result) }
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            handler.post { results.forEach { onScan(it) } }
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            listener.onLog("BLE scan failed: $errorCode")
            handler.postDelayed({
                if (running) startScanning(announce = false)
            }, 1_000)
        }
    }

    @SuppressLint("MissingPermission")
    private fun onScan(result: ScanResult) {
        if (!running) return
        val device = result.device ?: return
        val address = MeshPresence.normalizeAddress(device.address) ?: return
        val advertisedId = advertisedNodeId(result)
        val now = System.currentTimeMillis()
        // Android never surfaces our own advertisement, so a matching ID means
        // two phones picked the same node ID. Say so instead of going quiet,
        // but only occasionally: scan results arrive many times per second.
        if (advertisedId != null && advertisedId.equals(nodeId, ignoreCase = true)) {
            if (now - lastCollisionLogAt >= COLLISION_LOG_INTERVAL_MS) {
                lastCollisionLogAt = now
                listener.onLog("Another phone nearby uses node ID $nodeId. Change one of them in Profile.")
            }
            return
        }
        // Record the peer before deciding anything about connecting: the Nearby
        // list should show phones in range even while the link is still pending.
        if (presence.onAdvertisement(address, advertisedId, result.rssi, now)) publishPeers()
        links[address]?.rssi = result.rssi
        if (links[address]?.hasAnyRole() == true) return
        if (links.size >= MeshGattProtocol.MAX_LINKS) return
        val previous = lastAttempt[address] ?: 0L
        if (now - previous < CONNECT_RETRY_INTERVAL_MS) return
        if (!shouldInitiateLink(nodeId, advertisedId)) return
        connectAsClient(device, forced = false)
    }

    /**
     * Dials peers that have been visible for a while with no link. The
     * tie-break rule parks the lower-ID side, so without this a peer whose
     * scanner is throttled would never be connected by either phone.
     */
    @SuppressLint("MissingPermission")
    private fun dialStalledPeers() {
        if (links.size >= MeshGattProtocol.MAX_LINKS) return
        val now = System.currentTimeMillis()
        for (peer in presence.stalledPeers(now, LINK_STALL_GRACE_MS)) {
            if (links[peer.address]?.hasAnyRole() == true) continue
            if (now - (lastAttempt[peer.address] ?: 0L) < CONNECT_RETRY_INTERVAL_MS) continue
            val device = runCatching { adapter?.getRemoteDevice(peer.address) }.getOrNull() ?: continue
            connectAsClient(device, forced = false)
            if (links.size >= MeshGattProtocol.MAX_LINKS) return
        }
    }

    /**
     * Removes link slots that hold no GATT role. A stray write from an unknown
     * address creates one, and leaving it behind consumes a slot against
     * [MeshGattProtocol.MAX_LINKS] that real peers then cannot use.
     */
    private fun sweepIdleLinks() {
        for (entry in links.entries.toList()) {
            if (entry.value.hasAnyRole()) continue
            if (clientCallbacks.containsKey(entry.key)) continue
            links.remove(entry.key)
        }
    }

    private fun advertisedNodeId(result: ScanResult): String? {
        val data = result.scanRecord?.getManufacturerSpecificData(MeshGattProtocol.MANUFACTURER_ID) ?: return null
        val decoded = runCatching { String(data, StandardCharsets.UTF_8) }.getOrNull() ?: return null
        return MeshPresence.normalizeNodeId(decoded).takeIf { it.isNotEmpty() }
    }

    @SuppressLint("MissingPermission")
    private fun connectAsClient(device: BluetoothDevice, forced: Boolean) {
        if (!hasConnectPermission() || adapter == null) return
        val address = MeshPresence.normalizeAddress(device.address) ?: return
        if (links[address]?.gatt != null || clientCallbacks.containsKey(address)) {
            if (forced) listener.onLog("Already linking to $address")
            return
        }
        if (!forced && links.size >= MeshGattProtocol.MAX_LINKS) return
        lastAttempt[address] = System.currentTimeMillis()
        listener.onLog("BLE connecting to " + safeName(device))
        val callback = ClientCallback(address)
        clientCallbacks[address] = callback
        val gatt = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                device.connectGatt(
                    appContext,
                    false,
                    callback,
                    BluetoothDevice.TRANSPORT_LE,
                    BluetoothDevice.PHY_LE_1M_MASK or BluetoothDevice.PHY_LE_2M_MASK
                )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                device.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE)
            else -> device.connectGatt(appContext, false, callback)
        }
        if (gatt == null) {
            clientCallbacks.remove(address)
            listener.onLog("BLE connectGatt returned null for $address")
            return
        }
        armConnectTimeout(address, gatt)
    }

    /**
     * A BLE connect attempt can simply never call back. Without this the
     * address stays in [clientCallbacks] forever and that peer can never be
     * retried, so it disappears from the mesh until the app restarts.
     */
    private fun armConnectTimeout(address: String, gatt: BluetoothGatt) {
        cancelConnectTimeout(address)
        val timeout = Runnable {
            connectTimeouts.remove(address)
            if (links[address]?.gatt != null) return@Runnable
            clientCallbacks.remove(address)
            runCatching { gatt.close() }
            listener.onLog("BLE connect to $address timed out; will retry.")
            dropIfIdle(address)
        }
        connectTimeouts[address] = timeout
        handler.postDelayed(timeout, CONNECT_TIMEOUT_MS)
    }

    private fun cancelConnectTimeout(address: String) {
        connectTimeouts.remove(address)?.let { handler.removeCallbacks(it) }
    }

    private val serverCallback = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            handler.post {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    startAdvertising()
                    startScanning()
                } else {
                    listener.onLog("GATT service add failed: $status")
                    startScanning()
                }
            }
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            handler.post {
                val address = MeshPresence.normalizeAddress(device.address) ?: return@post
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    val link = links.getOrPut(address) { GattLink(address) }
                    link.serverDevice = device
                    link.displayDevice = device
                    listener.onLog("BLE peripheral linked " + safeName(device))
                    presence.onLinked(address, link.nodeId, System.currentTimeMillis())
                    publishPeers()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    links[address]?.serverDevice = null
                    dropIfIdle(address)
                }
            }
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            handler.post {
                MeshPresence.normalizeAddress(device.address)?.let { links[it]?.mtu = mtu }
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            handler.post {
                val address = MeshPresence.normalizeAddress(device.address)
                if (descriptor.uuid == MeshGattProtocol.CCCD_UUID && address != null) {
                    val link = links.getOrPut(address) { GattLink(address) }
                    link.serverDevice = device
                    link.displayDevice = device
                    link.notifyEnabled = true
                    drain(link)
                }
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value)
                }
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value)
            }
            if (value == null || value.isEmpty()) return
            val address = MeshPresence.normalizeAddress(device.address) ?: return
            handler.post { ingest(address, device, value) }
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            handler.post {
                val address = MeshPresence.normalizeAddress(device.address) ?: return@post
                completeWrite(links[address] ?: return@post, status == BluetoothGatt.GATT_SUCCESS)
            }
        }
    }

    private inner class ClientCallback(private val address: String) : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            handler.post {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    cancelConnectTimeout(address)
                    val link = links.getOrPut(address) { GattLink(address) }
                    link.gatt = gatt
                    link.displayDevice = gatt.device
                    listener.onLog("BLE central linked " + safeName(gatt.device))
                    presence.onLinked(address, link.nodeId, System.currentTimeMillis())
                    publishPeers()
                    boostLink(gatt)
                    // If the MTU request is refused its callback never fires, so
                    // discovery has to be kicked off here instead.
                    val requested = runCatching { gatt.requestMtu(MeshGattProtocol.REQUEST_MTU) }
                        .getOrDefault(false)
                    if (!requested) runCatching { gatt.discoverServices() }
                } else {
                    cancelConnectTimeout(address)
                    clientCallbacks.remove(address)
                    runCatching { gatt.close() }
                    links[address]?.gatt = null
                    links[address]?.remoteCharacteristic = null
                    dropIfIdle(address)
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            handler.post {
                links[address]?.mtu = mtu
                runCatching { gatt.discoverServices() }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            handler.post { enableClientNotifications(gatt) }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            handler.post {
                val link = links[address] ?: return@post
                link.notifyEnabled = status == BluetoothGatt.GATT_SUCCESS
                drain(link)
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            // WRITE_NO_RESPONSE already advanced the queue in drain().
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val value = characteristic.value ?: return
            handler.post { ingest(address, gatt.device, value) }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handler.post { ingest(address, gatt.device, value) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableClientNotifications(gatt: BluetoothGatt) {
        val address = MeshPresence.normalizeAddress(gatt.device.address) ?: return
        val service = gatt.getService(MeshGattProtocol.SERVICE_UUID)
        val characteristic = service?.getCharacteristic(MeshGattProtocol.PACKET_UUID)
        if (characteristic == null) {
            listener.onLog("Peer $address is not running the MESH service.")
            return
        }
        val link = links.getOrPut(address) { GattLink(address) }
        link.gatt = gatt
        link.displayDevice = gatt.device
        link.remoteCharacteristic = characteristic
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(MeshGattProtocol.CCCD_UUID)
        if (descriptor == null) {
            link.notifyEnabled = true
            drain(link)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }
    }

    private fun ingest(address: String, device: BluetoothDevice, chunk: ByteArray) {
        val link = links.getOrPut(address) { GattLink(address) }
        link.displayDevice = device
        val packets = runCatching { link.assembler.offer(chunk) }.getOrElse {
            listener.onLog("Dropped malformed BLE frame from $address")
            link.assembler.reset()
            return
        }
        for (payload in packets) {
            listener.onPayloadReceived(address, payload)
        }
    }

    private fun enqueue(link: GattLink, payload: ByteArray) {
        if (queuedBytes(link) >= MAX_QUEUED_BYTES * 4) {
            // Prefer a missing-chunk retry over an unbounded queue that kills the process.
            return
        }
        val encoded = LengthPrefixedCodec.encode(payload)
        val pieces = LengthPrefixedCodec.chunks(encoded, link.chunkSize())
        link.queue.addAll(pieces)
        if (!link.sending) drain(link)
    }

    private fun queuedBytes(link: GattLink): Int {
        var sum = 0
        for (chunk in link.queue) sum += chunk.size
        return sum
    }

    @SuppressLint("MissingPermission")
    private fun drain(link: GattLink) {
        if (link.sending) return
        val chunk = link.queue.peek() ?: return
        link.sending = true
        val clientWrite = link.gatt != null && link.remoteCharacteristic != null
        val written = when {
            clientWrite -> writeClient(link, chunk)
            link.serverDevice != null && packetCharacteristic != null -> notifyServer(link, chunk)
            else -> false
        }
        if (!written) {
            link.sending = false
            handler.removeCallbacks(link.writeTimeout)
            handler.postDelayed({ drain(link) }, 80)
            return
        }
        if (clientWrite) {
            // WRITE_NO_RESPONSE is handed to the controller immediately. Pace
            // just enough that a cheap radio is not flooded.
            link.queue.poll()
            link.sending = false
            handler.postDelayed({ drain(link) }, writePaceMs(link))
            return
        }
        handler.removeCallbacks(link.writeTimeout)
        link.writeTimeout = Runnable {
            if (!link.sending) return@Runnable
            completeWrite(link, success = true)
        }
        handler.postDelayed(link.writeTimeout, NOTIFY_CALLBACK_FALLBACK_MS)
    }

    private fun writePaceMs(link: GattLink): Long =
        if (link.mtu >= 64) 5L else 12L

    private fun completeWrite(link: GattLink, success: Boolean) {
        handler.removeCallbacks(link.writeTimeout)
        if (!link.sending) return
        if (success) link.queue.poll()
        link.sending = false
        drain(link)
    }

    @SuppressLint("MissingPermission")
    private fun writeClient(link: GattLink, chunk: ByteArray): Boolean {
        val gatt = link.gatt ?: return false
        val characteristic = link.remoteCharacteristic ?: return false
        val noResponse = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(
                characteristic,
                chunk,
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            ) == BluetoothGatt.GATT_SUCCESS
        } else {
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            characteristic.value = chunk
            gatt.writeCharacteristic(characteristic)
        }
        return noResponse
    }

    @SuppressLint("MissingPermission")
    private fun boostLink(gatt: BluetoothGatt) {
        runCatching { gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                gatt.setPreferredPhy(
                    BluetoothDevice.PHY_LE_2M,
                    BluetoothDevice.PHY_LE_2M,
                    BluetoothDevice.PHY_OPTION_NO_PREFERRED
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun notifyServer(link: GattLink, chunk: ByteArray): Boolean {
        val server = gattServer ?: return false
        val characteristic = packetCharacteristic ?: return false
        val device = link.serverDevice ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            server.notifyCharacteristicChanged(device, characteristic, false, chunk) == BluetoothGatt.GATT_SUCCESS
        } else {
            characteristic.value = chunk
            server.notifyCharacteristicChanged(device, characteristic, false)
        }
    }

    private fun dropIfIdle(address: String) {
        val link = links[address] ?: return
        if (link.hasAnyRole()) return
        handler.removeCallbacks(link.writeTimeout)
        link.queue.clear()
        links.remove(address)
        // The peer keeps its slot in the roster: it may still be advertising
        // nearby, and dropping it here would make it flicker out of the list.
        presence.onUnlinked(address, System.currentTimeMillis())
        listener.onLog("BLE disconnected $address")
        publishPeers()
    }

    private fun publishPeers() {
        val now = System.currentTimeMillis()
        val linked = links.keys
        val roster = presence.snapshot(now).map { peer ->
            if (peer.address in linked) peer.copy(rssi = links[peer.address]?.rssi ?: peer.rssi) else peer
        }
        listener.onPeersChanged(roster)
    }

    @SuppressLint("MissingPermission")
    private fun safeName(device: BluetoothDevice): String {
        val name = if (hasConnectPermission()) device.name else null
        return (if (name.isNullOrBlank()) "BLE peer" else name) + " (" + device.address + ")"
    }

    private fun hasConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasScanPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
        }
        return ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun hasAdvertisePermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_ADVERTISE) ==
            PackageManager.PERMISSION_GRANTED

    private class GattLink(val address: String) {
        var gatt: BluetoothGatt? = null
        var serverDevice: BluetoothDevice? = null
        var remoteCharacteristic: BluetoothGattCharacteristic? = null
        var displayDevice: BluetoothDevice? = null
        var mtu: Int = MeshGattProtocol.DEFAULT_ATT_MTU
        var notifyEnabled: Boolean = false
        var sending: Boolean = false
        var nodeId: String = ""
        var rssi: Int = 0
        var writeTimeout: Runnable = Runnable {}
        val queue: ArrayDeque<ByteArray> = ArrayDeque()
        val assembler = LengthPrefixedAssembler()

        fun chunkSize(): Int = (mtu - MeshGattProtocol.ATT_HEADER_BYTES).coerceAtLeast(20)

        /** Mesh node ID when the handshake has run, address otherwise. */
        fun label(): String =
            if (nodeId.isNotBlank()) "$nodeId ($address)" else "BLE peer ($address)"

        fun hasAnyRole(): Boolean = gatt != null || serverDevice != null
    }

    private companion object {
        const val DEFAULT_NODE_ID = "NODE"
        const val CONNECT_TIMEOUT_MS = 8_000L
        const val CONNECT_RETRY_INTERVAL_MS = 2_500L
        const val MAINTENANCE_INTERVAL_MS = 2_000L
        const val SCAN_CYCLE_INTERVAL_MS = 20_000L
        const val COLLISION_LOG_INTERVAL_MS = 30_000L

        /** How long to respect the tie-break before dialling anyway. */
        const val LINK_STALL_GRACE_MS = 3_500L

        /** In-flight GATT payload per link. One FILE packet is a few KB. */
        const val MAX_QUEUED_BYTES = 48 * 1024
        const val SEND_WINDOW_TIMEOUT_MS = 30_000L
        const val NOTIFY_CALLBACK_FALLBACK_MS = 16L
    }
}
