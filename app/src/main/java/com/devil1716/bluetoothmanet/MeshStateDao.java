package com.devil1716.bluetoothmanet;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface MeshStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) void upsertNeighbor(MeshNeighborEntity neighbor);
    @Query("SELECT * FROM mesh_neighbors ORDER BY lastSeen DESC") List<MeshNeighborEntity> neighbors();
    @Query("DELETE FROM mesh_neighbors WHERE lastSeen < :cutoff") void deleteExpiredNeighbors(long cutoff);

    /** Neighbours heard from recently, strongest signal first. */
    @Query("SELECT * FROM mesh_neighbors WHERE lastSeen >= :cutoff"
            + " ORDER BY connected DESC, rssi DESC, lastSeen DESC")
    List<MeshNeighborEntity> recentNeighbors(long cutoff);

    /**
     * Clears every connected flag. Nothing is connected while the radio is
     * down, and without this a peer stays "online" forever once seen.
     */
    @Query("UPDATE mesh_neighbors SET connected = 0") void markAllNeighborsOffline();

    @Query("UPDATE mesh_neighbors SET connected = 0 WHERE lastSeen < :cutoff")
    void markStaleNeighborsOffline(long cutoff);

    /**
     * Refreshes presence without touching the signal strength, which only the
     * scanner knows. Returns 0 when the neighbour is new.
     */
    @Query("UPDATE mesh_neighbors SET displayName = :displayName, hopCount = :hopCount,"
            + " lastSeen = :lastSeen, connected = :connected WHERE deviceId = :deviceId")
    int touchNeighbor(String deviceId, String displayName, int hopCount, long lastSeen, boolean connected);

    @Insert(onConflict = OnConflictStrategy.REPLACE) void upsertRoute(MeshRouteEntity route);
    @Query("SELECT * FROM mesh_routes WHERE destinationId = :destinationId LIMIT 1") MeshRouteEntity route(String destinationId);
    @Query("DELETE FROM mesh_routes WHERE lastUpdated < :cutoff") void deleteExpiredRoutes(long cutoff);

    @Insert(onConflict = OnConflictStrategy.IGNORE) long insertPacket(PacketHistoryEntity packet);
    @Query("SELECT EXISTS(SELECT 1 FROM packet_history WHERE packetId = :packetId AND expiresAt >= :now)") boolean hasLivePacket(String packetId, long now);
    @Query("DELETE FROM packet_history WHERE expiresAt < :cutoff") void deleteExpiredPackets(long cutoff);
}
