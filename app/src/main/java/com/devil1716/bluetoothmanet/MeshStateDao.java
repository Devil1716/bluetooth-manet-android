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

    @Insert(onConflict = OnConflictStrategy.REPLACE) void upsertRoute(MeshRouteEntity route);
    @Query("SELECT * FROM mesh_routes WHERE destinationId = :destinationId LIMIT 1") MeshRouteEntity route(String destinationId);
    @Query("DELETE FROM mesh_routes WHERE lastUpdated < :cutoff") void deleteExpiredRoutes(long cutoff);

    @Insert(onConflict = OnConflictStrategy.IGNORE) long insertPacket(PacketHistoryEntity packet);
    @Query("SELECT EXISTS(SELECT 1 FROM packet_history WHERE packetId = :packetId AND expiresAt >= :now)") boolean hasLivePacket(String packetId, long now);
    @Query("DELETE FROM packet_history WHERE expiresAt < :cutoff") void deleteExpiredPackets(long cutoff);
}
