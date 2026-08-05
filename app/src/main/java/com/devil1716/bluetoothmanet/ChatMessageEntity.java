package com.devil1716.bluetoothmanet;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "messages")
public class ChatMessageEntity {
    @PrimaryKey
    @NonNull
    public String id;
    @NonNull public String conversationId;
    @NonNull public String text;
    @NonNull public String sender;
    public long timestamp;
    @NonNull public MessageStatus status;
    public boolean sentByMe;

    public ChatMessageEntity(@NonNull String id, @NonNull String conversationId,
                             @NonNull String text, @NonNull String sender,
                             long timestamp, @NonNull MessageStatus status,
                             boolean sentByMe) {
        this.id = id;
        this.conversationId = conversationId;
        this.text = text;
        this.sender = sender;
        this.timestamp = timestamp;
        this.status = status;
        this.sentByMe = sentByMe;
    }
}
