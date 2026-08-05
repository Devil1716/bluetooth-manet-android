package com.devil1716.bluetoothmanet;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.ParcelUuid;
import androidx.core.content.ContextCompat;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class BleMeshAdvertiser {
    public static final UUID SERVICE_UUID = UUID.fromString("12345678-1234-1234-1234-123456789abd");
    private final Context context;
    private BluetoothLeAdvertiser advertiser;
    public BleMeshAdvertiser(Context context) { this.context = context.getApplicationContext(); }
    public void start(String nodeId) {
        if (android.os.Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) return;
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) return;
        advertiser = adapter.getBluetoothLeAdvertiser();
        if (advertiser == null) return;
        AdvertiseSettings settings = new AdvertiseSettings.Builder().setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM).setConnectable(false).build();
        AdvertiseData data = new AdvertiseData.Builder().addServiceUuid(new ParcelUuid(SERVICE_UUID))
                .addServiceData(new ParcelUuid(SERVICE_UUID), nodeId.getBytes(StandardCharsets.UTF_8)).setIncludeDeviceName(false).build();
        advertiser.startAdvertising(settings, data, new AdvertiseCallback() {});
    }
    public void stop() { if (advertiser != null) advertiser.stopAdvertising(new AdvertiseCallback() {}); }
}
