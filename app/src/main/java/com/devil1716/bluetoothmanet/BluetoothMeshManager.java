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
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

public class BluetoothMeshManager {
    public interface Listener {
        void onLog(String message);
        void onConnectionsChanged(List<String> peers);
        void onMessageDelivered(ManetMessage message);
        void onMessageStatusChanged(ManetMessage message, MessageStatus status);
        default void onMessageAcknowledged(String messageId) { }
        default void onPeerIdentityConflict(String nodeId, String fingerprint) { }
        default void onFileProgress(String transferId, int completed, int total, String fileName) { }
        default void onFileReceived(String fileName, String path) { }
    }

    private static final String SERVICE_NAME = "MANET";
    private static final UUID SERVICE_UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc");

    private final Context appContext;
    private final Listener listener;
    private final BluetoothAdapter adapter;
    private final ExecutorService executor = new java.util.concurrent.ThreadPoolExecutor(
            2, 8, 30L, java.util.concurrent.TimeUnit.SECONDS,
            new java.util.concurrent.LinkedBlockingQueue<Runnable>(64));
    private final ConcurrentHashMap<String, BluetoothSocket> sockets = new ConcurrentHashMap<>();
    private final Set<String> connectingAddresses = java.util.Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final ConcurrentHashMap<String, String> peerNodeIds = new ConcurrentHashMap<>();
    private final Map<String, Long> seenMessages = new LinkedHashMap<>(256, .75f, true);
    private final Set<String> seenFileChunks = java.util.Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final ConcurrentHashMap<String, FileTransferBuffer> fileBuffers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> helloSeen = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> peerPublicKeys = new ConcurrentHashMap<>();
    private final PeerIdentityStore identityStore;
    private final ConcurrentHashMap<String, OutgoingFile> outgoingFiles = new ConcurrentHashMap<>();
    private final AppDatabase database;
    private final PacketSigner signer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ConcurrentHashMap<String, Integer> pendingAttempts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> pendingRetryAt = new ConcurrentHashMap<>();
    private final java.util.Random retryJitter = new java.util.Random();
    private MeshLinkBridge extraLinks;
    private static final long SEEN_TTL_MS = 10 * 60 * 1000L;
    private static final long PENDING_FLUSH_MIN_INTERVAL_MS = 2_000L;
    private static final long FILE_BUFFER_TTL_MS = 10 * 60 * 1000L;
    private static final int MAX_SEEN_FILE_CHUNKS = 4_096;
    private static final int MAX_OUTGOING_FILES = 3;
    private static final int MAX_MISSING_INDEXES = 48;
    private volatile long lastPendingFlushAt;

    private volatile boolean accepting;
    private volatile boolean stopped;
    private volatile BluetoothServerSocket serverSocket;
    private volatile String myNodeId = "NODE";

