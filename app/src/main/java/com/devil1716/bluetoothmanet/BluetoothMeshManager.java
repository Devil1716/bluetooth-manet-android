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

import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BluetoothMeshManager {
    public interface Listener {
        void onLog(String message);
        void onConnectionsChanged(List<String> peers);
        void onMessageDelivered(ManetMessage message);
    }

    private static final String SERVICE_NAME = "MANET";
    private static final UUID SERVICE_UUID = UUID.fromString("12345678-1234-1234-1234-123456789abc");

    private final Context appContext;
    private final Listener listener;
    private final BluetoothAdapter adapter;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ConcurrentHashMap<String, BluetoothSocket> sockets = new ConcurrentHashMap<>();
    private final Set<String> seenMessages = ConcurrentHashMap.newKeySet();

    private volatile boolean accepting;
    private volatile BluetoothServerSocket serverSocket;
    private volatile String myNodeId = "NODE";

    public BluetoothMeshManager(Context context, Listener listener) {
        this.appContext = context.getApplicationContext();
        this.listener = listener;
        this.adapter = BluetoothAdapter.getDefaultAdapter();
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
                serverSocket = adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID);
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
        publishConnections();
        executor.shutdownNow();
    }

    @SuppressLint("MissingPermission")
    public void connectToDevice(BluetoothDevice device) {
        if (device == null || !hasConnectPermission()) {
            return;
        }
        executor.execute(() -> {
            BluetoothSocket socket = null;
            try {
                if (adapter.isDiscovering() && hasScanPermission()) {
                    adapter.cancelDiscovery();
                }
                socket = device.createRfcommSocketToServiceRecord(SERVICE_UUID);
                listener.onLog("Connecting to " + safeDeviceLabel(device) + "...");
                socket.connect();
                registerSocket(socket, "Connected");
            } catch (IOException e) {
                listener.onLog("Connection failed for " + safeDeviceLabel(device) + ": " + e.getMessage());
                closeSocket(socket);
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
        seenMessages.add(message.getId());
        if (trimmedDestination.equals(myNodeId)) {
            listener.onMessageDelivered(message);
            return true;
        }
        if (sockets.isEmpty()) {
            listener.onLog("No active peers. Connect to a device before sending.");
            return false;
        }
        return forwardMessage(message, null) > 0;
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
            ManetMessage message = ManetMessage.fromWire(payload);
            if (!seenMessages.add(message.getId())) {
                return;
            }

            listener.onLog("RX " + message.getSource() + " -> " + message.getDestination()
                    + " (ttl=" + message.getTtl() + ")");

            if (message.getDestination().equalsIgnoreCase(myNodeId)) {
                listener.onMessageDelivered(message);
                return;
            }

            if (message.getTtl() <= 1) {
                listener.onLog("Dropped " + message.getId() + " because TTL expired.");
                return;
            }

            forwardMessage(message.decrementedTtl(), fromAddress);
        } catch (IllegalArgumentException e) {
            listener.onLog("Ignored malformed payload: " + payload);
        }
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
