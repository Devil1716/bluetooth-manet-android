package com.devil1716.bluetoothmanet;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
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
    private static volatile BluetoothMeshManager activeManager;
    private final Handler handler = new Handler();
    private BluetoothMeshManager manager;
    private BleMeshAdvertiser advertiser;
    private AppDatabase database;
    private String nodeId = "NODE";
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
        advertiser = new BleMeshAdvertiser(this);
        manager.setMyNodeId(nodeId);
        manager.startAccepting();
        advertiser.start(nodeId);
        handler.post(connector);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getStringExtra("node_id") != null) {
            nodeId = intent.getStringExtra("node_id").trim().toUpperCase();
            if (manager != null) manager.setMyNodeId(nodeId);
        }
        if (intent != null && intent.hasExtra("send_destination") && manager != null) {
            manager.sendNewMessage(intent.getStringExtra("send_destination"), intent.getStringExtra("send_body"));
        }
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
    private void connectBondedPeers() {
        if (manager == null || Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return;
        BluetoothAdapter adapter = manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) return;
        for (BluetoothDevice device : adapter.getBondedDevices()) {
            if (!manager.isConnected(device.getAddress())) manager.connectToDevice(device);
        }
        updateNotification();
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
    @Override public void onDestroy() { activeManager = null; handler.removeCallbacksAndMessages(null); if (advertiser != null) advertiser.stop(); if (manager != null) manager.stop(); super.onDestroy(); }
    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onLog(String message) { }
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

    private void broadcastMessageEvent(ManetMessage message, MessageStatus status) {
        sendBroadcast(new Intent(ACTION_MESSAGE_EVENT).setPackage(getPackageName())
                .putExtra("message_id", message.getId()).putExtra("source", message.getSource())
                .putExtra("destination", message.getDestination()).putExtra("body", message.getData())
                .putExtra("status", status.name()));
    }
}
