package com.devil1716.bluetoothmanet;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.ParcelUuid;
import androidx.core.content.ContextCompat;

public class BleMeshScanner {
    public interface Listener { void onMeshDeviceFound(BluetoothDevice device); }
    private final Context context;
    private final Listener listener;
    private BluetoothLeScanner scanner;
    private boolean scanning;
    private final ScanCallback callback = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult result) {
            if (result != null && result.getDevice() != null) listener.onMeshDeviceFound(result.getDevice());
        }
        @Override public void onScanFailed(int errorCode) { }
    };
    public BleMeshScanner(Context context, Listener listener) {
        this.context = context.getApplicationContext(); this.listener = listener;
    }
    public void start() {
        if (android.os.Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) return;
        if (android.os.Build.VERSION.SDK_INT < 31 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        if (scanning) return;
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) return;
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) return;
        ScanFilter filter = new ScanFilter.Builder().setServiceUuid(new ParcelUuid(BleMeshAdvertiser.SERVICE_UUID)).build();
        ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        scanner.startScan(java.util.Collections.singletonList(filter), settings, callback);
        scanning = true;
    }
    public void stop() {
        if (scanner != null && scanning && (android.os.Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(context,
                Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED)) scanner.stopScan(callback);
        scanning = false;
    }
}
