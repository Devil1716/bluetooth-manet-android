package com.devil1716.bluetoothmanet;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements BluetoothMeshManager.Listener {
    private final Map<String, PeerDevice> discoveredPeers = new LinkedHashMap<>();

    private BluetoothMeshManager meshManager;
    private ArrayAdapter<PeerDevice> peerAdapter;
    private TextView logView;
    private TextView connectionView;
    private EditText nodeIdInput;
    private EditText destinationInput;
    private EditText messageInput;

    private final ActivityResultLauncher<Intent> enableBluetoothLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result ->
                    appendLog("Bluetooth enable flow finished."));

    private final ActivityResultLauncher<Intent> discoverableLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result ->
                    appendLog("Discoverable request finished."));

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean allGranted = true;
                for (Boolean granted : result.values()) {
                    if (Boolean.FALSE.equals(granted)) {
                        allGranted = false;
                        break;
                    }
                }

                appendLog(allGranted ? "Permissions granted." : "Some Bluetooth permissions were denied.");
            });

    private final BroadcastReceiver discoveryReceiver = new BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device == null || device.getAddress() == null) {
                    return;
                }
                PeerDevice peer = new PeerDevice(device.getName(), device.getAddress());
                discoveredPeers.put(peer.getAddress(), peer);
                refreshPeerList();
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                appendLog(String.format(Locale.US, "Discovery finished. %d peer(s) listed.", discoveredPeers.size()));
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        meshManager = new BluetoothMeshManager(this, this);
        if (!meshManager.isBluetoothSupported()) {
            Toast.makeText(this, "Bluetooth is not supported on this device.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        bindViews();
        ContextCompat.registerReceiver(
                this,
                discoveryReceiver,
                new IntentFilter(BluetoothDevice.ACTION_FOUND),
                ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(
                this,
                discoveryReceiver,
                new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED),
                ContextCompat.RECEIVER_NOT_EXPORTED);

        requestNeededPermissions();
        preloadBondedDevices();
    }

    private void bindViews() {
        logView = findViewById(R.id.logView);
        connectionView = findViewById(R.id.connectionView);
        nodeIdInput = findViewById(R.id.nodeIdInput);
        destinationInput = findViewById(R.id.destinationInput);
        messageInput = findViewById(R.id.messageInput);
        logView.setMovementMethod(new ScrollingMovementMethod());

        ListView peerListView = findViewById(R.id.peerListView);
        peerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        peerListView.setAdapter(peerAdapter);
        peerListView.setOnItemClickListener((parent, view, position, id) -> {
            PeerDevice peer = peerAdapter.getItem(position);
            if (peer == null) {
                return;
            }
            BluetoothAdapter adapter = meshManager.getAdapter();
            BluetoothDevice device = adapter.getRemoteDevice(peer.getAddress());
            meshManager.connectToDevice(device);
        });

        Button enableButton = findViewById(R.id.enableBluetoothButton);
        Button discoverableButton = findViewById(R.id.makeDiscoverableButton);
        Button discoverButton = findViewById(R.id.discoverPeersButton);
        Button listenButton = findViewById(R.id.startListeningButton);
        Button sendButton = findViewById(R.id.sendButton);

        enableButton.setOnClickListener(v -> ensureBluetoothEnabled());
        discoverableButton.setOnClickListener(v -> requestDiscoverableMode());
        discoverButton.setOnClickListener(v -> startDiscovery());
        listenButton.setOnClickListener(v -> meshManager.startAccepting());
        sendButton.setOnClickListener(v -> {
            meshManager.setMyNodeId(nodeIdInput.getText().toString());
            meshManager.sendNewMessage(destinationInput.getText().toString(), messageInput.getText().toString());
        });
    }

    private void requestNeededPermissions() {
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            maybeAddPermission(permissions, Manifest.permission.BLUETOOTH_CONNECT);
            maybeAddPermission(permissions, Manifest.permission.BLUETOOTH_SCAN);
            maybeAddPermission(permissions, Manifest.permission.BLUETOOTH_ADVERTISE);
        } else {
            maybeAddPermission(permissions, Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (!permissions.isEmpty()) {
            permissionLauncher.launch(permissions.toArray(new String[0]));
        }
    }

    private void maybeAddPermission(List<String> permissions, String permission) {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(permission);
        }
    }

    private void ensureBluetoothEnabled() {
        BluetoothAdapter adapter = meshManager.getAdapter();
        if (adapter != null && !adapter.isEnabled()) {
            enableBluetoothLauncher.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
        } else {
            appendLog("Bluetooth is already enabled.");
        }
    }

    private void requestDiscoverableMode() {
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
        discoverableLauncher.launch(discoverableIntent);
    }

    @SuppressLint("MissingPermission")
    private void startDiscovery() {
        BluetoothAdapter adapter = meshManager.getAdapter();
        if (adapter == null) {
            return;
        }
        if (!hasScanPermission()) {
            appendLog("Bluetooth scan permission missing.");
            requestNeededPermissions();
            return;
        }
        discoveredPeers.clear();
        preloadBondedDevices();
        if (adapter.isDiscovering()) {
            adapter.cancelDiscovery();
        }
        boolean started = adapter.startDiscovery();
        appendLog(started ? "Discovery started..." : "Failed to start discovery.");
    }

    @SuppressLint("MissingPermission")
    private void preloadBondedDevices() {
        BluetoothAdapter adapter = meshManager.getAdapter();
        if (adapter == null || !hasConnectPermission()) {
            return;
        }
        Set<BluetoothDevice> bondedDevices = adapter.getBondedDevices();
        for (BluetoothDevice device : bondedDevices) {
            discoveredPeers.put(device.getAddress(), new PeerDevice(device.getName(), device.getAddress()));
        }
        refreshPeerList();
    }

    private void refreshPeerList() {
        runOnUiThread(() -> {
            peerAdapter.clear();
            peerAdapter.addAll(discoveredPeers.values());
            peerAdapter.notifyDataSetChanged();
        });
    }

    private boolean hasConnectPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasScanPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void appendLog(String message) {
        runOnUiThread(() -> logView.append(message + "\n"));
    }

    @Override
    public void onLog(String message) {
        appendLog(message);
    }

    @Override
    public void onConnectionsChanged(List<String> peers) {
        runOnUiThread(() -> connectionView.setText(
                peers.isEmpty() ? getString(R.string.no_connections) : joinPeers(peers)));
    }

    @Override
    public void onMessageDelivered(ManetMessage message) {
        appendLog("Delivered to " + message.getDestination() + ": " + message.getData());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(discoveryReceiver);
        meshManager.stop();
    }

    private String joinPeers(List<String> peers) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < peers.size(); i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(peers.get(i));
        }
        return builder.toString();
    }
}
