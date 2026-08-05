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
import java.util.Collections;
import java.util.List;

public class MeshService extends Service implements BluetoothMeshManager.Listener {
    private static final String CHANNEL = "mesh_service";
    public static final String ACTION_MESSAGE_EVENT = "com.devil1716.bluetoothmanet.MESSAGE_EVENT";
    public static final String ACTION_MESH_STATUS = "com.devil1716.bluetoothmanet.MESH_STATUS";
    private static volatile BluetoothMeshManager activeManager;
    private final Handler handler = new Handler();
    private BluetoothMeshManager manager;
    private BleMeshAdvertiser advertiser;
    private AppDatabase database;
    private String nodeId = "NODE";
    private final BroadcastReceiver discoveryReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!BluetoothDevice.ACTION_FOUND.equals(intent.getAction())) return;
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (device != null) manager.connectToDevice(device);
        }
    };
    private final Runnable discoveryCycle = new Runnable() {
        @Override public void run() {
            startNearbyDiscovery();
            handler.postDelayed(this, 60_000L);
        }
    };
    private final Runnable connector = new Runnable() {
        @Override public void run() {
            connectBondedPeers();
            handler.postDelayed(this, 15_000L);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(42, notification(0));
        manager = new BluetoothMeshManager(this, this);
        activeManager = manager;
        database = AppDatabase.getInstance(this);
        ContextCompat.registerReceiver(this, discoveryReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND),
                ContextCompat.RECEIVER_NOT_EXPORTED);
        advertiser = new BleMeshAdvertiser(this);
        manager.setMyNodeId(nodeId);
        ensureTransportReady();
        handler.post(connector);
        handler.post(discoveryCycle);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getStringExtra("node_id") != null) {
            nodeId = intent.getStringExtra("node_id").trim().toUpperCase();
            if (manager != null) manager.setMyNodeId(nodeId);
        }
        if (intent != null && intent.hasExtra("send_destination") && manager != null) {
            manager.sendNewMessage(intent.getStringExtra("send_destination"), intent.getStringExtra("send_body"));
        }
        if (intent != null && intent.hasExtra("connect_address") && manager != null
                && manager.getAdapter() != null) {
            manager.connectToDevice(manager.getAdapter().getRemoteDevice(intent.getStringExtra("connect_address")));
        }
        ensureTransportReady();
        return START_STICKY;
    }

    public static boolean sendMessage(android.content.Context context, String destination, String body) {
        BluetoothMeshManager current = activeManager;
        if (current != null) return current.sendNewMessage(destination, body);
        Intent intent = new Intent(context, MeshService.class)
                .putExtra("send_destination", destination).putExtra("send_body", body);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
        else context.startService(intent);
        return true;
    }

    public static void connectToAddress(android.content.Context context, String address) {
        Intent intent = new Intent(context, MeshService.class).putExtra("connect_address", address);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
        else context.startService(intent);
    }

    public static boolean sendFile(String destination, String fileName, byte[] contents) {
        BluetoothMeshManager current = activeManager;
        return current != null && current.sendFile(destination, fileName, contents);
    }
    private void connectBondedPeers() {
        ensureTransportReady();
        if (manager == null || Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            status("Bluetooth Connect permission is required for automatic peer connections.");
            return;
        }
        BluetoothAdapter adapter = manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) return;
        for (BluetoothDevice device : adapter.getBondedDevices()) {
            if (!manager.isConnected(device.getAddress())) manager.connectToDevice(device);
        }
        if (adapter.getBondedDevices().isEmpty()) status("No bonded peers. Pair devices in Android Bluetooth settings first.");
        updateNotification();
    }

    @SuppressWarnings("MissingPermission")
    private void startNearbyDiscovery() {
        if (manager == null || Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) return;
        if (Build.VERSION.SDK_INT < 31 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        BluetoothAdapter adapter = manager.getAdapter();
        if (adapter != null && adapter.isEnabled() && !adapter.isDiscovering()) {
            status(adapter.startDiscovery() ? "Scanning for nearby Bluetooth peers..." : "Bluetooth discovery could not start.");
        }
    }
    private void ensureTransportReady() {
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) return;
        BluetoothAdapter adapter = manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) { status("Bluetooth is disabled."); return; }
        manager.startAccepting();
        if (Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE)
                == PackageManager.PERMISSION_GRANTED) advertiser.start(nodeId);
    }
    private Notification notification(int count) {
        return new NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle("MANET mesh active").setContentText(count + " peer(s) connected")
                .setOngoing(true).setCategory(NotificationCompat.CATEGORY_SERVICE).build();
    }
    private void updateNotification() { /* connection callback updates this on the next cycle */ }
    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager.class).createNotificationChannel(
                new NotificationChannel(CHANNEL, "MANET mesh", NotificationManager.IMPORTANCE_LOW));
    }
    @Override public void onDestroy() { activeManager = null; handler.removeCallbacksAndMessages(null); unregisterReceiver(discoveryReceiver); if (advertiser != null) advertiser.stop(); if (manager != null) manager.stop(); super.onDestroy(); }
    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onLog(String message) { status(message); }
    @Override public void onConnectionsChanged(List<String> peers) { startForeground(42, notification(peers == null ? 0 : peers.size())); }
    @Override public void onMessageDelivered(ManetMessage message) {
        boolean sentByMe = message.getSource().equalsIgnoreCase(nodeId);
        String conversation = sentByMe ? message.getDestination() : message.getSource();
        database.messageDao().insert(new ChatMessageEntity(message.getId(), conversation, message.getData(),
                message.getSource(), System.currentTimeMillis(), MessageStatus.DELIVERED, sentByMe));
        broadcastMessageEvent(message, MessageStatus.DELIVERED);
    }
    @Override public void onMessageStatusChanged(ManetMessage message, MessageStatus status) {
        if (status == MessageStatus.SENDING) {
            database.messageDao().insert(new ChatMessageEntity(message.getId(), message.getDestination(), message.getData(),
                    message.getSource(), System.currentTimeMillis(), status, true));
        } else {
            database.messageDao().updateStatus(message.getId(), status);
        }
        broadcastMessageEvent(message, status);
    }
    @Override public void onMessageAcknowledged(String messageId) {
        database.messageDao().updateStatus(messageId, MessageStatus.DELIVERED);
        sendBroadcast(new Intent(ACTION_MESSAGE_EVENT).setPackage(getPackageName())
                .putExtra("message_id", messageId).putExtra("status", MessageStatus.DELIVERED.name()));
    }

    @Override public void onFileProgress(String transferId, int completed, int total, String fileName) {
        sendBroadcast(new Intent(ACTION_MESH_STATUS).setPackage(getPackageName())
                .putExtra("file_id", transferId).putExtra("file_completed", completed)
                .putExtra("file_total", total).putExtra("file_name", fileName));
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
