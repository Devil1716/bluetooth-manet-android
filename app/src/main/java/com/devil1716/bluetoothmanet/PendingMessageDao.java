package com.devil1716.bluetoothmanet;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface PendingMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) void insert(PendingMessageEntity message);
    @Query("SELECT * FROM pending_messages WHERE destination = :destination AND createdAt >= :minTime")
    List<PendingMessageEntity> forDestination(String destination, long minTime);
    @Query("SELECT * FROM pending_messages WHERE createdAt >= :minTime")
    List<PendingMessageEntity> all(long minTime);
    @Query("DELETE FROM pending_messages WHERE id = :id") void delete(String id);
    @Query("DELETE FROM pending_messages WHERE createdAt < :minTime") void deleteExpired(long minTime);
}
