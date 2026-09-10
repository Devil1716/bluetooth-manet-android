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
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.provider.OpenableColumns;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.devil1716.bluetoothmanet.update.AppUpdater;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private interface AdapterAction {
        void run(BluetoothAdapter adapter);
    }

    private final Map<String, PeerDevice> discoveredPeers = new LinkedHashMap<>();

    private BluetoothAdapter bluetoothAdapter;
    private ArrayAdapter<PeerDevice> peerAdapter;
    private TextView logView;
    private TextView connectionView;
    private EditText nodeIdInput;
    private EditText destinationInput;
    private EditText messageInput;
    private TextView fileProgressView;
    private ChatAdapter chatAdapter;
    private RecyclerView chatRecyclerView;
    private TextView meshStatusView;
    private LinearLayout setupPanel;
    private Button setupToggle;
    private String lastReceivedFilePath;
    private MessageDao messageDao;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private boolean pendingDiscovery;
    private boolean pendingListening;

    private final ActivityResultLauncher<String> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) return;
                databaseExecutor.execute(() -> {
                    try (InputStream input = getContentResolver().openInputStream(uri)) {
                        String name = queryDisplayName(uri);
                        byte[] bytes = MeshFileStore.readLimited(input, MeshFileStore.MAX_SEND_BYTES);
                        syncNodeId();
                        boolean sent = MeshService.sendFile(MainActivity.this,
                                destinationInput.getText().toString(), name, bytes);
                        String result = sent
                                ? "Signed file transfer queued: " + name
                                : "File transfer failed. Start mesh and check the event log.";
                        runOnUiThread(() -> fileProgressView.setText(result));
                    } catch (Exception e) {
                        runOnUiThread(() -> fileProgressView.setText("File error: " + e.getMessage()));
                    }
                });
            });

    private final ActivityResultLauncher<Intent> enableBluetoothLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                appendLog("Bluetooth enable flow finished.");
                BluetoothAdapter adapter = bluetoothAdapter;
                if (adapter != null && adapter.isEnabled()) {
                    preloadBondedDevices();
                    resumePendingActions();
                    startMeshService();
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
                    startMeshService();
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

    private final BroadcastReceiver meshEventReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!MeshService.ACTION_MESSAGE_EVENT.equals(intent.getAction())) return;
            String id = intent.getStringExtra("message_id");
            MessageStatus status = MessageStatus.valueOf(intent.getStringExtra("status"));
            if (status == MessageStatus.DELIVERED && intent.hasExtra("body")) {
                loadMessages();
            } else {
                databaseExecutor.execute(() -> { messageDao.updateStatus(id, status); loadMessages(); });
            }
        }
    };

    private final BroadcastReceiver meshStatusReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String message = intent.getStringExtra("message");
            if (message != null) appendLog("Mesh: " + message);
            if (intent.hasExtra("peers")) {
                ArrayList<String> peers = intent.getStringArrayListExtra("peers");
                connectionView.setText(peers == null || peers.isEmpty()
                        ? getString(R.string.no_connections)
                        : joinPeers(peers));
                if (meshStatusView != null) {
                    meshStatusView.setText(peers == null || peers.isEmpty()
                            ? getString(R.string.mesh_idle)
                            : peers.size() + " signed link(s)");
                }
            }
            if (intent.hasExtra("file_path")) {
                lastReceivedFilePath = intent.getStringExtra("file_path");
                String fileName = intent.getStringExtra("file_name");
                fileProgressView.setText("Verified file saved. Tap to open: " + fileName);
                Toast.makeText(context, "File received: " + fileName, Toast.LENGTH_LONG).show();
                new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                        .setTitle("File received")
                        .setMessage(fileName + " was verified and saved. Open it now?")
                        .setPositiveButton("Open", (dialog, which) -> openReceivedFile(lastReceivedFilePath))
                        .setNegativeButton("Later", null)
                        .show();
                loadMessages();
            } else if (intent.hasExtra("file_total")) {
                fileProgressView.setText("File " + intent.getStringExtra("file_name") + ": "
                        + intent.getIntExtra("file_completed", 0) + "/" + intent.getIntExtra("file_total", 0));
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not supported on this device.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        bindViews();
        messageDao = AppDatabase.getInstance(this).messageDao();
        loadMessages();
        appendLog("Mesh v" + BuildConfig.VERSION_NAME + " ready. Messages and files are ECDSA-signed.");
        ContextCompat.registerReceiver(
                this,
                discoveryReceiver,
                new IntentFilter(BluetoothDevice.ACTION_FOUND),
                ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(this, meshEventReceiver,
                new IntentFilter(MeshService.ACTION_MESSAGE_EVENT), ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(this, meshStatusReceiver,
                new IntentFilter(MeshService.ACTION_MESH_STATUS), ContextCompat.RECEIVER_NOT_EXPORTED);
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
        connectionView = findViewById(R.id.connectionView);
        meshStatusView = findViewById(R.id.meshStatusView);
        nodeIdInput = findViewById(R.id.nodeIdInput);
        destinationInput = findViewById(R.id.destinationInput);
        messageInput = findViewById(R.id.messageInput);
        fileProgressView = findViewById(R.id.fileProgressView);
        setupPanel = findViewById(R.id.setupPanel);
        setupToggle = findViewById(R.id.setupToggle);
        logView.setMovementMethod(new ScrollingMovementMethod());
        chatRecyclerView = findViewById(R.id.chatRecyclerView);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        chatRecyclerView.setLayoutManager(layoutManager);
        chatAdapter = new ChatAdapter(this::openReceivedFile);
        chatRecyclerView.setAdapter(chatAdapter);
        nodeIdInput.setText(getSharedPreferences("mesh", MODE_PRIVATE).getString("node_id", "A"));

        setupToggle.setOnClickListener(v -> {
            boolean show = setupPanel.getVisibility() != android.view.View.VISIBLE;
            setupPanel.setVisibility(show ? android.view.View.VISIBLE : android.view.View.GONE);
            setupToggle.setText(show ? R.string.hide_setup : R.string.show_setup);
        });
        fileProgressView.setOnClickListener(v -> openReceivedFile());

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
            MeshService.connectToAddress(this, peer.getAddress());
            appendLog("Connecting to " + peer.getAddress() + " over BLE and RFCOMM...");
        });

        Button enableButton = findViewById(R.id.enableBluetoothButton);
        Button discoverableButton = findViewById(R.id.makeDiscoverableButton);
        Button discoverButton = findViewById(R.id.discoverPeersButton);
        Button listenButton = findViewById(R.id.startListeningButton);
        Button updateButton = findViewById(R.id.updateFromGithubButton);
        Button sendButton = findViewById(R.id.sendButton);
        Button sendFileButton = findViewById(R.id.sendFileButton);

        enableButton.setOnClickListener(v -> ensureBluetoothEnabled());
        discoverableButton.setOnClickListener(v -> requestDiscoverableMode());
        discoverButton.setOnClickListener(v -> startDiscovery());
        listenButton.setOnClickListener(v -> startListening());
        updateButton.setOnClickListener(v -> startAppUpdate());
        sendButton.setOnClickListener(v -> {
            syncNodeId();
            boolean sent = MeshService.sendMessage(this, destinationInput.getText().toString(), messageInput.getText().toString());
            if (sent) {
                Toast.makeText(this, "Message sent into the mesh.", Toast.LENGTH_SHORT).show();
                messageInput.setText("");
            } else {
                Toast.makeText(this, "Message was not sent. Check the event log.", Toast.LENGTH_SHORT).show();
            }
        });
        sendFileButton.setOnClickListener(v -> {
            if (destinationInput.getText().toString().trim().isEmpty()) {
                Toast.makeText(this, "Enter the destination node ID before sending a file.", Toast.LENGTH_SHORT).show();
                return;
            }
            syncNodeId();
            try {
                filePickerLauncher.launch("*/*");
            } catch (android.content.ActivityNotFoundException ignored) {
                Toast.makeText(this, "This phone has no file picker.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void requestNeededPermissions() {
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            maybeAddPermission(permissions, Manifest.permission.BLUETOOTH_CONNECT);
            maybeAddPermission(permissions, Manifest.permission.BLUETOOTH_SCAN);
            maybeAddPermission(permissions, Manifest.permission.BLUETOOTH_ADVERTISE);
            maybeAddPermission(permissions, Manifest.permission.ACCESS_FINE_LOCATION);
            maybeAddPermission(permissions, Manifest.permission.ACCESS_COARSE_LOCATION);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                maybeAddPermission(permissions, Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            maybeAddPermission(permissions, Manifest.permission.ACCESS_FINE_LOCATION);
            maybeAddPermission(permissions, Manifest.permission.ACCESS_COARSE_LOCATION);
        }

        if (!permissions.isEmpty()) {
            permissionLauncher.launch(permissions.toArray(new String[0]));
        } else {
            startMeshService();
        }
    }

    private void maybeAddPermission(List<String> permissions, String permission) {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(permission);
        }
    }

    private void ensureBluetoothEnabled() {
        BluetoothAdapter adapter = bluetoothAdapter;
        if (adapter != null && !adapter.isEnabled()) {
            enableBluetoothLauncher.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
        } else {
            appendLog("Bluetooth is already enabled.");
        }
    }

    private void ensureBluetoothEnabledForAction() {
        BluetoothAdapter adapter = bluetoothAdapter;
        if (adapter == null) {
            return;
        }
        if (!adapter.isEnabled()) {
            appendLog("Bluetooth is off. Requesting enable...");
            enableBluetoothLauncher.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
        }
    }

    private void requestDiscoverableMode() {
        runWithBluetoothPreconditions(
                "discoverable mode",
                false,
                true,
                adapter -> {
                    Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                    discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
                    discoverableLauncher.launch(discoverableIntent);
                });
    }

    private void startAppUpdate() {
        appendLog("Checking GitHub for the latest APK...");
        AppUpdater.checkAndInstall(this, BuildConfig.VERSION_NAME, message ->
                runOnUiThread(() -> {
                    appendLog(message);
                    if (!message.startsWith("Downloading update")) {
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                    }
                }));
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppUpdater.installPendingIfReady(this, state ->
                runOnUiThread(() -> appendLog(state.getMessage())));
    }

    @SuppressLint("MissingPermission")
    private void startDiscovery() {
        runWithBluetoothPreconditions("discovery", true, false, adapter -> {
            discoveredPeers.clear();
            preloadBondedDevices();
            if (adapter.isDiscovering()) {
                adapter.cancelDiscovery();
            }
            boolean started = adapter.startDiscovery();
            if (started) {
                appendLog("Discovery started...");
            } else {
                appendLog("Failed to start discovery. State="
                        + adapter.getState()
                        + ", scanPermission="
                        + hasScanPermission()
                        + ", connectPermission="
                        + hasConnectPermission());
            }
        });
    }

    private void runWithBluetoothPreconditions(
            String operationLabel,
            boolean requireScanPermission,
            boolean requireAdvertisePermission,
            AdapterAction action) {
        BluetoothAdapter adapter = bluetoothAdapter;
        if (adapter == null) {
            appendLog("Bluetooth adapter unavailable.");
            return;
        }
        if (!adapter.isEnabled()) {
            appendLog("Bluetooth is OFF. Enable it before " + operationLabel + ".");
            ensureBluetoothEnabled();
            return;
        }
        if (!hasConnectPermission()) {
            appendLog("Bluetooth connect permission missing. Requested permissions: " + getPermissionStateSummary());
            requestNeededPermissions();
            return;
        }
        if (requireScanPermission && !hasScanPermission()) {
            appendLog("Bluetooth scan permission missing. Requested permissions: " + getPermissionStateSummary());
            requestNeededPermissions();
            return;
        }
        if (requireAdvertisePermission && !hasAdvertisePermission()) {
            appendLog("Bluetooth advertise permission missing. Requested permissions: " + getPermissionStateSummary());
            requestNeededPermissions();
            return;
        }
        action.run(adapter);
    }

    private void startListening() {
        runWithBluetoothPreconditions("listening", false, false, adapter -> {
            syncNodeId();
            startMeshService();
            appendLog("Mesh started: BLE dual-role (no pairing) plus RFCOMM for classic/Windows peers.");
            warnIfLocationOff();
        });
    }

    @SuppressLint("MissingPermission")
    private void preloadBondedDevices() {
        BluetoothAdapter adapter = bluetoothAdapter;
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

    private boolean hasAdvertisePermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private String getPermissionStateSummary() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return "BLUETOOTH_SCAN="
                    + (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED)
                    + ", BLUETOOTH_CONNECT="
                    + (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
                    + ", BLUETOOTH_ADVERTISE="
                    + (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED);
        }
        return "ACCESS_FINE_LOCATION="
                + (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED);
    }

    private void appendLog(String message) {
        runOnUiThread(() -> logView.append(message + "\n"));
    }

    private void loadMessages() {
        databaseExecutor.execute(() -> {
            List<ChatMessageEntity> messages = messageDao.getAll();
            runOnUiThread(() -> {
                chatAdapter.setMessages(messages);
                if (chatRecyclerView != null && chatAdapter.getItemCount() > 0) {
                    chatRecyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);
                }
            });
        });
    }

    private void startMeshService() {
        getSharedPreferences("mesh", MODE_PRIVATE).edit()
                .putString("node_id", nodeIdInput.getText().toString().trim()).apply();
        Intent serviceIntent = new Intent(this, MeshService.class);
        serviceIntent.putExtra("node_id", nodeIdInput.getText().toString().trim());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void syncNodeId() {
        startMeshService();
    }

    private void warnIfLocationOff() {
        android.location.LocationManager locationManager =
                (android.location.LocationManager) getSystemService(LOCATION_SERVICE);
        if (locationManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                && !locationManager.isLocationEnabled()) {
            appendLog("Turn on Location in Android settings. Many phones will not BLE-scan while Location is off.");
        }
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

    private String queryDisplayName(Uri uri) {
        String fallback = uri.getLastPathSegment() == null ? "shared-file" : uri.getLastPathSegment();
        if (fallback.contains("/")) fallback = fallback.substring(fallback.lastIndexOf('/') + 1);
        try (android.database.Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && !name.trim().isEmpty()) return name;
            }
        } catch (Exception ignored) { }
        return fallback;
    }

    private void openReceivedFile() {
        openReceivedFile(lastReceivedFilePath);
    }

    private void openReceivedFile(String path) {
        if (path == null || path.trim().isEmpty()) return;
        File file = new File(path);
        if (!file.exists()) {
            Toast.makeText(this, "File is no longer on disk.", Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", file);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        String mime = android.webkit.MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(android.webkit.MimeTypeMap.getFileExtensionFromUrl(file.getName()));
        intent.setDataAndType(uri, mime == null ? "*/*" : mime);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(intent, "Open file"));
        } catch (Exception e) {
            Toast.makeText(this, "No app can open this file.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(discoveryReceiver);
        unregisterReceiver(meshEventReceiver);
        unregisterReceiver(meshStatusReceiver);
        databaseExecutor.shutdown();
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
