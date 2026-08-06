package com.devil1716.bluetoothmanet;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "mesh_neighbors", indices = @Index("lastSeen"))
public class MeshNeighborEntity {
    @PrimaryKey @NonNull public String deviceId;
    public String displayName;
    public int rssi;
    public Integer batteryPercent;
    public Long latencyMs;
    public int hopCount;
    public long lastSeen;
    public boolean connected;
    public MeshNeighborEntity(@NonNull String deviceId, String displayName, int rssi, Integer batteryPercent,
                              Long latencyMs, int hopCount, long lastSeen, boolean connected) {
        this.deviceId = deviceId; this.displayName = displayName; this.rssi = rssi;
        this.batteryPercent = batteryPercent; this.latencyMs = latencyMs; this.hopCount = hopCount;
        this.lastSeen = lastSeen; this.connected = connected;
    }
}
