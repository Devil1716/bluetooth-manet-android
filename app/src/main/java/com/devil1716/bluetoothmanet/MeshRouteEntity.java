package com.devil1716.bluetoothmanet;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "mesh_routes", indices = @Index("lastUpdated"))
public class MeshRouteEntity {
    @PrimaryKey @NonNull public String destinationId;
    @NonNull public String nextHopId;
    public int hopCount;
    public double score;
    public long lastUpdated;
    public MeshRouteEntity(@NonNull String destinationId, @NonNull String nextHopId, int hopCount,
                           double score, long lastUpdated) {
        this.destinationId = destinationId; this.nextHopId = nextHopId; this.hopCount = hopCount;
        this.score = score; this.lastUpdated = lastUpdated;
    }
}
