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
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements BluetoothMeshManager.Listener {
    private static final String LATEST_RELEASE_API =
            "https://api.github.com/repos/Devil1716/bluetooth-manet-android/releases/latest";
    private static final String LATEST_RELEASE_PAGE =
            "https://github.com/Devil1716/bluetooth-manet-android/releases/latest";

    private final Map<String, PeerDevice> discoveredPeers = new LinkedHashMap<>();

    private BluetoothMeshManager meshManager;
    private ArrayAdapter<PeerDevice> peerAdapter;
    private TextView logView;
    private TextView inboxView;
    private TextView connectionView;
    private EditText nodeIdInput;
    private EditText destinationInput;
    private EditText messageInput;
    private boolean pendingDiscovery;
    private boolean pendingListening;

    private final ActivityResultLauncher<Intent> enableBluetoothLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                appendLog("Bluetooth enable flow finished.");
                BluetoothAdapter adapter = meshManager.getAdapter();
                if (adapter != null && adapter.isEnabled()) {
                    preloadBondedDevices();
                    resumePendingActions();
                }
            });

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
                if (allGranted) {
                    preloadBondedDevices();
                    resumePendingActions();
                }
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
            } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                appendLog("Discovery started...");
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
                new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED),
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
        inboxView = findViewById(R.id.inboxView);
        connectionView = findViewById(R.id.connectionView);
        nodeIdInput = findViewById(R.id.nodeIdInput);
        destinationInput = findViewById(R.id.destinationInput);
        messageInput = findViewById(R.id.messageInput);
        logView.setMovementMethod(new ScrollingMovementMethod());
        inboxView.setMovementMethod(new ScrollingMovementMethod());
        syncNodeId();

        Spinner peerSpinner = findViewById(R.id.peerSpinner);
        peerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>());
        peerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        peerSpinner.setAdapter(peerAdapter);

        Button connectButton = findViewById(R.id.connectSelectedButton);
        connectButton.setOnClickListener(v -> {
            PeerDevice peer = (PeerDevice) peerSpinner.getSelectedItem();
            if (peer == null) {
                Toast.makeText(this, "No peer selected.", Toast.LENGTH_SHORT).show();
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
        Button updateButton = findViewById(R.id.updateFromGithubButton);
        Button sendButton = findViewById(R.id.sendButton);

        enableButton.setOnClickListener(v -> ensureBluetoothEnabled());
        discoverableButton.setOnClickListener(v -> requestDiscoverableMode());
        discoverButton.setOnClickListener(v -> startDiscovery());
        listenButton.setOnClickListener(v -> startListening());
        updateButton.setOnClickListener(v -> openGithubUpdate());
        sendButton.setOnClickListener(v -> {
            syncNodeId();
            boolean sent = meshManager.sendNewMessage(destinationInput.getText().toString(), messageInput.getText().toString());
            if (sent) {
                Toast.makeText(this, "Message sent into the mesh.", Toast.LENGTH_SHORT).show();
                messageInput.setText("");
            } else {
                Toast.makeText(this, "Message was not sent. Check the event log.", Toast.LENGTH_SHORT).show();
            }
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

    private void ensureBluetoothEnabledForAction() {
        BluetoothAdapter adapter = meshManager.getAdapter();
        if (adapter == null) {
            return;
        }
        if (!adapter.isEnabled()) {
            appendLog("Bluetooth is off. Requesting enable...");
            enableBluetoothLauncher.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
        }
    }

    private void requestDiscoverableMode() {
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
        discoverableLauncher.launch(discoverableIntent);
    }

    private void openGithubUpdate() {
        appendLog("Checking GitHub for the latest APK...");
        new Thread(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(LATEST_RELEASE_API).openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);

                int responseCode = connection.getResponseCode();
                if (responseCode < 200 || responseCode >= 300) {
                    throw new IllegalStateException("GitHub API returned " + responseCode);
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder payload = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    payload.append(line);
                }
                reader.close();
                connection.disconnect();

                JSONObject release = new JSONObject(payload.toString());
                String tagName = release.optString("tag_name", "latest");
                JSONArray assets = release.optJSONArray("assets");
                String apkUrl = null;

                if (assets != null) {
                    for (int index = 0; index < assets.length(); index++) {
                        JSONObject asset = assets.getJSONObject(index);
                        if ("app-debug.apk".equals(asset.optString("name"))) {
                            apkUrl = asset.optString("browser_download_url");
                            break;
                        }
                    }
                }

                final String resolvedUrl = apkUrl != null && !apkUrl.isEmpty() ? apkUrl : LATEST_RELEASE_PAGE;
                runOnUiThread(() -> {
                    appendLog("Opening GitHub release " + tagName + "...");
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(resolvedUrl)));
                    Toast.makeText(this, "Opening the latest release in your browser.", Toast.LENGTH_LONG).show();
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    appendLog("Update check failed. Opening releases page instead.");
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(LATEST_RELEASE_PAGE)));
                    Toast.makeText(this, "Could not resolve the APK directly. Opening releases page.", Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    @SuppressLint("MissingPermission")
    private void startDiscovery() {
        BluetoothAdapter adapter = meshManager.getAdapter();
        if (adapter == null) {
            return;
        }
        syncNodeId();
        if (!adapter.isEnabled()) {
            pendingDiscovery = true;
            ensureBluetoothEnabledForAction();
            return;
        }
        if (!hasScanPermission()) {
            appendLog("Bluetooth scan permission missing.");
            pendingDiscovery = true;
            requestNeededPermissions();
            return;
        }
        discoveredPeers.clear();
        preloadBondedDevices();
        if (adapter.isDiscovering()) {
            adapter.cancelDiscovery();
        }
        boolean started = adapter.startDiscovery();
        appendLog(started ? "Discovery request sent." : "Failed to start discovery. Check Bluetooth state and permissions.");
    }

    private void startListening() {
        BluetoothAdapter adapter = meshManager.getAdapter();
        if (adapter == null) {
            return;
        }
        syncNodeId();
        if (!adapter.isEnabled()) {
            pendingListening = true;
            ensureBluetoothEnabledForAction();
            return;
        }
        if (!hasConnectPermission()) {
            appendLog("Bluetooth connect permission missing.");
            pendingListening = true;
            requestNeededPermissions();
            return;
        }
        meshManager.startAccepting();
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

    private void syncNodeId() {
        meshManager.setMyNodeId(nodeIdInput.getText().toString());
    }

    private void resumePendingActions() {
        if (pendingListening && hasConnectPermission()) {
            pendingListening = false;
            startListening();
        }
        if (pendingDiscovery && hasScanPermission()) {
            pendingDiscovery = false;
            startDiscovery();
        }
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
        runOnUiThread(() -> {
            if (getString(R.string.no_messages_yet).contentEquals(inboxView.getText())) {
                inboxView.setText("");
            }
            inboxView.append(message.getSource() + " -> " + message.getDestination() + ": " + message.getData() + "\n");
        });
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
