package com.devil1716.bluetoothmanet;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ConversationRequestDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) void accept(AcceptedPeerEntity peer);

    @Query("SELECT peerId FROM accepted_peers") List<String> acceptedPeerIds();

    @Query("DELETE FROM accepted_peers WHERE peerId = :peerId") void forget(String peerId);
}