    public BluetoothMeshManager(Context context, Listener listener) {
        this.appContext = context.getApplicationContext();
        this.listener = listener;
        this.adapter = BluetoothAdapter.getDefaultAdapter();
        this.database = AppDatabase.getInstance(this.appContext);
        File identityDir = new File(this.appContext.getFilesDir(), "identity");
        this.signer = PacketSigner.createForApp(this.appContext, identityDir);
        this.identityStore = new PeerIdentityStore(new File(identityDir, "known-peers.txt"));
        this.peerPublicKeys.putAll(this.identityStore.snapshot());
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
        runMeshTask(() -> {
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

    public void pauseForBluetoothOff() {
        accepting = false;
        closeServerSocket();
        for (BluetoothSocket socket : sockets.values()) {
            closeSocket(socket);
        }
        sockets.clear();
        connectingAddresses.clear();
        publishConnections();
        listener.onLog("Bluetooth turned off. Queued messages stay on this phone.");
    }

    public void stop() {
        stopped = true;
        accepting = false;
        closeServerSocket();
        for (BluetoothSocket socket : sockets.values()) {
            closeSocket(socket);
        }
        sockets.clear();
        connectingAddresses.clear();
        peerNodeIds.clear();
        discardFileState();
        publishConnections();
        executor.shutdownNow();
    }

    private void runMeshTask(Runnable task) {
        if (stopped) return;
        try {
            executor.execute(task);
        } catch (RejectedExecutionException ignored) {
            MeshDiagnostics.record("lifecycle", "executor_rejected");
        }
    }

    @SuppressLint("MissingPermission")
    public void connectToDevice(BluetoothDevice device) {
        if (device == null || !hasConnectPermission() || !connectingAddresses.add(device.getAddress())) {
            return;
        }
        runMeshTask(() -> {
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
        storePending(message);
        if (trimmedDestination.equals(myNodeId)) {
            listener.onMessageDelivered(message);
            deletePending(message.getId());
            return true;
        }
        if (!hasAnyPeers()) {
            listener.onLog("No active mesh peers. Message is queued until a nearby phone links.");
            listener.onMessageStatusChanged(message, MessageStatus.QUEUED);
            return true;
        }
        boolean wrote = forwardMessage(message, null) > 0;
        listener.onMessageStatusChanged(message, MeshDelivery.statusAfterQueueAttempt(true, wrote));
        return true;
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
        runMeshTask(() -> {
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
            AuthResult auth = authenticate(message);
            if (auth == AuthResult.TAMPERED) {
                markSeen(message.getId());
                listener.onLog("Rejected tampered " + message.getType() + " " + message.getId()
                        + " from " + message.getSource());
                return;
            }
            if (auth == AuthResult.UNKNOWN) {
                boolean forMe = message.getDestination().equalsIgnoreCase(myNodeId);
                if (forMe) {
                    listener.onLog("Holding " + message.getType() + " from " + message.getSource()
                            + " until its signing key is known.");
                    return;
                }
                if (!markSeen(message.getId())) return;
                if (message.getTtl() > 1) {
                    forwardMessage(message.decrementedTtl(),
                            MeshDelivery.exceptAddress(false, fromAddress));
                }
                return;
            }
            if (!markSeen(message.getId())) {
                if (MeshDelivery.shouldResendAckOnDuplicate(
                        message.getType() == ManetMessage.Type.MSG,
                        message.getDestination().equalsIgnoreCase(myNodeId))) {
                    forwardMessage(sign(ManetMessage.ack(myNodeId, message.getSource(), message.getId())),
                            MeshDelivery.exceptAddress(true, fromAddress));
                }
                return;
            }

            listener.onLog("RX " + message.getSource() + " -> " + message.getDestination()
                    + " (ttl=" + message.getTtl() + ")");

            if (message.getType() == ManetMessage.Type.ACK) {
                if (message.getDestination().equalsIgnoreCase(myNodeId)) {
                    deletePending(message.getData());
                    listener.onMessageAcknowledged(message.getData());
                    return;
                }
                if (message.getTtl() > 1) {
                    forwardMessage(message.decrementedTtl(),
                            MeshDelivery.exceptAddress(false, fromAddress));
                }
                return;
            }

            boolean broadcast = "ALL".equalsIgnoreCase(message.getDestination())
                    || "*".equals(message.getDestination());
            if (message.getDestination().equalsIgnoreCase(myNodeId) || broadcast) {
                listener.onMessageDelivered(message);
                if (message.getDestination().equalsIgnoreCase(myNodeId)) {
                    forwardMessage(sign(ManetMessage.ack(myNodeId, message.getSource(), message.getId())),
                            MeshDelivery.exceptAddress(true, fromAddress));
                    return;
                }
            }

            if (message.getTtl() <= 1) {
                listener.onLog("Dropped " + message.getId() + " because TTL expired.");
                return;
            }

            int forwarded = forwardMessage(message.decrementedTtl(),
                    MeshDelivery.exceptAddress(false, fromAddress));
            if (forwarded == 0 && !broadcast) storePending(message);
        } catch (IllegalArgumentException e) {
            listener.onLog("Ignored malformed payload.");
        } catch (OutOfMemoryError e) {
            listener.onLog("Dropped a packet because this phone is low on memory.");
            MeshDiagnostics.record("transfer", "rx_oom");
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
        runMeshTask(() -> {
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

    private void deletePending(String messageId) {
        if (messageId == null || messageId.trim().isEmpty()) return;
        pendingAttempts.remove(messageId);
        pendingRetryAt.remove(messageId);
        runMeshTask(() -> database.pendingMessageDao().delete(messageId));
    }

    private void flushPending() {
        long now = System.currentTimeMillis();
        if (now - lastPendingFlushAt < PENDING_FLUSH_MIN_INTERVAL_MS) return;
        lastPendingFlushAt = now;
        runMeshTask(() -> {
            long cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L;
            for (PendingMessageEntity expired : database.pendingMessageDao().all(0L)) {
                if (expired.createdAt < cutoff) {
                    try {
                        ManetMessage message = ManetMessage.fromWire(expired.wire);
                        listener.onMessageStatusChanged(message, MessageStatus.FAILED);
                    } catch (IllegalArgumentException ignored) { }
                    pendingAttempts.remove(expired.id);
                    pendingRetryAt.remove(expired.id);
                    database.pendingMessageDao().delete(expired.id);
                }
            }
            database.pendingMessageDao().deleteExpired(cutoff);
            if (!hasAnyPeers()) return;
            long nowFlush = System.currentTimeMillis();
            for (PendingMessageEntity pending : database.pendingMessageDao().all(cutoff)) {
                Long retryAt = pendingRetryAt.get(pending.id);
                if (retryAt != null && nowFlush < retryAt) continue;
                try {
                    ManetMessage message = ManetMessage.fromWire(pending.wire);
                    boolean wrote = forwardMessage(message, null) > 0;
                    int attempt = pendingAttempts.getOrDefault(pending.id, 0);
                    pendingAttempts.put(pending.id, attempt + 1);
                    pendingRetryAt.put(pending.id, nowFlush + MeshDelivery.retryDelayMs(attempt)
                            + retryJitter.nextInt(1_000));
                    if (wrote
                            && message.getSource().equalsIgnoreCase(myNodeId)
                            && message.getType() == ManetMessage.Type.MSG) {
                        listener.onMessageStatusChanged(message, MessageStatus.SENT);
                    }
                } catch (IllegalArgumentException ignored) { }
            }
        });
    }

    public boolean sendFile(String destination, String fileName, byte[] contents) {
        if (contents == null || contents.length == 0) {
            listener.onLog("File is empty.");
            return false;
        }
        File temp = new File(MeshFileStore.outgoingDir(appContext), "tmp-" + System.nanoTime() + ".bin");
        try {
            MeshFileStore.outgoingDir(appContext).mkdirs();
            try (java.io.FileOutputStream output = new java.io.FileOutputStream(temp)) {
                output.write(contents);
            }
        } catch (IOException error) {
            listener.onLog("Couldn't prepare the file to send.");
            temp.delete();
            return false;
        }
        boolean sent = sendFile(destination, fileName, temp);
        if (temp.exists()) temp.delete();
        return sent;
    }

    public boolean sendFile(String destination, String fileName, File source) {
        String trimmedDestination = destination == null ? "" : destination.trim().toUpperCase();
        if (source == null || !source.isFile() || source.length() <= 0) {
            listener.onLog("File is empty.");
            return false;
        }
        if (trimmedDestination.isEmpty()) {
            listener.onLog("Destination is required to send a file.");
            return false;
        }
        int maxBytes = MeshIo.effectiveMaxFileBytes();
        if (source.length() > maxBytes) {
            listener.onLog("File is larger than " + MeshIo.MAX_FILE_LABEL + ". Choose a smaller file.");
            return false;
        }
        if (!hasAnyPeers()) {
            listener.onLog("No active mesh peers. Files are sent over BLE or RFCOMM once a phone is linked.");
            return false;
        }
        pruneOutgoingFiles();
        String safeName = fileName == null ? "file.bin" : fileName.replace("|", "_").replaceAll("[^a-zA-Z0-9._-]", "_");
        int size = (int) source.length();
        int total = MeshIo.chunkCount(size, FilePacket.CHUNK_SIZE);
        String id = UUID.randomUUID().toString();
        File owned = new File(MeshFileStore.outgoingDir(appContext), id + ".bin");
        try {
            if (!owned.getAbsolutePath().equals(source.getAbsolutePath())) {
                MeshIo.moveOrCopy(source, owned);
            }
        } catch (IOException | OutOfMemoryError error) {
            listener.onLog("Couldn't prepare the file to send.");
            MeshDiagnostics.record("transfer", "outgoing_copy_failed");
            owned.delete();
            return false;
        }
        try {
            String fileSha = MeshIntegrity.sha256Hex(owned);
            FilePacket meta = FilePacket.meta(id, myNodeId, trimmedDestination, ManetMessage.DEFAULT_TTL,
                    safeName, total, size, fileSha,
                    signer.sign(MeshIntegrity.fileMetaCanon(id, myNodeId, trimmedDestination, safeName, total, size, fileSha)));
            OutgoingFile outgoing = new OutgoingFile(meta, owned, size);
            outgoingFiles.put(id, outgoing);
            forwardMessage(signedHello(), null);
            listener.onLog("Sending signed file " + safeName + " (" + size + " bytes, " + total + " chunks).");
            awaitSendWindow();
            int chunksDelivered = 0;
            forwardBytes(meta.toBytes(), null);
            awaitSendWindow();
            forwardBytes(meta.toBytes(), null);
            byte[] scratch = new byte[FilePacket.CHUNK_SIZE];
            try (RandomAccessFile raf = new RandomAccessFile(owned, "r")) {
                for (int index = 0; index < total; index++) {
                    if (stopped) return false;
                    awaitSendWindow();
                    int length = MeshIo.readChunk(raf, scratch, index, FilePacket.CHUNK_SIZE, size);
                    if (length <= 0) break;
                    String chunkSha = MeshIntegrity.sha256Hex(scratch, 0, length);
                    String data = android.util.Base64.encodeToString(scratch, 0, length, android.util.Base64.NO_WRAP);
                    FilePacket packet = new FilePacket(FilePacket.Kind.CHUNK, id, myNodeId, trimmedDestination,
                            ManetMessage.DEFAULT_TTL, safeName, index, total, size, chunkSha, data,
                            signer.sign(MeshIntegrity.fileChunkCanon(id, myNodeId, trimmedDestination, index, total, chunkSha)));
                    if (forwardBytes(packet.toBytes(), null) > 0) chunksDelivered++;
                    listener.onFileProgress(id, index + 1, total, safeName);
                }
            }
            boolean sent = chunksDelivered > 0;
            listener.onLog(sent
                    ? "File " + safeName + " handed to nearby phones. Delivery is not confirmed yet."
                    : "File " + safeName + " could not be written to any peer.");
            ManetMessage fileNote = new ManetMessage(id, myNodeId, trimmedDestination,
                    ManetMessage.DEFAULT_TTL, "File: " + safeName);
            listener.onMessageStatusChanged(fileNote, MessageStatus.SENDING);
            listener.onMessageStatusChanged(fileNote, sent ? MessageStatus.SENT : MessageStatus.FAILED);
            if (!sent) {
                outgoingFiles.remove(id);
                owned.delete();
            }
            return sent;
        } catch (IOException | OutOfMemoryError error) {
            listener.onLog("This phone ran out of memory sending the file. Try a smaller one.");
            MeshDiagnostics.record("transfer", "send_failed");
            outgoingFiles.remove(id);
            owned.delete();
            return false;
        }
    }

    private void awaitSendWindow() {
        if (extraLinks != null) extraLinks.awaitSendWindow();
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
        // Node IDs are case-insensitive identities; keep one canonical form so
        // the same peer cannot occupy two slots in the roster.
        node = node == null ? "" : node.trim().toUpperCase();
        if (node.isEmpty()) {
            listener.onLog("Ignored HELLO with no node ID from " + fromAddress);
            return;
        }
        if (publicKey != null) {
            if (!message.hasSignature()
                    || !signer.verify(message.canonical(), message.getSignature(), publicKey)) {
                listener.onLog("Rejected tampered HELLO from " + fromAddress);
                return;
            }
            PeerIdentityStore.PutResult put = identityStore.putVerified(node, publicKey);
            if (put == PeerIdentityStore.PutResult.REJECTED_MISMATCH) {
                listener.onPeerIdentityConflict(node, identityStore.fingerprint(node));
                listener.onLog("Rejected identity change for " + node
                        + ". Known fingerprint " + identityStore.fingerprint(node)
                        + ". The advertised key does not match.");
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
        if (extraLinks != null) extraLinks.noteVerifiedNodeId(fromAddress, node);
        final String storedNode = node;
        // Update in place rather than replacing the row: only the scanner knows
        // this peer's signal strength, and a blind insert would reset it to 0
        // and break every distance estimate in the UI.
        runMeshTask(() -> {
            long seenAt = System.currentTimeMillis();
            if (database.meshStateDao().touchNeighbor(fromAddress, storedNode, 1, seenAt, true) == 0) {
                database.meshStateDao().upsertNeighbor(new MeshNeighborEntity(
                        fromAddress, storedNode, 0, null, null, 1, seenAt, true));
            }
        });
        long now = System.currentTimeMillis();
        Long previous = helloSeen.put(node, now);
        if (previous != null && now - previous < 3_000L) return;
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
        byte[] raw;
        try {
            raw = android.util.Base64.decode(packet.data, android.util.Base64.DEFAULT);
        } catch (IllegalArgumentException | OutOfMemoryError error) {
            listener.onLog("Dropped unreadable file chunk " + packet.index + " of " + packet.fileName);
            return;
        }
        if (raw.length > FilePacket.CHUNK_SIZE) {
            listener.onLog("Dropped oversized file chunk " + packet.index + " of " + packet.fileName);
            return;
        }
        if (!MeshIntegrity.sha256Hex(raw).equalsIgnoreCase(packet.digest)) {
            listener.onLog("Dropped tampered file chunk " + packet.index + " of " + packet.fileName);
            rememberFileChunk(chunkKey);
            return;
        }
        if (!verifyFileChunk(packet)) {
            listener.onLog("Dropped file chunk with invalid signature: " + packet.fileName);
            rememberFileChunk(chunkKey);
            return;
        }
        if (!rememberFileChunk(chunkKey)) {
            maybeRelayFile(packet, fromAddress);
            return;
        }
        FileTransferBuffer buffer = bufferFor(packet);
        try {
            buffer.reassembly.putChunk(packet.index, raw);
        } catch (IOException | OutOfMemoryError error) {
            listener.onLog("Couldn't store a file chunk. Storage may be full.");
            MeshDiagnostics.record("transfer", "chunk_write_failed");
            return;
        }
        buffer.lastUpdate = System.currentTimeMillis();
        listener.onFileProgress(packet.id, buffer.reassembly.receivedCount(),
                Math.max(packet.total, buffer.reassembly.total()), packet.fileName);
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
        if (packet.size > MeshIo.effectiveMaxFileBytes()) {
            listener.onLog("Rejected file " + packet.fileName + ": larger than this phone can receive.");
            buffer.reassembly.discard();
            fileBuffers.remove(packet.id);
            return;
        }
        try {
            if (!buffer.reassembly.setMeta(packet.total, packet.size)) {
                listener.onLog("Rejected file header with an invalid size for " + packet.fileName);
                return;
            }
        } catch (IOException | OutOfMemoryError error) {
            listener.onLog("Couldn't reserve space for " + packet.fileName);
            MeshDiagnostics.record("transfer", "meta_open_failed");
            return;
        }
        buffer.pendingMeta = null;
        buffer.fileName = packet.fileName;
        buffer.source = packet.source;
        buffer.destination = packet.destination;
        buffer.fileSha = packet.digest;
        buffer.metaOk = true;
        listener.onLog("Authenticated file header " + packet.fileName + " sha256=" + packet.digest.substring(0, Math.min(12, packet.digest.length())));
        scheduleFileRetry(packet.id);
        tryCompleteFile(buffer, packet);
        maybeRelayFile(packet, fromAddress);
    }

    private void handleFileRequest(FilePacket packet, String fromAddress) {
        OutgoingFile outgoing = outgoingFiles.get(packet.id);
        if (outgoing == null || outgoing.source == null || !outgoing.source.isFile()) {
            if (packet.ttl > 1) forwardBytes(packet.decrementedTtl().toBytes(), fromAddress);
            return;
        }
        listener.onLog("Resending missing chunks for " + outgoing.meta.fileName + ": " + packet.requestIndexes);
        awaitSendWindow();
        forwardBytes(outgoing.meta.toBytes(), null);
        byte[] scratch = new byte[FilePacket.CHUNK_SIZE];
        try (RandomAccessFile raf = new RandomAccessFile(outgoing.source, "r")) {
            int resent = 0;
            for (String item : packet.requestIndexes.split(",")) {
                if (item.trim().isEmpty()) continue;
                if (resent >= MAX_MISSING_INDEXES) break;
                try {
                    int index = Integer.parseInt(item.trim());
                    if (index < 0 || index >= outgoing.meta.total) continue;
                    awaitSendWindow();
                    int length = MeshIo.readChunk(raf, scratch, index, FilePacket.CHUNK_SIZE, outgoing.size);
                    if (length <= 0) continue;
                    String chunkSha = MeshIntegrity.sha256Hex(scratch, 0, length);
                    String data = android.util.Base64.encodeToString(scratch, 0, length, android.util.Base64.NO_WRAP);
                    FilePacket chunk = new FilePacket(FilePacket.Kind.CHUNK, outgoing.meta.id, outgoing.meta.source,
                            outgoing.meta.destination, ManetMessage.DEFAULT_TTL, outgoing.meta.fileName, index,
                            outgoing.meta.total, outgoing.size, chunkSha, data,
                            signer.sign(MeshIntegrity.fileChunkCanon(outgoing.meta.id, outgoing.meta.source,
                                    outgoing.meta.destination, index, outgoing.meta.total, chunkSha)));
                    forwardBytes(chunk.toBytes(), null);
                    resent++;
                } catch (NumberFormatException ignored) {
                    MeshDiagnostics.record("transfer", "bad_chunk_index");
                }
            }
        } catch (IOException | OutOfMemoryError error) {
            MeshDiagnostics.record("transfer", "retry_read_failed");
        }
    }

    private FileTransferBuffer bufferFor(FilePacket packet) {
        pruneStaleFileBuffers();
        FileTransferBuffer buffer = fileBuffers.get(packet.id);
        if (buffer != null) return buffer;
        FileTransferBuffer created = new FileTransferBuffer(
                new FileReassembly(MeshFileStore.incomingScratch(appContext, packet.id)));
        FileTransferBuffer existing = fileBuffers.putIfAbsent(packet.id, created);
        if (existing != null) {
            created.reassembly.discard();
            return existing;
        }
        return created;
    }

    private void tryCompleteFile(FileTransferBuffer buffer, FilePacket packet) {
        if (!packet.isFor(myNodeId) || !buffer.metaOk || !buffer.reassembly.isComplete()) {
            return;
        }
        try {
            if (!buffer.reassembly.matchesSha256(buffer.fileSha)) {
                listener.onLog("Rejected file " + buffer.fileName + ": content hash mismatch (tampered or corrupt).");
                buffer.reassembly.discard();
                fileBuffers.remove(packet.id);
                return;
            }
            File assembled = buffer.reassembly.finish();
            File saved = MeshFileStore.save(appContext, buffer.fileName, assembled);
            assembled.delete();
            fileBuffers.remove(packet.id);
            listener.onLog("Saved verified file to " + saved.getAbsolutePath());
            listener.onFileReceived(buffer.fileName, saved.getAbsolutePath());
            listener.onMessageDelivered(new ManetMessage(packet.id, buffer.source, myNodeId,
                    packet.ttl, MeshFileStore.chatLabel(buffer.fileName, saved.getAbsolutePath())));
        } catch (IOException | OutOfMemoryError e) {
            listener.onLog("Couldn't save the file. Storage may be full.");
            MeshDiagnostics.record("transfer", "save_failed");
        }
    }

    private void scheduleFileRetry(String transferId) {
        FileTransferBuffer buffer = fileBuffers.get(transferId);
        if (buffer == null) return;
        if (buffer.retry != null) handler.removeCallbacks(buffer.retry);
        buffer.retry = () -> requestMissingChunks(transferId);
        handler.postDelayed(buffer.retry, 1_200);
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
        if (buffer == null || !buffer.metaOk || buffer.reassembly.total() <= 0) return;
        if (!buffer.destination.equalsIgnoreCase(myNodeId) && !"ALL".equalsIgnoreCase(buffer.destination)) return;
        String missing = buffer.reassembly.missingIndexes(MAX_MISSING_INDEXES);
        if (missing.isEmpty()) return;
        FilePacket request = FilePacket.request(transferId, myNodeId, buffer.source, ManetMessage.DEFAULT_TTL, missing);
        listener.onLog("Requesting missing file chunks: " + missing);
        awaitSendWindow();
        forwardBytes(request.toBytes(), null);
        handler.postDelayed(() -> requestMissingChunks(transferId), 2_000);
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
        final FileReassembly reassembly;
        String fileName = "";
        String source = "";
        String destination = "";
        String fileSha = "";
        boolean metaOk;
        long lastUpdate;
        FilePacket pendingMeta;
        Runnable retry;

        FileTransferBuffer(FileReassembly reassembly) {
            this.reassembly = reassembly;
            this.lastUpdate = System.currentTimeMillis();
        }
    }

    private static class OutgoingFile {
        final FilePacket meta;
        final File source;
        final int size;
        final long createdAt = System.currentTimeMillis();
        OutgoingFile(FilePacket meta, File source, int size) {
            this.meta = meta;
            this.source = source;
            this.size = size;
        }
    }

    private boolean rememberFileChunk(String chunkKey) {
        if (!seenFileChunks.add(chunkKey)) return false;
        if (seenFileChunks.size() > MAX_SEEN_FILE_CHUNKS) {
            seenFileChunks.clear();
            seenFileChunks.add(chunkKey);
        }
        return true;
    }

    private void pruneStaleFileBuffers() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, FileTransferBuffer> entry : fileBuffers.entrySet()) {
            FileTransferBuffer buffer = entry.getValue();
            if (now - buffer.lastUpdate < FILE_BUFFER_TTL_MS) continue;
            if (buffer.retry != null) handler.removeCallbacks(buffer.retry);
            buffer.reassembly.discard();
            fileBuffers.remove(entry.getKey(), buffer);
        }
    }

    private void pruneOutgoingFiles() {
        if (outgoingFiles.size() < MAX_OUTGOING_FILES) return;
        String oldestId = null;
        long oldest = Long.MAX_VALUE;
        for (Map.Entry<String, OutgoingFile> entry : outgoingFiles.entrySet()) {
            if (entry.getValue().createdAt < oldest) {
                oldest = entry.getValue().createdAt;
                oldestId = entry.getKey();
            }
        }
        if (oldestId == null) return;
        OutgoingFile removed = outgoingFiles.remove(oldestId);
        if (removed != null && removed.source != null) removed.source.delete();
    }

    private void discardFileState() {
        for (FileTransferBuffer buffer : fileBuffers.values()) {
            if (buffer.retry != null) handler.removeCallbacks(buffer.retry);
            buffer.reassembly.discard();
        }
        fileBuffers.clear();
        for (OutgoingFile outgoing : outgoingFiles.values()) {
            if (outgoing.source != null) outgoing.source.delete();
        }
        outgoingFiles.clear();
        seenFileChunks.clear();
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

    /**
     * Prefers the mesh node ID over the Bluetooth device name. Labels are
     * parsed back into node IDs upstream, so a hardware name here would show
     * up in the UI as the peer's identity.
     */
    @SuppressLint("MissingPermission")
    private String safeDeviceLabel(BluetoothDevice device) {
        String address = device.getAddress();
        String node = peerNodeIds.get(address);
        if (node != null && !node.trim().isEmpty()) {
            return node.trim().toUpperCase() + " (" + address + ")";
        }
        String name = hasConnectPermission() ? device.getName() : null;
        return (name == null || name.trim().isEmpty() ? "Unknown" : name) + " (" + address + ")";
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
