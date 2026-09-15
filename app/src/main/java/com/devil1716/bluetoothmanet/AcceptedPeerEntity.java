package com.devil1716.bluetoothmanet;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * A peer whose messages the user has agreed to receive.
 *
 * Anyone not listed here who messages first lands in Message requests instead
 * of the inbox. Declining does not add a row: it deletes the conversation, so
 * a later message from that peer simply raises a fresh request.
 */
@Entity(tableName = "accepted_peers")
public class AcceptedPeerEntity {
    /** Upper-case mesh node ID. */
    @PrimaryKey @NonNull public String peerId;
    public long acceptedAt;

    public AcceptedPeerEntity(@NonNull String peerId, long acceptedAt) {
        this.peerId = peerId;
        this.acceptedAt = acceptedAt;
    }
}
