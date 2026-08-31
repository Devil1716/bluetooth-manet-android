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
        fun onPeersChanged(labels: List<String>)
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

    private var nodeId = "NODE"
    private var running = false
    private var gattServer: BluetoothGattServer? = null
    private var packetCharacteristic: BluetoothGattCharacteristic? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertiseCallback: AdvertiseCallback? = null

    fun start(nodeId: String) {
        this.nodeId = if (nodeId.isBlank()) "NODE" else nodeId.trim().uppercase()
        handler.post { startLocked() }
    }

    fun stop() {
        handler.post { stopLocked() }
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
            handler.post { enqueue(link, bytes) }
            sent++
        }
        return sent
    }

    fun hasPeers(): Boolean = links.isNotEmpty()

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
        if (openServer()) {
            listener.onLog("BLE GATT server registered. Advertising starts after the service is added.")
            handler.postDelayed({
                if (running && advertiseCallback == null) {
                    startAdvertising()
                    startScanning()
                }
            }, 1500)
        } else {
            listener.onLog("BLE GATT server could not start; scanning as central only.")
            startAdvertising()
            startScanning()
        }
        running = true
        listener.onLog("BitChat-style BLE mesh started (central + peripheral). Pairing is not required.")
    }

    @SuppressLint("MissingPermission")
    private fun stopLocked() {
        running = false
        runCatching {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        }
        advertiseCallback?.let { callback ->
            runCatching { advertiser?.stopAdvertising(callback) }
        }
        advertiseCallback = null
        links.values.forEach { link ->
            runCatching { link.gatt?.close() }
        }
        links.clear()
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
    private fun startAdvertising() {
        if (!hasAdvertisePermission()) return
        val leAdvertiser = adapter?.bluetoothLeAdvertiser ?: return
        advertiser = leAdvertiser
        advertiseCallback?.let { runCatching { leAdvertiser.stopAdvertising(it) } }
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
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
            }
        }
        advertiseCallback = callback
        leAdvertiser.startAdvertising(settings, advertiseData, scanResponse, callback)
    }

    private fun restartAdvertising() {
        if (running && hasAdvertisePermission()) startAdvertising()
    }

    @SuppressLint("MissingPermission")
    private fun startScanning() {
        if (!hasScanPermission()) return
        val scanner = adapter?.bluetoothLeScanner ?: return
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(MeshGattProtocol.SERVICE_UUID)).build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(listOf(filter), settings, scanCallback)
        listener.onLog("BLE scanning for nearby mesh phones...")
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handler.post { onScan(result) }
        }

        override fun onScanFailed(errorCode: Int) {
            listener.onLog("BLE scan failed: $errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    private fun onScan(result: ScanResult) {
        if (!running) return
        val device = result.device ?: return
        val advertisedId = advertisedNodeId(result)
        if (advertisedId != null && advertisedId.equals(nodeId, ignoreCase = true)) return
        if (links.containsKey(device.address)) return
        if (links.size >= MeshGattProtocol.MAX_LINKS) return
        val now = System.currentTimeMillis()
        val previous = lastAttempt[device.address] ?: 0L
        if (now - previous < 8_000L) return
        if (advertisedId != null && nodeId < advertisedId) {
            return
        }
        connectAsClient(device, forced = false)
    }

    private fun advertisedNodeId(result: ScanResult): String? {
        val data = result.scanRecord?.getManufacturerSpecificData(MeshGattProtocol.MANUFACTURER_ID) ?: return null
        return runCatching { String(data, StandardCharsets.UTF_8).trim() }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    @SuppressLint("MissingPermission")
    private fun connectAsClient(device: BluetoothDevice, forced: Boolean) {
        if (!hasConnectPermission() || adapter == null) return
        if (links.containsKey(device.address) || clientCallbacks.containsKey(device.address)) {
            if (forced) listener.onLog("Already linking to " + device.address)
            return
        }
        if (!forced && links.size >= MeshGattProtocol.MAX_LINKS) return
        lastAttempt[device.address] = System.currentTimeMillis()
        listener.onLog("BLE connecting to " + safeName(device))
        val callback = ClientCallback(device.address)
        clientCallbacks[device.address] = callback
        val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(appContext, false, callback)
        }
        if (gatt == null) {
            clientCallbacks.remove(device.address)
            listener.onLog("BLE connectGatt returned null for " + device.address)
        }
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
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    val link = links.getOrPut(device.address) { GattLink(device.address) }
                    link.serverDevice = device
                    link.displayDevice = device
                    listener.onLog("BLE peripheral linked " + safeName(device))
                    publishPeers()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    links[device.address]?.serverDevice = null
                    dropIfIdle(device.address)
                }
            }
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            handler.post {
                links[device.address]?.mtu = mtu
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
                if (descriptor.uuid == MeshGattProtocol.CCCD_UUID) {
                    val link = links.getOrPut(device.address) { GattLink(device.address) }
                    link.serverDevice = device
                    link.displayDevice = device
                    link.notifyEnabled = true
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
            handler.post { ingest(device.address, device, value) }
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            // Notifications are paced on the GATT worker.
        }
    }

    private inner class ClientCallback(private val address: String) : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            handler.post {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    val link = links.getOrPut(address) { GattLink(address) }
                    link.gatt = gatt
                    link.displayDevice = gatt.device
                    listener.onLog("BLE central linked " + safeName(gatt.device))
                    publishPeers()
                    runCatching { gatt.requestMtu(MeshGattProtocol.REQUEST_MTU) }
                } else {
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
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            // Writes are paced on the GATT worker; the callback is only used for logging.
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
        val service = gatt.getService(MeshGattProtocol.SERVICE_UUID)
        val characteristic = service?.getCharacteristic(MeshGattProtocol.PACKET_UUID)
        if (characteristic == null) {
            listener.onLog("Peer " + gatt.device.address + " is not running the MANET GATT service.")
            return
        }
        val link = links.getOrPut(gatt.device.address) { GattLink(gatt.device.address) }
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
        val encoded = LengthPrefixedCodec.encode(payload)
        val pieces = LengthPrefixedCodec.chunks(encoded, link.chunkSize())
        link.queue.addAll(pieces)
        if (!link.sending) drain(link)
    }

    @SuppressLint("MissingPermission")
    private fun drain(link: GattLink) {
        if (link.sending) return
        val chunk = link.queue.poll() ?: return
        link.sending = true
        val written = when {
            link.gatt != null && link.remoteCharacteristic != null -> writeClient(link, chunk)
            link.serverDevice != null && packetCharacteristic != null -> notifyServer(link, chunk)
            else -> false
        }
        if (!written) {
            link.sending = false
            return
        }
        handler.postDelayed({
            link.sending = false
            drain(link)
        }, 25)
    }

    @SuppressLint("MissingPermission")
    private fun writeClient(link: GattLink, chunk: ByteArray): Boolean {
        val gatt = link.gatt ?: return false
        val characteristic = link.remoteCharacteristic ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
        links.remove(address)
        listener.onLog("BLE disconnected $address")
        publishPeers()
    }

    private fun publishPeers() {
        listener.onPeersChanged(peerLabels())
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
        val queue: ArrayDeque<ByteArray> = ArrayDeque()
        val assembler = LengthPrefixedAssembler()

        fun chunkSize(): Int = (mtu - MeshGattProtocol.ATT_HEADER_BYTES).coerceAtLeast(20)

        fun label(): String {
            val name = displayDevice?.name ?: serverDevice?.name ?: gatt?.device?.name
            return (if (name.isNullOrBlank()) "BLE peer" else name) + " (" + address + ")"
        }

        fun hasAnyRole(): Boolean = gatt != null || serverDevice != null
    }
}
