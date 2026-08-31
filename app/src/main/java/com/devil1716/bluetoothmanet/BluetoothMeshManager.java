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
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.ContextCompat;

import com.devil1716.bluetoothmanet.crypto.MeshIntegrity;
import com.devil1716.bluetoothmanet.crypto.PacketSigner;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
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
        default void onFileReceived(String fileName, String path) { }
    }

    private static final String SERVICE_NAME = "MANET";
    private static final UUID SERVICE_UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc");

    private final Context appContext;
    private final Listener listener;
    private final BluetoothAdapter adapter;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ConcurrentHashMap<String, BluetoothSocket> sockets = new ConcurrentHashMap<>();
    private final Set<String> connectingAddresses = java.util.Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final ConcurrentHashMap<String, String> peerNodeIds = new ConcurrentHashMap<>();
    private final Map<String, Long> seenMessages = new LinkedHashMap<>(256, .75f, true);
    private final Set<String> seenFileChunks = java.util.Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final ConcurrentHashMap<String, FileTransferBuffer> fileBuffers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> helloSeen = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> peerPublicKeys = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, OutgoingFile> outgoingFiles = new ConcurrentHashMap<>();
    private final AppDatabase database;
    private final PacketSigner signer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MeshLinkBridge extraLinks;
    private static final long SEEN_TTL_MS = 10 * 60 * 1000L;

    private volatile boolean accepting;
    private volatile BluetoothServerSocket serverSocket;
    private volatile String myNodeId = "NODE";

    public BluetoothMeshManager(Context context, Listener listener) {
        this.appContext = context.getApplicationContext();
        this.listener = listener;
        this.adapter = BluetoothAdapter.getDefaultAdapter();
        this.database = AppDatabase.getInstance(this.appContext);
        this.signer = new PacketSigner(new File(this.appContext.getFilesDir(), "identity"));
    }

    public void setExtraLinks(MeshLinkBridge extraLinks) {
        this.extraLinks = extraLinks;
    }

    public void notifyLinksChanged() {
        publishConnections();
        flushPending();
    }

    public void ingestPayload(String payload, String fromPeerId) {
        if (payload == null || payload.trim().isEmpty()) return;
        for (String line : payload.split("\n")) {
            if (!line.trim().isEmpty()) handleIncoming(line.trim(), fromPeerId);
        }
    }

    public void broadcastHello() {
        if (!hasAnyPeers()) return;
        forwardMessage(signedHello(), null);
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
        if (adapter == null) {
            listener.onLog("Cannot listen: Bluetooth adapter missing.");
            return;
        }
        if (!hasConnectPermission()) {
            listener.onLog("Cannot listen: BLUETOOTH_CONNECT permission is missing.");
            return;
        }
        if (accepting) {
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
                listener.onLog("Connecting to " + safeDeviceLabel(device) + " over RFCOMM...");
                try {
                    socket = device.createInsecureRfcommSocketToServiceRecord(SERVICE_UUID);
                    socket.connect();
                } catch (IOException insecureFailure) {
                    closeSocket(socket);
                    listener.onLog("Insecure RFCOMM connect failed; trying secure RFCOMM...");
                    socket = device.createRfcommSocketToServiceRecord(SERVICE_UUID);
                    try {
                        socket.connect();
                    } catch (IOException secureFailure) {
                        closeSocket(socket);
                        listener.onLog("Secure RFCOMM connect failed; trying channel fallback...");
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

        ManetMessage message = sign(ManetMessage.outbound(myNodeId, trimmedDestination, trimmedBody, ManetMessage.DEFAULT_TTL));
        listener.onMessageStatusChanged(message, MessageStatus.SENDING);
        markSeen(message.getId());
        if (trimmedDestination.equals(myNodeId)) {
            listener.onMessageDelivered(message);
            return true;
        }
        if (!hasAnyPeers()) {
            listener.onLog("No active mesh peers. Stay on this screen so BLE can find nearby phones, or tap a discovered peer.");
            storePending(message);
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
            if (payload.startsWith("FILE")) {
                handleFilePacket(FilePacket.fromWire(payload), fromAddress);
                return;
            }
            ManetMessage message = ManetMessage.fromWire(payload);
            if (message.getType() == ManetMessage.Type.HELLO) {
                handleHello(message, fromAddress);
                return;
            }
            if (!markSeen(message.getId())) {
                return;
            }
            AuthResult auth = authenticate(message);
            if (auth == AuthResult.TAMPERED) {
                listener.onLog("Rejected tampered " + message.getType() + " " + message.getId()
                        + " from " + message.getSource());
                return;
            }

            listener.onLog("RX " + message.getSource() + " -> " + message.getDestination()
                    + " (ttl=" + message.getTtl() + ")");

            if (message.getType() == ManetMessage.Type.ACK) {
                if (message.getDestination().equalsIgnoreCase(myNodeId) && auth == AuthResult.OK) {
                    listener.onMessageAcknowledged(message.getData());
                    return;
                }
                if (message.getTtl() > 1) forwardMessage(message.decrementedTtl(), fromAddress);
                return;
            }

            boolean broadcast = "ALL".equalsIgnoreCase(message.getDestination())
                    || "*".equals(message.getDestination());
            if ((message.getDestination().equalsIgnoreCase(myNodeId) || broadcast) && auth == AuthResult.OK) {
                listener.onMessageDelivered(message);
                if (message.getDestination().equalsIgnoreCase(myNodeId)) {
                    forwardMessage(sign(ManetMessage.ack(myNodeId, message.getSource(), message.getId())), fromAddress);
                    return;
                }
            } else if (message.getDestination().equalsIgnoreCase(myNodeId) && auth == AuthResult.UNKNOWN) {
                listener.onLog("Ignored message from " + message.getSource() + " until its signing key is known.");
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
            java.util.Iterator<Map.Entry<String, Long>> iterator = seenMessages.entrySet().iterator();
            while (iterator.hasNext()) {
                if (now - iterator.next().getValue() > SEEN_TTL_MS) iterator.remove();
            }
            if (seenMessages.containsKey(id)) return false;
            seenMessages.put(id, now);
            while (seenMessages.size() > 1000) seenMessages.remove(seenMessages.keySet().iterator().next());
            return true;
        }
    }

    private void storePending(ManetMessage message) {
        executor.execute(() -> {
            database.pendingMessageDao().insert(new PendingMessageEntity(message.getId(),
                    message.getDestination(), message.toWire(), System.currentTimeMillis()));
            listener.onLog("Stored " + message.getId() + " for offline destination " + message.getDestination());
        });
    }

    private void sendHello(BluetoothSocket socket) {
        try {
            socket.getOutputStream().write(signedHello().toBytes());
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
        String trimmedDestination = destination == null ? "" : destination.trim().toUpperCase();
        if (contents == null || contents.length == 0) {
            listener.onLog("File is empty.");
            return false;
        }
        if (trimmedDestination.isEmpty()) {
            listener.onLog("Destination is required to send a file.");
            return false;
        }
        if (contents.length > 2 * 1024 * 1024) {
            listener.onLog("File is larger than 2 MB. Choose a smaller file.");
            return false;
        }
        if (!hasAnyPeers()) {
            listener.onLog("No active mesh peers. Files are sent over BLE or RFCOMM once a phone is linked.");
            return false;
        }
        String safeName = fileName == null ? "file.bin" : fileName.replace("|", "_").replaceAll("[^a-zA-Z0-9._-]", "_");
        int total = (contents.length + FilePacket.CHUNK_SIZE - 1) / FilePacket.CHUNK_SIZE;
        String id = UUID.randomUUID().toString();
        String fileSha = MeshIntegrity.sha256Hex(contents);
        FilePacket meta = FilePacket.meta(id, myNodeId, trimmedDestination, ManetMessage.DEFAULT_TTL,
                safeName, total, contents.length, fileSha,
                signer.sign(MeshIntegrity.fileMetaCanon(id, myNodeId, trimmedDestination, safeName, total, contents.length, fileSha)));
        OutgoingFile outgoing = new OutgoingFile(meta);
        outgoingFiles.put(id, outgoing);
        forwardMessage(signedHello(), null);
        listener.onLog("Sending signed file " + safeName + " (" + contents.length + " bytes, " + total + " chunks).");
        int delivered = 0;
        int chunksDelivered = 0;
        if (forwardBytes(meta.toBytes(), null) > 0) delivered++;
        try { Thread.sleep(40); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        if (forwardBytes(meta.toBytes(), null) > 0) delivered++;
        for (int index = 0; index < total; index++) {
            int start = index * FilePacket.CHUNK_SIZE;
            int end = Math.min(contents.length, start + FilePacket.CHUNK_SIZE);
            byte[] chunk = java.util.Arrays.copyOfRange(contents, start, end);
            String chunkSha = MeshIntegrity.sha256Hex(chunk);
            String data = android.util.Base64.encodeToString(chunk, android.util.Base64.NO_WRAP);
            FilePacket packet = new FilePacket(FilePacket.Kind.CHUNK, id, myNodeId, trimmedDestination,
                    ManetMessage.DEFAULT_TTL, safeName, index, total, contents.length, chunkSha, data,
                    signer.sign(MeshIntegrity.fileChunkCanon(id, myNodeId, trimmedDestination, index, total, chunkSha)));
            outgoing.chunks.add(packet);
            if (forwardBytes(packet.toBytes(), null) > 0) {
                delivered++;
                chunksDelivered++;
            }
            listener.onFileProgress(id, index + 1, total, safeName);
            if (index < total - 1) {
                try { Thread.sleep(35); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            }
        }
        boolean sent = chunksDelivered > 0;
        listener.onLog(sent
                ? "File " + safeName + " queued on the mesh with integrity checks."
                : "File " + safeName + " could not be written to any peer.");
        if (sent) {
            listener.onMessageDelivered(new ManetMessage(id, myNodeId, trimmedDestination,
                    ManetMessage.DEFAULT_TTL, "Sent file: " + safeName));
        }
        return sent;
    }

    private int forwardBytes(byte[] bytes, String exceptAddress) {
        int count = 0;
        for (Map.Entry<String, BluetoothSocket> entry : sockets.entrySet()) {
            if (entry.getKey().equals(exceptAddress) || !entry.getValue().isConnected()) continue;
            try { entry.getValue().getOutputStream().write(bytes); entry.getValue().getOutputStream().flush(); count++; }
            catch (IOException ignored) { }
        }
        if (extraLinks != null) count += extraLinks.send(bytes, exceptAddress);
        return count;
    }

    private void handleHello(ManetMessage message, String fromAddress) {
        String data = message.getData() == null ? message.getSource() : message.getData();
        String node = data;
        String publicKey = null;
        int separator = data.indexOf("::");
        if (separator >= 0) {
            node = data.substring(0, separator);
            publicKey = data.substring(separator + 2);
        }
        if (publicKey != null) {
            if (!message.hasSignature()
                    || !signer.verify(message.canonical(), message.getSignature(), publicKey)) {
                listener.onLog("Rejected tampered HELLO from " + fromAddress);
                return;
            }
            peerPublicKeys.put(node.toUpperCase(), publicKey);
            for (FileTransferBuffer pending : fileBuffers.values()) {
                if (!pending.metaOk && pending.pendingMeta != null
                        && node.equalsIgnoreCase(pending.pendingMeta.source)) {
                    handleFileMeta(pending.pendingMeta, fromAddress);
                }
            }
            requestMissingChunksForSource(node);
        }
        peerNodeIds.put(fromAddress, node);
        listener.onLog("Peer " + fromAddress + " is node " + node + (publicKey != null ? " (key verified)" : ""));
        final String storedNode = node;
        executor.execute(() -> database.meshStateDao().upsertNeighbor(new MeshNeighborEntity(
                fromAddress, storedNode, 0, null, null, 1, System.currentTimeMillis(), true)));
        long now = System.currentTimeMillis();
        Long previous = helloSeen.put(node, now);
        if (previous != null && now - previous < 8_000L) return;
        if (message.getTtl() > 1) forwardMessage(message.decrementedTtl(), fromAddress);
    }

    private void handleFilePacket(FilePacket packet, String fromAddress) {
        if (packet.kind == FilePacket.Kind.REQ) {
            handleFileRequest(packet, fromAddress);
            return;
        }
        if (packet.kind == FilePacket.Kind.META) {
            handleFileMeta(packet, fromAddress);
            return;
        }
        String chunkKey = packet.id + ":" + packet.index;
        if (seenFileChunks.contains(chunkKey)) {
            maybeRelayFile(packet, fromAddress);
            return;
        }
        if (packet.digest.isEmpty() || packet.signature.isEmpty()) {
            listener.onLog("Dropped unsigned file chunk for " + packet.fileName);
            return;
        }
        if (peerPublicKeys.get(packet.source.toUpperCase()) == null) {
            listener.onLog("Waiting for " + packet.source + " signing key before accepting chunk "
                    + packet.index + " of " + packet.fileName);
            maybeRelayFile(packet, fromAddress);
            return;
        }
        byte[] raw = android.util.Base64.decode(packet.data, android.util.Base64.DEFAULT);
        if (!MeshIntegrity.sha256Hex(raw).equalsIgnoreCase(packet.digest)) {
            listener.onLog("Dropped tampered file chunk " + packet.index + " of " + packet.fileName);
            seenFileChunks.add(chunkKey);
            return;
        }
        if (!verifyFileChunk(packet)) {
            listener.onLog("Dropped file chunk with invalid signature: " + packet.fileName);
            seenFileChunks.add(chunkKey);
            return;
        }
        if (!seenFileChunks.add(chunkKey)) {
            maybeRelayFile(packet, fromAddress);
            return;
        }
        FileTransferBuffer buffer = bufferFor(packet);
        buffer.chunks.put(packet.index, raw);
        buffer.lastUpdate = System.currentTimeMillis();
        listener.onFileProgress(packet.id, buffer.chunks.size(), Math.max(packet.total, buffer.total), packet.fileName);
        scheduleFileRetry(packet.id);
        tryCompleteFile(buffer, packet);
        maybeRelayFile(packet, fromAddress);
    }

    private void handleFileMeta(FilePacket packet, String fromAddress) {
        FileTransferBuffer buffer = bufferFor(packet);
        if (peerPublicKeys.get(packet.source.toUpperCase()) == null) {
            buffer.pendingMeta = packet;
            listener.onLog("Waiting for " + packet.source + " signing key before accepting file " + packet.fileName);
            maybeRelayFile(packet, fromAddress);
            return;
        }
        if (!verifyFileMeta(packet)) {
            listener.onLog("Rejected tampered file header for " + packet.fileName);
            return;
        }
        buffer.pendingMeta = null;
        buffer.fileName = packet.fileName;
        buffer.source = packet.source;
        buffer.destination = packet.destination;
        buffer.fileSha = packet.digest;
        buffer.total = packet.total;
        buffer.size = packet.size;
        buffer.metaOk = true;
        listener.onLog("Authenticated file header " + packet.fileName + " sha256=" + packet.digest.substring(0, Math.min(12, packet.digest.length())));
        scheduleFileRetry(packet.id);
        tryCompleteFile(buffer, packet);
        maybeRelayFile(packet, fromAddress);
    }

    private void handleFileRequest(FilePacket packet, String fromAddress) {
        OutgoingFile outgoing = outgoingFiles.get(packet.id);
        if (outgoing == null) {
            if (packet.ttl > 1) forwardBytes(packet.decrementedTtl().toBytes(), fromAddress);
            return;
        }
        listener.onLog("Resending missing chunks for " + outgoing.meta.fileName + ": " + packet.requestIndexes);
        forwardBytes(outgoing.meta.toBytes(), null);
        for (String item : packet.requestIndexes.split(",")) {
            if (item.trim().isEmpty()) continue;
            int index = Integer.parseInt(item.trim());
            if (index >= 0 && index < outgoing.chunks.size()) {
                forwardBytes(outgoing.chunks.get(index).toBytes(), null);
            }
        }
    }

    private FileTransferBuffer bufferFor(FilePacket packet) {
        FileTransferBuffer buffer = fileBuffers.get(packet.id);
        if (buffer != null) return buffer;
        FileTransferBuffer created = new FileTransferBuffer();
        FileTransferBuffer existing = fileBuffers.putIfAbsent(packet.id, created);
        return existing == null ? created : existing;
    }

    private void tryCompleteFile(FileTransferBuffer buffer, FilePacket packet) {
        if (!packet.isFor(myNodeId) || !buffer.metaOk || buffer.total <= 0 || buffer.chunks.size() != buffer.total) {
            return;
        }
        try {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            for (int i = 0; i < buffer.total; i++) {
                byte[] chunk = buffer.chunks.get(i);
                if (chunk == null) return;
                output.write(chunk);
            }
            byte[] assembled = output.toByteArray();
            String actual = MeshIntegrity.sha256Hex(assembled);
            if (!actual.equalsIgnoreCase(buffer.fileSha)) {
                listener.onLog("Rejected file " + buffer.fileName + ": content hash mismatch (tampered or corrupt).");
                fileBuffers.remove(packet.id);
                return;
            }
            java.io.File saved = MeshFileStore.save(appContext, buffer.fileName, assembled);
            fileBuffers.remove(packet.id);
            listener.onLog("Saved verified file to " + saved.getAbsolutePath());
            listener.onFileReceived(buffer.fileName, saved.getAbsolutePath());
            listener.onMessageDelivered(new ManetMessage(packet.id, buffer.source, myNodeId,
                    packet.ttl, MeshFileStore.chatLabel(buffer.fileName, saved.getAbsolutePath())));
        } catch (IOException e) {
            listener.onLog("File save failed: " + e.getMessage());
        }
    }

    private void scheduleFileRetry(String transferId) {
        FileTransferBuffer buffer = fileBuffers.get(transferId);
        if (buffer == null) return;
        if (buffer.retry != null) handler.removeCallbacks(buffer.retry);
        buffer.retry = () -> requestMissingChunks(transferId);
        handler.postDelayed(buffer.retry, 2500);
    }

    private void requestMissingChunksForSource(String sourceNode) {
        for (Map.Entry<String, FileTransferBuffer> entry : fileBuffers.entrySet()) {
            FileTransferBuffer buffer = entry.getValue();
            if (buffer.metaOk && sourceNode.equalsIgnoreCase(buffer.source)) {
                requestMissingChunks(entry.getKey());
            }
        }
    }

    private void requestMissingChunks(String transferId) {
        FileTransferBuffer buffer = fileBuffers.get(transferId);
        if (buffer == null || !buffer.metaOk || buffer.total <= 0) return;
        if (!buffer.destination.equalsIgnoreCase(myNodeId) && !"ALL".equalsIgnoreCase(buffer.destination)) return;
        StringBuilder missing = new StringBuilder();
        for (int i = 0; i < buffer.total; i++) {
            if (!buffer.chunks.containsKey(i)) {
                if (missing.length() > 0) missing.append(',');
                missing.append(i);
            }
        }
        if (missing.length() == 0) return;
        FilePacket request = FilePacket.request(transferId, myNodeId, buffer.source, ManetMessage.DEFAULT_TTL, missing.toString());
        listener.onLog("Requesting missing file chunks: " + missing);
        forwardBytes(request.toBytes(), null);
        handler.postDelayed(() -> requestMissingChunks(transferId), 4000);
    }

    private void maybeRelayFile(FilePacket packet, String fromAddress) {
        if (!packet.destination.equalsIgnoreCase(myNodeId) && packet.ttl > 1) {
            forwardBytes(packet.decrementedTtl().toBytes(), fromAddress);
        }
    }

    private boolean verifyFileMeta(FilePacket packet) {
        String pub = peerPublicKeys.get(packet.source.toUpperCase());
        if (pub == null || packet.signature.isEmpty()) return false;
        return signer.verify(MeshIntegrity.fileMetaCanon(packet.id, packet.source, packet.destination,
                packet.fileName, packet.total, packet.size, packet.digest), packet.signature, pub);
    }

    private boolean verifyFileChunk(FilePacket packet) {
        String pub = peerPublicKeys.get(packet.source.toUpperCase());
        if (pub == null || packet.signature.isEmpty()) return false;
        return signer.verify(MeshIntegrity.fileChunkCanon(packet.id, packet.source, packet.destination,
                packet.index, packet.total, packet.digest), packet.signature, pub);
    }

    private ManetMessage signedHello() {
        ManetMessage hello = ManetMessage.hello(myNodeId, signer.publicKeyHex(), "");
        return hello.withSignature(signer.sign(hello.canonical()));
    }

    private ManetMessage sign(ManetMessage message) {
        return message.withSignature(signer.sign(message.canonical()));
    }

    private AuthResult authenticate(ManetMessage message) {
        if (!message.hasSignature()) return AuthResult.TAMPERED;
        String pub = peerPublicKeys.get(message.getSource().toUpperCase());
        if (pub == null) return AuthResult.UNKNOWN;
        return signer.verify(message.canonical(), message.getSignature(), pub) ? AuthResult.OK : AuthResult.TAMPERED;
    }

    private enum AuthResult { OK, UNKNOWN, TAMPERED }

    private static class FileTransferBuffer {
        final Map<Integer, byte[]> chunks = new ConcurrentHashMap<>();
        String fileName = "";
        String source = "";
        String destination = "";
        String fileSha = "";
        int total;
        int size;
        boolean metaOk;
        long lastUpdate;
        FilePacket pendingMeta;
        Runnable retry;
    }

    private static class OutgoingFile {
        final FilePacket meta;
        final List<FilePacket> chunks = new ArrayList<>();
        OutgoingFile(FilePacket meta) { this.meta = meta; }
    }

    private int forwardMessage(ManetMessage message, String exceptAddress) {
        int forwarded = forwardBytes(message.toBytes(), exceptAddress);
        if (message.getType() != ManetMessage.Type.HELLO) {
            listener.onLog("Forwarded " + message.getId() + " to " + forwarded + " peer(s).");
        }
        return forwarded;
    }

    private boolean hasAnyPeers() {
        if (!sockets.isEmpty()) return true;
        return extraLinks != null && extraLinks.hasPeers();
    }

    private void publishConnections() {
        List<String> peers = new ArrayList<>();
        for (BluetoothSocket socket : sockets.values()) {
            peers.add(safeDeviceLabel(socket.getRemoteDevice()));
        }
        if (extraLinks != null) peers.addAll(extraLinks.peerLabels());
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
