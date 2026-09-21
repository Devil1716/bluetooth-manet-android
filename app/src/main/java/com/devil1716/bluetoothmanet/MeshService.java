package com.devil1716.bluetoothmanet;

import android.Manifest;
import android.app.PendingIntent;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.pm.ServiceInfo;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.devil1716.bluetoothmanet.bluetooth.MeshPeer;
import com.devil1716.bluetoothmanet.bluetooth.gatt.BleGattTransport;
import com.devil1716.bluetoothmanet.ui.ComposeMeshActivity;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MeshService extends Service implements BluetoothMeshManager.Listener {
    private static final String CHANNEL = "mesh_service";
    public static final String ACTION_MESSAGE_EVENT = "com.devil1716.bluetoothmanet.MESSAGE_EVENT";
    public static final String ACTION_MESH_STATUS = "com.devil1716.bluetoothmanet.MESH_STATUS";
    public static final String ACTION_STOP = "com.devil1716.bluetoothmanet.STOP_MESH";
    /** How long a neighbour row is kept before it is pruned entirely. */
    private static final long NEIGHBOR_RETENTION_MS = 24 * 60 * 60 * 1000L;
    private static final String PREFS = "mesh";
    private static final String KEY_NODE_ID = "node_id";
    private static volatile BluetoothMeshManager activeManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BluetoothMeshManager manager;
    private BleGattTransport bleTransport;
    private AppDatabase database;
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService transferExecutor = Executors.newSingleThreadExecutor();
    private boolean receiverRegistered;
    private boolean foregroundReady;
    private String nodeId = "NODE";
    private final Set<String> rfcommTargets = ConcurrentHashMap.newKeySet();
    private String pendingSendDestination;
    private String pendingSendName;
    private java.io.File pendingSendCache;
    private int pendingSendAttempts;
    private volatile boolean pendingSendBusy;
    private final Runnable pendingSendRetry = new Runnable() {
        @Override public void run() { processPendingFileSend(false); }
    };
    private final BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) return;
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
            if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
                MeshDiagnostics.record("conn", "bluetooth_off");
                status("Bluetooth turned off. Queued messages stay on this phone.");
                if (bleTransport != null) bleTransport.pause();
                if (manager != null) manager.pauseForBluetoothOff();
            } else if (state == BluetoothAdapter.STATE_ON) {
                MeshDiagnostics.record("conn", "bluetooth_on");
                ensureTransportReady();
            }
        }
    };
    private final BroadcastReceiver bondReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(intent.getAction())) return;
            if (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                    != BluetoothDevice.BOND_BONDED) return;
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (device != null && manager != null && rfcommTargets.contains(device.getAddress())) {
                manager.connectToDevice(device);
            }
        }
    };
    private final Runnable announcer = new Runnable() {
        @Override public void run() {
            if (manager != null) manager.broadcastHello();
            handler.postDelayed(this, 4_000L);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        // Load the saved ID before any radio work. onStartCommand delivers it
        // too, but the transport starts advertising here, and advertising the
        // placeholder "NODE" makes two fresh phones mistake each other for self.
        String saved = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_NODE_ID, null);
        if (saved != null && !saved.trim().isEmpty()) {
            nodeId = saved.trim().toUpperCase(Locale.US);
        }
        try {
            startForegroundCompat(notification(0));
            foregroundReady = true;
        } catch (IllegalStateException | SecurityException startFailure) {
            MeshDiagnostics.record("lifecycle", "foreground_start_failed", startFailure.getClass().getSimpleName());
            try {
                startForegroundCompat(notification(0));
            } catch (RuntimeException ignored) { }
            foregroundReady = false;
            stopSelf();
            return;
        }
        manager = new BluetoothMeshManager(this, this);
        bleTransport = new BleGattTransport(this, new BleGattTransport.Listener() {
            @Override public void onLog(String message) { status(message); }
            @Override public void onPeersChanged(List<MeshPeer> peers) {
                persistNearbyPeers(peers);
                if (manager != null) {
                    manager.notifyLinksChanged();
                    manager.broadcastHello();
                }
                broadcastNearbyChanged(peers);
            }
            @Override public void onPayloadReceived(String peerId, byte[] payload) {
                if (manager == null) return;
                manager.ingestPayload(new String(payload, StandardCharsets.UTF_8), peerId);
            }
        });
        manager.setExtraLinks(new MeshLinkBridge() {
            @Override public int send(byte[] bytes, String exceptPeerId) {
                return bleTransport == null ? 0 : bleTransport.send(bytes, exceptPeerId);
            }
            @Override public boolean hasPeers() {
                return bleTransport != null && bleTransport.hasPeers();
            }
            @Override public List<String> peerLabels() {
                return bleTransport == null ? new ArrayList<String>() : bleTransport.peerLabels();
            }
            @Override public void noteVerifiedNodeId(String peerId, String peerNodeId) {
                if (bleTransport != null) bleTransport.noteVerifiedNodeId(peerId, peerNodeId);
            }
            @Override public void awaitSendWindow() {
                if (bleTransport != null) bleTransport.awaitSendWindow();
            }
        });
        activeManager = manager;
        database = AppDatabase.getInstance(this);
        ContextCompat.registerReceiver(this, bondReceiver,
                new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(this, bluetoothStateReceiver,
                new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED);
        receiverRegistered = true;
        manager.setMyNodeId(nodeId);
        ensureTransportReady();
        handler.post(announcer);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!foregroundReady) return START_NOT_STICKY;
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            sendBroadcast(new Intent(ACTION_MESH_STATUS).setPackage(getPackageName())
                    .putExtra("mesh_stopped", true)
                    .putExtra("message", "Nearby chat stopped."));
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && intent.getStringExtra("node_id") != null) {
            nodeId = intent.getStringExtra("node_id").trim().toUpperCase();
            if (manager != null) manager.setMyNodeId(nodeId);
        }
        if (intent != null && intent.hasExtra("send_destination") && manager != null) {
            manager.sendNewMessage(intent.getStringExtra("send_destination"), intent.getStringExtra("send_body"));
        }
        if (intent != null && intent.hasExtra("connect_address") && manager != null
                && manager.getAdapter() != null) {
            String address = intent.getStringExtra("connect_address");
            if (address == null || address.trim().isEmpty()) {
                MeshDiagnostics.record("conn", "connect_ignored", "missing_address");
            } else {
                try {
                    rfcommTargets.add(address);
                    manager.connectToDevice(manager.getAdapter().getRemoteDevice(address));
                    if (bleTransport != null) bleTransport.connect(address);
                } catch (IllegalArgumentException invalidAddress) {
                    MeshDiagnostics.record("conn", "connect_ignored", "bad_address");
                    status("That device address is not valid.");
                }
            }
        }
        if (intent != null && intent.hasExtra("send_file_cache")) {
            String cachePath = intent.getStringExtra("send_file_cache");
            if (cachePath != null && !cachePath.trim().isEmpty()) {
                queuePendingFileSend(
                        intent.getStringExtra("send_file_dest"),
                        intent.getStringExtra("send_file_name"),
                        new java.io.File(cachePath));
            }
        }
        processPendingFileSend(false);
        ensureTransportReady();
        return START_STICKY;
    }

    public static boolean sendMessage(android.content.Context context, String destination, String body) {
        if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return false;
        BluetoothMeshManager current = activeManager;
        if (current != null) return current.sendNewMessage(destination, body);
        Intent intent = new Intent(context, MeshService.class)
                .putExtra("send_destination", destination).putExtra("send_body", body);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
        else context.startService(intent);
        return true;
    }

    public static void connectToAddress(android.content.Context context, String address) {
        if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return;
        Intent intent = new Intent(context, MeshService.class).putExtra("connect_address", address);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
        else context.startService(intent);
    }

    public static boolean sendFile(android.content.Context context, String destination, String fileName, byte[] contents) {
        if (contents == null || contents.length == 0 || contents.length > MeshIo.effectiveMaxFileBytes()) return false;
        java.io.File cache = new java.io.File(context.getCacheDir(), "pending-mesh-send-" + System.nanoTime() + ".bin");
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(cache)) {
            output.write(contents);
        } catch (java.io.IOException e) {
            return false;
        }
        return sendFile(context, destination, fileName, cache);
    }

    public static boolean sendFile(android.content.Context context, String destination, String fileName, java.io.File cache) {
        if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return false;
        if (cache == null || !cache.isFile() || cache.length() <= 0 || cache.length() > MeshIo.effectiveMaxFileBytes()) {
            return false;
        }
        BluetoothMeshManager current = activeManager;
        if (current != null && current.sendFile(destination, fileName, cache)) {
            return true;
        }
        Intent intent = new Intent(context, MeshService.class)
                .putExtra("send_file_cache", cache.getAbsolutePath())
                .putExtra("send_file_dest", destination)
                .putExtra("send_file_name", fileName);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
        else context.startService(intent);
        return true;
    }

    private void queuePendingFileSend(String destination, String fileName, java.io.File cache) {
        pendingSendDestination = destination;
        pendingSendName = fileName;
        pendingSendCache = cache;
        pendingSendAttempts = 0;
        pendingSendBusy = false;
        handler.removeCallbacks(pendingSendRetry);
    }

    private void processPendingFileSend(boolean fromPeerEvent) {
        if (manager == null || pendingSendCache == null || !pendingSendCache.exists()) return;
        try {
            transferExecutor.execute(() -> runPendingFileSend(fromPeerEvent));
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            MeshDiagnostics.record("transfer", "executor_rejected");
        }
    }

    private void runPendingFileSend(boolean fromPeerEvent) {
        java.io.File cache;
        String dest;
        String name;
        synchronized (this) {
            if (pendingSendBusy) return;
            cache = pendingSendCache;
            dest = pendingSendDestination;
            name = pendingSendName;
            if (cache == null || !cache.exists() || manager == null) return;
            pendingSendBusy = true;
        }
        try {
            if (manager.sendFile(dest, name, cache)) {
                clearPendingFileSend(true);
                return;
            }
        } catch (OutOfMemoryError oom) {
            status("This file is too large for this phone to send.");
            MeshDiagnostics.record("transfer", "send_oom");
            clearPendingFileSend(false);
            return;
        } finally {
            synchronized (this) {
                if (pendingSendCache != null) pendingSendBusy = false;
            }
        }
        pendingSendAttempts++;
        if (pendingSendAttempts >= 20) {
            status("File send failed. Start mesh, wait for a signed link, then try again.");
            clearPendingFileSend(false);
            return;
        }
        if (!fromPeerEvent) {
            status("Waiting for mesh link to send " + name + "...");
        }
        handler.removeCallbacks(pendingSendRetry);
        handler.postDelayed(pendingSendRetry, 2000L);
    }

    private void clearPendingFileSend(boolean sent) {
        handler.removeCallbacks(pendingSendRetry);
        synchronized (this) {
            if (!sent && pendingSendCache != null) {
                pendingSendCache.delete();
            }
            pendingSendCache = null;
            pendingSendDestination = null;
            pendingSendName = null;
            pendingSendAttempts = 0;
            pendingSendBusy = false;
        }
        if (sent) {
            status("Signed file transfer started.");
        }
    }

    /**
     * Mirrors the radio's view of who is nearby into the database, which is
     * what the Nearby list reads. Peers that are merely advertising are stored
     * too, so a phone shows up as soon as it is in range instead of only after
     * a GATT link and handshake have completed.
     */
    private void persistNearbyPeers(List<MeshPeer> peers) {
        if (database == null || dbExecutor.isShutdown()) return;
        final List<MeshPeer> snapshot = peers == null
                ? new ArrayList<MeshPeer>() : new ArrayList<>(peers);
        try {
            dbExecutor.execute(() -> storeNearbyPeers(snapshot));
        } catch (java.util.concurrent.RejectedExecutionException ignored) { }
    }

    private void storeNearbyPeers(List<MeshPeer> snapshot) {
        try {
            long now = System.currentTimeMillis();
            Set<String> live = new HashSet<>();
            for (MeshPeer peer : snapshot) {
                // Without a node ID there is no address to send messages to, so
                // listing the peer would offer a chat that could never be
                // delivered. It still counts toward the nearby tally.
                if (!peer.getHasNodeId()) continue;
                live.add(peer.getAddress());
                database.meshStateDao().upsertNeighbor(new MeshNeighborEntity(
                        peer.getAddress(), peer.getNodeId(), peer.getRssi(), null, null, 1,
                        peer.getLastSeenAt() > 0L ? peer.getLastSeenAt() : now,
                        peer.getLinked()));
            }
            // Anyone missing from this roster is out of range: clear the flag so
            // the UI stops reporting stale peers as online.
            for (MeshNeighborEntity known : database.meshStateDao().neighbors()) {
                if (!live.contains(known.deviceId) && known.connected) {
                    database.meshStateDao().touchNeighbor(known.deviceId, known.displayName,
                            known.hopCount, known.lastSeen, false);
                }
            }
            database.meshStateDao().deleteExpiredNeighbors(now - NEIGHBOR_RETENTION_MS);
        } catch (RuntimeException storeFailure) {
            MeshDiagnostics.record("conn", "neighbor_store_failed");
        }
    }

    /** Nothing is reachable once the radio work stops. */
    private void markEveryoneOffline() {
        if (database == null || dbExecutor.isShutdown()) return;
        try {
            dbExecutor.execute(() -> {
                try {
                    database.meshStateDao().markAllNeighborsOffline();
                } catch (RuntimeException ignored) { }
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) { }
    }

    /**
     * Signals that the nearby roster moved. The roster itself is read back from
     * the database rather than packed into the intent, so there is exactly one
     * source of truth. The "peers" extra is deliberately not set here: it means
     * "connected peers" everywhere else and is published by
     * {@link #onConnectionsChanged(List)}.
     */
    private void broadcastNearbyChanged(List<MeshPeer> peers) {
        int linked = 0;
        int total = 0;
        if (peers != null) {
            total = peers.size();
            for (MeshPeer peer : peers) {
                if (peer.getLinked()) linked++;
            }
        }
        sendBroadcast(new Intent(ACTION_MESH_STATUS).setPackage(getPackageName())
                .putExtra("nearby_changed", true)
                .putExtra("nearby_count", total)
                .putExtra("linked_count", linked));
    }

    private void ensureTransportReady() {
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            status("Bluetooth Connect permission is required before the mesh can start.");
            return;
        }
        BluetoothAdapter adapter = manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) { status("Bluetooth is disabled."); return; }
        manager.startAccepting();
        if (bleTransport != null) bleTransport.start(nodeId);
    }

    private Notification notification(int count) {
        Intent launch = new Intent(this, ComposeMeshActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) piFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent content = PendingIntent.getActivity(this, 0, launch, piFlags);
        Intent stop = new Intent(this, MeshService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stop, piFlags);
        return new NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_stat_mesh)
                .setContentTitle("Mesh is on")
                .setContentText(
                        count == 0 ? "Looking for nearby phones" :
                                (count == 1 ? "1 nearby" : count + " nearby"))
                .setContentIntent(content)
                .addAction(0, "Stop nearby chat", stopPi)
                .setOngoing(true).setCategory(NotificationCompat.CATEGORY_SERVICE).build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager.class).createNotificationChannel(
                new NotificationChannel(CHANNEL, "Nearby chat", NotificationManager.IMPORTANCE_LOW));
    }

    @Override public void onDestroy() {
        activeManager = null;
        markEveryoneOffline();
        handler.removeCallbacksAndMessages(null);
        if (receiverRegistered) {
            unregisterReceiver(bondReceiver);
            unregisterReceiver(bluetoothStateReceiver);
        }
        if (bleTransport != null) bleTransport.stop();
        if (manager != null) manager.stop();
        dbExecutor.shutdown();
        transferExecutor.shutdown();
        MeshDiagnostics.record("lifecycle", "service_destroy");
        super.onDestroy();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onLog(String message) { status(message); }

    @Override public void onConnectionsChanged(List<String> peers) {
        int count = peers == null ? 0 : peers.size();
        try {
            startForegroundCompat(notification(count));
        } catch (IllegalStateException | SecurityException ignored) { }
        Intent intent = new Intent(ACTION_MESH_STATUS).setPackage(getPackageName())
                .putExtra("message", count == 0 ? "No connected peers." : "Connected peers: " + String.join(", ", peers))
                .putExtra("peer_count", count)
                .putStringArrayListExtra("peers", peers == null ? new ArrayList<String>() : new ArrayList<>(peers));
        sendBroadcast(intent);
        processPendingFileSend(true);
    }

    @Override public void onMessageDelivered(ManetMessage message) {
        boolean sentByMe = message.getSource().equalsIgnoreCase(nodeId);
        String conversation = conversationKey(sentByMe ? message.getDestination() : message.getSource());
        dbExecutor.execute(() -> database.messageDao().insert(new ChatMessageEntity(message.getId(), conversation, message.getData(),
                message.getSource(), System.currentTimeMillis(), MessageStatus.DELIVERED, sentByMe)));
        broadcastMessageEvent(message, MessageStatus.DELIVERED);
    }

    @Override public void onMessageStatusChanged(ManetMessage message, MessageStatus status) {
        String conversation = conversationKey(message.getDestination());
        dbExecutor.execute(() -> {
            if (status == MessageStatus.SENDING || status == MessageStatus.QUEUED) {
                database.messageDao().insert(new ChatMessageEntity(message.getId(), conversation, message.getData(),
                        message.getSource(), System.currentTimeMillis(), status, true));
            } else {
                database.messageDao().updateStatus(message.getId(), status);
            }
        });
        broadcastMessageEvent(message, status);
    }

    /**
     * Conversation IDs must be case-stable. Node IDs arrive uppercased when we
     * send but verbatim off the wire, so without this the same peer produces
     * two separate threads in the inbox.
     */
    private static String conversationKey(String nodeId) {
        return nodeId == null ? "" : nodeId.trim().toUpperCase(Locale.US);
    }

    @Override public void onMessageAcknowledged(String messageId) {
        dbExecutor.execute(() -> database.messageDao().updateStatus(messageId, MessageStatus.DELIVERED));
        sendBroadcast(new Intent(ACTION_MESSAGE_EVENT).setPackage(getPackageName())
                .putExtra("message_id", messageId).putExtra("status", MessageStatus.DELIVERED.name()));
    }

    @Override public void onPeerIdentityConflict(String nodeId, String fingerprint) {
        status("Identity warning: " + nodeId + " advertised a different key. Known fingerprint "
                + fingerprint + ". Messages from the new key are ignored.");
        sendBroadcast(new Intent(ACTION_MESH_STATUS).setPackage(getPackageName())
                .putExtra("identity_conflict", nodeId)
                .putExtra("identity_fingerprint", fingerprint));
    }

    @Override public void onFileProgress(String transferId, int completed, int total, String fileName) {
        sendBroadcast(new Intent(ACTION_MESH_STATUS).setPackage(getPackageName())
                .putExtra("file_id", transferId).putExtra("file_completed", completed)
                .putExtra("file_total", total).putExtra("file_name", fileName));
    }

    @Override public void onFileReceived(String fileName, String path) {
        sendBroadcast(new Intent(ACTION_MESH_STATUS).setPackage(getPackageName())
                .putExtra("message", "Received file " + fileName)
                .putExtra("file_name", fileName)
                .putExtra("file_path", path)
                .putExtra("file_completed", 1)
                .putExtra("file_total", 1));
    }

    private void broadcastMessageEvent(ManetMessage message, MessageStatus status) {
        sendBroadcast(new Intent(ACTION_MESSAGE_EVENT).setPackage(getPackageName())
                .putExtra("message_id", message.getId()).putExtra("source", message.getSource())
                .putExtra("destination", message.getDestination())
                .putExtra("status", status.name()));
    }

    private void startForegroundCompat(Notification notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(42, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            startForeground(42, notification);
        }
    }

    private void status(String message) {
        sendBroadcast(new Intent(ACTION_MESH_STATUS).setPackage(getPackageName()).putExtra("message", message));
    }
}
