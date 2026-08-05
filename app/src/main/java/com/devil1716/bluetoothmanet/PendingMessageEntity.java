package com.devil1716.bluetoothmanet;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "pending_messages")
public class PendingMessageEntity {
    @PrimaryKey @NonNull public String id;
    @NonNull public String destination;
    @NonNull public String wire;
    public long createdAt;
    public PendingMessageEntity(@NonNull String id, @NonNull String destination,
                                @NonNull String wire, long createdAt) {
        this.id = id; this.destination = destination; this.wire = wire; this.createdAt = createdAt;
    }
}
