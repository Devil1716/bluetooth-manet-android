package com.devil1716.bluetoothmanet;

import androidx.annotation.NonNull;

public class PeerDevice {
    private final String name;
    private final String address;

    public PeerDevice(String name, String address) {
        this.name = name == null || name.trim().isEmpty() ? "Unknown device" : name;
        this.address = address;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    @NonNull
    @Override
    public String toString() {
        return name + "\n" + address;
    }
}
