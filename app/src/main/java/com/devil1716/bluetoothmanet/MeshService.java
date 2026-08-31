package com.devil1716.bluetoothmanet;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.devil1716.bluetoothmanet.bluetooth.gatt.BleGattTransport;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MeshService extends Service implements BluetoothMeshManager.Listener {
    private static final String CHANNEL = "mesh_service";
    public static final String ACTION_MESSAGE_EVENT = "com.devil1716.bluetoothmanet.MESSAGE_EVENT";
    public static final String ACTION_MESH_STATUS = "com.devil1716.bluetoothmanet.MESH_STATUS";
    private static volatile BluetoothMeshManager activeManager;
    private final Handler handler = new Handler();
    private BluetoothMeshManager manager;
    private BleGattTransport bleTransport;
    private AppDatabase database;
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    private boolean receiverRegistered;
    private boolean foregroundReady;
    private String nodeId = "NODE";
    private final Set<String> rfcommTargets = ConcurrentHashMap.newKeySet();
    private String pendingSendDestination;
    private String pendingSendName;
    private java.io.File pendingSendCache;
    private int pendingSendAttempts;
    private final Runnable pendingSendRetry = new Runnable() {
        @Override public void run() { processPendingFileSend(false); }
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
            handler.postDelayed(this, 12_000L);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        try {
            startForeground(42, notification(0));
            foregroundReady = true;
        } catch (SecurityException securityException) {
            foregroundReady = false;
            stopSelf();
            return;
        }
        manager = new BluetoothMeshManager(this, this);
        bleTransport = new BleGattTransport(this, new BleGattTransport.Listener() {
            @Override public void onLog(String message) { status(message); }
            @Override public void onPeersChanged(List<String> labels) {
                if (manager != null) {
                    manager.notifyLinksChanged();
                    manager.broadcastHello();
                }
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
        });
        activeManager = manager;
        database = AppDatabase.getInstance(this);
        ContextCompat.registerReceiver(this, bondReceiver,
                new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED);
        receiverRegistered = true;
        manager.setMyNodeId(nodeId);
        ensureTransportReady();
        handler.post(announcer);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!foregroundReady) return START_NOT_STICKY;
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
            rfcommTargets.add(address);
            manager.connectToDevice(manager.getAdapter().getRemoteDevice(address));
            if (bleTransport != null) bleTransport.connect(address);
        }
        if (intent != null && intent.hasExtra("send_file_cache")) {
            queuePendingFileSend(
                    intent.getStringExtra("send_file_dest"),
                    intent.getStringExtra("send_file_name"),
                    new java.io.File(intent.getStringExtra("send_file_cache")));
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
        if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return false;
        if (contents == null || contents.length == 0) return false;
        BluetoothMeshManager current = activeManager;
        if (current != null && current.sendFile(destination, fileName, contents)) {
            return true;
        }
        java.io.File cache = new java.io.File(context.getCacheDir(), "pending-mesh-send.bin");
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(cache)) {
            output.write(contents);
        } catch (java.io.IOException e) {
            return false;
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
        handler.removeCallbacks(pendingSendRetry);
    }

    private void processPendingFileSend(boolean fromPeerEvent) {
        if (manager == null || pendingSendCache == null || !pendingSendCache.exists()) return;
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(pendingSendCache.toPath());
            if (manager.sendFile(pendingSendDestination, pendingSendName, bytes)) {
                clearPendingFileSend(true);
                return;
            }
        } catch (java.io.IOException e) {
            status("Could not read queued file: " + e.getMessage());
            clearPendingFileSend(false);
            return;
        }
        pendingSendAttempts++;
        if (pendingSendAttempts >= 20) {
            status("File send failed. Start mesh, wait for a signed link, then try again.");
            clearPendingFileSend(false);
            return;
        }
        if (!fromPeerEvent) {
            status("Waiting for mesh link to send " + pendingSendName + "...");
        }
        handler.removeCallbacks(pendingSendRetry);
        handler.postDelayed(pendingSendRetry, 2000L);
    }

    private void clearPendingFileSend(boolean sent) {
        handler.removeCallbacks(pendingSendRetry);
        if (pendingSendCache != null) {
            pendingSendCache.delete();
        }
        pendingSendCache = null;
        pendingSendDestination = null;
        pendingSendName = null;
        pendingSendAttempts = 0;
        if (sent) {
            status("Signed file transfer started.");
        }
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
        return new NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle("MANET mesh active").setContentText(count + " peer(s) connected")
                .setOngoing(true).setCategory(NotificationCompat.CATEGORY_SERVICE).build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager.class).createNotificationChannel(
                new NotificationChannel(CHANNEL, "MANET mesh", NotificationManager.IMPORTANCE_LOW));
    }

    @Override public void onDestroy() {
        activeManager = null;
        handler.removeCallbacksAndMessages(null);
        if (receiverRegistered) unregisterReceiver(bondReceiver);
        if (bleTransport != null) bleTransport.stop();
        if (manager != null) manager.stop();
        dbExecutor.shutdown();
        super.onDestroy();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onLog(String message) { status(message); }

    @Override public void onConnectionsChanged(List<String> peers) {
        int count = peers == null ? 0 : peers.size();
        try {
            startForeground(42, notification(count));
        } catch (SecurityException ignored) { }
        Intent intent = new Intent(ACTION_MESH_STATUS).setPackage(getPackageName())
                .putExtra("message", count == 0 ? "No connected peers." : "Connected peers: " + String.join(", ", peers))
                .putExtra("peer_count", count)
                .putStringArrayListExtra("peers", peers == null ? new ArrayList<String>() : new ArrayList<>(peers));
        sendBroadcast(intent);
        processPendingFileSend(true);
    }

    @Override public void onMessageDelivered(ManetMessage message) {
        boolean sentByMe = message.getSource().equalsIgnoreCase(nodeId);
        String conversation = sentByMe ? message.getDestination() : message.getSource();
        dbExecutor.execute(() -> database.messageDao().insert(new ChatMessageEntity(message.getId(), conversation, message.getData(),
                message.getSource(), System.currentTimeMillis(), MessageStatus.DELIVERED, sentByMe)));
        broadcastMessageEvent(message, MessageStatus.DELIVERED);
    }

    @Override public void onMessageStatusChanged(ManetMessage message, MessageStatus status) {
        dbExecutor.execute(() -> {
            if (status == MessageStatus.SENDING) {
                database.messageDao().insert(new ChatMessageEntity(message.getId(), message.getDestination(), message.getData(),
                        message.getSource(), System.currentTimeMillis(), status, true));
            } else {
                database.messageDao().updateStatus(message.getId(), status);
            }
        });
        broadcastMessageEvent(message, status);
    }

    @Override public void onMessageAcknowledged(String messageId) {
        dbExecutor.execute(() -> database.messageDao().updateStatus(messageId, MessageStatus.DELIVERED));
        sendBroadcast(new Intent(ACTION_MESSAGE_EVENT).setPackage(getPackageName())
                .putExtra("message_id", messageId).putExtra("status", MessageStatus.DELIVERED.name()));
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
                .putExtra("destination", message.getDestination()).putExtra("body", message.getData())
                .putExtra("status", status.name()));
    }

    private void status(String message) {
        sendBroadcast(new Intent(ACTION_MESH_STATUS).setPackage(getPackageName()).putExtra("message", message));
    }
}
