package com.devil1716.bluetoothmanet;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY conversationId ASC, timestamp ASC")
    List<ChatMessageEntity> getAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(ChatMessageEntity message);

    @Query("UPDATE messages SET status = :status WHERE id = :id")
    void updateStatus(String id, MessageStatus status);
}
