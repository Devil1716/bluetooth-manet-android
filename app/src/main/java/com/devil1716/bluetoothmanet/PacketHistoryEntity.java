package com.devil1716.bluetoothmanet;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "packet_history", indices = @Index("expiresAt"))
public class PacketHistoryEntity {
    @PrimaryKey @NonNull public String packetId;
    @NonNull public String packetType;
    public long seenAt;
    public long expiresAt;
    public PacketHistoryEntity(@NonNull String packetId, @NonNull String packetType, long seenAt, long expiresAt) {
        this.packetId = packetId; this.packetType = packetType; this.seenAt = seenAt; this.expiresAt = expiresAt;
    }
}
