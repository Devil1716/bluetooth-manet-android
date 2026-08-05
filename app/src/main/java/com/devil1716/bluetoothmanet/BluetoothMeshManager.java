package com.devil1716.bluetoothmanet;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;

import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BluetoothMeshManager {
    public interface Listener {
        void onLog(String message);
        void onConnectionsChanged(List<String> peers);
        void onMessageDelivered(ManetMessage message);
        void onMessageStatusChanged(ManetMessage message, MessageStatus status);
        default void onMessageAcknowledged(String messageId) { }
        default void onFileProgress(String transferId, int completed, int total, String fileName) { }
    }

    private static final String SERVICE_NAME = "MANET";
    private static final UUID SERVICE_UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc");

    private final Context appContext;
    private final Listener listener;
    private final BluetoothAdapter adapter;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ConcurrentHashMap<String, BluetoothSocket> sockets = new ConcurrentHashMap<>();
    private final Set<String> connectingAddresses = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, String> peerNodeIds = new ConcurrentHashMap<>();
    private final Map<String, Long> seenMessages = new LinkedHashMap<>(256, .75f, true);
    private final Set<String> seenFileChunks = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, FileTransferBuffer> fileBuffers = new ConcurrentHashMap<>();
    private final AppDatabase database;
    private static final long SEEN_TTL_MS = 10 * 60 * 1000L;

    private volatile boolean accepting;
    private volatile BluetoothServerSocket serverSocket;
    private volatile String myNodeId = "NODE";

    public BluetoothMeshManager(Context context, Listener listener) {
        this.appContext = context.getApplicationContext();
        this.listener = listener;
        this.adapter = BluetoothAdapter.getDefaultAdapter();
        this.database = AppDatabase.getInstance(this.appContext);
    }

    public BluetoothAdapter getAdapter() {
        return adapter;
    }

    public boolean isBluetoothSupported() {
        return adapter != null;
    }

    public void setMyNodeId(String myNodeId) {
        this.myNodeId = myNodeId == null || myNodeId.trim().isEmpty() ? "NODE" : myNodeId.trim().toUpperCase();
        listener.onLog("Node ID set to " + this.myNodeId);
    }

    @SuppressLint("MissingPermission")
    public void startAccepting() {
        if (!hasConnectPermission() || adapter == null || accepting) {
            return;
        }

        accepting = true;
        executor.execute(() -> {
            try {
                try {
                    serverSocket = adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID);
                } catch (IOException secureFailure) {
                    listener.onLog("Secure RFCOMM listener failed; trying insecure listener: " + secureFailure.getMessage());
                    serverSocket = adapter.listenUsingInsecureRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID);
                }
                listener.onLog("Listening for MANET peers...");
                while (accepting) {
                    BluetoothSocket socket = serverSocket.accept();
                    if (socket != null) {
                        registerSocket(socket, "Accepted");
                    }
                }
            } catch (IOException e) {
                if (accepting) {
                    listener.onLog("Server stopped: " + e.getMessage());
                }
            } finally {
                accepting = false;
                closeServerSocket();
            }
        });
    }

    public void stop() {
        accepting = false;
        closeServerSocket();
        for (BluetoothSocket socket : sockets.values()) {
            closeSocket(socket);
        }
        sockets.clear();
        connectingAddresses.clear();
        peerNodeIds.clear();
        publishConnections();
        executor.shutdownNow();
    }

    @SuppressLint("MissingPermission")
    public void connectToDevice(BluetoothDevice device) {
        if (device == null || !hasConnectPermission() || !connectingAddresses.add(device.getAddress())) {
            return;
        }
        executor.execute(() -> {
            BluetoothSocket socket = null;
            try {
                if (adapter.isDiscovering() && hasScanPermission()) {
                    adapter.cancelDiscovery();
                }
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    listener.onLog("Requesting pairing with " + safeDeviceLabel(device) + "...");
                    if (device.createBond()) {
                        listener.onLog("Pairing requested. The peer must accept the Android pairing prompt.");
                    } else {
                        listener.onLog("Could not request pairing with " + safeDeviceLabel(device));
                    }
                    return;
                }
                listener.onLog("Connecting to " + safeDeviceLabel(device) + "...");
                try {
                    socket = device.createRfcommSocketToServiceRecord(SERVICE_UUID);
                    socket.connect();
                } catch (IOException secureFailure) {
                    closeSocket(socket);
                    listener.onLog("Secure RFCOMM connect failed; trying insecure RFCOMM...");
                    socket = device.createInsecureRfcommSocketToServiceRecord(SERVICE_UUID);
                    try {
                        socket.connect();
                    } catch (IOException insecureFailure) {
                        closeSocket(socket);
                        listener.onLog("Insecure RFCOMM connect failed; trying channel fallback...");
                        java.lang.reflect.Method method = BluetoothDevice.class.getMethod("createRfcommSocket", int.class);
                        socket = (BluetoothSocket) method.invoke(device, 1);
                        socket.connect();
                    }
                }
                registerSocket(socket, "Connected");
            } catch (Exception e) {
                listener.onLog("Connection failed for " + safeDeviceLabel(device) + ": " + e.getMessage());
                closeSocket(socket);
            } finally {
                connectingAddresses.remove(device.getAddress());
            }
        });
    }

    public boolean sendNewMessage(String destination, String body) {
        String trimmedBody = body == null ? "" : body.trim();
        String trimmedDestination = destination == null ? "" : destination.trim().toUpperCase();
        if (trimmedBody.isEmpty() || trimmedDestination.isEmpty()) {
            listener.onLog("Destination and message are required.");
            return false;
        }

        ManetMessage message = ManetMessage.outbound(myNodeId, trimmedDestination, trimmedBody, ManetMessage.DEFAULT_TTL);
        listener.onMessageStatusChanged(message, MessageStatus.SENDING);
        markSeen(message.getId());
        if (trimmedDestination.equals(myNodeId)) {
            listener.onMessageDelivered(message);
            return true;
        }
        if (sockets.isEmpty()) {
            listener.onLog("No active peers. Connect to a device before sending.");
            listener.onMessageStatusChanged(message, MessageStatus.FAILED);
            return false;
        }
        boolean sent = forwardMessage(message, null) > 0;
        if (!sent) storePending(message);
        listener.onMessageStatusChanged(message, sent ? MessageStatus.SENT : MessageStatus.FAILED);
        return sent;
    }

    private void registerSocket(BluetoothSocket socket, String label) {
        BluetoothDevice device = socket.getRemoteDevice();
        String address = device.getAddress();
        BluetoothSocket existing = sockets.put(address, socket);
        if (existing != null && existing != socket) {
            closeSocket(existing);
        }
        listener.onLog(label + " peer " + safeDeviceLabel(device));
        publishConnections();
        sendHello(socket);
        flushPending();
        startReaderLoop(socket);
    }

    private void startReaderLoop(BluetoothSocket socket) {
        executor.execute(() -> {
            BluetoothDevice device = socket.getRemoteDevice();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    handleIncoming(line, device.getAddress());
                }
            } catch (IOException e) {
                listener.onLog("Disconnected from " + safeDeviceLabel(device));
            } finally {
                sockets.remove(device.getAddress(), socket);
                closeSocket(socket);
                publishConnections();
            }
        });
    }

    private void handleIncoming(String payload, String fromAddress) {
        try {
            if (payload.startsWith("FILE|")) {
                handleFilePacket(FilePacket.fromWire(payload), fromAddress);
                return;
            }
            ManetMessage message = ManetMessage.fromWire(payload);
            if (!markSeen(message.getId())) {
                return;
            }

            listener.onLog("RX " + message.getSource() + " -> " + message.getDestination()
                    + " (ttl=" + message.getTtl() + ")");

            if (message.getType() == ManetMessage.Type.HELLO) {
                peerNodeIds.put(fromAddress, message.getData());
                listener.onLog("Peer " + fromAddress + " is node " + message.getData());
                return;
            }

            if (message.getType() == ManetMessage.Type.ACK) {
                if (message.getDestination().equalsIgnoreCase(myNodeId)) {
                    listener.onMessageAcknowledged(message.getData());
                    return;
                }
                if (message.getTtl() > 1) forwardMessage(message.decrementedTtl(), fromAddress);
                return;
            }

            boolean broadcast = "ALL".equalsIgnoreCase(message.getDestination())
                    || "*".equals(message.getDestination());
            if (message.getDestination().equalsIgnoreCase(myNodeId) || broadcast) {
                listener.onMessageDelivered(message);
                if (message.getDestination().equalsIgnoreCase(myNodeId)) {
                    forwardMessage(ManetMessage.ack(myNodeId, message.getSource(), message.getId()), fromAddress);
                    return;
                }
            }

            if (message.getTtl() <= 1) {
                listener.onLog("Dropped " + message.getId() + " because TTL expired.");
                return;
            }

            int forwarded = forwardMessage(message.decrementedTtl(), fromAddress);
            if (forwarded == 0 && !broadcast) storePending(message);
        } catch (IllegalArgumentException e) {
            listener.onLog("Ignored malformed payload: " + payload);
        }
    }

    public boolean isConnected(String address) {
        BluetoothSocket socket = sockets.get(address);
        return socket != null && socket.isConnected();
    }

    private boolean markSeen(String id) {
        synchronized (seenMessages) {
            long now = System.currentTimeMillis();
            seenMessages.entrySet().removeIf(entry -> now - entry.getValue() > SEEN_TTL_MS);
            if (seenMessages.containsKey(id)) return false;
            seenMessages.put(id, now);
            while (seenMessages.size() > 1000) seenMessages.remove(seenMessages.keySet().iterator().next());
            return true;
        }
    }

    private void storePending(ManetMessage message) {
        database.pendingMessageDao().insert(new PendingMessageEntity(message.getId(),
                message.getDestination(), message.toWire(), System.currentTimeMillis()));
        listener.onLog("Stored " + message.getId() + " for offline destination " + message.getDestination());
    }

    private void sendHello(BluetoothSocket socket) {
        try {
            socket.getOutputStream().write(ManetMessage.hello(myNodeId).toBytes());
            socket.getOutputStream().flush();
        } catch (IOException e) {
            listener.onLog("Could not send node handshake: " + e.getMessage());
        }
    }

    private void flushPending() {
        executor.execute(() -> {
            long cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L;
            database.pendingMessageDao().deleteExpired(cutoff);
            // A node ID handshake is not part of the legacy RFCOMM stream. Attempting
            // all pending packets lets the normal destination/TTL logic select the route.
            for (PendingMessageEntity pending : database.pendingMessageDao().all(cutoff)) {
                if (forwardMessage(ManetMessage.fromWire(pending.wire), null) > 0)
                    database.pendingMessageDao().delete(pending.id);
            }
        });
    }

    public boolean sendFile(String destination, String fileName, byte[] contents) {
        if (contents == null || contents.length == 0 || sockets.isEmpty()) return false;
        int total = (contents.length + FilePacket.CHUNK_SIZE - 1) / FilePacket.CHUNK_SIZE;
        String id = UUID.randomUUID().toString();
        for (int index = 0; index < total; index++) {
            int start = index * FilePacket.CHUNK_SIZE;
            int end = Math.min(contents.length, start + FilePacket.CHUNK_SIZE);
            String data = android.util.Base64.encodeToString(java.util.Arrays.copyOfRange(contents, start, end), android.util.Base64.NO_WRAP);
            FilePacket packet = new FilePacket(id, myNodeId, destination.toUpperCase(), ManetMessage.DEFAULT_TTL,
                    fileName.replace("|", "_"), index, total, data);
            forwardBytes(packet.toBytes(), null);
            listener.onFileProgress(id, index + 1, total, fileName);
        }
        return true;
    }

    private int forwardBytes(byte[] bytes, String exceptAddress) {
        int count = 0;
        for (Map.Entry<String, BluetoothSocket> entry : sockets.entrySet()) {
            if (entry.getKey().equals(exceptAddress) || !entry.getValue().isConnected()) continue;
            try { entry.getValue().getOutputStream().write(bytes); entry.getValue().getOutputStream().flush(); count++; }
            catch (IOException ignored) { }
        }
        return count;
    }

    private void handleFilePacket(FilePacket packet, String fromAddress) {
        if (!seenFileChunks.add(packet.id + ":" + packet.index)) return;
        FileTransferBuffer buffer = fileBuffers.computeIfAbsent(packet.id, id -> new FileTransferBuffer(packet));
        buffer.chunks.put(packet.index, android.util.Base64.decode(packet.data, android.util.Base64.DEFAULT));
        listener.onFileProgress(packet.id, buffer.chunks.size(), packet.total, packet.fileName);
        if (buffer.chunks.size() == packet.total && packet.destination.equalsIgnoreCase(myNodeId)) {
            try {
                java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
                for (int i = 0; i < packet.total; i++) output.write(buffer.chunks.get(i));
                java.io.File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!downloads.exists()) downloads.mkdirs();
                java.io.FileOutputStream fileOutput = new java.io.FileOutputStream(new java.io.File(downloads, packet.fileName));
                fileOutput.write(output.toByteArray());
                fileOutput.close();
                fileBuffers.remove(packet.id);
            } catch (IOException e) { listener.onLog("File save failed: " + e.getMessage()); }
        } else if (!packet.destination.equalsIgnoreCase(myNodeId) && packet.ttl > 1) {
            forwardBytes(packet.toWire().replace("|" + packet.ttl + "|", "|" + (packet.ttl - 1) + "|").getBytes(StandardCharsets.UTF_8), fromAddress);
        }
    }

    private static class FileTransferBuffer {
        final Map<Integer, byte[]> chunks = new ConcurrentHashMap<>();
        FileTransferBuffer(FilePacket ignored) { }
    }

    private int forwardMessage(ManetMessage message, String exceptAddress) {
        int forwarded = 0;
        for (String address : sockets.keySet()) {
            if (address.equals(exceptAddress)) {
                continue;
            }

            BluetoothSocket socket = sockets.get(address);
            if (socket == null || !socket.isConnected()) {
                continue;
            }

            try {
                OutputStream outputStream = socket.getOutputStream();
                outputStream.write(message.toBytes());
                outputStream.flush();
                forwarded++;
            } catch (IOException e) {
                listener.onLog("Failed to forward via " + address + ": " + e.getMessage());
            }
        }

        listener.onLog("Forwarded " + message.getId() + " to " + forwarded + " peer(s).");
        return forwarded;
    }

    private void publishConnections() {
        List<String> peers = new ArrayList<>();
        for (BluetoothSocket socket : sockets.values()) {
            peers.add(safeDeviceLabel(socket.getRemoteDevice()));
        }
        listener.onConnectionsChanged(peers);
    }

    @SuppressLint("MissingPermission")
    private String safeDeviceLabel(BluetoothDevice device) {
        String name = hasConnectPermission() ? device.getName() : null;
        return (name == null || name.trim().isEmpty() ? "Unknown" : name) + " (" + device.getAddress() + ")";
    }

    private void closeServerSocket() {
        BluetoothServerSocket socket = serverSocket;
        serverSocket = null;
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void closeSocket(BluetoothSocket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private boolean hasConnectPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasScanPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_SCAN)
                == PackageManager.PERMISSION_GRANTED;
    }
}
